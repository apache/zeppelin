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
  <tr>
    <td>zeppelin.bigquery.sql_dialect</td>
    <td></td>
    <td>BigQuery SQL dialect (standardSQL or legacySQL). If empty, [query prefix](https://cloud.google.com/bigquery/docs/reference/standard-sql/enabling-standard-sql#sql-prefix) like '#standardSQL' can be used.</td>
  </tr>
</table>


## BigQuery API
Zeppelin is built against BigQuery API version v2-rev265-1.21.0 - [API Javadocs](https://developers.google.com/resources/api-libraries/documentation/bigquery/v2/java/latest/)

## Enabling the BigQuery Interpreter

In a notebook, to enable the **BigQuery** interpreter, click the **Gear** icon and select **bigquery**.

### Provide Application Default Credentials

Within Google Cloud Platform (e.g. Google App Engine, Google Compute Engine),
built-in credentials are used by default.

Outside of GCP, follow the Google API authentication instructions for [Zeppelin Google Cloud Storage](https://zeppelin.apache.org/docs/latest/setup/storage/storage.html#notebook-storage-in-google-cloud-storage)

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
