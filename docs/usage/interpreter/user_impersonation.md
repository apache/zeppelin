---
layout: page
title: "Impersonation"
description: "Set up zeppelin interpreter process as web front end user."
group: usage/interpreter 
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

# Impersonation

User impersonation enables to run zeppelin interpreter process as a web frontend user

## Setup

#### 1. Enable Shiro auth in `conf/shiro.ini`

```
[users]
user1 = password1, role1
user2 = password2, role2
```

#### 2. Enable password-less ssh for the user you want to impersonate (say user1).

```
adduser user1
#ssh-keygen (optional if you don't already have generated ssh-key.
ssh user1@localhost mkdir -p .ssh
cat ~/.ssh/id_rsa.pub | ssh user1@localhost 'cat >> .ssh/authorized_keys'
```

Alternatively instead of password-less, user can override ZEPPELIN_IMPERSONATE_CMD in zeppelin-env.sh

```
export ZEPPELIN_IMPERSONATE_CMD='sudo -H -u ${ZEPPELIN_IMPERSONATE_USER} bash -c '
```


#### 4. Restart zeppelin server.

```
# for OSX, linux
bin/zeppelin-daemon restart

# for windows
bin\zeppelin.cmd
```

#### 5. Configure impersonation for interpreter 

<div class="row">
  <div class="col-md-12" >
      <a data-lightbox="compiler" href="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/user-impersonation.gif">
        <img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/user-impersonation.gif" />
      </a>

  </div>
</div>

<br/>

Go to interpreter setting page, and enable "User Impersonate" in any of the interpreter (in my example its shell interpreter)

#### 6. Test with a simple paragraph

```
%sh
whoami
```

Note that usage of "User Impersonate" option will enable Spark interpreter to use `--proxy-user` option with current user by default. If you want to disable `--proxy-user` option, then refer to `ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER` variable in `conf/zeppelin-env.sh`
