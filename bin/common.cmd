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
    set ZEPPELIN_MEM=-Xms1024m -Xmx1024m -XX:MaxPermSize=512m
)

if not defined ZEPPELIN_INTP_MEM (
    set ZEPPELIN_INTP_MEM=-Xms1024m -Xmx1024m -XX:MaxPermSize=512m
)

if not defined ZEPPELIN_JAVA_OPTS (
    set ZEPPELIN_JAVA_OPTS=-Dfile.encoding=%ZEPPELIN_ENCODING% %ZEPPELIN_MEM%
) else (
    set ZEPPELIN_JAVA_OPTS=%ZEPPELIN_JAVA_OPTS% -Dfile.encoding=%ZEPPELIN_ENCODING% %ZEPPELIN_MEM%
)

if not defined JAVA_OPTS (
    set JAVA_OPTS=%ZEPPELIN_JAVA_OPTS%
) else (
    set JAVA_OPTS=%JAVA_OPTS% %ZEPPELIN_JAVA_OPTS%
)


set JAVA_INTP_OPTS=%ZEPPELIN_INTP_JAVA_OPTS% -Dfile.encoding=%ZEPPELIN_ENCODING%

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
