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
Create the name of Zeppelin Spark interpreter.
*/}}
{{- define "zeppelin.interpreter.spark.name" -}}
{{- include "zeppelin.interpreter.name" . }}-spark
{{- end -}}

{{/*
Create the name of the config map for Zeppelin Spark interpreter.
*/}}
{{- define "zeppelin.interpreter.spark.configMapName" -}}
{{- include "zeppelin.interpreter.spark.name" . }}-conf
{{- end -}}

{{/*
Create the name of the Spark image to use
*/}}
{{- define "zeppelin.interpreter.spark.image" -}}
{{- $imageRegistry := .Values.interpreter.spark.image.registry | default .Values.global.image.registry | default "docker.io" }}
{{- $imageRepository := .Values.interpreter.spark.image.repository | default "spark" }}
{{- $imageTag := .Values.interpreter.spark.image.tag | default "latest" }}
{{- printf "%s/%s:%s" $imageRegistry $imageRepository $imageTag }}
{{- end -}}
