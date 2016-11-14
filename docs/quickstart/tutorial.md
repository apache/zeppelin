---
layout: page
title: "Apache Zeppelin Tutorial"
description: "This tutorial page contains a short walk-through tutorial that uses Apache Spark backend. Please note that this tutorial is valid for Spark 1.3 and higher."
group: quickstart
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Zeppelin Tutorial

<div id="toc"></div>

This tutorial walks you through some of the fundamental Zeppelin concepts. We will assume you have already installed Zeppelin. If not, please see [here](../install/install.html) first.

Current main backend processing engine of Zeppelin is [Apache Spark](https://spark.apache.org). If you're new to this system, you might want to start by getting an idea of how it processes data to get the most out of Zeppelin.

## Tutorial with Local File

### Data Refine

Before you start Zeppelin tutorial, you will need to download [bank.zip](http://archive.ics.uci.edu/ml/machine-learning-databases/00222/bank.zip). 

First, to transform csv format data into RDD of `Bank` objects, run following script. This will also remove header using `filter` function.

```scala

val bankText = sc.textFile("yourPath/bank/bank-full.csv")

case class Bank(age:Integer, job:String, marital : String, education : String, balance : Integer)

// split each line, filter out header (starts with "age"), and map it into Bank case class
val bank = bankText.map(s=>s.split(";")).filter(s=>s(0)!="\"age\"").map(
    s=>Bank(s(0).toInt, 
            s(1).replaceAll("\"", ""),
            s(2).replaceAll("\"", ""),
            s(3).replaceAll("\"", ""),
            s(5).replaceAll("\"", "").toInt
        )
)

// convert to DataFrame and create temporal table
bank.toDF().registerTempTable("bank")
```

### Data Retrieval

Suppose we want to see age distribution from `bank`. To do this, run:

```sql
%sql select age, count(1) from bank where age < 30 group by age order by age
```

You can make input box for setting age condition by replacing `30` with `${maxAge=30}`.

```sql
%sql select age, count(1) from bank where age < ${maxAge=30} group by age order by age
```

Now we want to see age distribution with certain marital status and add combo box to select marital status. Run:

```sql
%sql select age, count(1) from bank where marital="${marital=single,single|divorced|married}" group by age order by age
```

<br />
## Tutorial with Streaming Data 

### Data Refine

Since this tutorial is based on Twitter's sample tweet stream, you must configure authentication with a Twitter account. To do this, take a look at [Twitter Credential Setup](https://databricks-training.s3.amazonaws.com/realtime-processing-with-spark-streaming.html#twitter-credential-setup). After you get API keys, you should fill out credential related values(`apiKey`, `apiSecret`, `accessToken`, `accessTokenSecret`) with your API keys on following script.

This will create a RDD of `Tweet` objects and register these stream data as a table:

```scala
import org.apache.spark.streaming._
import org.apache.spark.streaming.twitter._
import org.apache.spark.storage.StorageLevel
import scala.io.Source
import scala.collection.mutable.HashMap
import java.io.File
import org.apache.log4j.Logger
import org.apache.log4j.Level
import sys.process.stringSeqToProcess

/** Configures the Oauth Credentials for accessing Twitter */
def configureTwitterCredentials(apiKey: String, apiSecret: String, accessToken: String, accessTokenSecret: String) {
  val configs = new HashMap[String, String] ++= Seq(
    "apiKey" -> apiKey, "apiSecret" -> apiSecret, "accessToken" -> accessToken, "accessTokenSecret" -> accessTokenSecret)
  println("Configuring Twitter OAuth")
  configs.foreach{ case(key, value) =>
    if (value.trim.isEmpty) {
      throw new Exception("Error setting authentication - value for " + key + " not set")
    }
    val fullKey = "twitter4j.oauth." + key.replace("api", "consumer")
    System.setProperty(fullKey, value.trim)
    println("\tProperty " + fullKey + " set as [" + value.trim + "]")
  }
  println()
}

// Configure Twitter credentials
val apiKey = "xxxxxxxxxxxxxxxxxxxxxxxxx"
val apiSecret = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
val accessToken = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
val accessTokenSecret = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
configureTwitterCredentials(apiKey, apiSecret, accessToken, accessTokenSecret)

import org.apache.spark.streaming.twitter._
val ssc = new StreamingContext(sc, Seconds(2))
val tweets = TwitterUtils.createStream(ssc, None)
val twt = tweets.window(Seconds(60))

case class Tweet(createdAt:Long, text:String)
twt.map(status=>
  Tweet(status.getCreatedAt().getTime()/1000, status.getText())
).foreachRDD(rdd=>
  // Below line works only in spark 1.3.0.
  // For spark 1.1.x and spark 1.2.x,
  // use rdd.registerTempTable("tweets") instead.
  rdd.toDF().registerAsTable("tweets")
)

twt.print

ssc.start()
```

### Data Retrieval

For each following script, every time you click run button you will see different result since it is based on real-time data.

Let's begin by extracting maximum 10 tweets which contain the word **girl**.

```sql
%sql select * from tweets where text like '%girl%' limit 10
```

This time suppose we want to see how many tweets have been created per sec during last 60 sec. To do this, run:

```sql
%sql select createdAt, count(1) from tweets group by createdAt order by createdAt
```


You can make user-defined function and use it in Spark SQL. Let's try it by making function named `sentiment`. This function will return one of the three attitudes( positive, negative, neutral ) towards the parameter.

```scala
def sentiment(s:String) : String = {
    val positive = Array("like", "love", "good", "great", "happy", "cool", "the", "one", "that")
    val negative = Array("hate", "bad", "stupid", "is")
    
    var st = 0;

    val words = s.split(" ")    
    positive.foreach(p =>
        words.foreach(w =>
            if(p==w) st = st+1
        )
    )
    
    negative.foreach(p=>
        words.foreach(w=>
            if(p==w) st = st-1
        )
    )
    if(st>0)
        "positivie"
    else if(st<0)
        "negative"
    else
        "neutral"
}

// Below line works only in spark 1.3.0.
// For spark 1.1.x and spark 1.2.x,
// use sqlc.registerFunction("sentiment", sentiment _) instead.
sqlc.udf.register("sentiment", sentiment _)

```

To check how people think about girls using `sentiment` function we've made above, run this:

```sql
%sql select sentiment(text), count(1) from tweets where text like '%girl%' group by sentiment(text)
```