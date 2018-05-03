# Overview
BigQuery interpreter for Apache Zeppelin

# Unit Tests
BigQuery Unit tests are excluded as these tests depend on the BigQuery external service. This is because BigQuery does not have a local mock at this point.

If you like to run these tests manually, please follow the following steps:
* [Create a new project](https://support.google.com/cloud/answer/6251787?hl=en)
* [Create a Google Compute Engine instance](https://cloud.google.com/compute/docs/instances/create-start-instance)
* Copy the project ID that you created and add it to the property "projectId" in `resources/constants.json`
* Run the command mvn <options> -Dbigquery.text.exclude='' test -pl bigquery -am

# Connection
The Interpreter opens a connection with the BigQuery Service using the supplied Google project ID and the compute environment variables.

# Google BigQuery API Javadoc
[API Javadocs](https://developers.google.com/resources/api-libraries/documentation/bigquery/v2/java/latest/)
[Source] (http://central.maven.org/maven2/com/google/apis/google-api-services-bigquery/v2-rev265-1.21.0/google-api-services-bigquery-v2-rev265-1.21.0-sources.jar)

We have used the curated veneer version of the Java APIs versus [Idiomatic Java client] (https://github.com/GoogleCloudPlatform/gcloud-java/tree/master/gcloud-java-bigquery) to build the interpreter. This is mainly for usability reasons.

# Sample Screenshot

![Zeppelin BigQuery](https://cloud.githubusercontent.com/assets/10060731/16938817/b9213ea0-4db6-11e6-8c3b-8149a0bdf874.png)
