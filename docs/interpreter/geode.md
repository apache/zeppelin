---
layout: page
title: "Geode OQL Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## Geode/Gemfire OQL Interpreter for Apache Zeppelin

<br/>
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%geode.oql</td>
    <td>GeodeOqlInterpreter</td>
    <td>Provides OQL environment for Apache Geode</td>
  </tr>
</table>

<br/>
This interpreter supports the [Geode](http://geode.incubator.apache.org/) [Object Query Language (OQL)](http://geode-docs.cfapps.io/docs/developing/querying_basics/oql_compared_to_sql.html).  With the OQL-based querying language:

[<img align="right" src="http://img.youtube.com/vi/zvzzA9GXu3Q/3.jpg" alt="zeppelin-view" hspace="10" width="200"></img>](https://www.youtube.com/watch?v=zvzzA9GXu3Q)

* You can query on any arbitrary object
* You can navigate object collections
* You can invoke methods and access the behavior of objects
* Data mapping is supported
* You are not required to declare types. Since you do not need type definitions, you can work across multiple languages
* You are not constrained by a schema

This [Video Tutorial](https://www.youtube.com/watch?v=zvzzA9GXu3Q) illustrates some of the features provided by the `Geode Interpreter`.

### Create Interpreter

By default Zeppelin creates one `Geode/OQL` instance. You can remove it or create more instances.

Multiple Geode instances can be created, each configured to the same or different backend Geode cluster. But over time a  `Notebook` can have only one Geode interpreter instance `bound`. That means you _cannot_ connect to different Geode clusters in the same `Notebook`. This is a known Zeppelin limitation.

To create new Geode instance open the `Interpreter` section and click the `+Create` button. Pick a `Name` of your choice and from the `Interpreter` drop-down select `geode`.  Then follow the configuration instructions and `Save` the new instance.

> Note: The `Name` of the instance is used only to distinguish the instances while binding them to the `Notebook`. The `Name` is irrelevant inside the `Notebook`. In the `Notebook` you must use `%geode.oql` tag.

### Bind to Notebook
In the `Notebook` click on the `settings` icon in the top right corner. The select/deselect the interpreters to be bound with the `Notebook`.

### Configuration
You can modify the configuration of the Geode from the `Interpreter` section.  The Geode interpreter expresses the following properties:


 <table class="table-configuration">
   <tr>
     <th>Property Name</th>
     <th>Description</th>
     <th>Default Value</th>
   </tr>
   <tr>
     <td>geode.locator.host</td>
     <td>The Geode Locator Host</td>
     <td>localhost</td>
   </tr>
   <tr>
     <td>geode.locator.port</td>
     <td>The Geode Locator Port</td>
     <td>10334</td>
   </tr>
   <tr>
     <td>geode.max.result</td>
     <td>Max number of OQL result to display to prevent the browser overload</td>
     <td>1000</td>
   </tr>
 </table>

### How to use

> *Tip 1: Use (CTRL + .) for OQL auto-completion.*

> *Tip 2: Always start the paragraphs with the full `%geode.oql` prefix tag! The short notation: `%geode` would still be able run the OQL queries but the syntax highlighting and the auto-completions will be disabled.*

#### Create / Destroy Regions

The OQL specification does not support  [Geode Regions](https://cwiki.apache.org/confluence/display/GEODE/Index#Index-MainConceptsandComponents) mutation operations. To `create`/`destroy` regions one should use the [GFSH](http://geode-docs.cfapps.io/docs/tools_modules/gfsh/chapter_overview.html) shell tool instead. In the following it is assumed that the GFSH is colocated with Zeppelin server.

```bash
%sh
source /etc/geode/conf/geode-env.sh
gfsh << EOF

 connect --locator=ambari.localdomain[10334]

 destroy region --name=/regionEmployee
 destroy region --name=/regionCompany
 create region --name=regionEmployee --type=REPLICATE
 create region --name=regionCompany --type=REPLICATE

 exit;
EOF
```

Above snippet re-creates two regions: `regionEmployee` and `regionCompany`. Note that you have to explicitly specify the locator host and port. The values should match those you have used in the Geode Interpreter configuration. Comprehensive list of [GFSH Commands by Functional Area](http://geode-docs.cfapps.io/docs/tools_modules/gfsh/gfsh_quick_reference.html).

#### Basic OQL  


```sql
%geode.oql
SELECT count(*) FROM /regionEmployee
```

OQL `IN` and `SET` filters:

```sql
%geode.oql
SELECT * FROM /regionEmployee
WHERE companyId IN SET(2) OR lastName IN SET('Tzolov13', 'Tzolov73')
```

OQL `JOIN` operations

```sql
%geode.oql
SELECT e.employeeId, e.firstName, e.lastName, c.id as companyId, c.companyName, c.address
FROM /regionEmployee e, /regionCompany c
WHERE e.companyId = c.id
```

By default the QOL responses contain only the region entry values. To access the keys, query the `EntrySet` instead:

```sql
%geode.oql
SELECT e.key, e.value.companyId, e.value.email
FROM /regionEmployee.entrySet e
```
Following query will return the EntrySet value as a Blob:

```sql
%geode.oql
SELECT e.key, e.value FROM /regionEmployee.entrySet e
```


> Note: You can have multiple queries in the same paragraph but only the result from the first is displayed. [[1](https://issues.apache.org/jira/browse/ZEPPELIN-178)], [[2](https://issues.apache.org/jira/browse/ZEPPELIN-212)].


#### GFSH Commands From The Shell

Use the Shell Interpreter (`%sh`) to run OQL commands form the command line:

```bash
%sh
source /etc/geode/conf/geode-env.sh
gfsh -e "connect" -e "list members"
```

#### Apply Zeppelin Dynamic Forms

You can leverage [Zeppelin Dynamic Form](../manual/dynamicform.html) inside your OQL queries. You can use both the `text input` and `select form` parameterization features

```sql
%geode.oql
SELECT * FROM /regionEmployee e WHERE e.employeeId > ${Id}
```

#### Geode REST API
To list the defined regions you can use the [Geode REST API](http://geode-docs.cfapps.io/docs/geode_rest/chapter_overview.html):

```
http://<geode server hostname>phd1.localdomain:8484/gemfire-api/v1/
```

```json
{
  "regions" : [{
    "name" : "regionEmployee",
    "type" : "REPLICATE",
    "key-constraint" : null,
    "value-constraint" : null
  }, {
    "name" : "regionCompany",
    "type" : "REPLICATE",
    "key-constraint" : null,
    "value-constraint" : null
  }]
}
```

> To enable Geode REST API with JSON support add the following properties to geode.server.properties.file and restart:

```
http-service-port=8484
start-dev-rest-api=true
```

### Auto-completion
The Geode Interpreter provides a basic auto-completion functionality. On `(Ctrl+.)` it list the most relevant suggestions in a pop-up window.
