# zeppelin

![Version: 0.1.0](https://img.shields.io/badge/Version-0.1.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.11.2](https://img.shields.io/badge/AppVersion-0.11.2-informational?style=flat-square)

A Helm chart for deploying Apache Zeppelin on Kubernetes.

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| nameOverride | string | `""` | String to partially override release name. |
| fullnameOverride | string | `""` | String to fully override release name. |
| global.image.registry | string | `"docker.io"` | Global image registry. |
| global.image.pullPolicy | string | `"IfNotPresent"` | Global image pull policy. |
| global.image.pullSecrets | list | `[]` | Global image pull secrets for private image registry. |
| server.image.registry | string | If not set, `global.image.registry` will be used. | Zeppelin server image registry. |
| server.image.repository | string | `"apache/zeppelin"` | Zeppelin server image repository. |
| server.image.tag | string | If not set, the chart appVersion will be used. | Zeppelin image tag. |
| server.image.pullPolicy | string | `"IfNotPresent"` | Zeppelin server image pull policy. |
| server.image.pullSecrets | list | `[]` | Zeppelin server image pull secrets for private image registry. |
| server.conf | object | `{}` | Zeppelin configurations. For detailed information, please refer: https://zeppelin.apache.org/docs/latest/setup/operation/configuration.html. |
| server.labels | object | `{}` | Extra labels for Zeppelin server pods. |
| server.annotations | object | `{}` | Extra annotations for Zeppelin server pods. |
| server.volumes | list | `[]` | Volumes for Zeppelin server pods. |
| server.nodeSelector | object | `{}` | Node selector for Zeppelin server pods. |
| server.affinity | object | `{}` | Affinity for Zeppelin server pods. |
| server.tolerations | list | `[]` | List of node taints to tolerate for Zeppelin server pods. |
| server.priorityClassName | string | `""` | Priority class for Zeppelin server pods. |
| server.podSecurityContext | object | `{}` | Security context for Zeppelin server pods. |
| server.env | list | `[{"name":"ZEPPELIN_HOME","value":"/opt/zeppelin"},{"name":"SPARK_HOME","value":"/opt/spark"},{"name":"SERVICE_DOMAIN","value":"local.zeppelin-project.org:8080"},{"name":"ZEPPELIN_PORT","value":"8080"},{"name":"ZEPPELIN_SERVER_RPC_PORTRANGE","value":"12320:12320"},{"name":"ZEPPELIN_K8S_TEMPLATE_DIR","value":"/opt/zeppelin/k8s"}]` | Environment variables for Zeppelin server containers. |
| server.envFrom | list | `[]` | Environment variable sources for Zeppelin server containers. |
| server.volumeMounts | list | `[]` | Volume mounts for Zeppelin server containers. |
| server.resources | object | `{}` | Resource requests and limits for Zeppelin server containers. |
| server.securityContext | object | `{"runAsGroup":0,"runAsNonRoot":true,"runAsUser":1000}` | Security context for Zeppelin server containers. |
| server.serviceAccount.create | bool | `true` | Specifies whether a service account should be created for the Zeppelin server. |
| server.serviceAccount.name | string | `""` | Optional name for the Zeppelin server service account. |
| server.serviceAccount.annotations | object | `{}` | Extra annotations for the Zeppelin server service account. |
| server.service.type | string | `"ClusterIP"` | Service type for Zeppelin server. |
| server.service.port | int | `8080` | Service port for Zeppelin server. |
| server.ingress.enable | bool | `false` | Enable ingress for Zeppelin server. |
| server.ingress.className | string | `""` | Ingress class name for Zeppelin server ingress. |
| server.ingress.annotations | object | `{}` | Annotations for the Zeppelin server ingress. |
| server.ingress.hosts | list | `[{"host":"local.zeppelin-project.org","paths":[{"path":"/","pathType":"ImplementationSpecific"}]}]` | Hosts for Zeppelin server ingress. |
| server.ingress.tls | list | `[]` | TLS configuration for Zeppelin server ingress. |
| interpreter.image.registry | string | If not set, `global.image.registry` will be used. | Zeppelin interpreter image registry. |
| interpreter.image.repository | string | `"apache/zeppelin-interpreter"` | Zeppelin interpreter image repository. |
| interpreter.image.tag | string | If not set, the chart appVersion will be used. | Zeppelin interpreter image tag. |
| interpreter.image.pullPolicy | string | `"IfNotPresent"` | Zeppelin interpreter image pull policy. |
| interpreter.image.pullSecrets | list | `[]` | Zeppelin interpreter image pull secrets for private image registry. |
| interpreter.spark.image.registry | string | If not set, `global.image.registry` will be used. | Spark image registry. |
| interpreter.spark.image.repository | string | `"spark"` | Spark image repository. |
| interpreter.spark.image.tag | string | `"3.5.3"` | Spark image tag. |
| interpreter.spark.conf | object | `{"spark.kubernetes.driver.podTemplateFile":"file:///opt/spark/conf/driver-pod-template.yaml","spark.kubernetes.executor.podTemplateFile":"file:///opt/spark/conf/executor-pod-template.yaml"}` | Spark configurations. |
| interpreter.labels | object | `{}` | Extra labels for Zeppelin interpreter pods. |
| interpreter.annotations | object | `{}` | Extra annotations for Zeppelin interpreter pods. |
| interpreter.volumes | list | `[]` | Volumes for Zeppelin interpreter pods. |
| interpreter.nodeSelector | object | `{}` | Node selector for Zeppelin interpreter pods. |
| interpreter.affinity | object | `{}` | Affinity for Zeppelin interpreter pods. |
| interpreter.tolerations | list | `[]` | List of node taints to tolerate for Zeppelin interpreter pods. |
| interpreter.priorityClassName | string | `""` | Priority class for Zeppelin interpreter pods. |
| interpreter.podSecurityContext | object | `{}` | Security context for Zeppelin interpreter pods. |
| interpreter.env | list | `[{"name":"ZEPPELIN_HOME","value":"/opt/zeppelin"},{"name":"SPARK_HOME","value":"/opt/spark"},{"name":"SPARK_CONF_DIR","value":"/opt/spark/conf"}]` | Environment variables for Zeppelin interpreter containers. |
| interpreter.envFrom | list | `[]` | Environment variable sources for Zeppelin interpreter containers. |
| interpreter.volumeMounts | list | `[]` | Volume mounts for Zeppelin interpreter containers. |
| interpreter.resources | object | `{}` | Resource requests and limits for Zeppelin interpreter containers. |
| interpreter.securityContext | object | `{"runAsGroup":0,"runAsNonRoot":true,"runAsUser":1000}` | Security context for Zeppelin interpreter containers. |
| interpreter.serviceAccount.serviceAccount | string | `nil` |  |
| interpreter.serviceAccount.create | bool | `true` | Specifies whether a service account should be created for the Zeppelin interpreter. |
| interpreter.serviceAccount.name | string | `""` | Optional name for the Zeppelin interpreter service account. |
| interpreter.serviceAccount.annotations | object | `{}` | Extra annotations for the Zeppelin interpreter service account. |

## Maintainers

| Name | Email | Url |
| ---- | ------ | --- |
| ChenYi015 | <github@chenyicn.net> |  |
