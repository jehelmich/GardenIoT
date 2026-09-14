# GardenIoT on Azure: IoT Hub as the transport, both applications on Container Apps.
#
# The controller authenticates to the hub with a user-assigned managed identity and Entra ID
# roles — no key leaves Azure. The simulated device, like a real one, authenticates with its
# own device credential; here the symmetric key of a device identity created in the hub.

resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}

locals {
  name = "${var.name}-${random_string.suffix.result}"
  tags = {
    project = "GardenIoT"
    source  = "https://github.com/jehelmich/GardenIoT"
  }
}

resource "azurerm_resource_group" "this" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = local.tags
}

# --- IoT Hub ------------------------------------------------------------------------------------

resource "azurerm_iothub" "this" {
  name                = "iot-${local.name}"
  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  tags                = local.tags

  sku {
    name     = var.iot_hub_sku
    capacity = 1
  }

  # Entra ID for the data plane; keys stay on for the device.
  local_authentication_enabled = true
}

# Device identities are a data-plane concept the azurerm provider does not manage, so the
# CLI creates it (idempotently) and reads back its connection string for the device app.
resource "terraform_data" "device_identity" {
  triggers_replace = [azurerm_iothub.this.id, var.device_id]

  provisioner "local-exec" {
    command = <<-EOT
      az iot hub device-identity show --hub-name ${azurerm_iothub.this.name} --device-id ${var.device_id} >/dev/null 2>&1 ||
      az iot hub device-identity create --hub-name ${azurerm_iothub.this.name} --device-id ${var.device_id} --output none
    EOT
  }
}

data "external" "device_connection_string" {
  depends_on = [terraform_data.device_identity]
  program = ["sh", "-c", <<-EOT
    az iot hub device-identity connection-string show \
      --hub-name ${azurerm_iothub.this.name} --device-id ${var.device_id} --output json
  EOT
  ]
}

# --- Identity and roles for the controller ------------------------------------------------------

resource "azurerm_user_assigned_identity" "controller" {
  name                = "id-${local.name}-controller"
  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  tags                = local.tags
}

# Invoke direct methods.
resource "azurerm_role_assignment" "controller_hub" {
  scope                = azurerm_iothub.this.id
  role_definition_name = "IoT Hub Data Contributor"
  principal_id         = azurerm_user_assigned_identity.controller.principal_id
}

# Read the built-in Event Hub-compatible endpoint.
resource "azurerm_role_assignment" "controller_events" {
  scope                = azurerm_iothub.this.id
  role_definition_name = "Azure Event Hubs Data Receiver"
  principal_id         = azurerm_user_assigned_identity.controller.principal_id
}

# --- Container Apps -----------------------------------------------------------------------------

resource "azurerm_log_analytics_workspace" "this" {
  name                = "log-${local.name}"
  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_container_app_environment" "this" {
  name                       = "cae-${local.name}"
  resource_group_name        = azurerm_resource_group.this.name
  location                   = azurerm_resource_group.this.location
  log_analytics_workspace_id = azurerm_log_analytics_workspace.this.id
  tags                       = local.tags
}

resource "azurerm_container_app" "controller" {
  name                         = "ca-${local.name}-controller"
  container_app_environment_id = azurerm_container_app_environment.this.id
  resource_group_name          = azurerm_resource_group.this.name
  revision_mode                = "Single"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.controller.id]
  }

  template {
    min_replicas = 1
    max_replicas = 1 # every replica would command watering; see the Helm values for the same note

    container {
      name   = "controller"
      image  = "ghcr.io/jehelmich/gardeniot-controller:${var.image_tag}"
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "TRANSPORT"
        value = "azure"
      }
      # DefaultAzureCredential picks the user-assigned identity by client id.
      env {
        name  = "AZURE_CLIENT_ID"
        value = azurerm_user_assigned_identity.controller.client_id
      }
      env {
        name  = "AZURE_IOTHUB_HOSTNAME"
        value = azurerm_iothub.this.hostname
      }
      env {
        name  = "AZURE_EVENTHUB_NAMESPACE"
        value = "${azurerm_iothub.this.event_hub_events_namespace}.servicebus.windows.net"
      }
      env {
        name  = "AZURE_EVENTHUB_NAME"
        value = azurerm_iothub.this.event_hub_events_path
      }
      env {
        name  = "HUMIDITY_THRESHOLD"
        value = tostring(var.humidity_threshold)
      }

      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/healthz"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/readyz"
      }
    }
  }

  depends_on = [azurerm_role_assignment.controller_hub, azurerm_role_assignment.controller_events]
}

resource "azurerm_container_app" "device" {
  name                         = "ca-${local.name}-device"
  container_app_environment_id = azurerm_container_app_environment.this.id
  resource_group_name          = azurerm_resource_group.this.name
  revision_mode                = "Single"
  tags                         = local.tags

  secret {
    name  = "device-connection-string"
    value = data.external.device_connection_string.result.connectionString
  }

  template {
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "device"
      image  = "ghcr.io/jehelmich/gardeniot-simulated-device:${var.image_tag}"
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "TRANSPORT"
        value = "azure"
      }
      env {
        name        = "IOTHUB_DEVICE_CONNECTION_STRING"
        secret_name = "device-connection-string"
      }
      env {
        name  = "TELEMETRY_INTERVAL_SECONDS"
        value = tostring(var.telemetry_interval_seconds)
      }

      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/healthz"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/readyz"
      }
    }
  }
}
