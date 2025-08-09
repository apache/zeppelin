#!/usr/bin/python
# -*- coding: utf-8 -*-
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import subprocess
import sys
from datetime import datetime, timedelta


def main(file_path, cmd):
    """
    Execute a given command, capture its combined stdout and stderr output in real time,
    write all output lines to a specified log file, and periodically print progress
    (elapsed seconds and line count) to the console.

    Args:
        file_path (str): Path to the log file where all command output will be written.
        cmd (list[str]): The command to execute, provided as a list of arguments.

    Returns:
        int: The exit code returned by the executed command.
    """

    print(f"{cmd} writing to {file_path}")
    count = 0

    process = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )

    start = datetime.now()
    next_print = start + timedelta(seconds=1)

    try:
        with open(file_path, "w", encoding="utf-8", buffering=1) as out:
            for line in process.stdout:
                count += 1
                now = datetime.now()
                if now > next_print:
                    diff = now - start
                    sys.stdout.write(f"\r{diff.seconds} seconds {count} log lines")
                    sys.stdout.flush()
                    next_print = now + timedelta(seconds=10)
                out.write(line)
    except KeyboardInterrupt:
        process.terminate()
        process.wait()
        raise

    errcode = process.wait()
    diff = datetime.now() - start
    sys.stdout.write(f"\r{diff.seconds} seconds {count} log lines")
    sys.stdout.write(f"\n{cmd} done {errcode}\n")
    sys.stdout.flush()
    return errcode


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <log_file> <cmd> [args ...]")
        sys.exit(1)

    log_file = sys.argv[1]
    command = sys.argv[2:]
    sys.exit(main(log_file, command))
