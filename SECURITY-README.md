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

To connect to Zeppelin, users will be asked to enter their credentials. Once logged in, a user has access to all notes, including other users' notes.
This marks the first step toward full security, as introduced in this [pull request](https://github.com/apache/zeppelin/pull/53).

Please check the [Shiro authentication documentation for Apache Zeppelin](https://zeppelin.apache.org/docs/0.12.0/setup/security/shiro_authentication.html) available on our official website for more detailed information (e.g., how to set up security, how to configure user groups and permissions, etc.).

# Implementation Notes

## Vocabulary

The terms *username*, *owner*, and *principal* are used interchangeably to refer to the currently authenticated user.

## What Are We Securing?

Zeppelin is essentially a web application that spawns remote interpreters to run commands and return HTML fragments to be displayed in the user's browser.  
The scope of this pull request is to require user credentials to access Zeppelin. To achieve this, we use Apache Shiro.

## HTTP Endpoint Security

Apache Shiro acts as a servlet filter between the browser and the exposed services.  
It handles authentication without requiring additional programming. (See [Apache Shiro](https://shiro.apache.org) for more information.)

## WebSocket Security

Securing HTTP endpoints alone is not sufficient, since Zeppelin also communicates with the browser via WebSockets. To secure this channel, we take the following approach:

1. On startup, the browser requests a ticket via HTTP.
2. The Apache Shiro servlet filter authenticates the user.
3. Once authenticated, a ticket is assigned to the user and returned to the browser.

Every WebSocket message must include both the username and the ticket.
When receiving a WebSocket message, the server checks that the ticket matches what was assigned to the username via the HTTP request (step 3 above).
