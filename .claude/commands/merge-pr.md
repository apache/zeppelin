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

Merge a pull request using the Go CLI tool (`dev/merge-pr.go`).

## Input

User input: $ARGUMENTS

This can be in any form — CLI flags, natural language, or a mix. Examples:
- `5167`
- `5167 fix-versions 0.13.0, also cherry-pick into branch-0.12`
- `5167 --fix-versions 0.13.0 --release-branches branch-0.12`
- `merge PR #5167 and resolve JIRA`

Parse the user's intent and build the appropriate `go run dev/merge-pr.go` command.

## Instructions

1. Check if `go` is available by running `go version`. If not found:
   - Detect OS and arch (`uname -s`, `uname -m`)
   - Download Go to `.go/` directory: `curl -fsSL https://go.dev/dl/go1.23.6.<os>-<arch>.tar.gz | tar -xz -C .go --strip-components=1`
   - Use `.go/bin/go` instead of `go` for all subsequent commands.
   - `.go/` is already in `.gitignore`.
2. Extract from the user input: PR number, fix-versions, release-branches, resolve-jira, and any other flags.
3. If the PR number is missing or no arguments given, run `go run dev/merge-pr.go --help` to show available flags, then ask the user for the PR number and any options they want.
4. Always add `--resolve-jira` unless the user explicitly says not to.
5. Run a dry-run first:

```
go run dev/merge-pr.go --pr <number> --resolve-jira [--fix-versions <versions>] [--release-branches <branches>] --dry-run
```

6. Show the dry-run output (including the effective command) to the user and ask:
   - Does the effective command look correct?
   - Do you want to change fix-versions, add release-branches, or adjust anything?
   - If the user wants changes, re-run dry-run with updated flags and ask again.
   - If the user confirms, proceed to step 7.
7. Run the actual merge command (without `--dry-run`), using the effective command from the dry-run output.
8. After merge, verify the result and report back.

## Flags Reference

| Flag | Description |
|------|-------------|
| `--pr` | PR number (required) |
| `--fix-versions` | JIRA fix version(s), comma-separated |
| `--release-branches` | Release branch(es) to cherry-pick into, comma-separated |
| `--resolve-jira` | Resolve associated JIRA issue(s) |
| `--dry-run` | Show what would be done without making changes |
| `--push-remote` | Git remote for pushing (default: apache) |
| `--target` | Target branch (default: PR base branch) |

## Notes

- Always dry-run first. Never merge without user confirmation.
- If `--fix-versions` is omitted and `--release-branches` is given, versions are auto-inferred from JIRA.
- Tokens are read from environment: `GITHUB_OAUTH_KEY`, `JIRA_ACCESS_TOKEN`.
