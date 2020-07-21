---
layout: page
title: "Cassandra CQL Interpreter for Apache Zeppelin"
description: "Apache Cassandra database is the right choice when you need scalability and high availability without compromising performance."
group: interpreter
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

# Cassandra CQL Interpreter for Apache Zeppelin

<div id="toc"></div>

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%cassandra</td>
    <td>CassandraInterpreter</td>
    <td>Provides interpreter for Apache Cassandra CQL query language</td>
  </tr>
</table>

## Enabling Cassandra Interpreter

In a notebook, to enable the **Cassandra** interpreter, click on the **Gear** icon and select **Cassandra**

 <center>
 ![Interpreter Binding]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/cassandra-InterpreterBinding.png)

 ![Interpreter Selection]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/cassandra-InterpreterSelection.png)
 </center>

## Using the Cassandra Interpreter

In a paragraph, use **_%cassandra_** to select the **Cassandra** interpreter and then input all commands.

To access the interactive help, type **HELP;**

 <center>
   ![Interactive Help]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/cassandra-InteractiveHelp.png)
 </center>

## Interpreter Commands

The **Cassandra** interpreter accepts the following commands

<center>
  <table class="table-configuration">
    <tr>
      <th>Command Type</th>
      <th>Command Name</th>
      <th>Description</th>
    </tr>
    <tr>
      <td nowrap>Help command</td>
      <td>`HELP`</td>
      <td>Display the interactive help menu</td>
    </tr>
    <tr>
      <td nowrap>Schema commands</td>
      <td>`DESCRIBE KEYSPACE`, `DESCRIBE CLUSTER`, `DESCRIBE TABLES` ...</td>
      <td>Custom commands to describe the Cassandra schema</td>
    </tr>
    <tr>
      <td nowrap>Option commands</td>
      <td>`@consistency`, `@fetchSize` ...</td>
      <td>Inject runtime options to all statements in the paragraph</td>
    </tr>
    <tr>
      <td nowrap>Prepared statement commands</td>
      <td>`@prepare`, `@bind`, `@remove_prepared`</td>
      <td>Let you register a prepared command and re-use it later by injecting bound values</td>
    </tr>
    <tr>
      <td nowrap>Native CQL statements</td>
      <td>All CQL-compatible statements (`SELECT`, `INSERT`, `CREATE`, ...)</td>
      <td>All CQL statements are executed directly against the Cassandra server</td>
    </tr>
  </table>
</center>

## CQL statements

This interpreter is compatible with any CQL statement supported by Cassandra. Ex:

```sql

INSERT INTO users(login,name) VALUES('jdoe','John DOE');
SELECT * FROM users WHERE login='jdoe';
```

Each statement should be separated by a semi-colon ( **;** ) except the special commands below:

1. `@prepare`
2. `@bind`
3. `@remove_prepare`
4. `@consistency`
5. `@serialConsistency`
6. `@timestamp`
7. `@fetchSize`
8. `@requestTimeOut`

Multi-line statements as well as multiple statements on the same line are also supported as long as they are separated by a semi-colon. Ex:

```sql

USE spark_demo;

SELECT * FROM albums_by_country LIMIT 1; SELECT * FROM countries LIMIT 1;

SELECT *
FROM artists
WHERE login='jlennon';
```

Batch statements are supported and can span multiple lines, as well as DDL (`CREATE`/`ALTER`/`DROP`) statements:

```sql

BEGIN BATCH
    INSERT INTO users(login,name) VALUES('jdoe','John DOE');
    INSERT INTO users_preferences(login,account_type) VALUES('jdoe','BASIC');
APPLY BATCH;

CREATE TABLE IF NOT EXISTS test(
    key int PRIMARY KEY,
    value text
);
```

CQL statements are <strong>case-insensitive</strong> (except for column names and values). This means that the following statements are equivalent and valid:

```sql

INSERT INTO users(login,name) VALUES('jdoe','John DOE');
Insert into users(login,name) vAlues('hsue','Helen SUE');
```

The complete list of all CQL statements and versions can be found below:

<center>
 <table class="table-configuration">
   <tr>
     <th>Cassandra Version</th>
     <th>Documentation Link</th>
   </tr>
   <tr>
     <td><strong>3.x</strong></td>
     <td>
        <a target="_blank"
          href="https://docs.datastax.com/en/archived/cql/3.3/cql/cqlIntro.html">
          https://docs.datastax.com/en/archived/cql/3.3/cql/cqlIntro.html
        </a>
     </td>
   </tr>   
   <tr>
     <td><strong>2.2</strong></td>
     <td>
        <a target="_blank"
          href="https://docs.datastax.com/en/archived/cql/3.3/cql/cqlIntro.html">
          https://docs.datastax.com/en/archived/cql/3.3/cql/cqlIntro.html
        </a>
     </td>
   </tr>
   <tr>
     <td><strong>2.1</strong></td>
     <td>
        <a target="_blank"
          href="http://docs.datastax.com/en/cql/3.1/cql/cql_intro_c.html">
          http://docs.datastax.com/en/cql/3.1/cql/cql_intro_c.html
        </a>
     </td>
   </tr>
 </table>
</center>

## Comments in statements

It is possible to add comments between statements. Single line comments start with the **hash sign** (`#`), **double slashes** (`//`),  **double dash** (`--`). Multi-line comments are enclosed between `/**` and `**/`. Ex:

```sql

# Single line comment style 1
INSERT INTO users(login,name) VALUES('jdoe','John DOE');

// Single line comment style 2

// Single line comment style 3

/**
 Multi line
 comments
 **/
Insert into users(login,name) vAlues('hsue','Helen SUE');
```

## Syntax Validation

The interpreters is shipped with a built-in syntax validator. This validator only checks for basic syntax errors.

All CQL-related syntax validation is delegated directly to **Cassandra**

Most of the time, syntax errors are due to **missing semi-colons** between statements or **typo errors**.

## Schema commands

To make schema discovery easier and more interactive, the following commands are supported:

<center>
 <table class="table-configuration">
   <tr>
     <th>Command</th>
     <th>Description</th>
   </tr>
   <tr>
     <td><strong>DESCRIBE CLUSTER;</strong></td>
     <td>Show the current cluster name and its partitioner</td>
   </tr>
   <tr>
     <td><strong>DESCRIBE KEYSPACES;</strong></td>
     <td>List all existing keyspaces in the cluster and their configuration (replication factor, durable write ...)</td>
   </tr>
   <tr>
     <td><strong>DESCRIBE TABLES;</strong></td>
     <td>List all existing keyspaces in the cluster and for each, all the tables name</td>
   </tr>
   <tr>
     <td><strong>DESCRIBE TYPES;</strong></td>
     <td>List all existing keyspaces in the cluster and for each, all the user-defined types name</strong></td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE FUNCTIONS;</strong></td>
     <td>List all existing keyspaces in the cluster and for each, all the functions name</td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE AGGREGATES;</strong></td>
     <td>List all existing keyspaces in the cluster and for each, all the aggregates name</td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE MATERIALIZED VIEWS;</strong></td>
     <td>List all existing keyspaces in the cluster and for each, all the materialized views name</td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE KEYSPACE &lt;keyspace_name&gt;;</strong></td>
     <td>Describe the given keyspace configuration and all its table details (name, columns, ...)</td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE TABLE (&lt;keyspace_name&gt;).&lt;table_name&gt;;</strong></td>
     <td>
        Describe the given table. If the keyspace is not provided, the current logged in keyspace is used.
        If there is no logged in keyspace, the default system keyspace is used.
        If no table is found, an error message is raised
     </td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE TYPE (&lt;keyspace_name&gt;).&lt;type_name&gt;;</strong></td>
     <td>
        Describe the given type(UDT). If the keyspace is not provided, the current logged in keyspace is used.
        If there is no logged in keyspace, the default system keyspace is used.
        If no type is found, an error message is raised
     </td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE FUNCTION (&lt;keyspace_name&gt;).&lt;function_name&gt;;</strong></td>
     <td>Describe the given function. If the keyspace is not provided, the current logged in keyspace is used.
         If there is no logged in keyspace, the default system keyspace is used.
         If no function is found, an error message is raised
     </td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE AGGREGATE (&lt;keyspace_name&gt;).&lt;aggregate_name&gt;;</strong></td>
     <td>Describe the given aggregate. If the keyspace is not provided, the current logged in keyspace is used.
         If there is no logged in keyspace, the default system keyspace is used.
         If no aggregate is found, an error message is raised
     </td>
   </tr>
   <tr>
     <td nowrap><strong>DESCRIBE MATERIALIZED VIEW (&lt;keyspace_name&gt;).&lt;view_name&gt;;</strong></td>
     <td>Describe the given view. If the keyspace is not provided, the current logged in keyspace is used.
         If there is no logged in keyspace, the default system keyspace is used.
         If no view is found, an error message is raised
     </td>
   </tr>
 </table>
</center>

The schema objects (cluster, keyspace, table, type, function and aggregate) are displayed in a tabular format.
There is a drop-down menu on the top left corner to expand objects details. On the top right menu is shown the Icon legend.

<center>
  ![Describe Schema]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/cassandra-DescribeSchema.png)
</center>

## Runtime Execution Parameters

Sometimes you want to be able to pass runtime query parameters to your statements.

Those parameters are not part of the CQL specs and are specific to the interpreter.

Below is the list of all parameters:

<center>
 <table class="table-configuration">
   <tr>
     <th>Parameter</th>
     <th>Syntax</th>
     <th>Description</th>
   </tr>
   <tr>
     <td nowrap>Consistency Level</td>
     <td><strong>@consistency=<em>value</em></strong></td>
     <td>Apply the given consistency level to all queries in the paragraph</td>
   </tr>
   <tr>
     <td nowrap>Serial Consistency Level</td>
     <td><strong>@serialConsistency=<em>value</em></strong></td>
     <td>Apply the given serial consistency level to all queries in the paragraph</td>
   </tr>
   <tr>
     <td nowrap>Timestamp</td>
     <td><strong>@timestamp=<em>long value</em></strong></td>
     <td>
        Apply the given timestamp to all queries in the paragraph.
        Please note that timestamp value passed directly in CQL statement will override this value
      </td>
   </tr>
   <tr>
     <td nowrap>Fetch Size</td>
     <td><strong>@fetchSize=<em>integer value</em></strong></td>
     <td>Apply the given fetch size to all queries in the paragraph</td>
   </tr>
   <tr>
     <td nowrap>Request Time Out</td>
     <td><strong>@requestTimeOut=<em>integer value</em></strong></td>
     <td>Apply the given request timeout <strong>in millisecs</strong> to all queries in the paragraph</td>
   </tr>   
 </table>
</center>

Some parameters only accept restricted values:

<center>
 <table class="table-configuration">
   <tr>
     <th>Parameter</th>
     <th>Possible Values</th>
   </tr>
   <tr>
     <td nowrap>Consistency Level</td>
     <td><strong>ALL, ANY, ONE, TWO, THREE, QUORUM, LOCAL_ONE, LOCAL_QUORUM, EACH_QUORUM</strong></td>
   </tr>
   <tr>
     <td nowrap>Serial Consistency Level</td>
     <td><strong>SERIAL, LOCAL_SERIAL</strong></td>
   </tr>
   <tr>
     <td nowrap>Timestamp</td>
     <td>Any long value</td>
   </tr>
   <tr>
     <td nowrap>Fetch Size</td>
     <td>Any integer value</td>
   </tr>
 </table>
</center>

>Please note that you should **not** add semi-colon ( **;** ) at the end of each parameter statement

Some examples:

```sql

CREATE TABLE IF NOT EXISTS spark_demo.ts(
    key int PRIMARY KEY,
    value text
);
TRUNCATE spark_demo.ts;

// Timestamp in the past
@timestamp=10

// Force timestamp directly in the first insert
INSERT INTO spark_demo.ts(key,value) VALUES(1,'first insert') USING TIMESTAMP 100;

// Select some data to make the clock turn
SELECT * FROM spark_demo.albums LIMIT 100;

// Now insert using the timestamp parameter set at the beginning(10)
INSERT INTO spark_demo.ts(key,value) VALUES(1,'second insert');

// Check for the result. You should see 'first insert'
SELECT value FROM spark_demo.ts WHERE key=1;
```

Some remarks about query parameters:

> 1. **many** query parameters can be set in the same paragraph
> 2. if the **same** query parameter is set many time with different values, the interpreter only take into account the first value
> 3. each query parameter applies to **all CQL statements** in the same paragraph, unless you override the option using plain CQL text (like forcing timestamp with the `USING` clause)
> 4. the order of each query parameter with regard to CQL statement does not matter

## Runtime Formatting Parameters

Sometimes you want to be able to format output of your statement. Cassandra interpreter allows to specify different parameters as local properties of the paragraph. Below is the list of all formatting parameters:

<center>
 <table class="table-configuration">
   <tr>
     <th>Parameter</th>
     <th>Syntax</th>
     <th>Description</th>
   </tr>
   <tr>
     <td nowrap>Output Format</td>
     <td><strong>outputFormat=<em>value</em></strong></td>
     <td>Controls, should we output data as CQL literals, or in human-readable form. Possible values: <strong>cql, human</strong> (default: <strong>human</strong></td>
   </tr>
   <tr>
     <td nowrap>Locale</td>
     <td><strong>locale=<em>value</em></strong></td>
     <td>Locale for formatting of numbers & time-related values. Could be any locale supported by JVM (default: <strong>en_US</strong>)</td>
   </tr>
   <tr>
     <td nowrap>Timezone</td>
     <td><strong>timezone=<em>value</em></strong></td>
     <td>Timezone for formatting of time-related values. Could be any timezone supported by JVM (default: <strong>UTC</strong>)</td>
   </tr>
   <tr>
     <td nowrap>Float precision</td>
     <td><strong>floatPrecision=<em>value</em></strong></td>
     <td>Precision when formatting <tt>float</tt> values. Any positive integer value</td>
   </tr>
   <tr>
     <td nowrap>Double precision</td>
     <td><strong>doublePrecision=<em>value</em></strong></td>
     <td>Precision when formatting <tt>double</tt> values. Any positive integer value</td>
   </tr>
   <tr>
     <td nowrap>Timestamp Format</td>
     <td><strong>timestampFormat=<em>value</em></strong></td>
     <td>Format string for <tt>timestamp</tt> values. Should be valid <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter</a> pattern</td>
   </tr>
   <tr>
     <td nowrap>Time Format</td>
     <td><strong>timeFormat=<em>value</em></strong></td>
     <td>Format string for <tt>time</tt> values. Should be valid <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter</a> pattern</td>
   </tr>
   <tr>
     <td nowrap>Date Format</td>
     <td><strong>dateFormat=<em>value</em></strong></td>
     <td>Format string for <tt>date</tt> values. Should be valid <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter</a> pattern</td>
   </tr>
 </table>
</center>


Some examples:

```sql
create table if not exists zep.test_format (
  id int primary key,
  text text,
  date date,
  timestamp timestamp,
  time time,
  double double,
  float float
);

insert into zep.test_format(id, text, date, timestamp, time, double, float)
  values (1, 'text', '2019-01-29', '2020-06-16T23:59:59.123Z', '04:05:00.234', 
  10.0153423453425634653463466346543, 20.0303443); 
```
  
```
%cassandra(outputFormat=human, locale=de_DE, floatPrecision=2, doublePrecision=4, timeFormat=hh:mma, timestampFormat=MM/dd/yy HH:mm, dateFormat="E, d MMM yy", timezone=Etc/GMT+2)
select id, double, float, text, date, time, timestamp from zep.test_format;
```

will output data formatted according to settings, including German locale:

```
id  double   float  text  date           time     timestamp
1   10,0153	 20,03  text  Di, 29 Jan 19  04:05AM  06/16/20 21:59
```

while with `outputFormat=cql`, data is formatted as CQL literals:

```
id double              float       text    date        time                  timestamp
1  10.015342345342564  20.030344  'text'  '2019-01-29' '04:05:00.234000000'  '2020-06-17T01:59:59.123+02:00'
```


## Support for Prepared Statements

For performance reason, it is better to prepare statements before-hand and reuse them later by providing bound values.

This interpreter provides 3 commands to handle prepared and bound statements:

1. **@prepare**
2. **@bind**
3. **@remove_prepared**

Example:

```
@prepare[statement-name]=...

@bind[statement-name]=’text’, 1223, ’2015-07-30 12:00:01’, null, true, [‘list_item1’, ’list_item2’]

@bind[statement-name-with-no-bound-value]

@remove_prepare[statement-name]
```

#### @prepare

You can use the syntax _"@prepare[statement-name]=SELECT..."_ to create a prepared statement.
The _statement-name_ is **mandatory** because the interpreter prepares the given statement with the Java driver and
saves the generated prepared statement in an **internal hash map**, using the provided _statement-name_ as search key.

> Please note that this internal prepared statement map is shared with **all notebooks** and **all paragraphs** because
there is only one instance of the interpreter for Cassandra

> If the interpreter encounters **many** `@prepare` for the **same _statement-name_ (key)**, only the **first** statement will be taken into account.

Example:

```
@prepare[select]=SELECT * FROM spark_demo.albums LIMIT ?

@prepare[select]=SELECT * FROM spark_demo.artists LIMIT ?
```

For the above example, the prepared statement is `SELECT * FROM spark_demo.albums LIMIT ?`.
`SELECT * FROM spark_demo.artists LIMIT ?` is ignored because an entry already exists in the prepared statements map with the key _select_.

In the context of **Zeppelin**, a notebook can be scheduled to be executed at regular interval,
thus it is necessary to **avoid re-preparing many time the same statement (considered an anti-pattern)**.

#### @bind
Once the statement is prepared (possibly in a separated notebook/paragraph). You can bind values to it:

```
@bind[select_first]=10
```

Bound values are not mandatory for the **@bind** statement. However if you provide bound values, they need to comply to some syntax:

* String values should be enclosed between simple quotes (**'**)
* Date values should be enclosed between simple quotes (**'**) and respect the formats (full list is in the [documentation](https://docs.datastax.com/en/cql/3.3/cql/cql_reference/timestamp_type_r.html)):
  1. yyyy-MM-dd HH:MM:ss
  2. yyyy-MM-dd HH:MM:ss.SSS
  2. yyyy-mm-dd'T'HH:mm:ss.SSSZ
* **null** is parsed as-is
* **boolean** (`true`|`false`) are parsed as-is
* collection values must follow the **[standard CQL syntax]**:
  * list: ['list_item1', 'list_item2', ...]
  * set: {'set_item1', 'set_item2', …}
  * map: {'key1': 'val1', 'key2': 'val2', …}
* **tuple** values should be enclosed between parenthesis (see **[Tuple CQL syntax]**): ('text', 123, true)
* **udt** values should be enclosed between brackets (see **[UDT CQL syntax]**): {stree_name: 'Beverly Hills', number: 104, zip_code: 90020, state: 'California', …}

> It is possible to use the @bind statement inside a batch:
>
> ```sql
>BEGIN BATCH
>    @bind[insert_user]='jdoe','John DOE'
>    UPDATE users SET age = 27 WHERE login='hsue';
>APPLY BATCH;
> ```

#### @remove_prepare

To avoid for a prepared statement to stay forever in the prepared statement map, you can use the
**@remove_prepare[statement-name]** syntax to remove it.
Removing a non-existing prepared statement yields no error.

## Using Dynamic Forms

Instead of hard-coding your CQL queries, it is possible to use **[Zeppelin dynamic form]** syntax to inject simple value or multiple choices forms.

The legacy mustache syntax ( **\{\{ \}\}** ) to bind input text and select form is still supported but is deprecated and will be removed in future releases.

> **Legacy**
> The syntax for simple parameter is: **\{\{input_Label=default value\}\}**. The default value is mandatory because the first time the paragraph is executed,
> we launch the CQL query before rendering the form so at least one value should be provided.
>
> The syntax for multiple choices parameter is: **\{\{input_Label=value1 | value2 | … | valueN \}\}**. By default the first choice is used for CQL query
> the first time the paragraph is executed.


Example:

{% raw %}
    #Secondary index on performer style
    SELECT name, country, performer
    FROM spark_demo.performers
    WHERE name='${performer=Sheryl Crow|Doof|Fanfarlo|Los Paranoia}'
    AND styles CONTAINS '${style=Rock}';
{% endraw %}

In the above example, the first CQL query will be executed for `performer='Sheryl Crow' AND style='Rock'`.
For subsequent queries, you can change the value directly using the form.

> Please note that we enclosed the **$\{ \}** block between simple quotes ( **'** ) because Cassandra expects a String here.
> We could have also use the **$\{style='Rock'\}** syntax but this time, the value displayed on the form is **_'Rock'_** and not **_Rock_**.

It is also possible to use dynamic forms for **prepared statements**:

{% raw %}
    @bind[select]=='${performer=Sheryl Crow|Doof|Fanfarlo|Los Paranoia}', '${style=Rock}'
{% endraw %}

## Shared states

It is possible to execute many paragraphs in parallel. However, at the back-end side, we're still using synchronous queries.
_Asynchronous execution_ is only possible when it is possible to return a `Future` value in the `InterpreterResult`.
It may be an interesting proposal for the **Zeppelin** project.

Recently, **Zeppelin** allows you to choose the level of isolation for your interpreters (see **[Interpreter Binding Mode]** ).

Long story short, you have 3 available bindings:
 
 - **shared** : _same JVM_ and _same Interpreter instance_ for all notes
 - **scoped** : _same JVM_ but _different Interpreter instances_, one for each note
 - **isolated**: _different JVM_ running a _single Interpreter instance_, one JVM for each note
     
Using the **shared** binding, the same `com.datastax.driver.core.Session` object is used for **all** notes and paragraphs.
Consequently, if you use the `USE keyspace_name;` statement to log into a keyspace, it will change the keyspace for
**all current users** of the **Cassandra** interpreter because we only create 1 `com.datastax.driver.core.Session` object
per instance of **Cassandra** interpreter.

The same remark does apply to the **prepared statement hash map**, it is shared by **all users** using the same instance of **Cassandra** interpreter.

When using **scoped** binding, in the _same JVM_ **Zeppelin** will create multiple instances of the Cassandra interpreter, thus 
multiple `com.datastax.driver.core.Session` objects. **Beware of resource and memory usage using this binding !** 

The **isolated** mode is the most extreme and will create as many JVM/`com.datastax.driver.core.Session` object as there are distinct notes.


## Interpreter Configuration

To configure the **Cassandra** interpreter, go to the **Interpreter** menu and scroll down to change the parameters.
The **Cassandra** interpreter is using the official **[Datastax Java Driver for Apache Cassandra]®** and most of the parameters are used to configure the Java driver

Below are the configuration parameters supported by interpreter and their default values.

 <table class="table-configuration">
   <tr>
     <th>Property Name</th>
     <th>Description</th>
     <th>Default Value</th>
   </tr>
   <tr>
     <td>`cassandra.cluster`</td>
     <td>Name of the Cassandra cluster to connect to</td>
     <td>Test Cluster</td>
   </tr>
   <tr>
     <td>`cassandra.compression.protocol`</td>
     <td>On wire compression. Possible values are: `NONE`, `SNAPPY`, `LZ4`</td>
     <td>`NONE`</td>
   </tr>
   <tr>
     <td>`cassandra.credentials.username`</td>
     <td>If security is enable, provide the login</td>
     <td>none</td>
   </tr>
   <tr>
     <td>`cassandra.credentials.password`</td>
     <td>If security is enable, provide the password</td>
     <td>none</td>
   </tr>
   <tr>
     <td>`cassandra.hosts`</td>
     <td>
        Comma separated Cassandra hosts (DNS name or IP address).
        <br/>
        Ex: `192.168.0.12,node2,node3`
      </td>
     <td>`localhost`</td>
   </tr>
   <tr>
     <td>`cassandra.interpreter.parallelism`</td>
     <td>Number of concurrent paragraphs(queries block) that can be executed</td>
     <td>10</td>
   </tr>
   <tr>
     <td>`cassandra.keyspace`</td>
     <td>
        Default keyspace to connect to.
        <strong>
          It is strongly recommended to let the default value
          and prefix the table name with the actual keyspace
          in all of your queries
        </strong>
     </td>
     <td>`system`</td>
   </tr>
   <tr>
     <td>`cassandra.load.balancing.policy`</td>
     <td>
        Load balancing policy. Default = `DefaultLoadBalancingPolicy`
        To Specify your own policy, provide the <em>fully qualify class name (FQCN)</em> of your policy.
        At runtime the driver will instantiate the policy using class name.
     </td>
     <td>DEFAULT</td>
   </tr>
   <tr>
     <td>`cassandra.max.schema.agreement.wait.second`</td>
     <td>Cassandra max schema agreement wait in second</td>
     <td>10</td>
   </tr>
   <tr>
     <td>`cassandra.pooling.connection.per.host.local`</td>
     <td>Protocol V3 and above default = 1</td>
     <td>1</td>
   </tr>
   <tr>
     <td>`cassandra.pooling.connection.per.host.remote`</td>
     <td>Protocol V3 and above default = 1</td>
     <td>1</td>
   </tr>
   <tr>
     <td>`cassandra.pooling.heartbeat.interval.seconds`</td>
     <td>Cassandra pool heartbeat interval in secs</td>
     <td>30</td>
   </tr>
   <tr>
     <td>`cassandra.pooling.max.request.per.connection`</td>
     <td>Protocol V3 and above default = 1024</td>
     <td>1024</td>
   </tr>
   <tr>
     <td>`cassandra.pooling.pool.timeout.millisecs`</td>
     <td>Cassandra pool time out in millisecs</td>
     <td>5000</td>
   </tr>
   <tr>
     <td>`cassandra.protocol.version`</td>
     <td>Cassandra binary protocol version (`V3`, `V4`, ...)</td>
     <td>`DEFAULT` (detected automatically)</td>
   </tr>
   <tr>
     <td>cassandra.query.default.consistency</td>
     <td>
      Cassandra query default consistency level
      <br/>
      Available values: `ONE`, `TWO`, `THREE`, `QUORUM`, `LOCAL_ONE`, `LOCAL_QUORUM`, `EACH_QUORUM`, `ALL`
     </td>
     <td>`ONE`</td>
   </tr>
   <tr>
     <td>`cassandra.query.default.fetchSize`</td>
     <td>Cassandra query default fetch size</td>
     <td>5000</td>
   </tr>
   <tr>
     <td>`cassandra.query.default.serial.consistency`</td>
     <td>
      Cassandra query default serial consistency level
      <br/>
      Available values: `SERIAL`, `LOCAL_SERIAL`
     </td>
     <td>`SERIAL`</td>
   </tr>
   <tr>
     <td>`cassandra.reconnection.policy`</td>
     <td>
        Cassandra Reconnection Policy.
        Default = `ExponentialReconnectionPolicy`
        To Specify your own policy, provide the <em>fully qualify class name (FQCN)</em> of your policy.
        At runtime the driver will instantiate the policy using class name.
     </td>
     <td>DEFAULT</td>
   </tr>
   <tr>
     <td>`cassandra.retry.policy`</td>
     <td>
        Cassandra Retry Policy.
        Default = `DefaultRetryPolicy`
        To Specify your own policy, provide the <em>fully qualify class name (FQCN)</em> of your policy.
        At runtime the driver will instantiate the policy using class name.
     </td>
     <td>DEFAULT</td>
   </tr>
   <tr>
     <td>`cassandra.socket.connection.timeout.millisecs`</td>
     <td>Cassandra socket default connection timeout in millisecs</td>
     <td>500</td>
   </tr>
   <tr>
     <td>`cassandra.socket.read.timeout.millisecs`</td>
     <td>Cassandra socket read timeout in millisecs</td>
     <td>12000</td>
   </tr>
   <tr>
     <td>`cassandra.socket.tcp.no_delay`</td>
     <td>Cassandra socket TCP no delay</td>
     <td>true</td>
   </tr>
   <tr>
     <td>`cassandra.speculative.execution.policy`</td>
     <td>
        Cassandra Speculative Execution Policy.
        Default = `NoSpeculativeExecutionPolicy`
        To Specify your own policy, provide the <em>fully qualify class name (FQCN)</em> of your policy.
        At runtime the driver will instantiate the policy using class name.
     </td>
     <td>DEFAULT</td>
   </tr>
   <tr>
     <td>`cassandra.ssl.enabled`</td>
     <td>
        Enable support for connecting to the Cassandra configured with SSL.
        To connect to Cassandra configured with SSL use <strong>true</strong>
        and provide a truststore file and password with following options.
     </td>
     <td>false</td>
   </tr>
   <tr>
     <td>`cassandra.ssl.truststore.path`</td>
     <td>
        Filepath for the truststore file to use for connection to Cassandra with SSL.
     </td>
     <td></td>
   </tr>
   <tr>
     <td>`cassandra.ssl.truststore.password`</td>
     <td>
        Password for the truststore file to use for connection to Cassandra with SSL.
     </td>
     <td></td>
   </tr>
   <tr>
     <td>`cassandra.format.output`</td>
     <td>Output format for data - strict CQL (`cql`), or human-readable (`human`)</td>
     <td>`human`</td>
   </tr>
   <tr>
     <td>`cassandra.format.locale`</td>
     <td>Which locale to use for output (any locale supported by JVM could be specified)</td>
     <td>`en_US`</td>
   </tr>
   <tr>
     <td>`cassandra.format.timezone`</td>
     <td>For which timezone format time/date-related types (any timezone supported by JVM could be specified)</td>
     <td>`UTC`</td>
   </tr>
   <tr>
     <td>`cassandra.format.timestamp`</td>
     <td>Format string for `timestamp` columns (any valid <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter</a> pattern could be used)</td>
     <td>`yyyy-MM-dd'T'HH:mm:ss.SSSXXX`</td>
   </tr>
   <tr>
     <td>`cassandra.format.time`</td>
     <td>Format string for `time` columns (any valid <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter</a> pattern could be used)</td>
     <td>`HH:mm:ss.SSS`</td>
   </tr>
   <tr>
     <td>`cassandra.format.date`</td>
     <td>Format string for `date` columns (any valid <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter</a> pattern could be used)</td>
     <td>`yyyy-MM-dd`</td>
   </tr>
   <tr>
     <td>`cassandra.format.float_precision`</td>
     <td>Precision when formatting values of `float` type</td>
     <td>`5`</td>
   </tr>
   <tr>
     <td>`cassandra.format.double_precision`</td>
     <td>Precision when formatting values of `double` type</td>
     <td>`12`</td>
   </tr>
 </table>

Besides these parameters, it's also possible to set other driver parameters by adding them into interpreter configuration.  The configuration key should have full form with `datastax-java-driver` prefix, as [described in documentation](https://docs.datastax.com/en/developer/java-driver/latest/manual/core/configuration/).  For example, to specify 5 seconds request timeout, you can use `datastax-java-driver.basic.request.timeout` with value of `5 seconds`.  Full list of available configuration options is [available in documentation](https://docs.datastax.com/en/developer/java-driver/latest/manual/core/configuration/reference/).  Additional options may override the options that are specified by the interpreter's configuration parameters.

## Change Log

**4.0** _(Zeppelin {{ site.ZEPPELIN_VERSION }})_ :

* Refactor to use unified Java driver 4.7 ([ZEPPELIN-4378](https://issues.apache.org/jira/browse/ZEPPELIN-4378):
  * changes in configuration were necessary, as new driver has different architecture, and configuration options
  * interpreter got support for DSE-specific data types, and other extensions
  * support for `@retryPolicy` is removed, as only single retry policy is shipped with driver
  * allow to specify any configuration option of Java driver
  * dropped support for Cassandra 1.2 & 2.0, that isn't supported by driver anymore
* added support for formatting options, both interpreter & cell level

**3.1** _(Zeppelin {{ site.ZEPPELIN_VERSION }})_ :

* Upgrade Java driver to 3.7.2 ([ZEPPELIN-4331](https://issues.apache.org/jira/browse/ZEPPELIN-4331);

**3.0** _(Zeppelin {{ site.ZEPPELIN_VERSION }})_ :

* Update documentation
* Update interactive documentation
* Add support for binary protocol **V4**
* Implement new `@requestTimeOut` runtime option
* Upgrade Java driver version to **3.0.1**
* Allow interpreter to add dynamic forms programmatically when using FormType.SIMPLE
* Allow dynamic form using default Zeppelin syntax
* Fixing typo on FallThroughPolicy
* Look for data in AngularObjectRegistry before creating dynamic form
* Add missing support for `ALTER` statements


**2.0** _(Zeppelin {{ site.ZEPPELIN_VERSION }})_ :

* Update help menu and add changelog
* Add Support for **User Defined Functions**, **User Defined Aggregates** and **Materialized Views**
* Upgrade Java driver version to **3.0.0-rc1**

**1.0** _(Zeppelin 0.5.5-incubating)_ :

* Initial version

## Bugs & Contacts

 If you encounter a bug for this interpreter, please create a **[JIRA]** ticket.

[Datastax Java Driver for Apache Cassandra]: https://docs.datastax.com/en/developer/java-driver/latest/
[standard CQL syntax]: http://docs.datastax.com/en/cql/3.1/cql/cql_using/use_collections_c.html
[Tuple CQL syntax]: http://docs.datastax.com/en/cql/3.1/cql/cql_reference/tupleType.html
[UDT CQL syntax]: http://docs.datastax.com/en/cql/3.1/cql/cql_using/cqlUseUDT.html
[Zeppelin Dynamic Form](../usage/dynamic_form/intro.html)
[Interpreter Binding Mode](../usage/interpreter/interpreter_binding_mode.html)
[JIRA]: https://issues.apache.org/jira/browse/ZEPPELIN
