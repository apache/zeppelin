{{/*
Expand the name of the chart.
*/}}
{{- define "zeppelin.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "zeppelin.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "zeppelin.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "zeppelin.labels" -}}
helm.sh/chart: {{ include "zeppelin.chart" . }}
{{ include "zeppelin.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "zeppelin.selectorLabels" -}}
app.kubernetes.io/name: {{ include "zeppelin.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the configmap to use
*/}}
{{- define "zeppelin.configMapName" -}}
{{- include "zeppelin.fullname" . }}-conf
{{- end -}}

{{/*
Create the name of the configmap to use
*/}}
{{- define "zeppelin.envConfigMapName" -}}
{{- include "zeppelin.fullname" . }}-env
{{- end -}}

{{/*
Create the name of the nginx configmap to use
*/}}
{{- define "zeppelin.nginxConfigMapName" -}}
{{- include "zeppelin.fullname" . }}-nginx-conf
{{- end -}}

{{/*
Create the name of the Zeppelin image to use
*/}}
{{- define "zeppelin.server.image" -}}
{{- $imageRegistry := .Values.server.image.registry | default .Values.global.image.registry | default "docker.io" }}
{{- $imageRepository := .Values.server.image.repository | default "apache/zeppelin" }}
{{- $imageTag := .Values.server.image.tag | default "latest" }}
{{- printf "%s/%s:%s" $imageRegistry $imageRepository $imageTag }}
{{- end -}}

{{/*
Create the name of the Zeppelin interpreter image to use
*/}}
{{- define "zeppelin.interpreter.image" -}}
{{- $imageRegistry := .Values.interpreter.image.registry | default .Values.global.image.registry | default "docker.io" }}
{{- $imageRepository := .Values.interpreter.image.repository | default "apache/zeppelin" }}
{{- $imageTag := .Values.interpreter.image.tag | default "latest" }}
{{- printf "%s/%s:%s" $imageRegistry $imageRepository $imageTag }}
{{- end -}}

{{/*
Create the name of the Spark image to use
*/}}
{{- define "zeppelin.spark.image" -}}
{{- $imageRegistry := .Values.spark.image.registry | default .Values.global.image.registry | default "docker.io" }}
{{- $imageRepository := .Values.spark.image.repository | default "spark" }}
{{- $imageTag := .Values.spark.image.tag | default "latest" }}
{{- printf "%s/%s:%s" $imageRegistry $imageRepository $imageTag }}
{{- end -}}
