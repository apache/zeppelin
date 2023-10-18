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

package org.apache.zeppelin.livy.cluster;

import java.io.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.HashMap;
import java.util.Optional;
import java.util.Properties;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

/**
 * A common interface to run test on real cluster and mini cluster.
 */
public interface LivyCluster {

    void deploy();

    void cleanUp();

    File configDir();

    void runLivy();

    void stopLivy();

    String livyEndpoint();

    Optional<String> jdbcEndpoint();

    Path hdfsScratchDir();

    //
    //  // The potential values for authScheme are kerberos for kerberos auth,
    //  // basic for basic auth, or nothing for no authentication
    //  def authScheme: String
    //  def user: String
    //  def password: String
    //  def sslCertPath: String
    //
    //  def principal: String
    //  def keytabPath: String
    //
    //  def doAsClusterUser[T](task: => T): T
    //
    //  def initKerberosConf(): Configuration = {
    //    val conf = new Configuration(false)
    //    configDir().listFiles().foreach { f =>
    //      if (f.getName().endsWith(".xml")) {
    //        conf.addResource(new Path(f.toURI()))
    //      }
    //    }
    //    UserGroupInformation.setConfiguration(conf)
    //    UserGroupInformation.loginUserFromKeytab(principal, keytabPath)
    //    conf
    //  }

//    lazy val hadoopConf = {
//        var conf = new Configuration(false)
//
//        if (authScheme == "kerberos"){
//            conf = initKerberosConf()
//        }
//        configDir().listFiles().foreach { f =>
//            if (f.getName().endsWith(".xml")) {
//                conf.addResource(new Path(f.toURI()))
//            }
//        }
//        conf
//    }
//
//    lazy val yarnConf = {
//        var conf = new Configuration(false)
//
//        if (authScheme == "kerberos"){
//            conf = initKerberosConf()
//        }
//        conf.addResource(new Path(s"${configDir().getCanonicalPath}/yarn-site.xml"))
//        conf
//    }
//
}

