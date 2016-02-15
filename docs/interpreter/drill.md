---
layout: page
title: "Apache Drill Interpreter"
description: "How to use Apache Drill with Zeppelin"
group: manual
---
{% include JB/setup %}

## Drill Interpreter for Apache Zeppelin
The [Apache Drill](http://drill.apache.org/) ™ supports a variety of NoSQL databases and file systems, including HBase, MongoDB, MapR-DB, HDFS, MapR-FS, Amazon S3, Azure Blob Storage, Google Cloud Storage, Swift, NAS and local files. A single query can join data from multiple datastores. For example, you can join a user profile collection in MongoDB with a directory of event logs in Hadoop.

Drill's datastore-aware optimizer automatically restructures a query plan to leverage the datastore's internal processing capabilities. In addition, Drill supports data locality, so it's a good idea to co-locate Drill and the datastore on the same nodes.

### Configuration

* Copy drill-jdbc-all-<VERSION>.jar to ZEPPELIN_HOME/interpreter/jdbc/
* Restart Zeppelin, if required.
* Modify the JDBC Interpreter.
You can use the JDBC Interpreter to add configurations specific to Drill.
You need to configure the driver & the JDBC URL.

<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>drill.driver</td>
    <td>org.apache.drill.jdbc.Driver</td>
    <td>JDBC Driver Class</td>
  </tr>
  <tr>
    <td>drill.url</td>
    <td>jdbc:drill:schema=dfs;zk=localhost:2181/drill/drillbits1</td>
    <td>This is a sample URL. Details on Drill JDBC URL @ https://drill.apache.org/docs/using-the-jdbc-driver</td>
  </tr>
</table>

## How to use
Basically, you can use

```%jdbc(drill)
select count(*) from retail_demo.`order_lineitems_pxf`;
```

### Apply Zeppelin Dynamic Forms
You can leverage [Zeppelin Dynamic Form]({{BASE_PATH}}/manual/dynamicform.html) inside your queries. You can use both the `text input` and `select form` parameterization features.

```sql
%hive
SELECT ${group_by}, count(*) as count
FROM retail_demo.order_lineitems_pxf
GROUP BY ${group_by=product_id,product_id|product_name|customer_id|store_id}
ORDER BY count ${order=DESC,DESC|ASC}
LIMIT ${limit=10};
```
