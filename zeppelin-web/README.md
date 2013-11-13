# Zeppelin web application
This is a Zeppelin web frontend project.


### Build
Web frontend uses [Brunch](http://brunch.io) to assemble so you need to:
 1. install Node.js from http://nodejs.org
 1. install Brunch with porject dependecies running

    ```
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
