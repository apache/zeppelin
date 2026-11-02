# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Refresh every package in the committed Linux Python environment locks."""

from pathlib import Path
import subprocess
import sys

import yaml


def main():
    locks = sorted(Path("testing/locks").glob("*.conda-lock.yml"))
    if not locks:
        raise SystemExit(
            "No Python environment locks found; run from the repository root"
        )
    for lock in locks:
        with lock.open(encoding="utf-8") as source:
            packages = yaml.safe_load(source)["package"]
        command = [
            sys.executable, "-m", "conda_lock", "lock", "--no-mamba",
            "--without-cuda", "--platform", "linux-64",
            "--lockfile", str(lock),
        ]
        # Conda solves afresh; explicitly update pip packages that Poetry
        # would otherwise retain from the previous lock.
        pip_packages = {
            package["name"]
            for package in packages
            if package["manager"] == "pip"
        }
        for name in sorted(pip_packages):
            command.extend(["--update", name])
        subprocess.run(command, check=True)


if __name__ == "__main__":
    main()
