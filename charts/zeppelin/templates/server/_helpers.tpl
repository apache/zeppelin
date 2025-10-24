{{- /*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/ -}}

{{/*
Create the name of Zeppelin server.
*/}}
{{- define "zeppelin.server.name" -}}
{{- include "zeppelin.fullname" . }}-server
{{- end -}}

{{/*
Common labels for Zeppelin server.
*/}}
{{- define "zeppelin.server.labels" -}}
{{ include "zeppelin.labels" . }}
app.kubernetes.io/component: zeppelin-server
{{- end }}

{{/*
Selector labels for Zeppelin server.
*/}}
{{- define "zeppelin.server.selectorLabels" -}}
{{ include "zeppelin.selectorLabels" . }}
app.kubernetes.io/component: zeppelin-server
{{- end }}

{{/*
Create the name of the service account for Zeppelin server.
*/}}
{{- define "zeppelin.server.serviceAccountName" -}}
{{- if .Values.server.serviceAccount.create }}
{{- default (include "zeppelin.server.name" .) .Values.server.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.server.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the name of the role for Zeppelin server.
*/}}
{{- define "zeppelin.server.roleName" -}}
{{- include "zeppelin.server.serviceAccountName" . }}
{{- end -}}

{{/*
Create the name of the role binding for Zeppelin server.
*/}}
{{- define "zeppelin.server.roleBindingName" -}}
{{- include "zeppelin.server.serviceAccountName" . }}
{{- end -}}

{{/*
Create the name of the configmap for Zeppelin server.
*/}}
{{- define "zeppelin.server.configMapName" -}}
{{- include "zeppelin.server.name" . }}-conf
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
Create the name of the deployment for Zeppelin server.
*/}}
{{- define "zeppelin.server.deploymentName" -}}
{{- include "zeppelin.server.name" . }}
{{- end -}}

{{/*
Create the name of the service for Zeppelin server.
*/}}
{{- define "zeppelin.server.serviceName" -}}
{{ include "zeppelin.server.name" . }}-svc
{{- end -}}

{{/*
Create the name of the ingress for Zeppelin server.
*/}}
{{- define "zeppelin.server.ingressName" -}}
{{ include "zeppelin.server.name" . }}-ingress
{{- end -}}
