#Shiro Authentication
To connect to Zeppelin, users will be asked to enter their credentials. Once logged, a user has access to all notes including other users notes.
This a a first step toward full security as implemented by this pull request (https://github.com/apache/incubator-zeppelin/pull/53).

#Security setup
1. Secure the HTTP channel: Comment the line "/** = anon" and uncomment the line "/** = authcBasic" in the file conf/shiro.ini. Read more about he shiro.ini file format at the following URL http://shiro.apache.org/configuration.html#Configuration-INISections.  
2. Secure the Websocket channel : Set to property "zeppelin.anonymous.allowed" to "true" in the file conf/zeppelin-site.xml. You can start by renaming conf/zeppelin-site.xml.template to conf/zeppelin-site.xml
3. Start Zeppelin : bin/zeppelin.sh
4. point your browser to http://localhost:8080
5. Login using one of the user/password combinations defined in the conf/shiro.ini file.
 

 
