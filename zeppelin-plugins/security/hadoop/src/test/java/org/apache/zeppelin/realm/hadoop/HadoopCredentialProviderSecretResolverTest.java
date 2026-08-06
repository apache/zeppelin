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
package org.apache.zeppelin.realm.hadoop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.alias.CredentialProvider;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HadoopCredentialProviderSecretResolverTest {

  @TempDir
  Path tempDir;

  @Test
  void resolvesExistingJceksAlias() throws Exception {
    String providerPath = "jceks://file" + tempDir.resolve("zeppelin.jceks").toAbsolutePath();
    Configuration configuration = new Configuration();
    configuration.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH, providerPath);
    CredentialProvider provider = CredentialProviderFactory.getProviders(configuration).get(0);
    provider.createCredentialEntry("ldapRealm.systemPassword", "secret".toCharArray());
    provider.flush();

    HadoopCredentialProviderSecretResolver resolver =
        new HadoopCredentialProviderSecretResolver();

    assertArrayEquals(
        "secret".toCharArray(),
        resolver.resolve(providerPath, "ldapRealm.systemPassword"));
  }
}
