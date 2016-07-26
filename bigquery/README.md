# Overview
BigQuery interpreter for Apache Zeppelin

# Pre requisities
You can follow the instructions at [Apache Zeppelin on Dataproc](https://github.com/GoogleCloudPlatform/dataproc-initialization-actions/blob/master/apache-zeppelin/README.MD) to bring up Zeppelin on Google dataproc. 
You could also install and bring up Zeppelin on Google compute Engine.

# Unit Tests
BigQuery Unit tests are excluded as these tests depend on the BigQuery external service. This is because BigQuery does not have a local mock at this point.

If you like to run these tests manually, please follow the following steps:
* [Create a new project](https://support.google.com/cloud/answer/6251787?hl=en)
* [Create a Google Compute Engine instance](https://cloud.google.com/compute/docs/instances/create-start-instance)
* Copy the project ID that you created and add it to the property "projectId" in `resources/constants.json`
* Run the command mvn <options> -Dbigquery.text.exclude='' test -pl bigquery -am


# Interpreter Configuration

Configure the following properties during Interpreter creation.

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

# Connection
The Interpreter opens a connection with the BigQuery Service using the supplied Google project ID and the compute environment variables.

# Google BigQuery API Javadoc
[API Javadocs](https://developers.google.com/resources/api-libraries/documentation/bigquery/v2/java/latest/)
[Source] (http://central.maven.org/maven2/com/google/apis/google-api-services-bigquery/v2-rev265-1.21.0/google-api-services-bigquery-v2-rev265-1.21.0-sources.jar)

We have used the curated veneer version of the Java APIs versus [Idiomatic Java client] (https://github.com/GoogleCloudPlatform/gcloud-java/tree/master/gcloud-java-bigquery) to build the interpreter. This is mainly for usability reasons.

# Enabling the BigQuery Interpreter

In a notebook, to enable the **BigQuery** interpreter, click the **Gear** icon and select **bigquery**.

# Using the BigQuery Interpreter

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

# Sample Screenshot

![Zeppelin BigQuery](https://cloud.githubusercontent.com/assets/10060731/16938817/b9213ea0-4db6-11e6-8c3b-8149a0bdf874.png)
