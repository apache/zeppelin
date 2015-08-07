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
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

/**
 * 
 * @author vgmartinez
 *
 */
public class Credentials {

  // Use a credential provider chain so that instance profiles can be utilized
  // on an EC2 instance. The order of locations where credentials are searched
  // is documented here
  //
  //    http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/
  //        auth/DefaultAWSCredentialsProviderChain.html
  //
  // In summary, the order is:
  //
  //  1. Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
  //  2. Java System Properties - aws.accessKeyId and aws.secretKey
  //  3. Credential profiles file at the default location (~/.aws/credentials)
  //       shared by all AWS SDKs and the AWS CLI
  //  4. Instance profile credentials delivered through the Amazon EC2 metadata service

  private static AWSCredentialsProviderChain credProvider =
      new DefaultAWSCredentialsProviderChain();

  private static AWSCredentials credentials = credProvider.getCredentials();

  public AWSCredentials getCredentials() {
    return credentials;
  }

  public static void setCredentials(AWSCredentials credentials) {
    Credentials.credentials = credentials;
  }
}

