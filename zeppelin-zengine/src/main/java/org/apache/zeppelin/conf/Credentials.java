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


package org.apache.zeppelin.conf;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;

/**
 * 
 * @author vgmartinez
 *
 */
public class Credentials {
  static String aws_access_key_id = System.getenv("AWS_ACCESS_KEY_ID");
  static String aws_secret_access_key = System.getenv("AWS_SECRET_ACCESS_KEY");
  
  private static AWSCredentials credentials = new BasicAWSCredentials(aws_access_key_id,
      aws_secret_access_key);

  public AWSCredentials getCredentials() {
    return credentials;
  }

  public static void setCredentials(AWSCredentials credentials) {
    Credentials.credentials = credentials;
  }
}
