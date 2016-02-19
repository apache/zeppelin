# Zeppelin Web Application
This is Zeppelin's frontend project.


## Compile Zeppelin web

### New environment

If you want to compile the WebApplication only, you will have to simply run `mvn package` in this folder.

This will Download all the dependencies including node js and npm (you will find the binaries in the folder `zeppelin-web/node`).

We are supposed to provide some **helper script** for __bower__ and __grunt__, but they are currently outdated, so you might want install them on your machine and use them instead.

### Configured environment

Here are the basic commands to compile the WebApplication with a configured environment (Installed grunt, bower, npm)

**Build the application for production**

`./grunt build`

**Run the application in dev mode**

``./grunt serve``

This will launch a Zeppelin WebApplication on port **9000** that will update on code changes.
(You will need to have Zeppelin running on the side)


#### Troubleshooting

**git error**

In case of the error `ECMDERR Failed to execute "git ls-remote --tags --heads git://xxxxx", exit code of #128`

change your git config with `git config --global url."https://".insteadOf git://`

**proxy issues**

Try to add to the `.bowerrc` file the following content:
```
  "proxy" : "http://<host>:<port>",
  "https-proxy" : "http://<host>:<port>"
  ```

also try to add proxy info  to npm install command:
```
<execution>
	<id>npm install</id>
	<goals>
    	<goal>npm</goal>
    </goals>
    <configuration>
    	<arguments>--proxy=http://<host>:<port> --https-proxy=http://<host>:<port></arguments>
    </configuration>
</execution>
```


and retry to build again.

## Contribute on Zeppelin Web
If you wish to help us and contribute to Zeppelin WebApplication, please look at [Zeppelin WebApplication's contribution guideline](CONTRIBUTING.md).
