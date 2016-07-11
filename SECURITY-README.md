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

# Shiro Authentication
To connect to Zeppelin, users will be asked to enter their credentials. Once logged, a user has access to all notes including other users notes.
This a a first step toward full security as implemented by this pull request (https://github.com/apache/zeppelin/pull/53).

# Security setup
1. Secure the HTTP channel: Comment the line "/** = anon" and uncomment the line "/** = authc" in the file conf/shiro.ini. Read more about he shiro.ini file format at the following URL http://shiro.apache.org/configuration.html#Configuration-INISections.  
2. Secure the Websocket channel : Set to property "zeppelin.anonymous.allowed" to "false" in the file conf/zeppelin-site.xml. You can start by renaming conf/zeppelin-site.xml.template to conf/zeppelin-site.xml
3. Start Zeppelin : bin/zeppelin.sh
4. point your browser to http://localhost:8080
5. Login using one of the user/password combinations defined in the conf/shiro.ini file.

# Implementation notes
## Vocabulary
username, owner and principal are used interchangeably to designate the currently authenticated user
## What are we securing ?
Zeppelin is basically a web application that spawn remote interpreters to run commands and return HTML fragments to be displayed on the user browser.
The scope of this PR is to require credentials to access Zeppelin. To achieve this, we use Apache Shiro. 
## HTTP Endpoint security
Apache Shiro sits as a servlet filter between the browser and the exposed services and handles the required authentication without any programming required. (See Apache Shiro for more info).
## Websocket security
Securing the HTTP endpoints is not enough, since Zeppelin also communicates with the browser through websockets. To secure this channel, we take the following approach:
  1. The browser on startup requests a ticket through HTTP
  2. The Apache Shiro Servlet filter handles the user auth
  3. Once the user is authenticated, a ticket is assigned to this user and the ticket is returned to the browser

All websockets communications require the username and ticket  to be submitted by the browser. Upon receiving a websocket message, the server checks that the ticket received is the one assigned to the username through the HTTP request (step 3 above).
