---
layout: page
title: "Zengine"
description: ""
group: nav-left
---
{% include JB/setup %}
### Zengine is java framework for data analytics.
####Examples

Word Count
{% highlight java %}
import com.nflabs.zeppelin.zengine.*
import com.nflabs.zeppelin.result.*
...
result = new Q("select text from myTable")                    // load some data
              .pipe(new L("wordcount"))                       // load wordcount library
              .execute()                                      // execute
	      .result();

result.write(System.out);                                     // Print result to stdout
...
{% endhighlight %}            

Checkout Zengine API doc. 

Maven dependency

to use it add dependency in your pom.xml
{% highlight xml %}
<dependency>
    <groupId>com.nflabs.zeppelin</groupId>
    <artifactId>zeppelin-zengine</artifactId>
    <version>0.1.2</version>
</dependency>
{% endhighlight %}
if you want access snapshot
{% highlight xml %}
<dependency>
    <groupId>com.nflabs.zeppelin</groupId>
    <artifactId>zeppelin-zengine</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

...

<repository>
    <id>oss.sonatype.org-snapshot</id>
    <url>http://oss.sonatype.org/content/repositories/snapshots</url>
    <releases>
        <enabled>false</enabled>
    </releases>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
{% endhighlight %}