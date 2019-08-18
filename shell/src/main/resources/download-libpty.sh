#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

bin=$(dirname "${BASH_SOURCE-$0}")
bin=$(cd "${bin}">/dev/null; pwd)

# freebsd lib pty
mkdir -p libpty/freebsd/x86/
rm libpty/freebsd/x86/*
wget -P libpty/freebsd/x86/ https://github.com/JetBrains/pty4j/blob/master/os/freebsd/x86/libpty.so

mkdir -p libpty/freebsd/x86_64/
rm libpty/freebsd/x86_64/*
wget -P libpty/freebsd/x86_64/ https://github.com/JetBrains/pty4j/blob/master/os/freebsd/x86_64/libpty.so

# linux lib pty
mkdir -p libpty/linux/ppc64le/
rm libpty/linux/ppc64le/*
wget -P libpty/linux/ppc64le/ https://github.com/JetBrains/pty4j/blob/master/os/linux/ppc64le/libpty.so

mkdir -p libpty/linux/x86/
rm libpty/linux/x86/*
wget -P libpty/linux/x86/ https://github.com/JetBrains/pty4j/blob/master/os/linux/x86/libpty.so

mkdir -p libpty/linux/x86_64/
rm libpty/linux/x86_64/*
wget -P libpty/linux/x86_64/ https://github.com/JetBrains/pty4j/blob/master/os/linux/x86_64/libpty.so

# macosx lib pty
mkdir -p libpty/macosx/x86/
rm libpty/macosx/x86/*
wget -P libpty/macosx/x86/ https://github.com/JetBrains/pty4j/blob/master/os/macosx/x86/libpty.dylib

mkdir -p libpty/macosx/x86_64/
rm libpty/macosx/x86_64/*
wget -P libpty/macosx/x86_64/ https://github.com/JetBrains/pty4j/blob/master/os/macosx/x86_64/libpty.dylib

# win lib pty
mkdir -p libpty/win/x86/
rm libpty/win/x86/*
wget -P libpty/win/x86/ https://github.com/JetBrains/pty4j/blob/master/os/win/x86/winpty-agent.exe
wget -P libpty/win/x86/ https://github.com/JetBrains/pty4j/blob/master/os/win/x86/winpty.dll

mkdir -p libpty/win/x86_64/
rm libpty/win/x86_64/*
wget -P libpty/win/x86_64/ https://github.com/JetBrains/pty4j/blob/master/os/win/x86_64/cyglaunch.exe
wget -P libpty/win/x86_64/ https://github.com/JetBrains/pty4j/blob/master/os/win/x86_64/winpty-agent.exe
wget -P libpty/win/x86_64/ https://github.com/JetBrains/pty4j/blob/master/os/win/x86_64/winpty.dll

mkdir -p libpty/win/xp/
rm libpty/win/xp/*
wget -P libpty/win/xp/ https://github.com/JetBrains/pty4j/blob/master/os/win/xp/winpty-agent.exe
wget -P libpty/win/xp/ https://github.com/JetBrains/pty4j/blob/master/os/win/xp/winpty.dll
