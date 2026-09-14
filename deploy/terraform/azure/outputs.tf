output "resource_group" {
  value = azurerm_resource_group.this.name
}

output "iot_hub_name" {
  value = azurerm_iothub.this.name
}

output "iot_hub_hostname" {
  value = azurerm_iothub.this.hostname
}

output "controller_app" {
  value = azurerm_container_app.controller.name
}

output "device_app" {
  value = azurerm_container_app.device.name
}

output "watch_logs" {
  description = "Follow the controller from a terminal."
  value       = "az containerapp logs show --name ${azurerm_container_app.controller.name} --resource-group ${azurerm_resource_group.this.name} --follow"
}
