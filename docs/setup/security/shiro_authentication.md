---
layout: page
title: "Apache Shiro Authentication for Apache Zeppelin"
description: "Apache Shiro is a powerful and easy-to-use Java security framework that performs authentication, authorization, cryptography, and session management. This document explains step by step how Shiro can be used for Zeppelin notebook authentication."
group: setup/security
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

# Apache Shiro authentication for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Shiro](http://shiro.apache.org/) is a powerful and easy-to-use Java security framework that performs authentication, authorization, cryptography, and session management. In this documentation, we will explain step by step how Shiro works for Zeppelin notebook authentication.

When you connect to Apache Zeppelin, you will be asked to enter your credentials. Once you logged in, then you have access to all notes including other user's notes.

## Security Setup
You can setup **Zeppelin notebook authentication** in some simple steps.

### 1. Enable Shiro
By default in `conf`, you will find `shiro.ini.template`, this file is used as an example and it is strongly recommended
to create a `shiro.ini` file by doing the following command line

```bash
cp conf/shiro.ini.template conf/shiro.ini
```

For the further information about  `shiro.ini` file format, please refer to [Shiro Configuration](http://shiro.apache.org/configuration.html#Configuration-INISections).

### 2. Secure the Websocket channel
Set to property **zeppelin.anonymous.allowed** to **false** in `conf/zeppelin-site.xml`. If you don't have this file yet, just copy `conf/zeppelin-site.xml.template` to `conf/zeppelin-site.xml`.

### 3. Start Zeppelin

```
bin/zeppelin-daemon.sh start (or restart)
```

Then you can browse Zeppelin at [http://localhost:8080](http://localhost:8080).

### 4. Login
Finally, you can login using one of the below **username/password** combinations.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/zeppelin-login.png"></center>

```
[users]

admin = password1, admin
user1 = password2, role1, role2
user2 = password3, role3
user3 = password4, role2
```
You can set the roles for each users next to the password.

## Groups and permissions (optional)
In case you want to leverage user groups and permissions, use one of the following configuration for LDAP or AD under `[main]` segment in `shiro.ini`.

```
activeDirectoryRealm = org.apache.zeppelin.realm.ActiveDirectoryGroupRealm
activeDirectoryRealm.systemUsername = userNameA
activeDirectoryRealm.systemPassword = passwordA
activeDirectoryRealm.searchBase = CN=Users,DC=SOME_GROUP,DC=COMPANY,DC=COM
activeDirectoryRealm.url = ldap://ldap.test.com:389
activeDirectoryRealm.groupRolesMap = "CN=aGroupName,OU=groups,DC=SOME_GROUP,DC=COMPANY,DC=COM":"group1"
activeDirectoryRealm.authorizationCachingEnabled = false
activeDirectoryRealm.principalSuffix = @corp.company.net

ldapRealm = org.apache.zeppelin.server.LdapGroupRealm
# search base for ldap groups (only relevant for LdapGroupRealm):
ldapRealm.contextFactory.environment[ldap.searchBase] = dc=COMPANY,dc=COM
ldapRealm.contextFactory.url = ldap://ldap.test.com:389
ldapRealm.userDnTemplate = uid={0},ou=Users,dc=COMPANY,dc=COM
ldapRealm.contextFactory.authenticationMechanism = simple
```

also define roles/groups that you want to have in system, like below;

```
[roles]
admin = *
hr = *
finance = *
group1 = *
```

## Configure Realm (optional)
Realms are responsible for authentication and authorization in Apache Zeppelin. By default, Apache Zeppelin uses [IniRealm](https://shiro.apache.org/static/latest/apidocs/org/apache/shiro/realm/text/IniRealm.html) (users and groups are configurable in `conf/shiro.ini` file under `[user]` and `[group]` section). You can also leverage Shiro Realms like [JndiLdapRealm](https://shiro.apache.org/static/latest/apidocs/org/apache/shiro/realm/ldap/JndiLdapRealm.html), [JdbcRealm](https://shiro.apache.org/static/latest/apidocs/org/apache/shiro/realm/jdbc/JdbcRealm.html) or create [our own](https://shiro.apache.org/static/latest/apidocs/org/apache/shiro/realm/AuthorizingRealm.html).
To learn more about Apache Shiro Realm, please check [this documentation](http://shiro.apache.org/realm.html).

We also provide community custom Realms.

### Active Directory

```
activeDirectoryRealm = org.apache.zeppelin.realm.ActiveDirectoryGroupRealm
activeDirectoryRealm.systemUsername = userNameA
activeDirectoryRealm.systemPassword = passwordA
activeDirectoryRealm.hadoopSecurityCredentialPath = jceks://file/user/zeppelin/conf/zeppelin.jceks
activeDirectoryRealm.searchBase = CN=Users,DC=SOME_GROUP,DC=COMPANY,DC=COM
activeDirectoryRealm.url = ldap://ldap.test.com:389
activeDirectoryRealm.groupRolesMap = "CN=aGroupName,OU=groups,DC=SOME_GROUP,DC=COMPANY,DC=COM":"group1"
activeDirectoryRealm.authorizationCachingEnabled = false
activeDirectoryRealm.principalSuffix = @corp.company.net
```


Also instead of specifying systemPassword in clear text in shiro.ini administrator can choose to specify the same in "hadoop credential".
Create a keystore file using the hadoop credential commandline, for this the hadoop commons should be in the classpath
`hadoop credential create activeDirectoryRealm.systempassword -provider jceks://file/user/zeppelin/conf/zeppelin.jceks`

Change the following values in the Shiro.ini file, and uncomment the line:
`activeDirectoryRealm.hadoopSecurityCredentialPath = jceks://file/user/zeppelin/conf/zeppelin.jceks`

### LDAP

Two options exist for configuring an LDAP Realm. The simpler to use is the LdapGroupRealm. How ever it has limited
flexibility with mapping of ldap groups to users and for authorization for user groups. A sample configuration file for
this realm is given below.

```
ldapRealm = org.apache.zeppelin.realm.LdapGroupRealm
# search base for ldap groups (only relevant for LdapGroupRealm):
ldapRealm.contextFactory.environment[ldap.searchBase] = dc=COMPANY,dc=COM
ldapRealm.contextFactory.url = ldap://ldap.test.com:389
ldapRealm.userDnTemplate = uid={0},ou=Users,dc=COMPANY,dc=COM
ldapRealm.contextFactory.authenticationMechanism = simple
```

The other more flexible option is to use the LdapRealm. It allows for mapping of ldapgroups to roles and also allows for
 role/group based authentication into the zeppelin server. Sample configuration for this realm is given below.

```
[main]
ldapRealm=org.apache.zeppelin.realm.LdapRealm

ldapRealm.contextFactory.authenticationMechanism=simple
ldapRealm.contextFactory.url=ldap://localhost:33389
ldapRealm.userDnTemplate=uid={0},ou=people,dc=hadoop,dc=apache,dc=org
# Ability to set ldap paging Size if needed default is 100
ldapRealm.pagingSize = 200
ldapRealm.authorizationEnabled=true
ldapRealm.contextFactory.systemAuthenticationMechanism=simple
ldapRealm.searchBase=dc=hadoop,dc=apache,dc=org
ldapRealm.userSearchBase = dc=hadoop,dc=apache,dc=org
ldapRealm.groupSearchBase = ou=groups,dc=hadoop,dc=apache,dc=org
ldapRealm.groupObjectClass=groupofnames
# Allow userSearchAttribute to be customized
ldapRealm.userSearchAttributeName = sAMAccountName
ldapRealm.memberAttribute=member
# force usernames returned from ldap to lowercase useful for AD
ldapRealm.userLowerCase = true
# ability set searchScopes subtree (default), one, base
ldapRealm.userSearchScope = subtree;
ldapRealm.groupSearchScope = subtree;
ldapRealm.memberAttributeValueTemplate=cn={0},ou=people,dc=hadoop,dc=apache,dc=org
ldapRealm.contextFactory.systemUsername=uid=guest,ou=people,dc=hadoop,dc=apache,dc=org
ldapRealm.contextFactory.systemPassword=S{ALIAS=ldcSystemPassword}
# enable support for nested groups using the LDAP_MATCHING_RULE_IN_CHAIN operator
ldapRealm.groupSearchEnableMatchingRuleInChain = true
# optional mapping from physical groups to logical application roles
ldapRealm.rolesByGroup = LDN_USERS: user_role, NYK_USERS: user_role, HKG_USERS: user_role, GLOBAL_ADMIN: admin_role
# optional list of roles that are allowed to authenticate. Incase not present all groups are allowed to authenticate (login).
# This changes nothing for url specific permissions that will continue to work as specified in [urls].
ldapRealm.allowedRolesForAuthentication = admin_role,user_role
ldapRealm.permissionsByRole= user_role = *:ToDoItemsJdo:*:*, *:ToDoItem:*:*; admin_role = *
securityManager.sessionManager = $sessionManager
securityManager.realms = $ldapRealm
```

### PAM
[PAM](https://en.wikipedia.org/wiki/Pluggable_authentication_module) authentication support allows the reuse of existing authentication
moduls on the host where Zeppelin is running. On a typical system modules are configured per service for example sshd, passwd, etc. under `/etc/pam.d/`. You can
either reuse one of these services or create your own for Zeppelin. Activiting PAM authentication requires two parameters:
 1. realm: The Shiro realm being used
 2. service: The service configured under `/etc/pam.d/` to be used. The name here needs to be the same as the file name under `/etc/pam.d/`

```
[main]
 pamRealm=org.apache.zeppelin.realm.PamRealm
 pamRealm.service=sshd
```

### ZeppelinHub
[ZeppelinHub](https://www.zeppelinhub.com) is a service that synchronize your Apache Zeppelin notebooks and enables you to collaborate easily.

To enable login with your ZeppelinHub credential, apply the following change in `conf/shiro.ini` under `[main]` section.

```
### A sample for configuring ZeppelinHub Realm
zeppelinHubRealm = org.apache.zeppelin.realm.ZeppelinHubRealm
## Url of ZeppelinHub
zeppelinHubRealm.zeppelinhubUrl = https://www.zeppelinhub.com
securityManager.realms = $zeppelinHubRealm
```

> Note: ZeppelinHub is not releated to Apache Zeppelin project.

## Secure Cookie for Zeppelin Sessions (optional)
Zeppelin can be configured to set `HttpOnly` flag in the session cookie. With this configuration, Zeppelin cookies can 
not be accessed via client side scripts thus preventing majority of Cross-site scripting (XSS) attacks.

To enable secure cookie support via Shiro, add the following lines in `conf/shiro.ini` under `[main]` section, after
defining a `sessionManager`.

```
cookie = org.apache.shiro.web.servlet.SimpleCookie
cookie.name = JSESSIONID
cookie.secure = true
cookie.httpOnly = true
sessionManager.sessionIdCookie = $cookie
```

## Secure your Zeppelin information (optional)
By default, anyone who defined in `[users]` can share **Interpreter Setting**, **Credential** and **Configuration** information in Apache Zeppelin.
Sometimes you might want to hide these information for your use case.
Since Shiro provides **url-based security**, you can hide the information by commenting or uncommenting these below lines in `conf/shiro.ini`.

```
[urls]

/api/interpreter/** = authc, roles[admin]
/api/configurations/** = authc, roles[admin]
/api/credential/** = authc, roles[admin]
```

In this case, only who have `admin` role can see **Interpreter Setting**, **Credential** and **Configuration** information.
If you want to grant this permission to other users, you can change **roles[ ]** as you defined at `[users]` section.

### Apply multiple roles in Shiro configuration
By default, Shiro will allow access to a URL if only user is part of "**all the roles**" defined like this:
```
[urls]

/api/interpreter/** = authc, roles[admin, role1]
```

If there is a need that user with "**any of the defined roles**" should be allowed, then following Shiro configuration can be used:
```
[main]
anyofroles = org.apache.zeppelin.utils.AnyOfRolesAuthorizationFilter

[urls]

/api/interpreter/** = authc, anyofroles[admin, role1]
/api/configurations/** = authc, roles[admin]
/api/credential/** = authc, roles[admin]
```

<br/>

> **NOTE :** All of the above configurations are defined in the `conf/shiro.ini` file.


## Other authentication methods

- [HTTP Basic Authentication using NGINX](./authentication_nginx.html)
