---
layout: page
title: "Manage Zeppelin with systemd"
description: "Zeppelin and systemd"
group: setup/basics
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
{% include JB/setup %}

## Zeppelin and systemd

### Unit file installation / deinstallation

This script accepts two parameters: `enable` and `disable` which, as you might have guessed, enable or disable the Zeppelin systemd unit file. Go ahead and type:

```
# ./bin/zeppelin-systemd-service.sh enable
```

This command activates the Zeppelin systemd unit file on your system.

If you wish to roll back and remove this unit file from said system, simply type:
```
# ./bin/zeppelin-systemd-service.sh disable
```

### Manage Zeppelin using systemd commands

To start Zeppelin using systemd;
```
# systemctl start zeppelin
```

To stop Zeppelin using systemd:
```
# systemctl stop zeppelin
```

To check the service health:
```
# systemctl status zeppelin"
```
