## Enabling SSL
Enabling SSL requires a few changes. The first is to set zeppelin.ssl to true. If you'll like to use client side certificate authentication as well, then set zeppelin.ssl.client.auth to true too.

Information how about to generate certificates and a keystore can be found [here](https://wiki.eclipse.org/Jetty/Howto/Configure_SSL).

A condensed example can be found in the top answer to this [StackOverflow post](http://stackoverflow.com/questions/4008837/configure-ssl-on-jetty).

The keystore holds the private key and certificate on the server end. The trustore holds the trusted client certificates. Be sure that the path and password for these two stores are correctly configured in the password fields below. They can be obfuscated using the Jetty password tool. After Maven pulls in all the dependency to build Zeppelin, one of the Jetty jars contain the Password tool. Invoke this command from the Zeppelin home build directory with the appropriate version, user, and password.

```
java -cp ./zeppelin-server/target/lib/jetty-all-server-<version>.jar org.eclipse.jetty.util.security.Password <user> <password>
```

If you are using a self-signed, a certificate signed by an untrusted CA, or if client authentication is enabled, then the client must have a browser create exceptions for both the normal HTTPS port and WebSocket port. This can by done by trying to establish an HTTPS connection to both ports in a browser (i.e. if the ports are 443 and 8443, then visit https://127.0.0.1:443 and https://127.0.0.1:8443). This step can be skipped if the server certificate is signed by a trusted CA and client auth is disabled.

