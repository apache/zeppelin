package org.apache.zeppelin.notebook.repo.zeppelinhub.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Authentication module.
 *
 */
public class Authentication implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Authentication.class);
  private String principal = "anonymous";
  private String ticket = "anonymous";
  private String roles = StringUtils.EMPTY;

  private final CloseableHttpClient client;
  private String loginEndpoint;

  // Cipher is an AES in CBC mode
  private static final String CIPHER_ALGORITHM = "AES";
  private static final String CIPHER_MODE = "AES/CBC/PKCS5PADDING";
  private static final int IV_SIZE = 16;

  private String token;
  private boolean authEnabled;
  private boolean authenticated;
  String userKey;

  private Gson gson = new Gson();
  private static Authentication instance = null;

  public static Authentication initialize(String token, ZeppelinConfiguration conf) {
    if (instance == null && conf != null) {
      instance = new Authentication(token, conf);
    }
    return instance;
  }

  public static Authentication getInstance() {
    return instance;
  }

  private Authentication(String token, ZeppelinConfiguration conf) {
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    client = HttpClients.custom()
    .setConnectionManager(cm)
    .build();

    this.token = token;

    authEnabled = !conf.isAnonymousAllowed();

    userKey = conf.getString(ConfVars.ZEPPELINHUB_USER_KEY);

    loginEndpoint = getLoginEndpoint(conf);
  }

  public String getPrincipal() {
    return this.principal;
  }

  public String getTicket() {
    return this.ticket;
  }

  public String getRoles() {
    return this.roles;
  }

  public boolean isAuthenticated() {
    return authenticated;
  }
  private String getLoginEndpoint(ZeppelinConfiguration conf) {
    int port = conf.getInt(ConfVars.ZEPPELIN_PORT);
    if (port <= 0) {
      port = 8080;
    }
    String scheme = "http";
    if (conf.useSsl()) {
      scheme = "https";
    }
    return scheme + "://localhost:" + port + "/api/login";
  }

  public boolean authenticate() {
    if (authEnabled) {
      if (!StringUtils.isEmpty(userKey)) {
        String authKey = getAuthKey(userKey);
        Map<String, String> authCredentials = login(authKey, loginEndpoint);
        if (isEmptyMap(authCredentials)) {
          return false;
        }
        principal = authCredentials.containsKey("principal") ? authCredentials.get("principal")
            : principal;
        ticket = authCredentials.containsKey("ticket") ? authCredentials.get("ticket") : ticket;
        roles = authCredentials.containsKey("roles") ? authCredentials.get("roles") : roles;
        LOG.info("Authenticated into Zeppelin as {} and roles {}", principal, roles);
        return true;
      } else {
        LOG.warn("ZEPPELINHUB_USER_KEY isn't provided. Please provide your credentials"
            + "for your instance in ZeppelinHub website and generate your key.");
      }
    }
    return false;
  }

  // returns login:password
  private String getAuthKey(String userKey) {
    if (StringUtils.isBlank(userKey)) {
      LOG.warn("ZEPPELINHUB_USER_KEY is blank");
      return StringUtils.EMPTY;
    }
    //use hashed token as a salt
    String hashedToken = Integer.toString(token.hashCode());
    return decrypt(userKey, hashedToken);
  }

  private String decrypt(String value, String initVector) {
    if (StringUtils.isBlank(value) || StringUtils.isBlank(initVector)) {
      LOG.error("String to decode or salt is not provided");
      return StringUtils.EMPTY;
    }
    try {
      IvParameterSpec iv = generateIV(initVector);
      Key key = generateKey();

      Cipher cipher = Cipher.getInstance(CIPHER_MODE);
      cipher.init(Cipher.DECRYPT_MODE, key, iv);

      byte[] decryptedString = Base64.decodeBase64(value.getBytes(StandardCharsets.UTF_8));
      decryptedString = cipher.doFinal(decryptedString);
      return new String(decryptedString);
    } catch (GeneralSecurityException e) {
      LOG.error("Error when decrypting", e);
      return StringUtils.EMPTY;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> login(String authKey, String endpoint) {
    String[] credentials = authKey.split(":");
    if (credentials.length != 2) {
      return Collections.emptyMap();
    }
    HttpPost post = new HttpPost(endpoint);
    ArrayList<NameValuePair> postParameters = new ArrayList<>();
    postParameters.add(new BasicNameValuePair("userName", credentials[0]));
    postParameters.add(new BasicNameValuePair("password", credentials[1]));
    post.setEntity(new UrlEncodedFormEntity(postParameters, StandardCharsets.UTF_8));
    post.addHeader("Origin", "http://localhost");

    try (CloseableHttpResponse postResponse = client.execute(post)) {
      if (postResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        String content = EntityUtils.toString(postResponse.getEntity(), StandardCharsets.UTF_8);
        Map<String, Object> resp = gson.fromJson(content,
            new TypeToken<Map<String, Object>>() {}.getType());
        LOG.info("Received from Zeppelin LoginRestApi : {}", content);
        return (Map<String, String>) resp.get("body");
      } else {
        LOG.error("Failed Zeppelin login {}, status code {}", endpoint, postResponse);
        return Collections.emptyMap();
      }
    } catch (IOException e) {
      LOG.error("Cannot login into Zeppelin", e);
      return Collections.emptyMap();
    }
  }

  private Key generateKey() {
    try {
      KeyGenerator kgen = KeyGenerator.getInstance(CIPHER_ALGORITHM);
      kgen.init(128, new SecureRandom());
      SecretKey secretKey = kgen.generateKey();
      byte[] enCodeFormat = secretKey.getEncoded();
      return new SecretKeySpec(enCodeFormat, CIPHER_ALGORITHM);
    } catch (Exception e) {
      LOG.warn("Cannot generate key for decryption", e);
    }
    return null;
  }


  private IvParameterSpec generateIV(String ivString) {
    byte[] ivFromBytes = ivString.getBytes(StandardCharsets.UTF_8);
    byte[] iv16ToBytes = new byte[IV_SIZE];
    System.arraycopy(ivFromBytes, 0, iv16ToBytes, 0, Math.min(ivFromBytes.length, IV_SIZE));
    return new IvParameterSpec(iv16ToBytes);
  }

  private boolean isEmptyMap(Map<String, String> map) {
    return map == null || map.isEmpty();
  }

  @Override
  public void run() {
    authenticated = authenticate();
    LOG.info("Scheduled authentication status is {}", authenticated);
  }
}
