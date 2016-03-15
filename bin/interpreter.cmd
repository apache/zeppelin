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

:loop
if "%~1"=="" goto cont
if /I "%~1"=="-h" goto usage
if /I "%~1"=="-d" (
    set INTERPRETER_DIR=%~2
    set INTERPRETER_ID=%~n2
)
if /I "%~1"=="-p" set PORT=%~2
if /I "%~1"=="-l" set LOCAL_INTERPRETER_REPO=%~2
shift
goto loop
:cont

if "%PORT%"=="" goto usage
if "%INTERPRETER_DIR%"=="" goto usage

call "%bin%\common.cmd"

if exist "%ZEPPELIN_HOME%\zeppelin-interpreter\target\classes" (
    set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;"%ZEPPELIN_HOME%\zeppelin-interpreter\target\classes"
) else (
    for %%d in ("%ZEPPELIN_HOME%\lib\zeppelin-interpreter*.jar") do (
        set ZEPPELIN_INTERPRETER_JAR=%%d
    )
    set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;"!ZEPPELIN_INTERPRETER_JAR!"
)

call "%bin%\functions.cmd" ADDJARINDIR "%ZEPPELIN_HOME%\zeppelin-interpreter\target\lib"
call "%bin%\functions.cmd" ADDJARINDIR "%INTERPRETER_DIR%"

set HOSTNAME=%COMPUTERNAME%
set ZEPPELIN_SERVER=org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer

set ZEPPELIN_LOGFILE=%ZEPPELIN_LOG_DIR%\zeppelin-interpreter-%INTERPRETER_ID%-%ZEPPELIN_IDENT_STRING%-%HOSTNAME%.log

if not exist "%ZEPPELIN_LOG_DIR%" (
    echo Log dir doesn't exist, create %ZEPPELIN_LOG_DIR%
    mkdir "%ZEPPELIN_LOG_DIR%"
)

if /I "%INTERPRETER_ID%"=="spark" (
    if defined SPARK_HOME (
        set SPARK_SUBMIT=%SPARK_HOME%\bin\spark-submit.cmd
        for %%d in ("%ZEPPELIN_HOME%\interpreter\spark\zeppelin-spark*.jar") do (
            set SPARK_APP_JAR=%%d
        )
        set ZEPPELIN_CLASSPATH="!SPARK_APP_JAR!"
        
        for %%d in ("%SPARK_HOME%\python\lib\py4j-*-src.zip") do (
            set py4j=%%d
        )
        
        if not defined PYTHONPATH (
            set PYTHONPATH=!py4j!;%SPARK_HOME%\python
        ) else (
            set PYTHONPATH=!py4j!;%SPARK_HOME%\python;%PYTHONPATH%
        )
    ) else (
        if defined HADOOP_HOME if exist "%HADOOP_HOME%\bin\hadoop.cmd" (
            for /f "tokens=*" %%d in ('"%HADOOP_HOME%\bin\hadoop.cmd" classpath') do (
                set LOCAL_HADOOP_CLASSPATH=%%d
            )
            set ZEPPELIN_CLASSPATH=!LOCAL_HADOOP_CLASSPATH!;%ZEPPELIN_CLASSPATH%
        )
        
        call "%bin%\functions.cmd" ADDJARINDIR "%INTERPRETER_DIR%\dep"
        
        for %%d in ("%ZEPPELIN_HOME%\interpreter\spark\pyspark\py4j-*-src.zip") do (
            set py4j=%%d
        )
        
        set PYSPARKPATH=%ZEPPELIN_HOME%\interpreter\spark\pyspark\pyspark.zip;!py4j!
        
        if not defined PYTHONPATH (
            set PYTHONPATH=!PYSPARKPATH!
        ) else (
            set PYTHONPATH=%PYTHONPATH%;!PYSPARKPATH!
        )
        
        set PYSPARKPATH=
        
        if defined HADOOP_HOME if not defined HADOOP_CONF_DIR (
            if exist "%HADOOP_HOME%\etc\hadoop" (
                set HADOOP_CONF_DIR=%HADOOP_HOME%\etc\hadoop
            )
        )
        
        if exist "%HADOOP_CONF_DIR%" (
            set ZEPPELIN_CLASSPATH=%ZEPPELIN_CLASSPATH%;"%HADOOP_CONF_DIR%"
        )        
    )
)

call "%bin%\functions.cmd" ADDJARINDIR "%LOCAL_INTERPRETER_REPO%"

if not defined ZEPPELIN_CLASSPATH_OVERRIDES (
    set CLASSPATH=%ZEPPELIN_CLASSPATH%
) else (
    set CLASSPATH=%ZEPPELIN_CLASSPATH_OVERRIDES%;%ZEPPELIN_CLASSPATH%
)

if defined SPARK_SUBMIT (
    set JAVA_INTP_OPTS=%JAVA_INTP_OPTS% -Dzeppelin.log.file='%ZEPPELIN_LOGFILE%'

    "%SPARK_SUBMIT%" --class %ZEPPELIN_SERVER% --jars %CLASSPATH% --driver-java-options "!JAVA_INTP_OPTS!" %SPARK_SUBMIT_OPTIONS% "%SPARK_APP_JAR%" %PORT%
) else (
    set JAVA_INTP_OPTS=%JAVA_INTP_OPTS% -Dzeppelin.log.file="%ZEPPELIN_LOGFILE%"

    "%ZEPPELIN_RUNNER%" !JAVA_INTP_OPTS! %ZEPPELIN_INTP_MEM% -cp %ZEPPELIN_CLASSPATH_OVERRIDES%;%CLASSPATH% %ZEPPELIN_SERVER% %PORT%
)

exit /b

:usage
echo Usage: %~n0 -p ^<port^> -d ^<interpreter dir to load^> -l ^<local interpreter repo dir to load^>
