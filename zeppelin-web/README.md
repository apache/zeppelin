# Zeppelin web application
This is a Zeppelin web frontend project.


### Build
Web frontend uses [Brunch.io](http://brunch.io) to assemble BUT you do not need to have Node installed upfront!
Thanks to awesome [frontend-maven-plugin](https://github.com/eirslett/frontend-maven-plugin) we have next stepse inside project build.
    ```
     #install Node.js to zeppelin-web/node/ and then
     npm install -g brunch
     rm -rf node_modules public
     npm install
    ```
    _Note: if you receive a "gyp + xcodebuild" error when running "npm install", confirm you have Xcode CLI tools installed (Xcode > Preferences > Downloads)_

### Run
To run in __isolation__ from actual Zeppelin backend server do:

    brunch watch --server (or use the shorthand: brunch w -s)

To run on actual zeppelin server do:

    brunch watch
    cd ../zeppelin-server
    mvn exec:java -Dexec.mainClass="com.nflabs.zeppelin.server.ZeppelinServer"



### Deployment
To build webapplication WAR run:
    mvn package
