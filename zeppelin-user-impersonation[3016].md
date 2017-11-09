# zeppelin-user-impersonation
How to setup Zeppelin User Impersonation with 0.7.x
Zeppelin User Impersonation Per User and Isolated Per User
This document completely focuses how to set up Zeppelin Interpreter Per User, Isolated Per User with User Impersonation using zeppelin 0.7.0/0.7.2/0.7.3 or HDP 2.6

How to set up user impersonation at Zeppelin Shell Interpreter level with shiro authentication can found from [here](https://zeppelin.apache.org/docs/0.7.0/manual/userimpersonation.html)
What the different kinds of Interpreter binding modes is well discussed in the upcoming version 0.8.0 of Zeppelin [documentation](https://zeppelin.apache.org/docs/0.8.0-SNAPSHOT/usage/interpreter/interpreter_binding_mode.html)

Before setting up the Zeppelin User Impersonation make sure you have integrated your zeppelin and your cluster with AD/LDAP, below is the shiro.ini for AD authentication: </br>
<i>
[users] </br>
###List of users with their password allowed to access Zeppelin </br>
###To use a different strategy (LDAP / Database / ...) check the shiro doc at http://shiro.apache.org/configuration.html#Configuration-INISections </br>
admin = admin, admin  </br>
user1 = user1, role1, role2 </br>
user2 = user2, role3 </br>
user3 = user3, role2 </br>
###Sample LDAP configuration, for user Authentication, currently tested for single Realm </br>
[main] </br>
###A sample for configuring Active Directory Realm </br>
activeDirectoryRealm = org.apache.zeppelin.realm.ActiveDirectoryGroupRealm </br>
activeDirectoryRealm.systemUsername = CN=Administrator,CN=Users,DC=ADSERVER,DC=com </br>
activeDirectoryRealm.systemPassword = loginpassword </br>
activeDirectoryRealm.searchBase = CN=Users,DC=ADSERVER,DC=com </br>
activeDirectoryRealm.url = ldap://ldap.ad.ADSERVER.com:389 </br>
activeDirectoryRealm.authorizationCachingEnabled = true </br>
sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager </br>
securityManager.sessionManager = $sessionManager </br>

###If caching of user is required then uncomment below lines </br>
cacheManager = org.apache.shiro.cache.MemoryConstrainedCacheManager </br>
securityManager.cacheManager = $cacheManager </br>
###securityManager.sessionManager = $sessionManager </br>
###86,400,000 milliseconds = 24 hour </br>
securityManager.sessionManager.globalSessionTimeout = 86400000 </br>
shiro.loginUrl = /api/login </br>
[roles] </br>
role1 = * </br>
role2 = * </br>
role3 = * </br>
admin = * </br>
[urls] </br>
###This section is used for url-based security. </br>
###You can secure interpreter, configuration and credential information by urls. Comment or uncomment the below urls that you want to hide. </br>
###anon means the access is anonymous. </br>
###authc means Form based Auth Security </br>
###To enfore security, comment the line below and uncomment the next one </br>
/api/version = anon </br>
#/api/interpreter/** = authc, roles[admin] </br>
#/api/configurations/** = authc, roles[admin] </br>
#/api/credential/** = authc, roles[admin] </br>
#/** = anon </br>
/** = authc </br>
</i>

<b>Below are the configurations required from the environment:</b> </br>
•	On the Zeppelin server node make sure to add the below to <i>/etc/sudoers</i>: </br>
With the root user: </br>

`$visudo` </br>
`zeppelin        ALL=(ALL)       NOPASSWD: ALL ` </br> 

If this entry is missing in the /etc/sudoers then the interpreter fails with the below error: </br>

`org.apache.zeppelin.interpreter.InterpreterException: Permission denied, please try again.`</br>
`Permission denied, please try again.` </br>
`Permission denied (publickey,gssapi-keyex,gssapi-with-mic,password).`</br> 
</br>

•	Log folder (usually /var/log/zeppelin or /var/log/hadoop/zeppelin) of zeppelin should be of 777 instead of 755 if not the Spark interpreter keep failing with below error as the impersonated user won’t able to write to the logs folder:

`java.lang.NullPointerException`</br>
	`at org.apache.zeppelin.spark.Utils.invokeMethod(Utils.java:38)`</br>
	`at org.apache.zeppelin.spark.Utils.invokeMethod(Utils.java:33)` </br>

</br>
•	If you are on a HDP platform this can’t be achieved from the CLI which needs a Ambari Zeppelin code level change </br>

`$ vi /var/lib/ambari-server/resources/common-services/ZEPPELIN/0.6.0.2.5/package/scripts/master.py` </br>

#### Find the below: </br>

```
def create_zeppelin_log_dir(self, env):
    import params
    env.set_params(params)
    Directory([params.zeppelin_log_dir],
              owner=params.zeppelin_user,
              group=params.zeppelin_group,
              cd_access="a",
              create_parents=True,
              mode=0755
              )
  ```          
##### Change this to:

```
def create_zeppelin_log_dir(self, env):
    import params
    env.set_params(params)
    Directory([params.zeppelin_log_dir],
              owner=params.zeppelin_user,
              group=params.zeppelin_group,
              cd_access="a",
              create_parents=True,
              mode=0777
              )
```
##### Please note this requires the stop and start of AMBARI-SERVER. </br>
Configurations required for Zeppelin User Impersonation: </br>

•	Add the proxy settings for zeppelin user under core-site.xml and restart the required services (HDFS, Mapreduce2 and YARN) </br>

`hadoop.proxyuser.zeppelin.hosts=*` </br>
`hadoop.procyuser.zeppelin.goups=*`
</br>

This configuration is required as we will be giving the control to zeppelin user to impersonate other users (users using the zeppelin UI) </br>

•	Add the below lines to zeppelin-env.sh file (or zeppelin-env from Ambari --> Zeppelin --> Configs --> Advanced zeppelin-env)

<i>#--- FOR IMPERSONATION -- START ----

ZEPPELIN_IMPERSONATE_USER=`echo ${ZEPPELIN_IMPERSONATE_USER} | cut -d "@" -f1` # we are trimming the user to get short name i.e. username from username@domain.com
export ZEPPELIN_IMPERSONATE_CMD='sudo -H -u zeppelin bash -c'  #zeppelin user in command to do the impersonation

export SPARK_HOME=/usr/hdp/current/spark2-client/
export PYTHONPATH=$SPARK_HOME/python/:$PYTHONPATH
export PYTHONPATH=$SPARK_HOME/python/lib/py4j-0.10.4-src.zip:$PYTHONPATH # This is version based config and needs to be changed based on the spark version

export SPARK_YARN_USER_ENV="PYTHONPATH=${PYTHONPATH}"

#--- FOR IMPERSONATION -- END ---- </i>

#### Now restart the zeppelin service. </br>

Once all the above configurations are completed and restarted the required services, now make sure to do the required configurations (Per User, Isolated Per User and User Impersonation as shown below) from Zeppelin --> Interpreter --> Spark/Spark2) </br>

![alt text](https://user-images.githubusercontent.com/27515562/32426948-9a92ef26-c2e3-11e7-954d-bdb2e41da8ee.JPG)

Once any command/job run from Spark Interpreter with YARN will launched with the login user:

![alt text](https://user-images.githubusercontent.com/27515562/32427197-2f69266e-c2e5-11e7-8993-6e837c302ba8.JPG)

![alt text](https://user-images.githubusercontent.com/27515562/32427231-557e7912-c2e5-11e7-85ea-bf6bd5df2f77.JPG)
 

Hope this helps someone who is struggling with Zeppelin User Impersonation while running the Zeppelin --> Spark --> YARN </br>

More details about the issue and the errors can be found from:
https://issues.apache.org/jira/browse/ZEPPELIN-3016

