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

package org.apache.zeppelin.dep;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;


import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.zeppelin.common.JsonSerializable;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Repository configuration for Maven dependency resolution.
 * 
 * <p>This class represents a Maven repository configuration that can be used
 * for dependency resolution. It supports authentication, proxy settings,
 * and both release and snapshot repositories.</p>
 * 
 * <p>All input parameters are validated to ensure configuration integrity:</p>
 * <ul>
 *   <li>Repository ID must contain only alphanumeric characters, dots, underscores, and hyphens</li>
 *   <li>URL must be a valid HTTP, HTTPS, or FILE protocol URL</li>
 *   <li>Credentials require both username and password</li>
 *   <li>Proxy settings require valid protocol, host, and port</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * Repository repo = new Repository("central")
 *     .url("https://repo.maven.apache.org/maven2/")
 *     .snapshot();
 * 
 * Repository privateRepo = new Repository("private")
 *     .url("https://private.repo/maven2/")
 *     .credentials("username", "password")
 *     .proxy("HTTP", "proxy.host", 8080, "proxyUser", "proxyPass");
 * }</pre>
 * 
 * @see RemoteRepository
 * @see RepositoryException
 */
public class Repository implements JsonSerializable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Repository.class);
  private static final Gson gson = new Gson();
  private static final Pattern REPOSITORY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

  private boolean snapshot = false;
  private String id;
  private String url;
  private String username = null;
  private String password = null;
  private String proxyProtocol = "HTTP";
  private String proxyHost = null;
  private Integer proxyPort = null;
  private String proxyLogin = null;
  private String proxyPassword = null;

  /**
   * Creates a new Repository with the specified ID.
   * 
   * @param id the repository ID, must contain only alphanumeric characters, dots, underscores, and hyphens
   * @throws RepositoryException if the ID is null, empty, or contains invalid characters
   */
  public Repository(String id){
    validateId(id);
    this.id = id;
  }

  /**
   * Sets the repository URL.
   * 
   * @param url the repository URL, must be a valid HTTP, HTTPS, or FILE protocol URL
   * @return this Repository instance for method chaining
   * @throws RepositoryException if the URL is null, empty, or has invalid format/protocol
   */
  public Repository url(String url) {
    validateUrl(url);
    this.url = url;
    return this;
  }

  public Repository snapshot() {
    snapshot = true;
    return this;
  }

  public boolean isSnapshot() {
    return snapshot;
  }

  public String getId() {
    return id;
  }

  public String getUrl() {
    return url;
  }
  
  public Repository username(String username) {
    this.username = username;
    return this;
  }
  
  public Repository password(String password) {
    this.password = password;
    return this;
  }
  
  public Repository credentials(String username, String password) {
    validateCredentials(username, password);
    this.username = username;
    this.password = password;
    return this;
  }
  
  public Repository proxy(String protocol, String host, int port, String username, String password) {
    validateProxy(protocol, host, port);
    this.proxyProtocol = protocol;
    this.proxyHost = host;
    this.proxyPort = port;
    this.proxyLogin = username;
    this.proxyPassword = password;
    return this;
  }
  
  public Authentication getAuthentication() {
    Authentication auth = null;
    if (this.username != null && this.password != null) {
      auth = new AuthenticationBuilder().addUsername(this.username).addPassword(this.password).build();
    }
    return auth;
  }

  public Proxy getProxy() {
    if (isNotBlank(proxyHost) && proxyPort != null) {
      if (isNotBlank(proxyLogin)) {
        return new Proxy(proxyProtocol, proxyHost, proxyPort,
                new AuthenticationBuilder().addUsername(proxyLogin).addPassword(proxyPassword).build());
      } else {
        return new Proxy(proxyProtocol, proxyHost, proxyPort, null);
      }
    }
    return null;
  }

  public String toJson() {
    return gson.toJson(this);
  }

  /**
   * Creates a Repository instance from JSON string.
   * 
   * @param json the JSON string representing a Repository
   * @return the Repository instance parsed from JSON
   * @throws RepositoryException if the JSON is null, empty, invalid format, or contains invalid data
   */
  public static Repository fromJson(String json) {
    if (isBlank(json)) {
      throw new RepositoryException("JSON string cannot be null or empty");
    }
    try {
      Repository repository = gson.fromJson(json, Repository.class);
      if (repository == null) {
        throw new RepositoryException("Failed to parse JSON: resulted in null repository");
      }
      // Validate the parsed repository
      validateRepository(repository);
      return repository;
    } catch (JsonSyntaxException e) {
      LOGGER.error("Failed to parse Repository JSON: {}", json, e);
      throw new RepositoryException("Invalid JSON format for Repository: " + e.getMessage(), e);
    }
  }

  /**
   * Converts this repository to Maven RemoteRepository.
   * 
   * @return the RemoteRepository instance configured from this Repository
   * @throws RepositoryException if the repository ID or URL is missing
   */
  public RemoteRepository toRemoteRepository() {
    validateForRemoteRepository();
    RepositoryPolicy policy = new RepositoryPolicy(
        true,
        RepositoryPolicy.UPDATE_POLICY_DAILY,
        RepositoryPolicy.CHECKSUM_POLICY_WARN);
    RemoteRepository.Builder builder = new RemoteRepository.Builder(id, "default", url);
    if (isSnapshot()) {
      builder.setSnapshotPolicy(policy);
    } else {
      builder.setPolicy(policy);
    }
    if (getAuthentication() != null) {
      builder.setAuthentication(getAuthentication());
    }
    if (getProxy() != null) {
      builder.setProxy(getProxy());
    }
    return builder.build();
  }

  /**
   * Creates a Repository instance from Maven RemoteRepository.
   * 
   * <p>Note: Credentials will not be populated because they are not accessible from RemoteRepository.</p>
   * 
   * @param repo the RemoteRepository to convert from
   * @return the Repository instance created from the RemoteRepository
   * @throws RepositoryException if the RemoteRepository is null
   */
  public static Repository fromRemoteRepository(RemoteRepository repo) {
    if (repo == null) {
      throw new RepositoryException("RemoteRepository cannot be null");
    }
    Repository r = new Repository(repo.getId());
    r.url(repo.getUrl());
    if (repo.getPolicy(true) != null && repo.getPolicy(true).isEnabled()) {
      r.snapshot();
    }
    return r;
  }

  // Validation methods
  private static void validateId(String id) {
    if (isBlank(id)) {
      throw new RepositoryException("Repository ID cannot be null or empty");
    }
    if (!REPOSITORY_ID_PATTERN.matcher(id).matches()) {
      throw new RepositoryException("Repository ID must contain only alphanumeric characters, dots, underscores, and hyphens: " + id);
    }
  }

  private static void validateUrl(String url) {
    if (isBlank(url)) {
      throw new RepositoryException("Repository URL cannot be null or empty");
    }
    try {
      URL parsedUrl = new URL(url);
      String protocol = parsedUrl.getProtocol();
      if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol) && !"file".equalsIgnoreCase(protocol)) {
        throw new RepositoryException("Repository URL must use HTTP, HTTPS, or FILE protocol, but was: " + protocol);
      }
    } catch (MalformedURLException e) {
      throw new RepositoryException("Invalid URL format: " + url, e);
    }
  }

  private static void validateCredentials(String username, String password) {
    if (isBlank(username) && isNotBlank(password)) {
      throw new RepositoryException("Username cannot be empty when password is provided");
    }
    if (isNotBlank(username) && isBlank(password)) {
      throw new RepositoryException("Password cannot be empty when username is provided");
    }
  }

  private static void validateProxy(String protocol, String host, int port) {
    if (isBlank(protocol)) {
      throw new RepositoryException("Proxy protocol cannot be null or empty");
    }
    if (isBlank(host)) {
      throw new RepositoryException("Proxy host cannot be null or empty");
    }
    if (port <= 0 || port > 65535) {
      throw new RepositoryException("Proxy port must be between 1 and 65535, but was: " + port);
    }
    if (!"HTTP".equalsIgnoreCase(protocol) && !"HTTPS".equalsIgnoreCase(protocol)) {
      throw new RepositoryException("Proxy protocol must be HTTP or HTTPS, but was: " + protocol);
    }
  }

  private static void validateRepository(Repository repository) {
    if (isBlank(repository.getId())) {
      throw new RepositoryException("Repository ID cannot be null or empty in parsed JSON");
    }
    // URL validation is optional for parsed repositories to maintain backward compatibility
    if (isNotBlank(repository.getUrl())) {
      validateUrl(repository.getUrl());
    }
  }

  private void validateForRemoteRepository() {
    if (isBlank(this.id)) {
      throw new RepositoryException("Repository ID is required for RemoteRepository conversion");
    }
    if (isBlank(this.url)) {
      throw new RepositoryException("Repository URL is required for RemoteRepository conversion");
    }
  }
}
