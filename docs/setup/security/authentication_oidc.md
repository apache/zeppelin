---
layout: page
title: "Authentication using Oidc"
description: "There are multiple ways to enable authentication in Apache Zeppelin. This page describes how to enable OIDC auth."
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

# Authentication with OIDC

<div id="toc"></div>

[Build in authentication mechanism](./shiro_authentication.html) is recommended way for authentication. In case of you want authenticate using [OpenId Connect](https://en.wikipedia.org/wiki/OpenID_Connect), please read this document.

## Enabling OpenId Connect

You need to use a layer on top of Shiro to enable authentication/authorization feature with OIDC in Zeppelin.

1. Add Pac4J and Buji to classpath

  Put on the classpath (e.g. ./zeppelin/lib)the following libraries and all the transitive dependencies:
  ```
  +-io.buji:buji-pac4j:3.0.0
  | +-org.apache.shiro:shiro-web:1.4.0
  |
  +-org.pac4j:pac4j-core:3.0.0-SNAPSHOT
  | +-org.slf4j:slf4j-api:1.7.25
  |
  +-org.pac4j:pac4j-oidc:3.0.0-SNAPSHOT
    +-com.nimbusds:nimbus-jose-jwt:4.34.2
    | +-com.github.stephenc.jcip:jcip-annotations:1.0-1
    | +-net.minidev:json-smart:1.3.1
    | +-org.bouncycastle:bcpkix-jdk15on:1.55
    |   +-org.bouncycastle:bcprov-jdk15on:1.55
    |   
    +-com.nimbusds:oauth2-oidc-sdk:5.24.2
    | +-com.github.stephenc.jcip:jcip-annotations:1.0-1
    | +-com.nimbusds:lang-tag:1.4.3
    | | +-net.minidev:json-smart:1.3.1
    | |
    | +-javax.mail:mail:1.4.7
    | | +-javax.activation:activation:1.1
    | |
    | +-net.minidev:json-smart:1.3.1
    | +-org.apache.commons:commons-collections4:4.1
    | +-org.apache.commons:commons-lang3:3.5
    |
    +-org.pac4j:pac4j-core:3.0.0-SNAPSHOT
      +-org.slf4j:slf4j-api:1.7.25
  ```

2. Configure your `shiro.ini` file

  In this example we will configure `shiro.ini` to perform auth through Google API endpoint, you can use a similar configuration with multiple other OIDC servers (Keycloak, Twitter, Facebook, ...)
  To keep it working you need first to [create a Google API console project and client](https://developers.google.com/identity/sign-in/web/devconsole-project).
  Additionally you will need to add to `Authorized redirect URI` your callback endpoint (i.e. `http://localhost:8080/api/callback`).

  Then edit your `shiro.ini` file:

  ```
  [main]
  roleAdminAuthGenerator = org.pac4j.core.authorization.generator.FromAttributesAuthorizationGenerator
  roleAdminAuthGenerator.roleAttributes = ROLE_ADMIN

  oidcConfig = org.pac4j.oidc.config.OidcConfiguration
  oidcConfig.clientId =<YOUR ID CLIENT>
  oidcConfig.secret =<YOUR CLIENT SECRET>
  oidcConfig.useNonce = true

  oidcConfig.logoutUrl = https://www.google.com/accounts/Logout?continue=https://appengine.google.com/_ah/logout?continue=http://localhost:8080

  oidcRedirectActionBuilder = org.pac4j.oidc.logout.OidcLogoutActionBuilder
  oidcRedirectActionBuilder.configuration = $oidcConfig

  googleOidcClient = org.pac4j.oidc.client.GoogleOidcClient
  googleOidcClient.configuration = $oidcConfig
  googleOidcClient.authorizationGenerator = $roleAdminAuthGenerator
  googleOidcClient.logoutActionBuilder = $oidcRedirectActionBuilder
  googleOidcClient.includeClientNameInCallbackUrl = false

  clients = org.pac4j.core.client.Clients
  clients.callbackUrl = http://localhost:8080/api/callback
  clients.clients = $googleOidcClient
  clients.defaultClient = $googleOidcClient

  requireRoleAdmin = org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
  requireRoleAdmin.elements = ROLE_ADMIN

  config = org.pac4j.core.config.Config
  config.clients = $clients
  config.authorizers = admin:$requireRoleAdmin

  pac4jRealm = io.buji.pac4j.realm.Pac4jRealm
  pac4jSubjectFactory = io.buji.pac4j.subject.Pac4jSubjectFactory
  securityManager.subjectFactory = $pac4jSubjectFactory

  oidcSecurityFilter = io.buji.pac4j.filter.SecurityFilter
  oidcSecurityFilter.config = $config
  oidcSecurityFilter.clients = googleOidcClient

  sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager

  callbackFilter = io.buji.pac4j.filter.CallbackFilter
  callbackFilter.defaultUrl = /
  callbackFilter.config = $config

  logoutFilter = io.buji.pac4j.filter.LogoutFilter
  #https://stackoverflow.com/questions/17050575/logout-link-with-return-url-oauth
  logoutFilter.defaultUrl =https://www.google.com/accounts/Logout?continue=https://appengine.google.com/_ah/logout?continue=http://localhost:8080

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

  Substitute `<YOUR ID CLIENT>` and `<YOUR CLIENT SECRET>` with appropriate values.

  Restart zeppelin and enjoy your new Google login!
