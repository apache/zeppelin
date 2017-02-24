# Zeppelin Web Application

This is Zeppelin's frontend project.

## Development Guide 

### Packaging 

If you want to package the zeppelin-web only, simply run this command in this folder.  
This will download all the dependencies including node (the binaries in the folder `zeppelin-web/node`)

```
$ mvn package 
```
#### You also can use maven for build and test

If you want just build web application use:
```
$ mvn compile
```  

If you want just test web application use:
```
$ mvn test
``` 

Please note! Run

```
$ mvn clean
```

will clean local nodejs installation and all installed packages
So, if you want to save time for rebuild use maven option:

```
-Dmaven.clean.skip=true
```

If you want skip tests of web application maven option:
```
-Dmaven.test.skip=true 
```
 or 
```
-DskipTests
```

### Local Development

It is recommended to install node 6.0.0+ since Zeppelin uses 6.9.1+ (see [creationix/nvm](https://github.com/creationix/nvm))

All build commands are described in [package.json](./package.json)

# install required depepdencies and bower packages (only once)
```
$ npm install -g yarn
```

```
$ yarn install
```

```
$ bower install --silent
```

# build zeppelin-web for production
```
$ yarn run build
```

# run frontend application only in dev mode (localhost:9000) 
# you need to run zeppelin backend instance also
```
$ yarn run dev
```

# execute tests
```
$ yarn run test
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
