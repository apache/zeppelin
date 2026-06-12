<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to you under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Security Policy

## Reporting a Vulnerability

Please report suspected security vulnerabilities in Apache Zeppelin privately
to the Apache Security Team at <security@apache.org>, following the ASF process
at <https://www.apache.org/security/>. Do not open public GitHub issues or pull
requests for security reports.

## Threat Model

Apache Zeppelin's security threat model — what is in and out of scope, the
security properties the project provides and disclaims, the adversary model,
the configuration knobs whose defaults change the security envelope, and how
findings are triaged — is documented in [THREAT_MODEL.md](./THREAT_MODEL.md).

Note that Apache Zeppelin executes user-supplied notebook code through its
interpreters by design; that capability is the product's primary function, and
the threat model is about *who may reach it and with what isolation*. See
`THREAT_MODEL.md` §3, §9, and §11a.

## Operational Security Setup

Operator-facing security configuration — authentication via Apache Shiro,
notebook authorization, interpreter user impersonation, and HTTPS / HTTP
security headers — is documented on the project website:
<https://zeppelin.apache.org/docs/latest/setup/security/>
