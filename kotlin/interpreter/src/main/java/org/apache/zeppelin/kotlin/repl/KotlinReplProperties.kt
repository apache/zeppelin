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

package org.apache.zeppelin.kotlin.repl.building;

import static kotlin.script.experimental.jvm.JvmScriptingHostConfigurationKt.getDefaultJvmScriptingHostConfiguration;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kotlin.script.experimental.host.ScriptingHostConfiguration;
import org.apache.zeppelin.kotlin.context.KotlinReceiver;

/**
 * Class that holds properties for Kotlin REPL creation,
 * namely implicit receiver, classpath, preloaded code, directory for class bytecode output,
 * max result limit and shortening types flag.
 *
 * Set its parameters by chaining corresponding methods, e.g.
 * properties.outputDir(dir).shortenTypes(false)
 *
 * Get its parameters via getters.
 */
public class KotlinReplProperties {

  private ScriptingHostConfiguration hostConf = getDefaultJvmScriptingHostConfiguration();

  private KotlinReceiver receiver;
  private Set<String> classpath;
  private List<String> codeOnLoad;
  private String outputDir;
  private int maxResult = 1000;
  private boolean shortenTypes = true;

  public KotlinReplProperties() {
    this.receiver = new KotlinReceiver();

    this.classpath = new HashSet<>();
    String[] javaClasspath = System.getProperty("java.class.path").split(File.pathSeparator);
    Collections.addAll(classpath, javaClasspath);

    this.codeOnLoad = new ArrayList<>();
  }

  public KotlinReplProperties receiver(KotlinReceiver receiver) {
    this.receiver = receiver;
    return this;
  }

  public KotlinReplProperties classPath(String path) {
    this.classpath.add(path);
    return this;
  }

  public KotlinReplProperties classPath(Collection<String> paths) {
    this.classpath.addAll(paths);
    return this;
  }

  public KotlinReplProperties codeOnLoad(String code) {
    this.codeOnLoad.add(code);
    return this;
  }

  public KotlinReplProperties codeOnLoad(Collection<String> code) {
    this.codeOnLoad.addAll(code);
    return this;
  }

  public KotlinReplProperties outputDir(String outputDir) {
    this.outputDir = outputDir;
    return this;
  }

  public KotlinReplProperties maxResult(int maxResult) {
    this.maxResult = maxResult;
    return this;
  }

  public KotlinReplProperties shortenTypes(boolean shortenTypes) {
    this.shortenTypes = shortenTypes;
    return this;
  }

  public ScriptingHostConfiguration getHostConf() {
    return hostConf;
  }

  public KotlinReceiver getReceiver() {
    return receiver;
  }

  public Set<String> getClasspath() {
    return classpath;
  }

  public List<String> getCodeOnLoad() {
    return codeOnLoad;
  }

  public String getOutputDir() {
    return outputDir;
  }

  public int getMaxResult() {
    return maxResult;
  }

  public boolean getShortenTypes() {
    return shortenTypes;
  }
}
