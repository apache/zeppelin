---
layout: page
title: "Generic JDBC Interpreter for Apache Zeppelin"
description: "Generic JDBC Interpreter lets you create a JDBC connection to any data source. You can use Postgres, MySql, MariaDB, Redshift, Apache Hive, Presto/Trino, Impala, Apache Phoenix, Apache Drill and Apache Tajo using JDBC interpreter."
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

# Generic JDBC Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview

JDBC interpreter lets you create a JDBC connection to any data sources seamlessly.

Inserts, Updates, and Upserts are applied immediately after running each statement.

By now, it has been tested with:

<div class="row" style="margin: 30px auto;">
  <div class="col-md-6">
    <img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/tested_databases.png" width="300px"/>
  </div>
  <div class="col-md-6">
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="http://www.postgresql.org/" target="_blank" rel="noopener noreferrer">Postgresql</a> -
      <a href="https://jdbc.postgresql.org/" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://www.mysql.com/" target="_blank" rel="noopener noreferrer">Mysql</a> -
      <a href="https://dev.mysql.com/downloads/connector/j/" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://mariadb.org/" target="_blank" rel="noopener noreferrer">MariaDB</a> -
      <a href="https://mariadb.com/kb/en/mariadb/about-mariadb-connector-j/" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://aws.amazon.com/documentation/redshift/" target="_blank" rel="noopener noreferrer">Redshift</a> -
      <a href="https://docs.aws.amazon.com/redshift/latest/mgmt/configure-jdbc-connection.html" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://hive.apache.org/" target="_blank" rel="noopener noreferrer">Apache Hive</a> - 
      <a href="https://cwiki.apache.org/confluence/display/Hive/HiveServer2+Clients#HiveServer2Clients-JDBC" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://trino.io/" target="_blank" rel="noopener noreferrer">Presto/Trino</a> - 
      <a href="https://trino.io/docs/current/installation/jdbc.html" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://impala.apache.org/" target="_blank" rel="noopener noreferrer">Impala</a> - 
      <a href="https://impala.apache.org/docs/build/html/topics/impala_jdbc.html" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://phoenix.apache.org/" target="_blank" rel="noopener noreferrer">Apache Phoenix</a> itself is a JDBC driver
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="https://drill.apache.org/" target="_blank" rel="noopener noreferrer">Apache Drill</a> - 
      <a href="https://drill.apache.org/docs/using-the-jdbc-driver" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
    <li style="padding-bottom: 5px; list-style: circle">
      <a href="http://tajo.apache.org/" target="_blank" rel="noopener noreferrer">Apache Tajo</a> - 
      <a href="https://tajo.apache.org/docs/current/jdbc_driver.html" target="_blank" rel="noopener noreferrer">JDBC Driver</a>
    </li>
  </div>
</div>

If you are using other databases not in the above list, please feel free to share your use case. It would be helpful to improve the functionality of JDBC interpreter.

## Create a new JDBC Interpreter

First, click `+ Create` button at the top-right corner in the interpreter setting page.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/click_create_button.png" width="600px"/>

Fill `Interpreter name` field with whatever you want to use as the alias(e.g. mysql, mysql2, hive, redshift, and etc..). 
Please note that this alias will be used as `%interpreter_name` to call the interpreter in the paragraph. 
Then select `jdbc` as an `Interpreter group`. 

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/select_name_and_group.png" width="200px"/>

The default driver of JDBC interpreter is set as `PostgreSQL`. It means Zeppelin includes `PostgreSQL` driver jar in itself.
So you don't need to add any dependencies(e.g. the artifact name or path for `PostgreSQL` driver jar) for `PostgreSQL` connection.
The JDBC interpreter properties are defined by default like below.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>common.max_count</td>
    <td>1000</td>
    <td>The maximun number of SQL result to display</td>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.postgresql.Driver</td>
    <td>JDBC Driver Name</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td></td>
    <td>The JDBC user password</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:postgresql://localhost:5432/</td>
    <td>The URL for JDBC</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>gpadmin</td>
    <td>The JDBC user name</td>
  </tr>
  <tr>
    <td>default.precode</td>
    <td></td>
    <td>Some SQL which executes every time after initialization of the interpreter (see <a href="../usage/interpreter/overview.html#interpreter-binding-mode">Binding mode</a>)</td>
  </tr>
  <tr>
    <td>default.statementPrecode</td>
    <td></td>
    <td>SQL code which executed before the SQL from paragraph, in the same database session (database connection)</td>
  </tr>
  <tr>
    <td>default.completer.schemaFilters</td>
    <td></td>
    <td>Сomma separated schema (schema = catalog = database) filters to get metadata for completions. Supports '%' symbol is equivalent to any set of characters. (ex. prod_v_%,public%,info)</td>
  </tr>
  <tr>
    <td>default.completer.ttlInSeconds</td>
    <td>120</td>
    <td>Time to live sql completer in seconds (-1 to update everytime, 0 to disable update)</td>
  </tr>
</table>

If you want to connect other databases such as `Mysql`, `Redshift` and `Hive`, you need to edit the property values.
You can also use [Credential](../setup/security/datasource_authorization.html) for JDBC authentication.
If `default.user` and `default.password` properties are deleted(using X button) for database connection in the interpreter setting page,
the JDBC interpreter will get the account information from [Credential](../setup/security/datasource_authorization.html).

The below example is for `Mysql` connection.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/edit_properties.png" width="600px" />

The last step is **Dependency Setting**. Since Zeppelin only includes `PostgreSQL` driver jar by default, you need to add each driver's maven coordinates or JDBC driver's jar file path for the other databases.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/edit_dependencies.png" width="600px" />

That's it. You can find more JDBC connection setting examples([Mysql](#mysql), [MariaDB](#mariadb), [Redshift](#redshift), [Apache Hive](#apache-hive), [Presto/Trino](#prestotrino), [Impala](#impala), [Apache Kyuubi (Incubating)](#apache-kyuubi-(incubating)), [Apache Phoenix](#apache-phoenix), and [Apache Tajo](#apache-tajo)) in [this section](#examples).

## JDBC Interpreter Datasource Pool Configuration
The Jdbc interpreter uses the connection pool technology, and supports users to do some personal configuration of the connection pool. For example, we can configure `default.validationQuery='select 1'` and `default.testOnBorrow=true` in the Interpreter configuration to avoid the "Invalid SessionHandle" runtime error caused by Session timeout when connecting to HiveServer2 through JDBC interpreter.

The Jdbc Interpreter supports the following database connection pool configurations:

<table class="table-configuration">
  <tr>
    <th>Property Name</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>testOnBorrow</td>
    <td>false</td>
    <td>The indication of whether objects will be validated before being borrowed from the pool. If the object fails to validate, it will be dropped from the pool, and we will attempt to borrow another.</td>
  </tr>
  <tr>
    <td>testOnCreate</td>
    <td>false</td>
    <td>The indication of whether objects will be validated after creation. If the object fails to validate, the borrow attempt that triggered the object creation will fail.</td>
  </tr>
  <tr>
    <td>testOnReturn</td>
    <td>false</td>
    <td>The indication of whether objects will be validated before being returned to the pool.</td>
  </tr>
  <tr>
    <td>testWhileIdle</td>
    <td>false</td>
    <td>The indication of whether objects will be validated by the idle object evictor (if any). If an object fails to validate, it will be dropped from the pool.</td>
  </tr>
  <tr>
    <td>timeBetweenEvictionRunsMillis</td>
    <td>-1L</td>
    <td>The number of milliseconds to sleep between runs of the idle object evictor thread. When non-positive, no idle object evictor thread will be run.</td>
  </tr>
  <tr>
    <td>maxWaitMillis</td>
    <td>-1L</td>
    <td>The maximum number of milliseconds that the pool will wait (when there are no available connections) for a connection to be returned before throwing an exception, or -1 to wait indefinitely.</td>
  </tr>
  <tr>
    <td>maxIdle</td>
    <td>8</td>
    <td>The maximum number of connections that can remain idle in the pool, without extra ones being released, or negative for no limit.</td>
  </tr>
  <tr>
    <td>minIdle</td>
    <td>0</td>
    <td>The minimum number of connections that can remain idle in the pool, without extra ones being created, or zero to create none.</td>
  </tr>
  <tr>
    <td>maxTotal</td>
    <td>-1</td>
    <td>The maximum number of active connections that can be allocated from this pool at the same time, or negative for no limit.</td>
  </tr>
  <tr>
    <td>validationQuery</td>
    <td>show database</td>
    <td>The SQL query that will be used to validate connections from this pool before returning them to the caller. If specified, this query MUST be an SQL SELECT statement that returns at least one row. If not specified, connections will be validation by calling the isValid() method.</td>
  </tr>
</table>


## More properties
There are more JDBC interpreter properties you can specify like below.

<table class="table-configuration">
  <tr>
    <th>Property Name</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>common.max_result</td>
    <td>Max number of SQL result to display to prevent the browser overload. This is  common properties for all connections</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.auth.type</td>
    <td>Types of authentications' methods supported are <code>SIMPLE</code>, and <code>KERBEROS</code></td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.principal</td>
    <td>The principal name to load from the keytab</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.keytab.location</td>
    <td>The path to the keytab file</td>
  </tr>
  <tr>
      <td>zeppelin.jdbc.auth.kerberos.proxy.enable</td>
      <td>When auth type is Kerberos, enable/disable Kerberos proxy with the login user to get the connection. Default value is true.</td>
  </tr>
  <tr>
    <td>default.jceks.file</td>
    <td>jceks store path (e.g: jceks://file/tmp/zeppelin.jceks)</td>
  </tr>
  <tr>
    <td>default.jceks.credentialKey</td>
    <td>jceks credential key</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.interpolation</td>
    <td>Enables ZeppelinContext variable interpolation into paragraph text. Default value is false.</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.maxConnLifetime</td>
    <td>Maximum of connection lifetime in milliseconds. A value of zero or less means the connection has an infinite lifetime.</td>
  </tr>
</table>

You can also add more properties by using this [method](http://docs.oracle.com/javase/7/docs/api/java/sql/DriverManager.html#getConnection%28java.lang.String,%20java.util.Properties%29).
For example, if a connection needs a schema parameter, it would have to add the property as follows:

<table class="table-configuration">
  <tr>
    <th>name</th>
    <th>value</th>
  </tr>
  <tr>
    <td>default.schema</td>
    <td>schema_name</td>
  </tr>
</table>

## How to use
### Run the paragraph with JDBC interpreter
To test whether your databases and Zeppelin are successfully connected or not, type `%jdbc_interpreter_name`(e.g. `%mysql`) at the top of the paragraph and run `show databases`.

```sql
%jdbc_interpreter_name

show databases
```
If the paragraph is `FINISHED` without any errors, a new paragraph will be automatically added after the previous one with `%jdbc_interpreter_name`.
So you don't need to type this prefix in every paragraphs' header.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/run_paragraph_with_jdbc.png" width="600px" />


### Multiple SQL statements

You can write multiple sql statements in one paragraph, just separate them with semi-colon. e.g

```sql
%jdbc_interpreter_name

USE zeppelin_demo;

CREATE TABLE pet (name VARCHAR(20), owner VARCHAR(20),
       species VARCHAR(20), sex CHAR(1), birth DATE, death DATE);
```

### SQL Comment

2 kinds of SQL comments are supported:

* Single line comment start with `--`
* Multiple line comment around with `/*  ... */`

```sql

%jdbc_interpreter_name

-- single line comment
show tables;
/* multiple 
   line 
   comment
 */
select * from test_1;

```


### Apply Zeppelin Dynamic Forms

You can leverage [Zeppelin Dynamic Form](../usage/dynamic_form/intro.html) inside your queries. You can use both the `text input` and `select form` parametrization features.

### Run SQL Continuously

By default, sql statements in one paragraph are executed only once. But you can run it continuously by specifying local property `refreshInterval` (unit: milli-seconds),
So that the sql statements are executed every interval of `refreshInterval` milli-seconds. This is useful when your data in database is updated continuously by external system,
and you can build dynamic dashboard in Zeppelin via this approach.

e.g. Here we query the mysql which is updated continuously by other external system.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/jdbc_refresh.gif" width="800px" />

### Usage *precode*
You can set *precode* for each data source. Code runs once while opening the connection.

##### Properties
An example settings of interpreter for the two data sources, each of which has its *precode* parameter.

<table class="table-configuration">
  <tr>
    <th>Property Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.postgresql.Driver</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>1</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:postgresql://localhost:5432/</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>postgres</td>
  </tr>
  <tr>
    <td>default.precode</td>
    <td>set search_path='test_path'</td>
  </tr>
</table>

<table class="table-configuration">
  <tr>
    <td>default.driver</td>
    <td>com.mysql.jdbc.Driver</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>1</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:mysql://localhost:3306/</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>root</td>
  </tr>
  <tr>
    <td>default.precode</td>
    <td>set @v=12</td>
  </tr>
</table>

##### Usage
Test of execution *precode* for each data source.

```sql
%jdbc

show search_path
```
Returns value of `search_path` which is set in the default jdbc (use postgresql) interpreter's *default.precode*.


```sql
%mysql

select @v
```
Returns value of `v` which is set in the mysql interpreter's *default.precode*.


## Examples
Here are some examples you can refer to. Including the below connectors, you can connect every databases as long as it can be configured with it's JDBC driver.

### Postgres

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/postgres_setting.png" width="600px" />

##### Properties
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.postgresql.Driver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:postgresql://localhost:5432/</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>mysql_user</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>mysql_password</td>
  </tr>
</table>

[Postgres JDBC Driver Docs](https://jdbc.postgresql.org/documentation/documentation.html)

##### Dependencies
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.postgresql:postgresql:42.3.3</td>
    <td></td>
  </tr>
</table>

[Maven Repository: org.postgresql:postgresql](https://mvnrepository.com/artifact/org.postgresql/postgresql)

### Mysql

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/mysql_setting.png" width="600px" />

##### Properties
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>com.mysql.jdbc.Driver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:mysql://localhost:3306/</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>mysql_user</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>mysql_password</td>
  </tr>
</table>

[Mysql JDBC Driver Docs](https://dev.mysql.com/downloads/connector/j/)

##### Dependencies
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>mysql:mysql-connector-java:5.1.38</td>
    <td></td>
  </tr>
</table>

[Maven Repository: mysql:mysql-connector-java](https://mvnrepository.com/artifact/mysql/mysql-connector-java)

### MariaDB

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/mariadb_setting.png" width="600px" />

##### Properties
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.mariadb.jdbc.Driver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:mariadb://localhost:3306</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>mariadb_user</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>mariadb_password</td>
  </tr>
</table>

[MariaDB JDBC Driver Docs](https://mariadb.com/kb/en/mariadb/about-mariadb-connector-j/)

##### Dependencies
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.mariadb.jdbc:mariadb-java-client:1.5.4</td>
    <td></td>
  </tr>
</table>

[Maven Repository: org.mariadb.jdbc:mariadb-java-client](https://mvnrepository.com/artifact/org.mariadb.jdbc/mariadb-java-client)

### Redshift

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/redshift_setting.png" width="600px" />

##### Properties
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>com.amazon.redshift.jdbc42.Driver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:redshift://your-redshift-instance-address.redshift.amazonaws.com:5439/your-database</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>redshift_user</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>redshift_password</td>
  </tr>
</table>

[AWS Redshift JDBC Driver Docs](http://docs.aws.amazon.com/redshift/latest/mgmt/configure-jdbc-connection.html)

##### Dependencies
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>com.amazonaws:aws-java-sdk-redshift:1.11.51</td>
    <td></td>
  </tr>
</table>

[Maven Repository: com.amazonaws:aws-java-sdk-redshift](https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk-redshift)

### Apache Hive

Zeppelin just connect to `hiveserver2` to run hive sql via hive jdbc. There are 2 cases of connecting with Hive:

* Connect to Hive without KERBEROS
* Connect to Hive with KERBEROS

Each case requires different settings.

##### Connect to Hive without KERBEROS

In this scenario, you need to make the following settings at least. By default, hive job run as user of `default.user`.
Refer [impersonation](../interpreter/jdbc.html#impersonation) if you want hive job run as the Zeppelin login user when authentication is enabled.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.hive.jdbc.HiveDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:hive2://localhost:10000</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>hive_user</td>
  </tr>
</table>


<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.apache.hive:hive-jdbc:2.3.4</td>
    <td></td>
  </tr>
</table>


##### Connect to Hive with KERBEROS

In this scenario, you need to make the following settings at least. By default, hive job run as user of client principal (`zeppelin.jdbc.principal`).
Refer [impersonation](../interpreter/jdbc.html#impersonation) if you want hive job run as the Zeppelin login user when authentication is enabled.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.hive.jdbc.HiveDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:hive2://emr-header-1:10000/default;principal={hive_server2_principal}</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.auth.type</td>
    <td>KERBEROS</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.keytab.location</td>
    <td>keytab of client</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.principal</td>
    <td>principal of client</td>
  </tr>
</table>


<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.apache.hive:hive-jdbc:2.3.4</td>
    <td></td>
  </tr>
  <tr>
    <td>org.apache.hive:hive-exec:2.3.4</td>
    <td></td>
  </tr>
</table>


[Maven Repository : org.apache.hive:hive-jdbc](https://mvnrepository.com/artifact/org.apache.hive/hive-jdbc)

##### Impersonation

When Zeppelin server is running with authentication enabled, then the interpreter can utilize Hive's user proxy feature 
i.e. send extra parameter for creating and running a session ("hive.server2.proxy.user=": "${loggedInUser}"). 
This is particularly useful when multiple users are sharing a notebook.

To enable this set following:

  - `default.proxy.user.property` as `hive.server2.proxy.user`
  
See [User Impersonation in interpreter](../usage/interpreter/user_impersonation.html) for more information.

##### Sample configuration
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.hive.jdbc.HiveDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:hive2://hive-server-host:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2</td>
  </tr>
  <tr>
    <td>default.proxy.user.property</td>
    <td>default.server2.proxy.user</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.auth.type</td>
    <td>SIMPLE</td>
  </tr>
</table>

See [Hive Interpreter](../interpreter/hive.html) for more properties about Hive interpreter.

### Presto/Trino

Properties

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>io.prestosql.jdbc.PrestoDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:presto://presto-server:9090/hive</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>presto_user</td>
  </tr>
</table>

[Trino JDBC Driver Docs](https://trino.io/docs/current/installation/jdbc.html) <br/>
[Presto JDBC Driver Docs](https://prestodb.io/docs/current/installation/jdbc.html)

Dependencies

<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>io.prestosql:presto-jdbc:350</td>
    <td></td>
  </tr>
</table>

### Impala

Properties

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.hive.jdbc.HiveDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:hive2://emr-header-1.cluster-47080:21050/;auth=noSasl</td>
  </tr>
</table>

Dependencies

<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.apache.hive:hive-jdbc:2.3.4</td>
    <td></td>
  </tr>
</table>

[Impala JDBC Driver Docs](https://impala.apache.org/docs/build/html/topics/impala_jdbc.html)

Dependencies

<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>io.prestosql:presto-jdbc:350</td>
    <td></td>
  </tr>
</table>

### Apache Kyuubi (Incubating)

Zeppelin connect to `Kyuubi` to run sql via `KyuubiHiveDriver`. There are 2 cases of connecting with Kyuubi:

* Connect to Kyuubi without KERBEROS
* Connect to Kyuubi with KERBEROS

Each case requires different settings.

##### Connect to Kyuubi without KERBEROS

In this scenario, you need to make the following settings at least. Kyuubi engine run as user of `default.user`.

Properties

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.kyuubi.jdbc.KyuubiHiveDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:hive2://kyuubi-server:10009</td>
  </tr>
</table>

Dependencies

<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.apache.kyuubi:kyuubi-hive-jdbc-shaded:1.5.2-incubating</td>
    <td></td>
  </tr>
  <tr>
    <td>org.apache.hive:hive-jdbc:3.1.2</td>
    <td></td>
  </tr>
</table>


##### Connect to Kyuubi with KERBEROS

In this scenario, you need to make the following settings at least. Kyuubi engine run as user of client principal (`zeppelin.jdbc.principal`).

Properties

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.kyuubi.jdbc.KyuubiHiveDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:hive2://kyuubi-server:10009/default;principal={kyuubi_principal}</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.auth.type</td>
    <td>KERBEROS</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.keytab.location</td>
    <td>keytab of client</td>
  </tr>
  <tr>
    <td>zeppelin.jdbc.principal</td>
    <td>principal of client</td>
  </tr>
</table>

Dependencies

<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.apache.kyuubi:kyuubi-hive-jdbc-shaded:1.5.2-incubating</td>
    <td></td>
  </tr>
  <tr>
    <td>org.apache.hive:hive-jdbc:3.1.2</td>
    <td></td>
  </tr>
</table>

### Apache Phoenix

Phoenix supports `thick` and `thin` connection types:

  - [Thick client](#thick-client-connection) is faster, but must connect directly to ZooKeeper and HBase RegionServers.
  - [Thin client](#thin-client-connection) has fewer dependencies and connects through a [Phoenix Query Server](http://phoenix.apache.org/server.html) instance.

Use the appropriate `default.driver`, `default.url`, and the dependency artifact for your connection type.

#### Thick client connection

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/phoenix_thick_setting.png" width="600px" />

##### Properties
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.phoenix.jdbc.PhoenixDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:phoenix:localhost:2181:/hbase-unsecure</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>phoenix_user</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>phoenix_password</td>
  </tr>
</table>

##### Dependencies
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.apache.phoenix:phoenix-core:4.4.0-HBase-1.0</td>
    <td></td>
  </tr>
</table>

[Maven Repository: org.apache.phoenix:phoenix-core](https://mvnrepository.com/artifact/org.apache.phoenix/phoenix-core)

#### Thin client connection

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/phoenix_thin_setting.png" width="600px" />

##### Properties
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.phoenix.queryserver.client.Driver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:phoenix:thin:url=http://localhost:8765;serialization=PROTOBUF</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>phoenix_user</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>phoenix_password</td>
  </tr>
</table>

##### Dependencies
 
Before Adding one of the below dependencies, check the Phoenix version first.
 
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>org.apache.phoenix:phoenix-server-client:4.7.0-HBase-1.1</td>
    <td></td>
    <td>For Phoenix <code>4.7</code></td>
  </tr>
  <tr>
    <td>org.apache.phoenix:phoenix-queryserver-client:4.8.0-HBase-1.2</td>
    <td></td>
    <td>For Phoenix <code>4.8+</code></td>
  </tr>
</table>

[Maven Repository: org.apache.phoenix:phoenix-queryserver-client](https://mvnrepository.com/artifact/org.apache.phoenix/phoenix-queryserver-client)

### Apache Tajo

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/tajo_setting.png" width="600px" />

##### Properties
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.tajo.jdbc.TajoDriver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:tajo://localhost:26002/default</td>
  </tr>
</table>

[Apache Tajo JDBC Driver Docs](https://tajo.apache.org/docs/current/jdbc_driver.html)

##### Dependencies
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Excludes</th>
  </tr>
  <tr>
    <td>org.apache.tajo:tajo-jdbc:0.11.0</td>
    <td></td>
  </tr>
</table>

[Maven Repository: org.apache.tajo:tajo-jdbc](https://mvnrepository.com/artifact/org.apache.tajo/tajo-jdbc)

## Object Interpolation
The JDBC interpreter also supports interpolation of `ZeppelinContext` objects into the paragraph text.
The following example shows one use of this facility:

#### In Scala cell:

```scala
z.put("country_code", "KR")
    // ...
```

#### In later JDBC cell:

```sql
%jdbc_interpreter_name

select * from patents_list where 
priority_country = '{country_code}' and filing_date like '2015-%'
```

Object interpolation is disabled by default, and can be enabled for all instances of the JDBC interpreter by 
setting the value of the property `zeppelin.jdbc.interpolation` to `true` (see _More Properties_ above). 
More details of this feature can be found in the Spark interpreter documentation under 
[Zeppelin-Context](../usage/other_features/zeppelin_context.html)

## Bug reporting

If you find a bug using JDBC interpreter, please create a [JIRA](https://issues.apache.org/jira/browse/ZEPPELIN) ticket.
