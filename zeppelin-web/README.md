# Zeppelin web application




### Build
Web frontend uses [Brunch](http://brunch.io) to assemble so
 1. install Node.js from http://nodejs.org
 1. install Brunch with porject dependecies running
     npm install -g brunch 
     rm -rf node_modules public
     npm install
     __Note: if you receive a "gyp + xcodebuild" error when running "npm install", confirm you have Xcode CLI tools installed (Xcode > Preferences > Downloads)__

To run in isolation from actual Zeppelin backend server run:
     brunch watch --server (or use the shorthand: brunch w -s)

### Deployment
To build webapplication WAR run:
    mvn package
