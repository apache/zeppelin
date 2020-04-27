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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class PEMImporter {
     private PEMImporter() {
        // do nothing
    }

    public static KeyStore loadTrustStore(File certificateChainFile)
        throws IOException, GeneralSecurityException
    {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        List<X509Certificate> certificateChain = readCertificateChain(certificateChainFile);
        for (X509Certificate certificate : certificateChain) {
            X500Principal principal = certificate.getSubjectX500Principal();
            keyStore.setCertificateEntry(principal.getName("RFC2253"), certificate);
        }
        return keyStore;
    }

    public static KeyStore loadKeyStore(File certificateChainFile, File privateKeyFile, String keyPassword)
        throws IOException, GeneralSecurityException
    {
        PrivateKey key;
        try {
            key = createPrivateKey(privateKeyFile, keyPassword);
        } catch (OperatorCreationException | IOException | GeneralSecurityException | PKCSException e) {
            throw new GeneralSecurityException("Private Key issues", e);
        }

        List<X509Certificate> certificateChain = readCertificateChain(certificateChainFile);
        if (certificateChain.isEmpty()) {
            throw new CertificateException("Certificate file does not contain any certificates: " + certificateChainFile);
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("key", key, keyPassword.toCharArray(), certificateChain.stream().toArray(Certificate[]::new));
        return keyStore;
    }

    private static List<X509Certificate> readCertificateChain(File certificateChainFile)
        throws IOException, GeneralSecurityException
    {
        final List<X509Certificate> certs = new ArrayList<>();
        try(final PemReader pemReader = new PemReader(Files.newBufferedReader(certificateChainFile.toPath())))
        {
            final PemObject pemObject = pemReader.readPemObject();
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
            final ByteArrayInputStream bais = new ByteArrayInputStream(pemObject.getContent());

            for (final Certificate cert : certificateFactory.generateCertificates(bais)) {
                if (cert instanceof X509Certificate) {
                    certs.add((X509Certificate) cert);
                }
            }
            if (certs.isEmpty()) {
                throw new IllegalStateException("Unable to decode certificate chain");
            }
        }
        return certs;
    }

    private static PrivateKey createPrivateKey(File privateKeyPem, String keyPassword) throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        // add provider only if it's not in the JVM
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(privateKeyPem.toPath()))) {
            Object privateKeyObject = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            KeyPair kp;
            if (privateKeyObject instanceof PEMEncryptedKeyPair) {
                // Encrypted key - we will use provided password
                PEMEncryptedKeyPair ckp = (PEMEncryptedKeyPair) privateKeyObject;
                PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(keyPassword.toCharArray());
                kp = converter.getKeyPair(ckp.decryptKeyPair(decProv));
            }
            else if (privateKeyObject instanceof PEMKeyPair)
            {
                // Unencrypted key - no password needed
                PEMKeyPair ukp = (PEMKeyPair) privateKeyObject;
                kp = converter.getKeyPair(ukp);
            }
            else if (privateKeyObject instanceof PrivateKeyInfo)
            {
                PrivateKeyInfo pki = (PrivateKeyInfo) privateKeyObject;
                return converter.getPrivateKey(pki);
            }
            else if (privateKeyObject instanceof PKCS8EncryptedPrivateKeyInfo)
            {
                PKCS8EncryptedPrivateKeyInfo ckp = (PKCS8EncryptedPrivateKeyInfo) privateKeyObject;
                InputDecryptorProvider devProv = new JceOpenSSLPKCS8DecryptorProviderBuilder().build(keyPassword.toCharArray());
                return converter.getPrivateKey(ckp.decryptPrivateKeyInfo(devProv));
            }
            else
            {
                throw new GeneralSecurityException("Unsupported key type: " + privateKeyObject.getClass());
            }
            return kp.getPrivate();
        }
    }
}
