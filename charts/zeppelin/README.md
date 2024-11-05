# zeppelin

![Version: 0.1.0](https://img.shields.io/badge/Version-0.1.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.11.2](https://img.shields.io/badge/AppVersion-0.11.2-informational?style=flat-square)

A Helm chart for deploying Apache Zeppelin on Kubernetes.

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| nameOverride | string | `""` | String to partially override release name. |
| fullnameOverride | string | `""` | String to fully override release name. |
| global.image.registry | string | `"docker.io"` | Image registry. |
| global.image.pullPolicy | string | `"IfNotPresent"` | Image pull policy. |
| global.image.pullSecrets | list | `[]` | Image pull secrets for private image registry. |
| server.image.registry | string | If not set, `global.image.registry` will be used. | Zeppelin server image registry. |
| server.image.repository | string | `"apache/zeppelin"` | Zeppelin server image repository. |
| server.image.tag | string | If not set, the chart appVersion will be used. | Zeppelin image tag. |
| server.serviceAccount.create | bool | `true` | Specifies whether a service account should be created for the Zeppelin server. |
| server.serviceAccount.name | string | `""` | Optional name for the Zeppelin server service account. |
| server.serviceAccount.annotations | object | `{}` | Extra annotations for the Zeppelin server service account. |
| interpreter.image.registry | string | If not set, `global.image.registry` will be used. | Zeppelin interpreter image registry. |
| interpreter.image.repository | string | `"apache/zeppelin-interpreter"` | Zeppelin interpreter image repository. |
| interpreter.image.tag | string | If not set, the chart appVersion will be used. | Zeppelin interpreter image tag. |
| interpreter.serviceAccount.serviceAccount | string | `nil` |  |
| interpreter.serviceAccount.create | bool | `true` | Specifies whether a service account should be created for the Zeppelin interpreter. |
| interpreter.serviceAccount.name | string | `""` | Optional name for the Zeppelin interpreter service account. |
| interpreter.serviceAccount.annotations | object | `{}` | Extra annotations for the Zeppelin interpreter service account. |
| spark.image.registry | string | If not set, `global.image.registry` will be used. | Spark image registry. |
| spark.image.repository | string | `"spark"` | Spark image repository. |
| spark.image.tag | string | `"3.5.3"` | Spark image tag. |
| replicas | int | `1` |  |
| labels | object | `{}` | Extra labels for controller pods. |
| annotations | object | `{}` | Extra annotations for controller pods. |
| volumes | list | `[]` | Volumes for controller pods. |
| nodeSelector | object | `{}` | Node selector for controller pods. |
| affinity | object | `{}` | Affinity for controller pods. |
| tolerations | list | `[]` | List of node taints to tolerate for controller pods. |
| priorityClassName | string | `""` | Priority class for controller pods. |
| podSecurityContext | object | `{}` | Security context for controller pods. |
| env | list | `[]` | Environment variables for controller containers. |
| envFrom | list | `[]` | Environment variable sources for controller containers. |
| volumeMounts | list | `[]` | Volume mounts for controller containers. |
| resources | object | `{}` | Pod resource requests and limits for controller containers. |
| securityContext | object | `{}` | Security context for controller containers. |
| service.type | string | `"ClusterIP"` |  |

## Maintainers

| Name | Email | Url |
| ---- | ------ | --- |
| ChenYi015 | <github@chenyicn.net> |  |
