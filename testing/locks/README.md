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

# Python dependencies in Linux CI

For the Linux CI environments listed below, the YAML files in `testing/` describe the dependencies we want, while CI installs the resolved Python 3.9 packages from the files here. Changing one of those YAML files alone will not change its CI environment; update the corresponding lock as well.

| Lock file | Environment it supplies |
|---|---|
| `python-3.9-linux-64.conda.lock` | Shared Conda environment for core, Python/Jupyter, integration, Spark, Livy, Flink, and frontend integration jobs. It covers `env_python_3.9.yml`, `env_python_3.yml`, and the Conda dependencies in both `env_python_3_with_flink_*.yml` files. |
| `python-3.9-tensorflow-linux-64.conda.lock` | Conda environment for non-core interpreter jobs, described by `env_python_3_with_tensorflow.yml`. |
| `pyflink-1.19-python-3.9.requirements.lock` | PyPI packages for the Flink 1.19 job, starting from the pip requirement in `env_python_3_with_flink_119.yml`. |
| `pyflink-1.20-python-3.9.requirements.lock` | PyPI packages for the Flink 1.20 job, starting from the pip requirement in `env_python_3_with_flink_120.yml`. |

## Changing dependencies

1. Edit the relevant YAML file in `testing/`. If a shared Conda dependency changes, check that the other environments using the shared lock still have the packages they need.
2. On Ubuntu 24.04 x86-64, resolve the changed Conda environment using conda-forge and export it with `conda list --explicit --md5`. For example, to update the shared lock:

   ```bash
   conda env create -n zeppelin-lock-source -f testing/env_python_3.9.yml
   conda list -n zeppelin-lock-source --explicit --md5
   ```

   Put the exported explicit specification in the corresponding `.conda.lock` file, retaining its ASF license header. Use `env_python_3_with_tensorflow.yml` instead when updating the TensorFlow lock.
3. For a PyFlink change, start with a fresh environment installed from the shared Conda lock. Install the `apache-flink` version specified in the matching Flink YAML file with pip, then record every PyPI package and version it installs in the matching `.requirements.lock` file. CI uses `pip install --no-deps`, so the file must include transitive PyPI dependencies, not just `apache-flink`.
4. Validate the lock in a new environment, not just the environment used to generate it. For the shared lock and Flink 1.19, for example:

   ```bash
   conda create -n zeppelin-lock-check --file testing/locks/python-3.9-linux-64.conda.lock
   conda run -n zeppelin-lock-check python -m pip install --no-deps -r testing/locks/pyflink-1.19-python-3.9.requirements.lock
   ```

   Use the TensorFlow lock or Flink 1.20 requirements as appropriate, then run the affected imports and CI tests.

The `.conda.lock` files contain exact Linux x86-64 package URLs and MD5 hashes; they are not portable to other platforms. The PyFlink files pin package versions but do not pin wheel hashes. A cache miss may still download packages, but installation from the Conda locks does not run the dependency solver.
