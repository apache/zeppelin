---

layout: page

title: "SAP BusinessObjects Interpreter for Apache Zeppelin"

description: "SAP BusinessObjects BI platform can simplify the lives of business users and IT staff. SAP BusinessObjects is based on universes. The universe contains dual-semantic layer model. The users make queries upon universes. This interpreter is new interface for universes."

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

# SAP BusinessObjects (Universe) Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview

[SAP BusinessObjects BI platform (universes)](https://help.sap.com/viewer/p/SAP_BUSINESSOBJECTS_BUSINESS_INTELLIGENCE_PLATFORM) can simplify the lives of business users and IT staff. SAP BusinessObjects is based on universes. The universe contains dual-semantic layer model. The users make queries upon universes. This interpreter is new interface for universes.

*Disclaimer* SAP interpreter is not official interpreter for SAP BusinessObjects BI platform. It uses [BI Semantic Layer REST API](https://help.sap.com/viewer/5431204882b44fc98d56bd752e69f132/4.2.5/en-US/ec54808e6fdb101497906a7cb0e91070.html)

This interpreter is not directly supported by SAP AG.

Tested with versions 4.2SP3 (14.2.3.2220) and 4.2SP5. There is no support for filters in UNX-universes converted from old UNV format.

The universe name must be unique.

## Configuring SAP Universe Interpreter

At the "Interpreters" menu, you can edit SAP interpreter or create new one. Zeppelin provides these properties for SAP.

<table class="table-configuration">
  <tr>
    <th>Property Name</th>
    <th>Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>universe.api.url</td>
    <td>http://localhost:6405/biprws</td>
    <td>The base url for the SAP BusinessObjects BI platform. You have to edit "localhost" that you may use (ex. http://0.0.0.0:6405/biprws)</td>
  </tr>
  <tr>
    <td>universe.authType</td>
    <td>secEnterprise</td>
    <td>The type of authentication for API of Universe. Available values: secEnterprise, secLDAP, secWinAD, secSAPR3</td>
  </tr>
  <tr>
    <td>universe.password</td>
    <td></td>
    <td>The BI platform user password</td>
  </tr>
  <tr>
    <td>universe.user</td>
    <td>Administrator</td>
    <td>The BI platform user login</td>
  </tr>
</table>

![SAP Interpreter Setting]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/sap-interpreter-setting.png)

### How to use

<li> Choose the universe
<li> Choose dimensions and measures in `select` statement
<li> Define conditions in `where` statement
You can compare two dimensions/measures or use Filter (without value). 
Dimesions/Measures can be compared with static values, may be `is null` or `is not null`, contains or not in list.
Available the nested conditions (using braces "()"). "and" operator have more priority than "or". 


If generated query contains promtps, then promtps will appear as dynamic form after paragraph submitting.

Example query

```sql
%sap

universe [Universe Name];

select

  [Folder1].[Dimension2],

  [Folder2].[Dimension3],

  [Measure1]

where

  [Filter1]

  and [Date] > '2018-01-01 00:00:00'

  and [Folder1].[Dimension4] is not null

  and [Folder1].[Dimension5] in ('Value1', 'Value2');
```

### `distinct` keyword
You can write keyword `distinct` after keyword `select` to return only distinct (different) values.

Example query
```sql
%sap
universe [Universe Name];

select distinct
  [Folder1].[Dimension2], [Measure1]
where
  [Filter1];
```

### `limit` keyword
You can write keyword `limit` and limit value in the end of query to limit the number of records returned based on a limit value.

Example query
```sql
%sap
universe [Universe Name];

select
  [Folder1].[Dimension2], [Measure1]
where
  [Filter1]
limit 100;
```

## Object Interpolation
The SAP interpreter also supports interpolation of `ZeppelinContext` objects into the paragraph text.
To enable this feature set `universe.interpolation` to `true`. The following example shows one use of this facility:

####In Scala cell:

```scala
z.put("curr_date", "2018-01-01 00:00:00")
```

####In later SAP cell:

```sql
where
   [Filter1]
   and [Date] > '{curr_date}'
```