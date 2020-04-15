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
package org.apache.zeppelin.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

public class PEMImporterTest {
    private final File pemkey = new File(this.getClass().getResource("/example-pem-files/zeppelin.com.key").getFile());
    private final File pemCert = new File(this.getClass().getResource("/example-pem-files/zeppelin.com.crt").getFile());
    private final File rootCACert = new File(this.getClass().getResource("/example-pem-files/rootCA.crt").getFile());
    private final File privkeyWithPasswordPKCS1 = new File(this.getClass().getResource("/example-pem-files/privkey_with_password_PKCS_1.pem").getFile());
    private final File privkeyWithPasswordPKCS8 = new File(this.getClass().getResource("/example-pem-files/privkey_with_password_PKCS_8.pem").getFile());
    private final File privkeyWithoutPasswordPKCS1 = new File(this.getClass().getResource("/example-pem-files/privkey_without_password_PKCS_1.pem").getFile());
    private final File privkeyWithoutPasswordPKCS8 = new File(this.getClass().getResource("/example-pem-files/privkey_without_password_PKCS_8.pem").getFile());

    @Test
    public void testParsingPKCS1WithoutPassword() throws IOException, GeneralSecurityException {
        KeyStore keystore = PEMImporter.loadKeyStore(pemCert, privkeyWithoutPasswordPKCS1, "");
        assertEquals(1, keystore.size());
        assertTrue(keystore.containsAlias("key"));
        assertEquals(1, keystore.getCertificateChain("key").length);
    }

    @Test
    public void testParsingPKCS1WithPassword() throws IOException, GeneralSecurityException {
        KeyStore keystore = PEMImporter.loadKeyStore(pemCert, privkeyWithPasswordPKCS1, "test");
        assertEquals(1, keystore.size());
        assertTrue(keystore.containsAlias("key"));
        assertEquals(1, keystore.getCertificateChain("key").length);
    }

    @Test(expected = GeneralSecurityException.class)
    public void testParsingPKCS1WithWrongPassword() throws IOException, GeneralSecurityException {
        PEMImporter.loadKeyStore(pemCert, privkeyWithPasswordPKCS1, "nottest");
    }

    @Test
    public void testParsingPKCS8WithoutPassword() throws IOException, GeneralSecurityException {
        KeyStore keystore = PEMImporter.loadKeyStore(pemCert, privkeyWithoutPasswordPKCS8, "");
        assertEquals(1, keystore.size());
        assertTrue(keystore.containsAlias("key"));
        assertEquals(1, keystore.getCertificateChain("key").length);
    }

    @Test
    public void testParsingPKCS8WithPassword() throws IOException, GeneralSecurityException {
        KeyStore keystore = PEMImporter.loadKeyStore(pemCert, privkeyWithPasswordPKCS8, "test");
        assertEquals(1, keystore.size());
        assertTrue(keystore.containsAlias("key"));
        assertEquals(1, keystore.getCertificateChain("key").length);
    }

    @Test(expected = GeneralSecurityException.class)
    public void testParsingPKCS8WithWrongPassword() throws IOException, GeneralSecurityException {
        PEMImporter.loadKeyStore(pemCert, privkeyWithPasswordPKCS8, "nottest");
    }

    @Test
    public void testCertKeyAndChain() throws Exception {
        KeyStore truststore = PEMImporter.loadTrustStore(rootCACert);
        KeyStore keystore = PEMImporter.loadKeyStore(pemCert, pemkey, "");
        assertEquals(1, keystore.size());
        assertTrue(keystore.containsAlias("key"));
        assertEquals(1, keystore.getCertificateChain("key").length);
        assertEquals(1, truststore.size());
        Certificate[] certs = keystore.getCertificateChain("key");

        PublicKey publicKey = certs[0].getPublicKey();
        PrivateKey privateKey = (PrivateKey) keystore.getKey("key", "".toCharArray());
        assertTrue(verifyPubAndPrivKey(publicKey, privateKey));

        X509Certificate ca = (X509Certificate) truststore.getCertificate("cn=localhost");
        X509Certificate cert = (X509Certificate) keystore.getCertificate("key");
        assertTrue(verifyChain(cert, ca));
    }

    private boolean verifyPubAndPrivKey(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        // create a challenge
        byte[] challenge = new byte[10000];
        ThreadLocalRandom.current().nextBytes(challenge);
        // sign using the private key
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(challenge);
        byte[] signature = sig.sign();
        // verify signature using the public key
        sig.initVerify(publicKey);
        sig.update(challenge);
        return sig.verify(signature);
    }

    private boolean verifyChain(X509Certificate cert, X509Certificate ca) {
        return cert.getIssuerX500Principal().equals(ca.getSubjectX500Principal());
    }
}
