---
layout: page
title: "SpringXD Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## SpringXD  Interpreter for Apache Zeppelin

### Overview
[Spring XD](http://docs.spring.io/spring-xd/docs/current/reference/html/#_reference_guide) is a unified, distributed, and extensible service for data ingestion, real time analytics, batch processing, and data export. SpringXD helps to build big-data applications that integrate many disparate systems into one cohesive solution across a range of use-cases. 
SpringXD supports two processing abstractions: (1) [Streams](http://docs.spring.io/spring-xd/docs/current/reference/html/#streams) for event driven data, and (2) [Jobs](http://docs.spring.io/spring-xd/docs/current/reference/html/#jobs) like MR, PIG, Hive, Cascading, SQL and so on, for batch data types. Spring XD tries to blur the boundaries between the two using a channel abstraction, so that, for example, a stream can trigger a batch job, and a batch job can send events and, in turn, trigger other streams. An expressive DSL is provided for defining streams and jobs: 

```
myStream = http | filter --expression=payload='foo' | log
```

Zeppelin provides interpreters for SpringXD `Streams` and `Jobs`: 

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%xd.stream</td>
    <td>SpringXdStreamInterpreter</td>
    <td>Provides an environment to create SpringXD Streams - defines the ingestion of event driven data from a `source` to a sink that passes through any number of `processors. </td>
  </tr>
  <tr>
    <td>%xd.job</td>
    <td>SpringXdJobInterpreter</td>
    <td>Provides an environment to create SpringXD Jobs - combine interactions with standard enterprise systems (e.g. RDBMS) as well as Hadoop operations (e.g. MapReduce, HDFS, Pig, Hive or HBase). </td>
  </tr>  
</table>

> Note: A single Zeppelin `paragraph` can contain multiple `streams` or multiple `jobs`, but it can not mix-up jobs and streams in the same paragraph!

<br/>
[<img align="right" src="http://img.youtube.com/vi/uSUBe8Hgk8w/0.jpg" alt="zeppelin-view" hspace="10" width="250"></img>](https://www.youtube.com/watch?v=uSUBe8Hgk8w)

Following [video tutorial](https://www.youtube.com/watch?v=uSUBe8Hgk8w) illustrates some of the features provided by the `SpringXD Interpreter`. It shows how to build SpringXD pipeline to ingest a real-time Tweet stream into Geode, HDFS and HAWQ backends. 

### Create Interpreter

By default Zeppelin creates one `SpringXD` interpreter instance that communicates with the backend SpringXD system

To create new SpringXD instance open the `Interpreter` section and click the `+Create` button. Pick a `Name` of your choice and from the `Interpreter` drop-down select `xd`. Then follow the configuration instructions and `Save` the new instance.

> Note: The `Name` of the instance is used only to distinct the instances while binding them to the `Notebook`. The `Name` is irrelevant inside the `Notebook`. In the `Notebook` you must use `%xd.stream` or `%xd.job` tag.

### Bind to Notebook
In the `Notebook` click on the `settings` icon in the top right corner. The select/deselect the XD interpreter to be bound with the `Notebook`.

### Configuration
You can modify the configuration of the SpringXD from the `Interpreter` section. The SpringXD interpreter express the following properties:

 <table class="table-configuration">
   <tr>
     <th>Property Name</th>
     <th>Description</th>
     <th>Default Value</th>
   </tr>
   <tr>
     <td>springxd.url</td>
     <td>The URL for SpringXD REST API. </td>
     <td>http://localhost:9393</td>
   </tr>
 </table>

### How to use
```
Tip: Use (CTRL + .) for SpringXD auto-completion.
```

#### XD Streams

Start the paragraphs with the full `%xd.stream` prefix tag. The paragraph can contain one or more stream definitions, each on a separate line.
Every stream definition should following the format: `STREAM-NAME = STREAM-DEFINITION`. STREAM-NAME is a name identifier unique in the SpringXD backend. The stream definition follows the Spring XD DSL for stream definition: [DSL Guide](http://docs.spring.io/spring-xd/docs/current/reference/html/#dsl-guide) 

Following example creates a stream that ingests a live tweeter stream and stores it into HDFS files:

```
%xd.stream

tweets = twittersearch --query=Zeppelin --outputType=application/json | hdfs
``` 

Another example is to fork the above `tweets` stream, extract the tweets's IDs and use the SpringXD [Counter Sink](http://docs.spring.io/spring-xd/docs/current/reference/html/#counters-and-gauges) to count the number for tweets:

```
tweetsCount = tap:stream:tweets > json-to-tuple | transform --expression='payload.id_str' | counter --name=tweetCounter
```

* `%xd.stream` - identifies the SpringXD Streams interpreter.
* Use `Ctrl+.` for auto-completion.
* Streams must have names. Follow the convention: `stream name = stream definition`. Streams without names are ignored.
* New Run destroys any previous streams created in the same paragraph. The previous streams are destroyed even if their names have been changed or removed.
* Paragraph `Run` command will `Create` and automatically `Deploy` all streams defined in the Paragraph.
* If one stream fails to create or to deploy then all streams in the paragraph are destroyed
* Zeppelin returns after the streams deployment (e.g. Zeppelin goes into Finished state).
* When streams have successfully been deployed the result contains a `button` that lists the just deployed streams and status `DEPLOYED`
* To destroy streams in a paragraph press the `Annular Button` in the paragraph result section. The button state will switch from `DEPLOYED` to `DESTROYED`.
* Streams can refer other streams or jobs in any paragraph and even different notebooks.

#### XD Jobs

Start the paragraphs with the full `%xd.job` prefix tag. The paragraph can contain one or more job definitions, each on a separate line.
Every job definition should following the format: `JOB-NAME = JOB-DEFINITION`. JOB-NAME is a name identifier unique in the SpringXD backend. The job definition follows the Spring XD DSL for job definition. 

Example module which loads [CSV files into a JDBC table](http://docs.spring.io/spring-xd/docs/current/reference/html/#file-jdbc) using a single batch job.

```
%xd.job

myJob = filejdbc --resources=file:///mycsvdir/*.csv --names=forename,surname,address --url=jdbc:mysql://localhost:3306/myTable --username=jdbcUser --password=jdbcPassword --tableName=people --initializeDatabase=true
``` 
The job should be defined with the resources parameter defining the files which should be loaded. It also requires a names parameter (for the CSV field names) and these should match the database column names into which the data should be stored. You can either pre-create the database table or the module will create it for you if you use --initializeDatabase=true when the job is created. The table initialization is configured in a similar way to the JDBC sink and uses the same parameters. The default table name is the job name and can be customized by setting the tableName parameter. As an example, if you run the command

Another example illustrates how a Spark Application can be deployed and launched from Spring XD as a batch job. SparkTasklet submits the Spark application into Spark cluster manager using org.apache.spark.deploy.SparkSubmit:

```
%xd.job

SparkPiExample  = sparkapp --appJar=<the location of spark-examples-1.2.1 jar> --name=MyApp --master=<spark master url or local> --mainClass=org.apache.spark.examples.SparkPi
```
###### Notes about the Job execution model:
* `%xd.job` - identifies the SpringXD Job interpreter.
* Use `Ctrl+.` for auto-completion.
* Jobs must have names. Follow the convention: `job name = job definition`. Streams without names are ignored.
* New Paragraph Run destroys any previous jobs created in the same paragraph. The previous jobs are destroyed even if their names have been changed or removed.
* New Paragraph Run will `Create`, automatically `Deploy` and then `Start` all jobs defined in the Paragraph.
* If a job deployment fail then all jobs in the paragraph are destroyed
* Zeppelin returns after the jobs have started (e.g. Zeppelin goes into `Finished` state).
* When job have successfully been deployed the result contains a button that lists the just deployed jobs and status `DEPLOYED`
* To destroy all jobs in the paragraph, press the `Annular button` in the result section. Button state should switch from `DEPLOYED` to `DESTROYED`.
* Jobs can refer other streams or jobs in any paragraph and even different notebooks.

#### Apply Zeppelin Dynamic Forms

You can leverage [Zeppelin Dynamic Form](../manual/dynamicform.html) inside your stream and job definitions. You can use both the `text input` and `select form` parameterization features. For example to parameterize the search parameter in the twitter search stream :

```
%xd.stream

tweets = twittersearch --query=${MySearchParameter=DefaultSearchKeyword} --outputType=application/json | hdfs
``` 


### Auto-completion
The SpringXD Interpreter provides a comprehensive auto-completion for `Job` and `Stream` DSL definitions. On `(Ctrl+.)` it list the most relevant suggestions in a pop-up window. Implementation leverages [SpringXD's Completion API](http://docs.spring.io/spring-xd/docs/current/reference/html/#completions) and provides the same expressiveness similar with the SpringXD Shell.
