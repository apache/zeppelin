---
layout: page
title: "Generic JDBC Interpreter"
description: "JDBC user guide"
group: manual
---
{% include JB/setup %}


## Generic JDBC  Interpreter for Apache Zeppelin

This interpreter lets you create a JDBC connection to any data source, by now it has been tested with:

* Postgres
* MySql
* MariaDB
* Redshift
* Hive

If someone else used another database please report how it works to improve functionality.

### Create Interpreter

When create a interpreter by default use PostgreSQL with the next properties:

<table class="table-configuration">
  <tr>
    <th>name</th>
    <th>value</th>
  </tr>
  <tr>
    <td>common.max_count</td>
    <td>1000</td>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.postgresql.Driver</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>`********`</td>
  </tr>
  <tr>
    <td>default.driver.name</td>
    <td>jdbc:postgresql://localhost:5432/</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>gpadmin</td>
  </tr>      
</table>

Is not necessary add jar driver to the classpath for PostgreSQL.

#### Simple connection

Before creating the interpreter it is necessary to add to the Zeppelin classpath the path of the JDBC you want to use, to do it you must edit the file `zeppelin-daemon.sh` as shown in the table:

```
# Add jdbc connector jar
ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/jdbc/jars/mysql-connector-java-5.1.6.jar"
```

For create the interpreter you need to specify connection parameters as shown in the table.

<table class="table-configuration">
  <tr>
    <th>name</th>
    <th>value</th>
  </tr>
  <tr>
    <td>common.max_count</td>
    <td>1000</td>
  </tr>
  <tr>
    <td>{prefix}.driver</td>
    <td>driver name</td>
  </tr>
  <tr>
    <td>{prefix}.password</td>
    <td>`********`</td>
  </tr>
  <tr>
    <td>{prefix}.driver.name</td>
    <td>jdbc url</td>
  </tr>
  <tr>
    <td>{prefix}.user</td>
    <td>user name</td>
  </tr>      
</table>

#### Multiple connections

This JDBC interpreter also allows connections to multiple data sources. For every connection is necessary a prefix for reference in the paragraph this way `%jdbc(prefix)`. Before creating the interpreter it is necessary to add to the Zeppelin classpath all paths to access to each driver's jar file you want to use, to do it you must edit the file `zeppelin-daemon.sh` as following:

```
# Add jdbc connector jar
ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/jdbc/jars/RedshiftJDBC41-1.1.10.1010.jar"
ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/jdbc/jars/mysql-connector-java-5.1.6.jar"
```
You can add all the jars you need to make multiple connections into the same interpreter. To create the interpreter you must specify the parameters, for example  we will create two connections to PostgreSQL and Redshift, the respective prefixes are `default` and `redshift`:

<table class="table-configuration">
  <tr>
    <th>name</th>
    <th>value</th>
  </tr>
  <tr>
    <td>common.max_count</td>
    <td>1000</td>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.postgresql.Driver</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td>`********`</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:postgresql://localhost:5432/</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td>gpadmin</td>
  </tr>
  <tr>
    <td>redshift.driver</td>
    <td>com.amazon.redshift.jdbc4.Driver</td>
  </tr>
  <tr>
    <td>redshift.password</td>
    <td>`********`</td>
  </tr>
  <tr>
    <td>redshift.url</td>
    <td>jdbc:redshift://examplecluster.abc123xyz789.us-west-2.redshift.amazonaws.com:5439</td>
  </tr>
  <tr>
    <td>redshift.user</td>
    <td>redshift-user</td>
  </tr>      
</table>


### Bind to Notebook
In the `Notebook` click on the `settings` icon at the top-right corner. Use select/deselect to specify the interpreters to be used in the `Notebook`.

### More Properties
You can modify the interpreter configuration in the `Interpreter` section. The most common properties are as follows, but you can specify other properties that need to be connected.

 <table class="table-configuration">
   <tr>
     <th>Property Name</th>
     <th>Description</th>
   </tr>
   <tr>
     <td>{prefix}.url</td>
     <td>JDBC URL to connect, the URL must include the name of the database </td>
   </tr>
   <tr>
     <td>{prefix}.user</td>
     <td>JDBC user name</td>
   </tr>
   <tr>
     <td>{prefix}.password</td>
     <td>JDBC password</td>
   </tr>
   <tr>
     <td>{prefix}.driver.name</td>
     <td>JDBC driver name.</td>
   </tr>
   <tr>
     <td>common.max_result</td>
     <td>Max number of SQL result to display to prevent the browser overload. This is  common properties for all connections</td>
   </tr>      
 </table>

To develop this functionality use this [method](http://docs.oracle.com/javase/7/docs/api/java/sql/DriverManager.html#getConnection(java.lang.String,%20java.util.Properties). For example if a connection needs a schema parameter, it would have to add the property as follows:

<table class="table-configuration">
  <tr>
    <th>name</th>
    <th>value</th>
  </tr>
  <tr>
    <td>{prefix}.schema</td>
    <td>schema_name</td>
  </tr>
</table>

### How to use

#### Reference in paragraph

Start the paragraphs with the `%jdbc`, this will use the `default` prefix for connection. If you want to use other connection you should specify the prefix of it as follows `%jdbc(prefix)`:

```sql
%jdbc
SELECT * FROM db_name;

```
or
```sql
%jdbc(prefix)
SELECT * FROM db_name;

```

#### Apply Zeppelin Dynamic Forms

You can leverage [Zeppelin Dynamic Form](../manual/dynamicform.html) inside your queries. You can use both the `text input` and `select form` parametrization features

```sql
%jdbc(prefix)
SELECT name, country, performer
FROM demo.performers
WHERE name='{{performer=Sheryl Crow|Doof|Fanfarlo|Los Paranoia}}'
```

### Bugs & Contacts
If you find a bug for this interpreter, please create a [JIRA]( https://issues.apache.org/jira/browse/ZEPPELIN-382?jql=project%20%3D%20ZEPPELIN) ticket.
