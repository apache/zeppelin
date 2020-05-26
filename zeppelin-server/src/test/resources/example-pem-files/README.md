# Generate Certs and Keys

## Create some private Keys

Using password `test`, if needed

```bash
openssl genrsa -aes128 -passout pass:test -out privkey_with_password_PKCS_1.pem 4096
openssl genrsa -aes128 -passout pass:test 4096 | openssl pkcs8 -topk8 -inform pem -outform pem -passin pass:test -passout pass:test -out privkey_with_password_PKCS_8.pem
openssl genrsa -out privkey_without_password_PKCS_1.pem 4096
openssl genrsa 4096 | openssl pkcs8 -topk8 -inform pem -outform pem -nocrypt -out privkey_without_password_PKCS_8.pem
```

## Build a small custom CA

```bash
openssl genrsa -out rootCA.key 4096
openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 1024 -out rootCA.crt -subj '/CN=localhost'
openssl genrsa -out zeppelin.com.key 4096
openssl req -new -sha256 -key zeppelin.com.key -subj "/C=US/ST=CA/O=MyOrg, Inc./CN=zeppelin.com" -out zeppelin.com.csr
openssl x509 -req -in zeppelin.com.csr -CA rootCA.crt -CAkey rootCA.key -CAcreateserial -days 500 -sha256 -out zeppelin.com.crt
```