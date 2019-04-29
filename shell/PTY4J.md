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
# Pty4J - Pseudo terminal(PTY) implementation in Java.

https://github.com/JetBrains/pty4j

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
<https://github.com/nyholku/purejavacomm>. Pty4j depends on the version of PureJavaComm,
not uploaded to [the maven central](https://search.maven.org/artifact/com.github.purejavacomm/purejavacomm).
It'a available as [org.jetbrains.pty4j:purejavacomm](https://bintray.com/jetbrains/pty4j/org.jetbrains.pty4j%3Apurejavacomm).

Windows pty implementation used here is the magnificent WinPty library written by Ryan Prichard: https://github.com/rprichard/winpty

## Adding Pty4J to your build

The releases are uploaded to [org.jetbrains.pty4j:pty4j](https://bintray.com/jetbrains/pty4j/org.jetbrains.pty4j%3Apty4j).
Please click on the "Set Me Up!" button there and follow the instructions.

### Maven

```
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.pty4j</groupId>
      <artifactId>pty4j</artifactId>
      <version>0.8.6</version>
    </dependency>
  </dependencies>

  <repositories>
    <repository>
      <id>bintray-jetbrains-pty4j</id>
      <name>bintray</name>
      <url>https://jetbrains.bintray.com/pty4j</url>
    </repository>
  </repositories>
```

### Gradle

```
repositories {
  maven {
    url  "https://jetbrains.bintray.com/pty4j"
  }
}

// or use jcenter
// repositories {
//   jcenter()
// }

dependencies {
    compile 'org.jetbrains.pty4j:pty4j:0.8.6'
}
```

## Usage

Using this library is relatively easy:

    // The command to run in a PTY...
    String[] cmd = { "/bin/sh", "-l" };
    // The initial environment to pass to the PTY child process...
    String[] env = { "TERM=xterm" };

    PtyProcess pty = PtyProcess.exec(cmd, env);

    OutputStream os = pty.getOutputStream();
    InputStream is = pty.getInputStream();

    // ... work with the streams ...

    // wait until the PTY child process terminates...
    int result = pty.waitFor();

    // free up resources.
    pty.close();

The operating systems currently supported by pty4j are: Linux, OSX and
Windows.

**Note that this library is not yet fully tested on all platforms.**

## Changes

    0.8 | 28-08-2018 | Native binaries are bundled into pty4j.jar
    0.7 | 14-06-2016 | Updated to match the new winpty API
    0.6 | 08-02-2016 | Correct NamedPipe usage for winpty
                       FreeBSD support
    0.5 | 28-09-2015 | Supports JNA 4.1, Cygwin support, fixes
    0.4 | 13-01-2015 | Fixes
    0.3 | 16-08-2013 | Native code for fork and process exec.
    0.2 | 03-08-2013 | Linux and Windows supported.
    0.1 | 20-07-2013 | Initial version.

## License

The code in this library is licensed under Eclipse Public License, version
1.0 and can be found online at: <http://www.eclipse.org/legal/epl-v10.html>.
