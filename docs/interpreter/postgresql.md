---
layout: page
title: "PostgreSQL and HAWQ Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## PostgreSQL, HAWQ  Interpreter for Apache Zeppelin

<br/>
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%psql.sql</td>
    <td>PostgreSqlInterpreter</td>
    <td>Provides SQL environment for PostgreSQL, HAWQ and Greenplum</td>
  </tr>
</table>

<br/>
[<img align="right" src="http://img.youtube.com/vi/wqXXQhJ5Uk8/0.jpg" alt="zeppelin-view" hspace="10" width="250"></img>](https://www.youtube.com/watch?v=wqXXQhJ5Uk8)

This interpreter seamlessly supports the following SQL data processing engines:

* [PostgreSQL](http://www.postgresql.org/) - OSS, Object-relational database management system (ORDBMS)
* [Apache HAWQ](http://pivotal.io/big-data/pivotal-hawq) - Powerful [Open Source](https://wiki.apache.org/incubator/HAWQProposal) SQL-On-Hadoop engine.
* [Greenplum](http://pivotal.io/big-data/pivotal-greenplum-database) - MPP database built on open source PostgreSQL.


This [Video Tutorial](https://www.youtube.com/watch?v=wqXXQhJ5Uk8) illustrates some of the features provided by the `Postgresql Interpreter`.

### Create Interpreter

By default Zeppelin creates one `PSQL` instance. You can remove it or create new instances.

Multiple PSQL instances can be created, each configured to the same or different backend databases. But over time a  `Notebook` can have only one PSQL interpreter instance `bound`. That means you _cannot_ connect to different databases in the same `Notebook`. This is a known Zeppelin limitation.

To create new PSQL instance open the `Interpreter` section and click the `+Create` button. Pick a `Name` of your choice and from the `Interpreter` drop-down select `psql`.  Then follow the configuration instructions and `Save` the new instance.

> Note: The `Name` of the instance is used only to distinct the instances while binding them to the `Notebook`. The `Name` is irrelevant inside the `Notebook`. In the `Notebook` you must use `%psql.sql` tag.

### Bind to Notebook
In the `Notebook` click on the `settings` icon in the top right corner. The select/deselect the interpreters to be bound with the `Notebook`.

### Configuration
You can modify the configuration of the PSQL from the `Interpreter` section.  The PSQL interpreter expenses the following properties:


 <table class="table-configuration">
   <tr>
     <th>Property Name</th>
     <th>Description</th>
     <th>Default Value</th>
   </tr>
   <tr>
     <td>postgresql.url</td>
     <td>JDBC URL to connect to </td>
     <td>jdbc:postgresql://localhost:5432</td>
   </tr>
   <tr>
     <td>postgresql.user</td>
     <td>JDBC user name</td>
     <td>gpadmin</td>
   </tr>
   <tr>
     <td>postgresql.password</td>
     <td>JDBC password</td>
     <td></td>
   </tr>
   <tr>
     <td>postgresql.driver.name</td>
     <td>JDBC driver name. In this version the driver name is fixed and should not be changed</td>
     <td>org.postgresql.Driver</td>
   </tr>
   <tr>
     <td>postgresql.max.result</td>
     <td>Max number of SQL result to display to prevent the browser overload</td>
     <td>1000</td>
   </tr>      
 </table>


### How to use
```
Tip: Use (CTRL + .) for SQL auto-completion.
```
#### DDL and SQL commands

Start the paragraphs with the full `%psql.sql` prefix tag! The short notation: `%psql` would still be able run the queries but the syntax highlighting and the auto-completions will be disabled.

You can use the standard CREATE / DROP / INSERT commands to create or modify the data model:

```sql
%psql.sql
drop table if exists mytable;
create table mytable (i int);
insert into mytable select generate_series(1, 100);
```

Then in a separate paragraph run the query.

```sql
%psql.sql
select * from mytable;
```

> Note: You can have multiple queries in the same paragraph but only the result from the first is displayed. [[1](https://issues.apache.org/jira/browse/ZEPPELIN-178)], [[2](https://issues.apache.org/jira/browse/ZEPPELIN-212)].

For example, this will execute both queries but only the count result will be displayed. If you revert the order of the queries the mytable content will be shown instead.

```sql
%psql.sql
select count(*) from mytable;
select * from mytable;
```

#### PSQL command line tools

Use the Shell Interpreter (`%sh`) to access the command line [PSQL](http://www.postgresql.org/docs/9.4/static/app-psql.html) interactively:

```bash
%sh
psql -h phd3.localdomain -U gpadmin -p 5432 <<EOF
 \dn  
 \q
EOF
```
This will produce output like this:

```
        Name        |  Owner  
--------------------+---------
 hawq_toolkit       | gpadmin
 information_schema | gpadmin
 madlib             | gpadmin
 pg_catalog         | gpadmin
 pg_toast           | gpadmin
 public             | gpadmin
 retail_demo        | gpadmin
```

#### Apply Zeppelin Dynamic Forms

You can leverage [Zeppelin Dynamic Form](../manual/dynamicform.html) inside your queries. You can use both the `text input` and `select form` parametrization features

```sql
%psql.sql
SELECT ${group_by}, count(*) as count
FROM retail_demo.order_lineitems_pxf
GROUP BY ${group_by=product_id,product_id|product_name|customer_id|store_id}
ORDER BY count ${order=DESC,DESC|ASC}
LIMIT ${limit=10};
```
#### Example HAWQ PXF/HDFS Tables

Create HAWQ external table that read data from tab-separated-value data in HDFS.

```sql
%psql.sql
CREATE EXTERNAL TABLE retail_demo.payment_methods_pxf (
  payment_method_id smallint,
  payment_method_code character varying(20)
) LOCATION ('pxf://${NAME_NODE_HOST}:50070/retail_demo/payment_methods.tsv.gz?profile=HdfsTextSimple') FORMAT 'TEXT' (DELIMITER = E'\t');
```
And retrieve content

```sql
%psql.sql
select * from retail_demo.payment_methods_pxf
```
### Auto-completion
The PSQL Interpreter provides a basic auto-completion functionality. On `(Ctrl+.)` it list the most relevant suggestions in a pop-up window. In addition to the SQL keyword the interpreter provides suggestions for the Schema, Table, Column names as well.
