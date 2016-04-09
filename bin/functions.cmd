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

if not "%1"=="" goto %1

exit /b

:ADDEACHJARINDIR
for %%d in ("%~2\*.jar") do (
    set ZEPPELIN_CLASSPATH="%%d";!ZEPPELIN_CLASSPATH!
)
exit /b

:ADDEACHJARINDIRRECURSIVE
for /r "%~2" %%d in (*.jar) do (
    set ZEPPELIN_CLASSPATH="%%d";!ZEPPELIN_CLASSPATH!
)
exit /b

:ADDJARINDIR
if exist "%~2" (
    set ZEPPELIN_CLASSPATH="%~2\*";%ZEPPELIN_CLASSPATH%
)
exit /b
