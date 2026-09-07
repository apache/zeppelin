@echo off

REM Licensed to the Apache Software Foundation (ASF) under one or more
REM contributor license agreements.  See the NOTICE file distributed with
REM this work for additional information regarding copyright ownership.
REM The ASF licenses this file to You under the Apache License, Version 2.0
REM (the "License"); you may not use this file except in compliance with
REM the License.  You may obtain a copy of the License at
REM
REM    http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.

if not defined ZEPPELIN_HOME (
    for %%d in ("%~dp0..") do (
        set ZEPPELIN_HOME=%%~fd
    )
)

if not defined ZEPPELIN_CONF_DIR (
    set ZEPPELIN_CONF_DIR=%ZEPPELIN_HOME%\conf
)

if not defined ZEPPELIN_LOG_DIR (
    set ZEPPELIN_LOG_DIR=%ZEPPELIN_HOME%\logs
)

if not defined ZEPPELIN_PID_DIR (
    set ZEPPELIN_PID_DIR=%ZEPPELIN_HOME%\run
)

if not defined ZEPPELIN_WAR (
    if exist "%ZEPPELIN_HOME%\zeppelin-web\dist" (
        set ZEPPELIN_WAR=%ZEPPELIN_HOME%\zeppelin-web\dist
    ) else (
        for %%d in ("%ZEPPELIN_HOME%\zeppelin-web*.war") do (
            set ZEPPELIN_WAR=%%d
        )
    )
)

if exist "%ZEPPELIN_CONF_DIR%\zeppelin-env.cmd" (
    call "%ZEPPELIN_CONF_DIR%\zeppelin-env.cmd"
)

if not defined ZEPPELIN_CLASSPATH (
    set ZEPPELIN_CLASSPATH="%ZEPPELIN_CONF_DIR%"
) else (
    set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;"%ZEPPELIN_CONF_DIR%"
)

if not defined ZEPPELIN_ENCODING (
    set ZEPPELIN_ENCODING=UTF-8
)

if not defined ZEPPELIN_MEM (
    set ZEPPELIN_MEM=-Xms1024m -Xmx1024m
)

if not defined ZEPPELIN_INTP_MEM (
    set ZEPPELIN_INTP_MEM=-Xms1024m -Xmx1024m
)

if not defined ZEPPELIN_JAVA_OPTS (
    set ZEPPELIN_JAVA_OPTS=-Dfile.encoding=%ZEPPELIN_ENCODING% %ZEPPELIN_MEM%
) else (
    set ZEPPELIN_JAVA_OPTS=%ZEPPELIN_JAVA_OPTS% -Dfile.encoding=%ZEPPELIN_ENCODING% %ZEPPELIN_MEM%
)

REM JPMS (Java Platform Module System) args mirrored from pom.xml extraJavaTestArgs.
REM Targets JDK 17+: --add-modules, --enable-native-access, --sun-misc-unsafe-memory-access
REM require JDK 17+. -XX:+IgnoreUnrecognizedVMOptions only silences unknown -XX flags.
set JPMS_JAVA_OPTS=-XX:+IgnoreUnrecognizedVMOptions
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.lang=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.lang.invoke=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.io=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.net=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.nio=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.util=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.util.concurrent=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/sun.nio.cs=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/sun.security.action=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --add-opens=java.base/sun.util.calendar=ALL-UNNAMED
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% -Dio.netty.tryReflectionSetAccessible=true
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% -Dio.netty.allocator.type=pooled
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% -Dio.netty.handler.ssl.defaultEndpointVerificationAlgorithm=NONE
set JPMS_JAVA_OPTS=%JPMS_JAVA_OPTS% --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED

if not defined JAVA_OPTS (
    set JAVA_OPTS=%ZEPPELIN_JAVA_OPTS%
) else (
    set JAVA_OPTS=%JAVA_OPTS% %ZEPPELIN_JAVA_OPTS%
)
set JAVA_OPTS=%JAVA_OPTS% %JPMS_JAVA_OPTS%


set JAVA_INTP_OPTS=%ZEPPELIN_INTP_JAVA_OPTS% -Dfile.encoding=%ZEPPELIN_ENCODING% %JPMS_JAVA_OPTS%

if not defined JAVA_HOME (
    set ZEPPELIN_RUNNER=java
) else (
    set ZEPPELIN_RUNNER=%JAVA_HOME%\bin\java
)

if not defined ZEPPELIN_IDENT_STRING (
    set ZEPPELIN_IDENT_STRING=%USERNAME%
)

if not defined ZEPPELIN_INTERPRETER_REMOTE_RUNNER (
    set ZEPPELIN_INTERPRETER_REMOTE_RUNNER=bin\interpreter.cmd
)

exit /b
