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
package org.apache.zeppelin.realm.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class KnoxJwtRealmTest {

  private KnoxJwtRealm knoxJwtRealm;

  @BeforeEach
  void setUp() throws Exception {
    knoxJwtRealm = new KnoxJwtRealm();
  }

  private SignedJWT createJWT(String subject, Date expiration) throws Exception {
    JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
        .subject(subject)
        .issuer("KNOXSSO")
        .issueTime(new Date());
    
    if (expiration != null) {
      claimsBuilder.expirationTime(expiration);
    }
    
    JWTClaimsSet claimsSet = claimsBuilder.build();
    SignedJWT signedJWT = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
        claimsSet
    );

    // Note: We don't sign the JWT for these tests as we're only testing expiration validation
    return signedJWT;
  }

  @Test
  void testValidateExpiration_WithNullExpiration_ShouldReturnFalse() throws Exception {
    // Given: JWT token without expiration time
    SignedJWT jwtWithoutExpiration = createJWT("testuser", null);
    
    // When: validating expiration
    boolean result = knoxJwtRealm.validateExpiration(jwtWithoutExpiration);
    
    // Then: should return false
    assertFalse(result, "JWT token without expiration time should be rejected");
  }

  @Test
  void testValidateExpiration_WithValidFutureExpiration_ShouldReturnTrue() throws Exception {
    // Given: JWT token with future expiration
    Date futureDate = new Date(System.currentTimeMillis() + 3600000); // 1 hour from now
    SignedJWT jwtWithValidExpiration = createJWT("testuser", futureDate);
    
    // When: validating expiration
    boolean result = knoxJwtRealm.validateExpiration(jwtWithValidExpiration);
    
    // Then: should return true
    assertTrue(result, "JWT token with valid future expiration should be accepted");
  }

  @Test
  void testValidateExpiration_WithPastExpiration_ShouldReturnFalse() throws Exception {
    // Given: JWT token with past expiration
    Date pastDate = new Date(System.currentTimeMillis() - 3600000); // 1 hour ago
    SignedJWT jwtWithPastExpiration = createJWT("testuser", pastDate);
    
    // When: validating expiration
    boolean result = knoxJwtRealm.validateExpiration(jwtWithPastExpiration);
    
    // Then: should return false
    assertFalse(result, "JWT token with past expiration should be rejected");
  }

  // Note: Full token validation tests are omitted as they require complex setup
  // including certificate files and signature validation. The core expiration 
  // validation logic is tested above through direct method calls.
}
