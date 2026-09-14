{{- define "gardeniot.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "gardeniot.fullname" -}}
{{- printf "%s" .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "gardeniot.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "gardeniot.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "gardeniot.selectorLabels" -}}
app.kubernetes.io/name: {{ include "gardeniot.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "gardeniot.image" -}}
{{ .root.Values.image.registry }}/{{ .root.Values.image.prefix }}-{{ .module }}:{{ .root.Values.image.tag | default .root.Chart.AppVersion }}
{{- end -}}

{{- define "gardeniot.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}{{ include "gardeniot.fullname" . }}{{- else -}}default{{- end -}}
{{- end -}}

{{/* Environment shared by the controller and the devices: transport selection and its settings. */}}
{{- define "gardeniot.transportEnv" -}}
- name: TRANSPORT
  value: {{ .Values.transport | quote }}
- name: METRICS_PORT
  value: "8080"
{{- if eq .Values.transport "mqtt" }}
- name: MQTT_HOST
  value: {{ include "gardeniot.fullname" . }}-mosquitto
- name: MQTT_PORT
  value: "1883"
{{- end }}
{{- end -}}

{{- define "gardeniot.azureServiceEnv" -}}
{{- with .Values.azure }}
{{- if .hubHostName }}
- name: AZURE_IOTHUB_HOSTNAME
  value: {{ .hubHostName | quote }}
{{- end }}
{{- if .eventHubNamespace }}
- name: AZURE_EVENTHUB_NAMESPACE
  value: {{ .eventHubNamespace | quote }}
{{- end }}
{{- if .eventHubName }}
- name: AZURE_EVENTHUB_NAME
  value: {{ .eventHubName | quote }}
{{- end }}
- name: EVENTHUB_CONSUMER_GROUP
  value: {{ .consumerGroup | quote }}
{{- if .serviceConnectionStringSecret }}
- name: IOTHUB_SERVICE_CONNECTION_STRING
  valueFrom:
    secretKeyRef:
      name: {{ .serviceConnectionStringSecret }}
      key: connectionString
{{- end }}
{{- if .eventHubConnectionStringSecret }}
- name: EVENTHUB_COMPATIBLE_CONNECTION_STRING
  valueFrom:
    secretKeyRef:
      name: {{ .eventHubConnectionStringSecret }}
      key: connectionString
{{- end }}
{{- end }}
{{- end -}}

{{- define "gardeniot.probes" -}}
livenessProbe:
  httpGet:
    path: /healthz
    port: http
  initialDelaySeconds: 10
  periodSeconds: 15
readinessProbe:
  httpGet:
    path: /readyz
    port: http
  initialDelaySeconds: 5
  periodSeconds: 5
{{- end -}}
