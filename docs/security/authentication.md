---
layout: page
title: "Authentication for NGINX"
description: "Authentication for NGINX"
group: security
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
# Authentication for NGINX

Authentication is company-specific.

One option is to use [Basic Access Authentication](https://en.wikipedia.org/wiki/Basic_access_authentication)

### HTTP Basic Authentication using NGINX

> **Quote from Wikipedia:** NGINX is a web server. It can act as a reverse proxy server for HTTP, HTTPS, SMTP, POP3, and IMAP protocols, as well as a load balancer and an HTTP cache.

So you can use NGINX server as proxy server to serve HTTP Basic Authentication as a separate process along with Zeppelin server.
Here are instructions how to accomplish the setup NGINX as a front-end authentication server and connect Zeppelin at behind.

This instruction based on Ubuntu 14.04 LTS but may work with other OS with few configuration changes.

1. Install NGINX server on your server instance

    You can install NGINX server with same box where zeppelin installed or separate box where it is dedicated to serve as proxy server.

    ```
    $ apt-get install nginx
    ```
    *Important: On pre 1.3.13 version of NGINX, Proxy for Websocket may not fully works. Please use latest version of NGINX. See: [NGINX documentation](https://www.nginx.com/blog/websocket-nginx/)*

1. Setup init script in NGINX

    In most cases, NGINX configuration located under `/etc/nginx/sites-available`. Create your own configuration or add your existing configuration at `/etc/nginx/sites-available`.

    ```
    $ cd /etc/nginx/sites-available
    $ touch my-zeppelin-auth-setting
    ```

    Now add this script into `my-zeppelin-auth-setting` file. You can comment out `optional` lines If you want serve Zeppelin under regular HTTP 80 Port.

    ```
    upstream zeppelin {
        server [YOUR-ZEPPELIN-SERVER-IP]:[YOUR-ZEPPELIN-SERVER-PORT];   # For security, It is highly recommended to make this address/port as non-public accessible
    }

    # Zeppelin Website
    server {
        listen [YOUR-ZEPPELIN-WEB-SERVER-PORT];
        listen 443 ssl;                                      # optional, to serve HTTPS connection
        server_name [YOUR-ZEPPELIN-SERVER-HOST];             # for example: zeppelin.mycompany.com

        ssl_certificate [PATH-TO-YOUR-CERT-FILE];            # optional, to serve HTTPS connection
        ssl_certificate_key [PATH-TO-YOUR-CERT-KEY-FILE];    # optional, to serve HTTPS connection

        if ($ssl_protocol = "") {
            rewrite ^ https://$host$request_uri? permanent;  # optional, to force use of HTTPS
        }

        location / {    # For regular websever support
            proxy_pass http://zeppelin;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header Host $http_host;
            proxy_set_header X-NginX-Proxy true;
            proxy_redirect off;
            auth_basic "Restricted";
            auth_basic_user_file /etc/nginx/.htpasswd;
        }

        location /ws {  # For websocket support
            proxy_pass http://zeppelin;
            proxy_http_version 1.1;
            proxy_set_header Upgrade websocket;
            proxy_set_header Connection upgrade;
            proxy_read_timeout 86400;
        }
    }
    ```

    Then make a symbolic link to this file from `/etc/nginx/sites-enabled/` to enable configuration above when NGINX reloads.

    ```
    $ ln -s /etc/nginx/sites-enabled/my-zeppelin-auth-setting /etc/nginx/sites-available/my-zeppelin-auth-setting
    ```

1. Setup user credential into `.htpasswd` file and restart server

    Now you need to setup `.htpasswd` file to serve list of authenticated user credentials for NGINX server.

    ```
    $ cd /etc/nginx
    $ htpasswd -c htpasswd [YOUR-ID]
    $ NEW passwd: [YOUR-PASSWORD]
    $ RE-type new passwd: [YOUR-PASSWORD-AGAIN]
    ```
    Or you can use your own apache `.htpasswd` files in other location for setting up property: `auth_basic_user_file`

    Restart NGINX server.

    ```
    $ service nginx restart
    ```
    Then check HTTP Basic Authentication works in browser. If you can see regular basic auth popup and then able to login with credential you entered into `.htpasswd` you are good to go.

1. More security consideration

* Using HTTPS connection with Basic Authentication is highly recommended since basic auth without encryption may expose your important credential information over the network.
* Using [Shiro Security feature built-into Zeppelin](https://github.com/apache/incubator-zeppelin/blob/master/SECURITY-README.md) is recommended if you prefer all-in-one solution for authentication but NGINX may provides ad-hoc solution for re-use authentication served by your system's NGINX server or in case of you need to separate authentication from zeppelin server.
* It is recommended to isolate direct connection to Zeppelin server from public internet or external services to secure your zeppelin instance from unexpected attack or problems caused by public zone.

### Another option

Another option is to have an authentication server that can verify user credentials in an LDAP server.
If an incoming request to the Zeppelin server does not have a cookie with user information encrypted with the authentication server public key, the user
is redirected to the authentication server. Once the user is verified, the authentication server redirects the browser to a specific URL in the Zeppelin server which sets the authentication cookie in the browser.
The end result is that all requests to the Zeppelin web server have the authentication cookie which contains user and groups information.
