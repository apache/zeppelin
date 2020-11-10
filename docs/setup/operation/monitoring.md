---
layout: page
title: "Apache Zeppelin Monitoring"
description: "This page shows you the monitoring options you have in Apache Zeppelin"
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
# Apache Zeppelin Monitoring

<div id="toc"></div>

## Monitoring Options

Apache Zeppelin is using [Micrometer](https://micrometer.io/) - a vendor-neutral application metrics facade.

### Prometheus Monitoring

[Prometheus](https://prometheus.io/) is the leading monitoring solution for [Kubernetes](https://kubernetes.io/). The Prometheus endpoint can be activated with the configuration property `zeppelin.metric.enable.prometheus`. The metrics are accessible via the unauthenticated endpoint `/metrics`.

## Healthcheck Probe

Apache Zeppelin has two healthcheck related unauthenticated endpoints (`/health/readiness`, `/health/liveness`) that could be used for proxy and/or cloud setups.
