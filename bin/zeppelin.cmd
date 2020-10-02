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

setlocal enableextensions enabledelayedexpansion

set bin=%~dp0

if not "%1"=="--config" goto MAIN

:SET_CONFIG
shift
set conf_dir=%~f1
shift
if not exist "%conf_dir%" (
    echo ERROR: %conf_dir% is not a directory
    echo Usage: %~n0 [--config ^<conf-dir^>]
    exit /b 1
) else (
    set ZEPPELIN_CONF_DIR=%conf_dir%
)

:MAIN
call "%bin%\common.cmd"

set HOSTNAME=%COMPUTERNAME%
set ZEPPELIN_LOGFILE=%ZEPPELIN_LOG_DIR%\zeppelin-%ZEPPELIN_IDENT_STRING%-%HOSTNAME%.log

set ZEPPELIN_SERVER=org.apache.zeppelin.server.ZeppelinServer
set JAVA_OPTS=%JAVA_OPTS% -Dzeppelin.log.file="%ZEPPELIN_LOGFILE%"

if exist "%ZEPPELIN_HOME%\zeppelin-interpreter\target\classes" (
    set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;"%ZEPPELIN_HOME%\zeppelin-interpreter\target\classes"
)

if exist "%ZEPPELIN_HOME%\zeppelin-zengine\target\classes" (
    set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;"%ZEPPELIN_HOME%\zeppelin-zengine\target\classes"
)

if exist "%ZEPPELIN_HOME%\zeppelin-server\target\classes" (
    set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;"%ZEPPELIN_HOME%\zeppelin-server\target\classes"
)

call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%"
call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\lib"
call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\lib\interpreter"
call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\zeppelin-interpreter\target\lib"
call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\zeppelin-zengine\target\lib"
call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\zeppelin-server\target\lib"
call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\zeppelin-web\target\lib"
call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\zeppelin-web-angular\target\lib"

if defined CLASSPATH (
    set ZEPPELIN_CLASSPATH=%CLASSPATH%;%ZEPPELIN_CLASSPATH%
)

if defined HADOOP_CONF_DIR (
    set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;%HADOOP_CONF_DIR%
)

if not exist %ZEPPELIN_LOG_DIR% (
    echo Log dir doesn't exist, create %ZEPPELIN_LOG_DIR%
    mkdir "%ZEPPELIN_LOG_DIR%"
)

if not exist %ZEPPELIN_PID_DIR% (
    echo Pid dir doesn't exist, create %ZEPPELIN_PID_DIR%
    mkdir "%ZEPPELIN_PID_DIR%"
)

"%ZEPPELIN_RUNNER%" %JAVA_OPTS% -cp %ZEPPELIN_CLASSPATH_OVERRIDES%;!ZEPPELIN_CLASSPATH!  %ZEPPELIN_SERVER% "%*"
