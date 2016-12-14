## Enabling SSL
Enabling SSL requires a few configuration changes. First you need to create certificates and then update necessary configurations to enable server side SSL and/or client side certificate authentication.

### Creating and configuring the Certificates

Information how about to generate certificates and a keystore can be found [here](https://wiki.eclipse.org/Jetty/Howto/Configure_SSL).

A condensed example can be found in the top answer to this [StackOverflow post](http://stackoverflow.com/questions/4008837/configure-ssl-on-jetty).

The keystore holds the private key and certificate on the server end. The trustore holds the trusted client certificates. Be sure that the path and password for these two stores are correctly configured in the password fields below. They can be obfuscated using the Jetty password tool. After Maven pulls in all the dependency to build Zeppelin, one of the Jetty jars contain the Password tool. Invoke this command from the Zeppelin home build directory with the appropriate version, user, and password.

```
java -cp ./zeppelin-server/target/lib/jetty-all-server-<version>.jar org.eclipse.jetty.util.security.Password <user> <password>
```

If you are using a self-signed, a certificate signed by an untrusted CA, or if client authentication is enabled, then the client must have a browser create exceptions for both the normal HTTPS port and WebSocket port. This can by done by trying to establish an HTTPS connection to both ports in a browser (i.e. if the ports are 443 and 8443, then visit https://127.0.0.1:443 and https://127.0.0.1:8443). This step can be skipped if the server certificate is signed by a trusted CA and client auth is disabled.

### Configuring server side SSL

The following properties needs to be updated in the **zeppeling-site.xml** in order to enable server side SSL.

```
<property>
  <name>zeppelin.server.ssl.port</name>
  <value>8443</value>
  <description>Server ssl port. (used when ssl property is set to true)</description>
</property>

<property>
  <name>zeppelin.ssl</name>
  <value>true</value>
  <description>Should SSL be used by the servers?</description>
</property>

<property>
  <name>zeppelin.ssl.keystore.path</name>
  <value>keystore</value>
  <description>Path to keystore relative to Zeppelin configuration directory</description>
</property>

<property>
  <name>zeppelin.ssl.keystore.type</name>
  <value>JKS</value>
  <description>The format of the given keystore (e.g. JKS or PKCS12)</description>
</property>

<property>
  <name>zeppelin.ssl.keystore.password</name>
  <value>change me</value>
  <description>Keystore password. Can be obfuscated by the Jetty Password tool</description>
</property>

<property>
  <name>zeppelin.ssl.key.manager.password</name>
  <value>change me</value>
  <description>Key Manager password. Defaults to keystore password. Can be obfuscated.</description>
</property>
```


### Enabling client side certificate authentication

The following properties needs to be updated in the **zeppeling-site.xml** in order to enable client side certificate authentication.

```
<property>
  <name>zeppelin.server.ssl.port</name>
  <value>8443</value>
  <description>Server ssl port. (used when ssl property is set to true)</description>
</property>

<property>
  <name>zeppelin.ssl.client.auth</name>
  <value>true</value>
  <description>Should client authentication be used for SSL connections?</description>
</property>

<property>
  <name>zeppelin.ssl.truststore.path</name>
  <value>truststore</value>
  <description>Path to truststore relative to Zeppelin configuration directory. Defaults to the keystore path</description>
</property>

<property>
  <name>zeppelin.ssl.truststore.type</name>
  <value>JKS</value>
  <description>The format of the given truststore (e.g. JKS or PKCS12). Defaults to the same type as the keystore type</description>
</property>

<!--
<property>
  <name>zeppelin.ssl.truststore.password</name>
  <value>change me</value>
  <description>Truststore password. Can be obfuscated by the Jetty Password tool. Defaults to the keystore password</description>
</property>
-->
```


**Note:** After updating these configurations, Zeppelin server needs to be restarted.
