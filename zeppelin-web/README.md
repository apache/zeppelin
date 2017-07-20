# Zeppelin Web Application

This is Zeppelin's frontend project.

## Development Guide 

### Local Development

It is recommended to install node 6.0.0+ since Zeppelin uses 6.9.1+ (see [creationix/nvm](https://github.com/creationix/nvm))

All build commands are described in [package.json](./package.json)

```sh
# install required depepdencies and bower packages (only once)
$ npm install -g yarn
$ yarn install

# build zeppelin-web for production
$ yarn run build

# run frontend application only in dev mode (localhost:9000) 
# you need to run zeppelin backend instance also
$ yarn run dev
```

Supports the following options with using npm environment variable when running the web development mode.

```
# if you are using a custom port instead of default(8080), 
# you must use the 'SERVER_PORT' variable to run the web application development mode
$ SERVER_PORT=YOUR_ZEPPELIN_PORT yarn run dev

# if you want to use a web dev port instead of default(9000), 
# you can use the 'WEB_PORT' variable
$ WEB_PORT=YOUR_WEB_DEV_PORT yarn run dev
```

### Testing

```sh
# running unit tests
$ yarn run test

# running e2e tests: make sure that zeppelin instance started (localhost:8080)
$ yarn run e2e
```

- to write unit tests, please refer [Angular Test Patterns](https://github.com/daniellmb/angular-test-patterns)
- to write e2e tests, please refer [Protractor Tutorial](http://www.protractortest.org/#/tutorial#step-1-interacting-with-elements)

### Packaging 

If you want to package the zeppelin-web only, simply run this command in this folder.  
This will download all the dependencies including node (the binaries in the folder `zeppelin-web/node`)

```
$ mvn package 
```

## Troubleshooting

#### Git error

In case of the error `ECMDERR Failed to execute "git ls-remote --tags --heads git://xxxxx", exit code of #128`

change your git config with `git config --global url."https://".insteadOf git://`

#### Proxy issues

Try to add to the `.bowerrc` file the following content:
```
  "proxy" : "http://<host>:<port>",
  "https-proxy" : "http://<host>:<port>"
```

also try to add proxy info to yarn install command:
```xml
<execution>
	<id>yarn install</id>
	<goals>
    	<goal>yarn</goal>
    </goals>
    <configuration>
    	<arguments>--proxy=http://<host>:<port> --https-proxy=http://<host>:<port></arguments>
    </configuration>
</execution>
```

and retry to build again.

## Contribute on Zeppelin Web

If you wish to help us and contribute to Zeppelin WebApplication, please look at the overall project [contribution guidelines](https://zeppelin.apache.org/contribution/contributions.html) and the more focused [Zeppelin WebApplication's documentation](https://zeppelin.apache.org/contribution/webapplication.html).
