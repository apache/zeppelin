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

# Updating Python CI dependencies

Use this guide to change Python dependencies, generate Linux CI locks, and test the changes before submitting a PR.

Dependency definitions describe the packages and version constraints. A lock records the exact Conda and pip packages to install. Regular CI installs the committed locks. Editing a definition alone does not change the packages that regular CI tests.

## Find the dependency definition

| File | What to change |
|---|---|
| `testing/envs/common.yml` | Packages shared by the test environments |
| `testing/envs/python39.yml` | Python 3.9 and its dependency constraints |
| `testing/envs/profiles/<profile>.yml` | Packages and constraints specific to one test environment |

Each profile uses these three files and has a lock at `testing/locks/<profile>.conda-lock.yml`.

| Profile | Tests that use its lock |
|---|---|
| `py39` | Core modules, Python/Jupyter, interpreter integration, Spark, Livy, and Selenium/Spark integration |
| `py39-tensorflow` | Non-core interpreters |
| `py39-flink119` | Flink 1.19 unit/integration tests with Java 11 |
| `py39-flink120` | Flink 1.20 unit/integration tests with Java 17 |

## Update an existing dependency

Edit the relevant definition, then choose one of the following paths. Do not edit generated lock entries by hand.

### Generate and test with GitHub Actions

Use this path from any operating system. You do not need a local Conda or Docker installation.

The `Python dependency refresh` workflow must exist on your fork's default branch. If it is absent, use the local procedure below. The workflow refreshes existing locks. For a new profile, first follow [Add a supported version](#add-a-supported-version).

1. Push the changed definitions to a branch in your fork. Ordinary dependency updates do not require workflow changes.
2. Open your fork's **Actions** tab. Enable workflows if GitHub prompts you.
3. Select **Python dependency refresh → Run workflow**, then select your branch.
4. Wait for candidate generation, backend tests, and Selenium tests to pass.
5. Download and extract the run's `python-lock-candidate` artifact.
6. Copy its `.conda-lock.yml` files into `testing/locks/` in your checkout.

The workflow resolves dependencies again. Use its tested artifact, not an earlier local copy of the locks. If you change the definitions again, repeat the run. Fork runs do not publish automated PRs.

Continue with [Submit the update](#submit-the-update).

### Generate locally on Linux, macOS, or Windows

The commands need a Linux x86-64 shell. On a Linux x86-64 host, use Conda and open a shell at the repository root. If Conda is absent, install [Miniforge](https://github.com/conda-forge/miniforge).

On macOS or Windows, use Docker Desktop to provide that shell. On Windows, select Linux containers. From the repository root, run this command in your macOS terminal or Windows PowerShell:

```bash
docker run --rm -it --platform linux/amd64 --mount "type=bind,source=${PWD},target=/work" --workdir /work condaforge/miniforge3:latest bash
```

The explicit `linux/amd64` platform matches CI, including when your host uses ARM64. Run the remaining commands in the container's shell. The image already includes Conda.

On first use, create a separate environment for the lock tool:

```bash
conda create --yes --override-channels --channel conda-forge \
  --name conda-lock-tools python=3.11 pip
conda run --no-capture-output -n conda-lock-tools python -m pip install conda-lock==4.0.3
```

Refresh the existing locks:

```bash
conda run --no-capture-output -n conda-lock-tools python testing/update_python_locks.py
```

The script refreshes every existing lock, including its pip dependencies. Locks outside the profile you edited can also change.

Install each changed lock to make sure that its packages are available. This example installs `py39`:

```bash
conda run --no-capture-output -n conda-lock-tools conda-lock install \
  --no-mamba --name py39 testing/locks/py39.conda-lock.yml
```

For another profile, replace both `py39` values with its name. Installation alone does not prove backend compatibility.

With Docker, `/work` is your host checkout. Generated locks remain in your host's `testing/locks/` directory. Exit the container when you finish. Docker removes its installed environments, but not the files in your checkout.

Continue with [Submit the update](#submit-the-update). You do not need to install the test JDKs and services locally if you use regular CI.

## Submit the update

Include the edited definitions and all changed locks in the same PR. Do not include installed Conda environments.

Commit and push these files to your branch. For locally generated locks, wait for the backend and Selenium jobs in the `core` and `frontend` workflows to pass. These jobs test the committed locks without resolving dependencies again.

The manual refresh workflow tests its own generated candidates. It is not a test of an arbitrary local lock. If you use that workflow, submit the locks from its successful artifact.

To run tests locally, provide the JDKs and services listed in [backend-tests.yml](../.github/workflows/backend-tests.yml) and [selenium-integration.yml](../.github/workflows/selenium-integration.yml). The Docker image above does not include those prerequisites.

For example, with a JDK available, build and test Python/Jupyter in the installed `py39` environment:

```bash
conda run --no-capture-output -n py39 ./mvnw install -DskipTests \
  -pl python,zeppelin-jupyter-interpreter -am
conda run --no-capture-output -n py39 ./mvnw test \
  -pl python,zeppelin-jupyter-interpreter -DfailIfNoTests=false
```

This example covers Python/Jupyter, not every consumer of the lock. The regular CI jobs cover the consumers listed in the table above.

## Add a supported version

Use the Linux or Docker shell from the local procedure above. Prepare `conda-lock-tools` before generating the first lock.

1. Add the new profile under `testing/envs/profiles/`. If it needs another Python version, add a version-specific definition.
2. Generate its first lock with explicit source files. The refresh script discovers existing locks, so it cannot create this first lock.
3. Select the new profile in the relevant reusable test workflow. Update Maven profiles if the backend requires them.
4. Install the lock and follow [Submit the update](#submit-the-update). Include the definitions, lock, and test configuration changes together.

The following command shows the sources for `py39`. For a new profile, replace both profile paths and select its Python definition:

```bash
conda run --no-capture-output -n conda-lock-tools conda-lock lock \
  --no-mamba --without-cuda --platform linux-64 \
  --lockfile testing/locks/py39.conda-lock.yml \
  -f testing/envs/common.yml \
  -f testing/envs/python39.yml \
  -f testing/envs/profiles/py39.yml
```

Keep existing profiles while their combinations remain supported. Do not copy Python 3.9 compatibility constraints into a newer Python definition without reviewing them.

## Reproduce a failed dependency check

If resolution fails, read the generation job's logs. That run does not upload a candidate artifact.

If a refresh run fails during installation or tests, download its `python-lock-candidate` artifact. The artifact remains available for 14 days. Copy its locks into a checkout of the run's source revision.

For a failure in regular CI, use that run's committed locks and source revision instead.

Prepare the lock tool in a Linux or Docker shell. Do not run the refresh script. It can select different versions and overwrite the failing candidate. Install the candidate lock in a fresh Conda environment. Use the failing job's JDK, services, and test command to reproduce the failure.

Fix the code or add a justified dependency constraint. Generate new locks and repeat the tests before submitting the fix.

## Review scheduled updates

[Python dependency refresh](../.github/workflows/python-dependencies.yml) runs every Sunday at 04:17 UTC in `apache/zeppelin`. It resolves fresh dependencies within the definitions' constraints. It does not change the supported Python or backend versions.

The workflow installs the candidate locks without environment caching and runs the backend and Selenium tests. It tests candidates even when the locks are unchanged.

Successful Apache master runs with changed locks open or update a draft PR on `automation/python-dependencies`. Review the lock changes and the linked test run before merging. There is no automatic merge.

Resolution or test failures leave committed locks and the existing update PR unchanged. If publication fails, read the publish job's error before rerunning it. Make compatibility fixes on a separate branch because a later refresh can replace the automation branch.
