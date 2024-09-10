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

Original link: https://github.com/JetBrains/pty4j

# Pty4J - Pseudo terminal(PTY) implementation in Java.

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)


[![Build Status](https://travis-ci.com/traff/pty4j.svg?branch=master)](https://travis-ci.com/JetBrains/pty4j)

This is a Java implementation of PTY. Written in JNA with native code to make fork of a process.

It is based on two projects that provide the same functionality: [JPty](https://github.com/jawi/JPty)
and [elt](https://code.google.com/p/elt/).

While JPty is pretty good and written using only JNA it suffers from a
hang on Mac OS X when Java is under debug (https://github.com/jawi/JPty/issues/2), because
fork doesn't work well in java.

elt works ok, but it has mostly native code(using JNI).

So this one is a mix of the other two: a port of elt to JNA in the style it is made in JPty with only
fork and process exec written in native.

Also pty4j implements java interface for pty for windows, using [WinPty](https://github.com/rprichard/winpty) library.

## Dependencies

This library depends on JTermios, part of the PureJavaComm library found at
<https://github.com/nyholku/purejavacomm>. Pty4j depends on the forked version of PureJavaComm ([org.jetbrains.pty4j:purejavacomm](https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/pty4j/purejavacomm/)).

Windows pty implementation used here is the magnificent WinPty library written by Ryan Prichard: https://github.com/rprichard/winpty

## Adding Pty4J to your build

The releases are published to Maven Central: [org.jetbrains.pty4j:pty4j](https://search.maven.org/artifact/org.jetbrains.pty4j/pty4j).

### Maven

```
<dependency>
  <groupId>org.jetbrains.pty4j</groupId>
  <artifactId>pty4j</artifactId>
  <version>0.12.5</version>
</dependency>
```

### Gradle

```
dependencies {
  implementation 'org.jetbrains.pty4j:pty4j:0.12.5'
}
```

## Usage

Using this library is relatively easy:

    String[] cmd = { "/bin/sh", "-l" };
    Map<String, String> env = new HashMap<>(System.getenv());
    env.put("TERM", "xterm");
    PtyProcess process = new PtyProcessBuilder().setCommand(cmd).setEnvironment(env).start();

    OutputStream os = process.getOutputStream();
    InputStream is = process.getInputStream();
    
    // ... work with the streams ...
    
    // wait until the PTY child process is terminated
    int result = process.waitFor();

The operating systems currently supported by pty4j are: Linux, OSX, Windows and FreeBSD.

## License

The code in this library is licensed under Eclipse Public License, version
1.0 and can be found online at: <http://www.eclipse.org/legal/epl-v10.html>.

