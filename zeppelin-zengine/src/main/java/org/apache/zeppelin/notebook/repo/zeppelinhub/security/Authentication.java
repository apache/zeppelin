package org.apache.zeppelin.notebook.repo.zeppelinhub.security;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
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

  private final HttpClient client;
  private String loginEndpoint;

  // Cipher is an AES in CBC mode
  private static final String CIPHER_ALGORITHM = "AES";
  private static final String CIPHER_MODE = "AES/CBC/PKCS5PADDING";
  private static final int ivSize = 16;

  private static final String ZEPPELINHUB_USER_KEY = "zeppelinhub.user.key";
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
    MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
    client = new HttpClient(connectionManager);
    this.token = token;

    authEnabled = !conf.isAnonymousAllowed();

    userKey = conf.getString("ZEPPELINHUB_USER_KEY",
        ZEPPELINHUB_USER_KEY, "");

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
    int port = conf.getInt("ZEPPELIN_PORT", "zeppelin.server.port" , 8080);
    if (port <= 0) {
      port = 8080;
    }
    String scheme = "http";
    if (conf.useSsl()) {
      scheme = "https";
    }
    String endpoint = scheme + "://localhost:" + port + "/api/login";
    return endpoint;
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

      byte[] decryptedString = Base64.decodeBase64(toBytes(value));
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
    PostMethod post = new PostMethod(endpoint);
    post.addRequestHeader("Origin", "http://localhost");
    post.addParameter(new NameValuePair("userName", credentials[0]));
    post.addParameter(new NameValuePair("password", credentials[1]));
    try {
      int code = client.executeMethod(post);
      if (code == HttpStatus.SC_OK) {
        String content = post.getResponseBodyAsString();
        Map<String, Object> resp = gson.fromJson(content, 
            new TypeToken<Map<String, Object>>() {}.getType());
        LOG.info("Received from Zeppelin LoginRestApi : " + content);
        return (Map<String, String>) resp.get("body");
      } else {
        LOG.error("Failed Zeppelin login {}, status code {}", endpoint, code);
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

  private byte[] toBytes(String value) {
    byte[] bytes;
    try {
      bytes = value.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      LOG.warn("UTF-8 isn't supported ", e);
      bytes = value.getBytes();
    }
    return bytes;
  }

  private IvParameterSpec generateIV(String ivString) {
    byte[] ivFromBytes = toBytes(ivString);
    byte[] iv16ToBytes = new byte[ivSize];
    System.arraycopy(ivFromBytes, 0, iv16ToBytes, 0, Math.min(ivFromBytes.length, ivSize));
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
