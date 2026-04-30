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
[BigQuery](https://cloud.google.com/bigquery/what-is-bigquery) is a highly scalable no-ops data warehouse in the Google Cloud Platform. This interpreter uses the modern [google-cloud-bigquery](https://github.com/googleapis/java-bigquery) Cloud Client Library to provide high-performance data analytics.

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
  <tr>
    <td>zeppelin.bigquery.region</td>
    <td></td>
    <td>BigQuery dataset region (Needed for single region dataset)</td>
  </tr>
</table>

## Authentication

The BigQuery interpreter supports two primary ways to authenticate:

### 1. Application Default Credentials (ADC)

This is the recommended approach for server environments.
- **Within GCP**: If Zeppelin is running on Google Compute Engine (GCE) or Google Kubernetes Engine (GKE), it will automatically use the attached service account.
- **Outside GCP**: Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable to the path of your service account JSON key file, or run `gcloud auth application-default login` on the server.

### 2. Service Account JSON Key (GUI Fallback)

If no environment-level credentials are found, the interpreter will prompt you to paste your **Service Account JSON key** directly in the notebook paragraph using a **masked password form**, so the key is not displayed in plaintext.

**How to get a Service Account JSON key:**
1. Go to the [IAM & Admin > Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts) page in the GCP Console.
2. Select or create a service account with `BigQuery User` and `BigQuery Data Viewer` roles.
3. Click the **Keys** tab, then **Add Key > Create new key**.
4. Choose **JSON** format and click **Create**.
5. When the BigQuery interpreter prompts you in Zeppelin, copy and paste the entire content of this JSON file into the masked password field. The input is masked so it is not displayed in plaintext, but you should still treat this JSON key as a secret.

**Security caution:** Do not paste this key into shared notes, notebooks, version control, or any place where it might be stored or visible to others. Prefer using Application Default Credentials (ADC) or Zeppelin's secure credentials mechanisms where possible, and only use this manual JSON key approach as a fallback when more secure options are not available.

## Using the BigQuery Interpreter

In a paragraph, use `%bigquery.sql` to select the **BigQuery** interpreter.

Example: Query top 10 departure delays using the flights public dataset (Standard SQL)

```sql
%bigquery.sql
SELECT departure_airport, count(case when departure_delay>0 then 1 else 0 end) as no_of_delays 
FROM `bigquery-samples.airline_ontime_data.flights`
GROUP BY departure_airport 
ORDER BY 2 DESC 
LIMIT 10
```

## Technical description

For more implementation details, please refer to the [BigQuery module README](https://github.com/apache/zeppelin/blob/master/bigquery/README.md).
