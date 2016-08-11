---
layout: page
title: "BigQuery Interpreter for Apache Zeppelin"
description: "BigQuery is a highly scalable no-ops data warehouse in the Google Cloud Platform."
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

# BigQuery Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[BigQuery](https://cloud.google.com/bigquery/what-is-bigquery) is a highly scalable no-ops data warehouse in the Google Cloud Platform. Querying massive datasets can be time consuming and expensive without the right hardware and infrastructure. Google BigQuery solves this problem by enabling super-fast SQL queries against append-only tables using the processing power of Google's infrastructure. Simply move your data into BigQuery and let us handle the hard work. You can control access to both the project and your data based on your business needs, such as giving others the ability to view or query your data.  

## Configuration
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>zeppelin.bigquery.project_id</td>
    <td>  </td>
    <td>Google Project Id</td>
  </tr>
  <tr>
    <td>zeppelin.bigquery.wait_time</td>
    <td>5000</td>
    <td>Query Timeout in Milliseconds</td>
  </tr>
  <tr>
    <td>zeppelin.bigquery.max_no_of_rows</td>
    <td>100000</td>
    <td>Max result set size</td>
  </tr>
</table>


## BigQuery API
Zeppelin is built against BigQuery API version v2-rev265-1.21.0 - [API Javadocs](https://developers.google.com/resources/api-libraries/documentation/bigquery/v2/java/latest/)

## Enabling the BigQuery Interpreter

In a notebook, to enable the **BigQuery** interpreter, click the **Gear** icon and select **bigquery**.

### Setup service account credentials

In order to run BigQuery interpreter outside of Google Cloud Engine you need to provide authentication credentials,
by [following this instructions](https://developers.google.com/identity/protocols/application-default-credentials):

 - Go to the [API Console Credentials page](https://console.developers.google.com/project/_/apis/credentials)
 - From the project drop-down, select your project.
 - On the `Credentials` page, select the `Create credentials` drop-down, then select `Service account key`.
 - From the Service account drop-down, select an existing service account or create a new one.
 - For `Key type`, select the `JSON` key option, then select `Create`. The file automatically downloads to your computer.
 - Put the `*.json` file you just downloaded in a directory of your choosing. This directory must be private (you can't let anyone get access to this), but accessible to your Zeppelin instance.
 - Set the environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the path of the JSON file downloaded.
    * either though GUI: in interpreter configuration page property names in CAPITAL_CASE set up env vars
    * or though `zeppelin-env.sh`: just add it to the end of the file.

## Using the BigQuery Interpreter

In a paragraph, use `%bigquery.sql` to select the **BigQuery** interpreter and then input SQL statements against your datasets stored in BigQuery.
You can use [BigQuery SQL Reference](https://cloud.google.com/bigquery/query-reference) to build your own SQL.

For Example, SQL to query for top 10 departure delays across airports using the flights public dataset

```bash
%bigquery.sql
SELECT departure_airport,count(case when departure_delay>0 then 1 else 0 end) as no_of_delays 
FROM [bigquery-samples:airline_ontime_data.flights] 
group by departure_airport 
order by 2 desc 
limit 10
```

Another Example, SQL to query for most commonly used java packages from the github data hosted in BigQuery 

```bash
%bigquery.sql
SELECT
  package,
  COUNT(*) count
FROM (
  SELECT
    REGEXP_EXTRACT(line, r' ([a-z0-9\._]*)\.') package,
    id
  FROM (
    SELECT
      SPLIT(content, '\n') line,
      id
    FROM
      [bigquery-public-data:github_repos.sample_contents]
    WHERE
      content CONTAINS 'import'
      AND sample_path LIKE '%.java'
    HAVING
      LEFT(line, 6)='import' )
  GROUP BY
    package,
    id )
GROUP BY
  1
ORDER BY
  count DESC
LIMIT
  40
```

## Technical description

For in-depth technical details on current implementation please refer to [bigquery/README.md](https://github.com/apache/zeppelin/blob/master/bigquery/README.md).
