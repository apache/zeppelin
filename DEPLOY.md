Publish Zeppelin Distribution Package
------

A. Build package

Following command will create package zeppelin-VERSION.tar.gz under _zeppelin-distribution/target_ directory.

      mvn clean package -P build-distr


B. Upload to S3 bucket ~~web server~~

     ~~scp zeppelin-distribution/target/zeppelin-VERSION.tar.gz root@www.nflabs.com:/var/www/html/pub/zeppelin/~~
     mvn package -P publish-distr


C. Edit www.zeppelin-project.org

Edit download page to have link to new release.


Publish javadoc
-------

Generate javadoc with following command

    mvn javadoc:javadoc
    mv "zeppelin-zengine/target/site/apidocs" "ZEPPELIN_HOMEPAGE/docs/zengine-api/VERSION"

and publish the web.


Publish Maven artifact
------------

**Publish to snapshot repository**

     mvn -DperformRelease=true deploy


**Publish to release repository**

    mvn -DperformRelease=true release:clean
    mvn -DperformRelease=true release:prepare
    mvn -DperformRelease=true release:perform

Artifact is now in staging repository.
Connect https://oss.sonatype.org/ , select staging repository and click "close" -> "release" will finally release it.


**Reference**

https://docs.sonatype.org/display/Repository/How+To+Generate+PGP+Signatures+With+Maven

https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-1a.TermsofServiceRepository%3ACentralRepositoryTermsofServiceforSubmitters
