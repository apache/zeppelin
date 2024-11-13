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
Create the name of Zeppelin interpreter.
*/}}
{{- define "zeppelin.interpreter.name" -}}
{{- include "zeppelin.fullname" . }}-interpreter
{{- end -}}

{{/*
Common labels for Zeppelin server.
*/}}
{{- define "zeppelin.interpreter.labels" -}}
{{ include "zeppelin.labels" . }}
app.kubernetes.io/component: zeppelin-interpreter
{{- end }}

{{/*
Create the name of the service account for Zeppelin interpreter.
*/}}
{{- define "zeppelin.interpreter.serviceAccountName" -}}
{{- if .Values.interpreter.serviceAccount.create }}
{{- default (include "zeppelin.interpreter.name" .) .Values.interpreter.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.interpreter.serviceAccount.name }}
{{- end }}
{{- end -}}

{{/*
Create the name of the role for Zeppelin interpreter.
*/}}
{{- define "zeppelin.interpreter.roleName" -}}
{{- include "zeppelin.interpreter.serviceAccountName" . }}
{{- end -}}

{{/*
Create the name of the role binding for Zeppelin interpreter.
*/}}
{{- define "zeppelin.interpreter.roleBindingName" -}}
{{- include "zeppelin.interpreter.serviceAccountName" . }}
{{- end -}}

{{/*
Create the name of the config map for Zeppelin interpreter.
*/}}
{{- define "zeppelin.interpreter.configMapName" -}}
{{- include "zeppelin.interpreter.name" . }}-conf
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
