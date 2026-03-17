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

1. Extract from the user input: PR number, fix-versions, release-branches, resolve-jira, and any other flags.
2. If the PR number is missing, ask for it.
3. Always add `--resolve-jira` unless the user explicitly says not to.
4. Run a dry-run first:

```
go run dev/merge-pr.go --pr <number> --resolve-jira [--fix-versions <versions>] [--release-branches <branches>] --dry-run
```

5. Show the dry-run output to the user and ask for confirmation before proceeding.
6. If the user confirms, run the actual merge command (without `--dry-run`), using the effective command from the dry-run output.
7. After merge, verify the result and report back.

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
