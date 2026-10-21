<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Python CI environments

The Linux backend and Selenium CI environments are defined in the root `pixi.toml` and resolved in `pixi.lock`. Change the manifest, regenerate the lock, and include both files in the same PR. Normal CI installs the committed lock without refreshing dependencies.

## Before you start

Install [Pixi](https://pixi.sh/latest/installation/) v0.76.2, the version used by the CI setup action. Run the commands below from the repository root.

The committed environments target Linux x86-64. Installing them and running their tests requires that platform. Lock generation can work from another host, but PyPI source distributions, including dependencies of PyFlink, may require Linux to generate package metadata. If you use macOS or Windows, see [Working from another platform](#working-from-another-platform).

## Change a dependency

Edit the relevant feature in `pixi.toml`, then regenerate the lock:

```bash
pixi lock
```

On Linux x86-64, install the affected environment from the new lock. For example:

```bash
pixi install --locked -e py39
```

Choose the environment for the job you are changing:

| Environment | Test coverage |
|---|---|
| `py39` | Core modules, Python/Jupyter, interpreter integration, Spark, Livy, and Selenium/Spark integration |
| `py39-tensorflow` | Non-core interpreters |
| `py39-flink119` | Flink 1.19 unit/integration tests with Java 11 |
| `py39-flink120` | Flink 1.20 unit/integration tests with Java 17 |

Run the affected tests: their Maven commands, Java versions, services and other prerequisites are defined in [backend-tests.yml](../.github/workflows/backend-tests.yml) and [selenium-integration.yml](../.github/workflows/selenium-integration.yml). For Python/Jupyter, build the modules and their dependencies before running their tests:

```bash
pixi run --locked -e py39 ./mvnw install -DskipTests \
  -pl python,zeppelin-jupyter-interpreter -am
pixi run --locked -e py39 ./mvnw test \
  -pl python,zeppelin-jupyter-interpreter -DfailIfNoTests=false
```

## Add a supported Python or backend version

1. Add a Python-version feature and the new backend's dependency constraints in `pixi.toml`. Reuse shared features where their requirements apply. `notebook-legacy` contains bounds for the existing stack; do not automatically apply those bounds to a newer stack.
2. Compose a named environment from the required features. Keep existing environments while their backend combinations remain supported.
3. Select the environment in the appropriate reusable workflow's test matrix, and update the backend's Maven configuration if needed.
4. Run `pixi lock` and validate the new combination. Include the manifest, lock and test-matrix changes in the same PR. Locked installation rejects a manifest change that has not been reflected in the lock.

To update packages without changing the supported versions or other manifest constraints, use `pixi update`. You can limit a local refresh to one environment:

```bash
pixi update -e py39-flink120
```

## Working from another platform

You do not need to export package lists manually or prepare a local Linux environment just to request a lock update:

1. Open **Actions → Python dependency refresh → Run workflow** in your fork and select your development branch.
2. Wait for candidate generation, backend tests and Selenium integration to finish successfully.
3. Download the `python-lock-candidate` artifact, copy its `pixi.lock` into your checkout, and include that lock in your PR.

Manual runs on forks and feature branches generate and validate candidates but do not publish automated PRs. A lock from a failed run is useful for diagnosis, not a validated update.

If you prefer local generation, use an Ubuntu x86-64 container to generate the lock and check installation. On an Apple Silicon host, select amd64 emulation with `--platform linux/amd64`:

```bash
docker run --rm --platform linux/amd64 -v "$PWD:/work" -w /work ubuntu:24.04 \
  bash -euo pipefail -c '
    apt-get update
    apt-get install -y --no-install-recommends ca-certificates curl
    curl -fsSL https://github.com/prefix-dev/pixi/releases/download/v0.76.2/pixi-x86_64-unknown-linux-musl.tar.gz |
      tar -xz -C /usr/local/bin pixi
    pixi lock
    pixi install --locked -e py39
  '
```

This example checks resolution and installation of `py39`, not full backend compatibility. Run the affected backend tests with their workflow's JDK and other prerequisites as well.

## Scheduled refreshes and review

[Python dependency refresh](../.github/workflows/python-dependencies.yml) runs every Sunday at **04:17 UTC** in `apache/zeppelin`. It resolves fresh dependencies with `pixi update`, then calls the same reusable tests as normal CI with one candidate lock and a fixed source revision. Candidate validation disables environment caching. Unchanged candidates are still tested.

After both validation workflows succeed, a changed lock is proposed on `automation/python-dependencies` using [create-pull-request](https://github.com/peter-evans/create-pull-request), following the [pandas precedent](https://github.com/pandas-dev/pandas/blob/main/.github/workflows/update-pixi-lock.yml). Only Apache `master` runs publish. New PRs are drafts, and there is no automatic merge.

Before merging, review the lock diff, the tested source revision, and the complete validation run linked in the PR. The run retains the candidate lock and JSON update report for 14 days. Repository policy must allow GitHub Actions to create PRs; only the publication job requests write permissions.

The automation owns its generated branch and PR content. Make dependency or compatibility fixes in the manifest rather than hand-editing that branch: a later successful refresh can replace its contents. Marking a PR ready does not stop later updates, and closing it does not permanently suppress future proposals.

## When a refresh fails

Check the failed jobs in Actions and their step logs to find the failing environment and stage. Resolution, installation or test failures block publication and leave the committed lock and any existing refresh PR unchanged.

For a compatibility failure, reproduce the failing job using the candidate lock. Fix the code or a justified dependency constraint in a PR, regenerate its lock, and rerun the affected tests. For a transient download or service failure, rerun the failed jobs or dispatch a new refresh.

For a publication failure, check the Actions permissions and API error, then rerun the failed job. The publishing action can reuse an existing generated branch, including one left behind when a previous PR-creation attempt failed.
