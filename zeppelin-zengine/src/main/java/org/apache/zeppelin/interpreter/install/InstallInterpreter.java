/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.interpreter.install;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.util.Util;
import org.sonatype.aether.RepositoryException;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Commandline utility to install interpreter from maven repository
 */
public class InstallInterpreter {
  static ZeppelinConfiguration conf = ZeppelinConfiguration.create();

  static String [] availableInterpreters = {
    "md",     "zeppelin-markdown",   "Markdown interpreter",
    "sh",     "zeppelin-shell",      "Allows shell command",
    "jdbc",   "zeppelin-jdbc",
    "Generic JDBC interpreter for hive, phoenix, postsgresql, mysql, etc",
  };

  public static void list() {
    for (int i = 0; i < availableInterpreters.length; i++) {
      System.out.println(availableInterpreters[i] + "\t\t" + availableInterpreters[i + 2]);
      i += 2;
    }
  }

  public static void installAll() {
    for (int i = 0; i < availableInterpreters.length; i++) {
      install(availableInterpreters[i], getArtifactName(availableInterpreters[i + 1]));
      i += 2;
    }
  }

  public static void install(String name) {
    // find artifact name
    for (int i = 0; i < availableInterpreters.length; i++) {
      if (name.equals(availableInterpreters[i])) {
        install(name, getArtifactName(availableInterpreters[i + 1]));
        return;
      }
      i += 2;
    }

    System.err.println("Can't find interpreter '" + name + "'");
    System.exit(1);
  }

  private static String getArtifactName(String name) {
    return "org.apache.zeppelin:" + name + ":" + Util.getVersion();
  }

  public static void install(String name, String artifact) {
    DependencyResolver depResolver = new DependencyResolver(
        conf.getInterpreterLocalRepoPath());

    File interpreterBaseDir = new File(conf.getInterpreterDir());
    File installDir = new File(interpreterBaseDir, name);
    if (installDir.exists()) {
      System.err.println("Directory " + installDir.getAbsolutePath() + " already exists. Skipping");
      return;
    }

    System.out.println("Install " + name + "(" + artifact + ") to "
        + installDir.getAbsolutePath() + " ... ");

    try {
      depResolver.load(artifact, installDir);
      System.out.println("Interpreter " + name + " installed under " +
          installDir.getAbsolutePath());
    } catch (RepositoryException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void usage() {
    System.out.println("Options");
    System.out.println("  -l, --list                  List available interpreters");
    System.out.println("  -a, --all                   Install all available interpreters");
    System.out.println("  -n, --name     [NAME]       Install an interpreter");
    System.out.println("  -t, --artifact [ARTIFACT]   (Optional with -n) custom artifact name." +
        "e.g. customGroup:customArtifact:customVersion");
  }

  public static void main(String [] args) {
    if (args.length == 0) {
      usage();
      return;
    }

    String name = null;
    String artifact = null;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i].toLowerCase(Locale.US);
      switch (arg) {
          case "--list":
          case "-l":
            list();
            System.exit(0);
            break;
          case "--all":
          case "-a":
            installAll();
            System.exit(0);
            break;
          case "--name":
          case "-n":
            name = args[++i];
            break;
          case "--artifact":
          case "-t":
            artifact = args[++i];
            break;
          case "--version":
          case "-v":
            Util.getVersion();
            break;
          case "--help":
          case "-h":
            usage();
            System.exit(0);
            break;
          default:
            System.out.println("Unknown option " + arg);
      }
    }

    if (name != null) {
      if (artifact != null) {
        install(name, artifact);
      } else {
        install(name);
      }
    }
  }
}
