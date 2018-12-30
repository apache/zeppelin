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

```bash
bin/zeppelin-daemon.sh start #(or restart)
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

**Note**: When using any of the below realms the default 
      password-based (IniRealm) authentication needs to be disabled.

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

Also instead of specifying systemPassword in clear text in `shiro.ini` administrator can choose to specify the same in "hadoop credential". 
Create a keystore file using the hadoop credential command line:
``` 
hadoop credential create ldapRealm.systemPassword -provider jceks://file/user/zeppelin/conf/zeppelin.jceks
```

Add the following line in the `shiro.ini` file:
``` 
ldapRealm.hadoopSecurityCredentialPath = jceks://file/user/zeppelin/conf/zeppelin.jceks
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

### Knox SSO
[KnoxSSO](https://knox.apache.org/books/knox-0-13-0/dev-guide.html#KnoxSSO+Integration) provides an abstraction for integrating any number of authentication systems and SSO solutions and enables participating web applications to scale to those solutions more easily. Without the token exchange capabilities offered by KnoxSSO each component UI would need to integrate with each desired solution on its own.

To enable this, apply the following change in `conf/shiro.ini` under `[main]` section.

```
### A sample for configuring Knox JWT Realm
knoxJwtRealm = org.apache.zeppelin.realm.jwt.KnoxJwtRealm
## Domain of Knox SSO
knoxJwtRealm.providerUrl = https://domain.example.com/
## Url for login
knoxJwtRealm.login = gateway/knoxsso/knoxauth/login.html
## Url for logout
knoxJwtRealm.logout = gateway/knoxssout/api/v1/webssout
knoxJwtRealm.redirectParam = originalUrl
knoxJwtRealm.cookieName = hadoop-jwt
knoxJwtRealm.publicKeyPath = /etc/zeppelin/conf/knox-sso.pem
knoxJwtRealm.groupPrincipalMapping = group.principal.mapping
knoxJwtRealm.principalMapping = principal.mapping
# This is required if KNOX SSO is enabled, to check if "knoxJwtRealm.cookieName" cookie was expired/deleted.  
authc = org.apache.zeppelin.realm.jwt.KnoxAuthenticationFilter
```

### OpenID Connect
OpenID Connect is a protocol used by many auth providers like google or facebook but also with some external tools like keycloak. Below you'll find a `conf/shiro.ini` related to `keycloak` integration.

To enable this, make sure you've built zeppelin with oidc profile and then apply the following change in `conf/shiro.ini` under `[main]` section.

You'll have to fill the different parameters below :
 - CLIENT_ID: for instance zeppelin
 - CLIENT_SECRET: for instance 4bde2ee4-80bb-4b72-9369-53940201d554. In order to get a secret, you'll need to define the client with `confidential` access-type.
 - REALM: the realm defined in keycloak. By default, it's `master`.
 - KEYCLOAK_BASE_URI: base uri of keycloak. For instance `http://localhost:8080/auth`. This attribute is then concataned with **"/realms/"+realm+"/.well-known/openid-configuration"**
 - LOGOUT_URL: url to logout from keycloak. For instance `http://localhost:8080/auth/realms/master/protocol/openid-connect/logout`
 - ZEPPELIN_CALLBACK_URL: url redirected to after successful login. For instance `http://localhost:8090/api/callback`
 - LOGOUT_REDIRECT_URI: For instance go back to zeppelin. `http://localhost:8090`. 

```
[main]
roleAdminAuthGenerator = org.pac4j.core.authorization.generator.FromAttributesAuthorizationGenerator
roleAdminAuthGenerator.roleAttributes = ROLE_ADMIN

oidcConfig = org.pac4j.oidc.config.KeycloakOidcConfiguration
oidcConfig.clientId = <CLIENT_ID>
oidcConfig.secret = <CLIENT_SECRET>
oidcConfig.realm = <REALM>
oidcConfig.baseUri = <KEYCLOAK_BASE_URI>
oidcConfig.useNonce = true
oidcConfig.clientAuthenticationMethodAsString = client_secret_basic

oidcConfig.logoutUrl = <LOGOUT_URL>

keycloakOidcClient = org.pac4j.oidc.client.KeycloakOidcClient
keycloakOidcClient.configuration = $oidcConfig
keycloakOidcClient.authorizationGenerator = $roleAdminAuthGenerator

clients = org.pac4j.core.client.Clients
clients.callbackUrl = <ZEPPELIN_CALLBACK_URL>
clients.clients = $keycloakOidcClient

requireRoleAdmin = org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
requireRoleAdmin.elements = ROLE_ADMIN

config = org.pac4j.core.config.Config
config.clients = $clients
config.authorizers = admin:$requireRoleAdmin

pac4jRealm = io.buji.pac4j.realm.Pac4jRealm
pac4jRealm.principalNameAttribute = preferred_username
pac4jSubjectFactory = io.buji.pac4j.subject.Pac4jSubjectFactory
securityManager.subjectFactory = $pac4jSubjectFactory

oidcSecurityFilter = io.buji.pac4j.filter.SecurityFilter
oidcSecurityFilter.config = $config
oidcSecurityFilter.clients = keycloakOidcClient

sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager

callbackFilter = io.buji.pac4j.filter.CallbackFilter
callbackFilter.defaultUrl = /
callbackFilter.config = $config

logoutFilter = io.buji.pac4j.filter.LogoutFilter
logoutFilter.defaultUrl = <LOGOUT_REDIRECT_URI>

logoutFilter.localLogout = true
logoutFilter.centralLogout = true
logoutFilter.config = $config

### If caching of user is required then uncomment below lines
#cacheManager = org.apache.shiro.cache.MemoryConstrainedCacheManager
#securityManager.cacheManager = $cacheManager

securityManager.sessionManager = $sessionManager
# 86,400,000 milliseconds = 24 hour
securityManager.sessionManager.globalSessionTimeout = 86400000

[roles]

[urls]
# This section is used for url-based security.
# You can secure interpreter, configuration and credential information by urls. Comment or uncomment the below urls that you want to hide.
# anon means the access is anonymous.
# authc means Form based Auth Security
/api/version = anon
/api/callback = callbackFilter
/api/login/logout = logoutFilter
/** = oidcSecurityFilter
```

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

### Apply multiple roles or user in Shiro configuration
If there is a need that user with "**any of the defined roles or user itself**" should be allowed, then following Shiro configuration can be used:

```
[main]
anyofrolesuser = org.apache.zeppelin.utils.AnyOfRolesUserAuthorizationFilter

[urls]

/api/interpreter/** = authc, anyofrolesuser[admin, user1]
/api/configurations/** = authc, roles[admin]
/api/credential/** = authc, roles[admin]
```

<br/>

> **NOTE :** All of the above configurations are defined in the `conf/shiro.ini` file.


## Other authentication methods

- [HTTP Basic Authentication using NGINX](./authentication_nginx.html)
