# Overview
BigQuery interpreter for Apache Zeppelin using the modern [google-cloud-bigquery](https://github.com/googleapis/java-bigquery) library.

# Authentication
The interpreter supports multiple ways to authenticate with Google Cloud:

1. **Application Default Credentials (ADC)**:
   This is the recommended way. If Zeppelin is running on GCE, GKE, or any environment where `gcloud auth application-default login` has been executed, the interpreter will automatically discover the credentials.

2. **Service Account JSON Key (Manual Fallback)**:
   If ADC is not available, the interpreter will prompt you to paste your Service Account JSON key into a **masked password field** rendered in the notebook paragraph.
   - To get a JSON key:
     1. Go to the [GCP Console Service Accounts page](https://console.cloud.google.com/iam-admin/serviceaccounts).
     2. Select your project and service account.
     3. Click **Keys** -> **Add Key** -> **Create new key**.
     4. Select **JSON** and click **Create**.
     5. Copy the entire content of the downloaded JSON file and paste it into the Zeppelin masked password field when prompted. The input is masked so it is not displayed in plaintext, but you should still treat this JSON key as a secret.
   - **Security caution:** Do not paste this key into shared notes, notebooks, version control, or any place where it might be stored or visible to others. Prefer using Application Default Credentials (ADC) or Zeppelin's secure credentials mechanisms where possible, and only use this manual JSON key approach as a fallback when more secure options are not available.

# Configuration
| Property | Default | Description |
| --- | --- | --- |
| `zeppelin.bigquery.project_id` | | GCP Project ID |
| `zeppelin.bigquery.wait_time` | 5000 | Query Timeout in ms |
| `zeppelin.bigquery.max_no_of_rows` | 100000 | Max Result size |
| `zeppelin.bigquery.sql_dialect` | | SQL Dialect (standardsql or legacysql) |
| `zeppelin.bigquery.region` | | GCP Region |

# Unit Tests
BigQuery unit tests are integration tests that require access to a real GCP project.
By default, they are excluded. To run them:
1. Setup ADC locally (`gcloud auth application-default login`).
2. Create `src/test/resources/constants.json` with your project and test queries.
3. Run: `./mvnw test -pl bigquery -am -Dbigquery.test.exclude=""`

# Sample Screenshot

![Zeppelin BigQuery](https://cloud.githubusercontent.com/assets/10060731/16938817/b9213ea0-4db6-11e6-8c3b-8149a0bdf874.png)
