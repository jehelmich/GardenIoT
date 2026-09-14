variable "location" {
  description = "Azure region."
  type        = string
  default     = "westeurope"
}

variable "name" {
  description = "Base name for every resource; a random suffix keeps globally unique names unique."
  type        = string
  default     = "gardeniot"
}

variable "image_tag" {
  description = "Tag of the ghcr.io/jehelmich/gardeniot-* images to run."
  type        = string
  default     = "latest"
}

variable "device_id" {
  description = "Identity of the (one) simulated device registered in the hub."
  type        = string
  default     = "basil"
}

variable "humidity_threshold" {
  description = "Soil humidity in percent below which the controller waters."
  type        = number
  default     = 25
}

variable "iot_hub_sku" {
  description = "F1 is free (8,000 messages/day, one per subscription); B1/S1 are the paid tiers."
  type        = string
  default     = "F1"
}

variable "telemetry_interval_seconds" {
  description = "Seconds between readings. 15 keeps one device under the F1 tier's 8,000 messages a day."
  type        = number
  default     = 15
}
