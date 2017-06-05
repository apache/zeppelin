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

import org.apache.commons.io.FileUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.util.Util;
import org.sonatype.aether.RepositoryException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commandline utility to install interpreter from maven repository
 */
public class InstallInterpreter {
  private final File interpreterListFile;
  private final File interpreterBaseDir;
  private final List<AvailableInterpreterInfo> availableInterpreters;
  private final String localRepoDir;
  private URL proxyUrl;
  private String proxyUser;
  private String proxyPassword;

  /**
   *
   * @param interpreterListFile
   * @param interpreterBaseDir interpreter directory for installing binaries
   * @throws IOException
   */
  public InstallInterpreter(File interpreterListFile, File interpreterBaseDir, String localRepoDir)
      throws IOException {
    this.interpreterListFile = interpreterListFile;
    this.interpreterBaseDir = interpreterBaseDir;
    this.localRepoDir = localRepoDir;
    availableInterpreters = new LinkedList<>();
    readAvailableInterpreters();
  }


  /**
   * Information for available informations
   */
  private static class AvailableInterpreterInfo {
    public final String name;
    public final String artifact;
    public final String description;

    public AvailableInterpreterInfo(String name, String artifact, String description) {
      this.name = name;
      this.artifact = artifact;
      this.description = description;
    }
  }

  private void readAvailableInterpreters() throws IOException {
    if (!interpreterListFile.isFile()) {
      System.err.println("Can't find interpreter list " + interpreterListFile.getAbsolutePath());
      return;
    }
    String text = FileUtils.readFileToString(interpreterListFile);
    String[] lines = text.split("\n");

    Pattern pattern = Pattern.compile("(\\S+)\\s+(\\S+)\\s+(.*)");

    int lineNo = 0;
    for (String line : lines) {
      lineNo++;
      if (line == null || line.length() == 0 || line.startsWith("#")) {
        continue;
      }

      Matcher match = pattern.matcher(line);
      if (match.groupCount() != 3) {
        System.err.println("Error on line " + lineNo + ", " + line);
        continue;
      }

      match.find();

      String name = match.group(1);
      String artifact = match.group(2);
      String description = match.group(3);

      availableInterpreters.add(new AvailableInterpreterInfo(name, artifact, description));
    }
  }

  public List<AvailableInterpreterInfo> list() {
    for (AvailableInterpreterInfo info : availableInterpreters) {
      System.out.println(info.name + "\t\t\t" + info.description);
    }

    return availableInterpreters;
  }

  public void installAll() {
    for (AvailableInterpreterInfo info : availableInterpreters) {
      install(info.name, info.artifact);
    }
  }

  public void install(String [] names) {
    for (String name : names) {
      install(name);
    }
  }

  public void install(String name) {
    // find artifact name
    for (AvailableInterpreterInfo info : availableInterpreters) {
      if (name.equals(info.name)) {
        install(name, info.artifact);
        return;
      }
    }

    throw new RuntimeException("Can't find interpreter '" + name + "'");
  }

  public void install(String [] names, String [] artifacts) {
    if (names.length != artifacts.length) {
      throw new RuntimeException("Length of given names and artifacts are different");
    }

    for (int i = 0; i < names.length; i++) {
      install(names[i], artifacts[i]);
    }
  }

  public void install(String name, String artifact) {
    DependencyResolver depResolver = new DependencyResolver(localRepoDir);
    if (proxyUrl != null) {
      depResolver.setProxy(proxyUrl, proxyUser, proxyPassword);
    }

    File installDir = new File(interpreterBaseDir, name);
    if (installDir.exists()) {
      System.err.println("Directory " + installDir.getAbsolutePath()
        + " already exists"
        + "\n\nSkipped");
      return;
    }

    System.out.println("Install " + name + "(" + artifact + ") to "
        + installDir.getAbsolutePath() + " ... ");

    try {
      depResolver.load(artifact, installDir);
      System.out.println("Interpreter " + name + " installed under " +
          installDir.getAbsolutePath() + ".");
      startTip();
    } catch (RepositoryException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void setProxy(URL proxyUrl, String proxyUser, String proxyPassword) {
    this.proxyUrl = proxyUrl;
    this.proxyUser = proxyUser;
    this.proxyPassword = proxyPassword;
  }

  public static void usage() {
    System.out.println("Options");
    System.out.println("  -l, --list                   List available interpreters");
    System.out.println("  -a, --all                    Install all available interpreters");
    System.out.println("  -n, --name       [NAMES]     Install interpreters (comma separated " +
        "list)" +
        "e.g. md,shell,jdbc,python,angular");
    System.out.println("  -t, --artifact   [ARTIFACTS] (Optional with -n) custom artifact names" +
        ". " +
        "(comma separated list correspond to --name) " +
        "e.g. customGroup:customArtifact:customVersion");
    System.out.println("  --proxy-url      [url]       (Optional) proxy url. http(s)://host:port");
    System.out.println("  --proxy-user     [user]      (Optional) proxy user");
    System.out.println("  --proxy-password [password]  (Optional) proxy password");
  }

  public static void main(String [] args) throws IOException {
    if (args.length == 0) {
      usage();
      return;
    }

    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    InstallInterpreter installer = new InstallInterpreter(
        new File(conf.getInterpreterListPath()),
        new File(conf.getInterpreterDir()),
        conf.getInterpreterLocalRepoPath());

    String names = null;
    String artifacts = null;
    URL proxyUrl = null;
    String proxyUser = null;
    String proxyPassword = null;
    boolean all = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i].toLowerCase(Locale.US);
      switch (arg) {
        case "--list":
        case "-l":
          installer.list();
          System.exit(0);
          break;
        case "--all":
        case "-a":
          all = true;
          break;
        case "--name":
        case "-n":
          names = args[++i];
          break;
        case "--artifact":
        case "-t":
          artifacts = args[++i];
          break;
        case "--version":
        case "-v":
          Util.getVersion();
          break;
        case "--proxy-url":
          proxyUrl = new URL(args[++i]);
          break;
        case "--proxy-user":
          proxyUser = args[++i];
          break;
        case "--proxy-password":
          proxyPassword = args[++i];
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

    if (proxyUrl != null) {
      installer.setProxy(proxyUrl, proxyUser, proxyPassword);
    }

    if (all) {
      installer.installAll();
      System.exit(0);
    }

    if (names != null) {
      if (artifacts != null) {
        installer.install(names.split(","), artifacts.split(","));
      } else {
        installer.install(names.split(","));
      }
    }
  }

  private static void startTip() {
    System.out.println("\n1. Restart Zeppelin"
      + "\n2. Create interpreter setting in 'Interpreter' menu on Zeppelin GUI"
      + "\n3. Then you can bind the interpreter on your note");
  }
}
