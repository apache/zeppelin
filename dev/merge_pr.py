#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""merge_pr.py - Merge Apache Zeppelin pull requests via the GitHub API.

Optionally cherry-picks into release branches and resolves JIRA issues.
No external dependencies — uses only Python 3 built-in libraries.

Usage:
    python3 dev/merge_pr.py --pr 5167 --dry-run
    python3 dev/merge_pr.py --pr 5167 --resolve-jira --fix-versions 0.13.0
    python3 dev/merge_pr.py --pr 5167 --resolve-jira --release-branches branch-0.12
"""

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

GITHUB_API_BASE = "https://api.github.com/repos/apache/zeppelin"
JIRA_API_BASE = "https://issues.apache.org/jira/rest/api/2"

JIRA_ID_RE = re.compile(r"ZEPPELIN-\d{3,6}")
TITLE_FORMATTED_RE = re.compile(r"^\[ZEPPELIN-\d{3,6}](\[[A-Z0-9_\s,]+] )+\S+")
TITLE_REF_RE = re.compile(r"(?i)(ZEPPELIN[-\s]*\d{3,6})")
COMPONENT_RE = re.compile(r"(?i)(\[[\w\s,.\-]+])")
WHITESPACE_RE = re.compile(r"\s+")
LEADING_NON_WORD_RE = re.compile(r"^\W+")
SEMANTIC_VER_RE = re.compile(r"^\d+\.\d+\.\d+$")


class MergePR:
    def __init__(self, args):
        self.pr = args.pr
        self.target = args.target or ""
        self.fix_versions = _parse_csv(args.fix_versions) if args.fix_versions else []
        self.release_branches = _parse_csv(args.release_branches) if args.release_branches else []
        self.resolve_jira = args.resolve_jira
        self.dry_run = args.dry_run
        self.push_remote = args.push_remote or os.environ.get("PUSH_REMOTE_NAME", "apache")
        self.github_token = args.github_token or os.environ.get("GITHUB_OAUTH_KEY", "")
        self.jira_token = args.jira_token or os.environ.get("JIRA_ACCESS_TOKEN", "")

    # ── Git ──────────────────────────────────────────────────────────────

    def _git(self, *args):
        result = subprocess.run(
            ["git"] + list(args),
            capture_output=True, text=True,
        )
        if result.returncode != 0:
            output = (result.stdout + result.stderr).strip()
            raise RuntimeError(f"git {' '.join(args)} failed:\n{output}")
        return result.stdout.strip()

    def _git_current_ref(self):
        ref = self._git("rev-parse", "--abbrev-ref", "HEAD")
        return self._git("rev-parse", "HEAD") if ref == "HEAD" else ref

    # ── HTTP ─────────────────────────────────────────────────────────────

    def _http(self, method, url, body=None, auth=""):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "application/json")
        if auth:
            req.add_header("Authorization", auth)
        try:
            with urllib.request.urlopen(req) as resp:
                return resp.status, json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            body_text = e.read().decode() if e.fp else ""
            try:
                return e.code, json.loads(body_text)
            except json.JSONDecodeError:
                return e.code, {"error": body_text}

    # ── GitHub ───────────────────────────────────────────────────────────

    def _gh_auth(self):
        return f"token {self.github_token}" if self.github_token else ""

    def _gh_get_pr(self, num):
        code, data = self._http("GET", f"{GITHUB_API_BASE}/pulls/{num}", auth=self._gh_auth())
        if code != 200:
            raise RuntimeError(f"GET PR #{num}: HTTP {code}")
        return data

    def _gh_merge_pr(self, num, title, msg):
        body = {"commit_title": title, "commit_message": msg, "merge_method": "squash"}
        code, data = self._http("PUT", f"{GITHUB_API_BASE}/pulls/{num}/merge", body, self._gh_auth())
        if code == 405:
            raise RuntimeError(f"Merge PR #{num} is not allowed")
        if code != 200:
            raise RuntimeError(f"Merge PR #{num}: HTTP {code}")
        return data

    def _gh_comment_pr(self, num, comment):
        code, _ = self._http("POST", f"{GITHUB_API_BASE}/issues/{num}/comments",
                             {"body": comment}, self._gh_auth())
        if code != 201:
            print(f"Warning: comment PR #{num}: HTTP {code}", file=sys.stderr)

    # ── JIRA ─────────────────────────────────────────────────────────────

    def _jira_auth(self):
        return f"Bearer {self.jira_token}" if self.jira_token else ""

    def _jira_get_issue(self, key):
        code, data = self._http("GET", f"{JIRA_API_BASE}/issue/{key}", auth=self._jira_auth())
        if code != 200:
            raise RuntimeError(f"GET {key}: HTTP {code}")
        return data

    def _jira_unreleased_versions(self):
        code, data = self._http("GET", f"{JIRA_API_BASE}/project/ZEPPELIN/versions", auth=self._jira_auth())
        if code != 200:
            raise RuntimeError(f"GET versions: HTTP {code}")
        versions = []
        for v in data:
            name = v.get("name", "")
            if not v.get("released") and not v.get("archived") and SEMANTIC_VER_RE.match(name):
                versions.append({"id": str(v["id"]), "name": name})
        versions.sort(key=lambda v: _ver_tuple(v["name"]), reverse=True)
        return versions

    def _jira_transitions(self, key):
        code, data = self._http("GET", f"{JIRA_API_BASE}/issue/{key}/transitions", auth=self._jira_auth())
        if code != 200:
            raise RuntimeError(f"GET transitions {key}: HTTP {code}")
        return [{"id": t["id"], "name": t["name"]} for t in data.get("transitions", [])]

    def _jira_resolve(self, key, transition_id, fix_ver, comment):
        body = {
            "transition": {"id": transition_id},
            "update": {
                "comment": [{"add": {"body": comment}}],
                "fixVersions": [{"add": {"id": fv["id"], "name": fv["name"]}} for fv in fix_ver],
            },
        }
        code, _ = self._http("POST", f"{JIRA_API_BASE}/issue/{key}/transitions", body, self._jira_auth())
        if code != 204:
            raise RuntimeError(f"Resolve {key}: HTTP {code}")

    # ── Fix version inference ────────────────────────────────────────────

    def _infer_fix_versions(self, merged, versions, infer_master):
        names, seen = [], set()
        for branch in merged:
            if branch == "master":
                if infer_master and versions[0]["name"] not in seen:
                    names.append(versions[0]["name"])
                    seen.add(versions[0]["name"])
            else:
                prefix = branch[len("branch-"):] if branch.startswith("branch-") else branch
                found = [v["name"] for v in versions if v["name"].startswith(prefix + ".") or v["name"] == prefix]
                if found:
                    pick = found[-1]  # smallest matching (list is desc-sorted)
                    if pick not in seen:
                        names.append(pick)
                        seen.add(pick)
                else:
                    print(f"Warning: no version found for {branch}, skipping", file=sys.stderr)

        # Remove redundant X.Y.0 when X.(Y-1).0 is also present
        filtered = []
        for v in names:
            parts = v.split(".")
            if len(parts) == 3 and parts[2] == "0":
                minor = int(parts[1])
                if minor > 0 and f"{parts[0]}.{minor - 1}.0" in seen:
                    continue
            filtered.append(v)

        vm = {v["name"]: v for v in versions}
        result = [vm[n] for n in filtered if n in vm]
        if result:
            print(f"Auto-inferred fix version(s): {', '.join(filtered)}")
        return result

    # ── Effective command ────────────────────────────────────────────────

    def _print_effective_command(self, target_branch, resolved_versions):
        parts = ["python3 dev/merge_pr.py", f"--pr {self.pr}"]
        if target_branch and target_branch != "master":
            parts.append(f"--target {target_branch}")
        if self.release_branches:
            parts.append(f"--release-branches {','.join(self.release_branches)}")
        if self.resolve_jira:
            parts.append("--resolve-jira")
        if resolved_versions:
            parts.append(f"--fix-versions {','.join(resolved_versions)}")
        if self.push_remote != "apache":
            parts.append(f"--push-remote {self.push_remote}")
        print(f"[dry-run] Effective command:\n  {' '.join(parts)}")

    # ── Main flow ────────────────────────────────────────────────────────

    def run(self):
        original_head = self._git_current_ref()

        pr_data = self._gh_get_pr(self.pr)
        if not pr_data.get("mergeable"):
            raise RuntimeError(f"PR #{self.pr} is not mergeable")
        pr_title = pr_data["title"]
        if "[WIP]" in pr_title:
            print(f"WARNING: PR title contains [WIP]: {pr_title}", file=sys.stderr)

        target_branch = self.target or pr_data["base"]["ref"]
        title = _standardize_title(pr_title)
        src = f"{pr_data['user']['login']}/{pr_data['head']['ref']}"
        pr_body = pr_data.get("body", "") or ""

        print(f"=== Pull Request #{self.pr} ===")
        print(f"title:  {title}")
        print(f"source: {src}")
        print(f"target: {target_branch}")
        print(f"url:    {pr_data['url']}")
        if self.release_branches:
            print(f"release-branches: {', '.join(self.release_branches)}")

        resolved_fix_versions = self._resolve_fix_version_names(title, target_branch)

        if self.dry_run:
            print()
            self._print_effective_command(target_branch, resolved_fix_versions)
            return

        # Merge
        body = pr_body.replace("@", "<at>")
        try:
            name = self._git("config", "--get", "user.name")
        except RuntimeError:
            name = ""
        try:
            email = self._git("config", "--get", "user.email")
        except RuntimeError:
            email = ""
        msg = f"{body}\n\nCloses #{self.pr} from {src}.\n\nSigned-off-by: {name} <{email}>"

        merge_data = self._gh_merge_pr(self.pr, title, msg)
        sha = merge_data["sha"]
        print(f"\nPR #{self.pr} merged! (hash: {_short_sha(sha)})")

        try:
            self._git("fetch", self.push_remote, target_branch)
        except RuntimeError:
            pass

        # Cherry-pick into release branches
        merged = [target_branch]
        for branch in self.release_branches:
            pick = f"PR_TOOL_PICK_PR_{self.pr}_{branch.upper()}"
            try:
                self._git("fetch", self.push_remote, f"{branch}:{pick}")
            except RuntimeError as e:
                print(f"Warning: fetch {branch} failed: {e}", file=sys.stderr)
                continue
            self._git("checkout", pick)
            try:
                self._git("cherry-pick", "-sx", sha)
            except RuntimeError as e:
                print(f"Warning: cherry-pick into {branch} failed: {e}", file=sys.stderr)
                try:
                    self._git("cherry-pick", "--abort")
                except RuntimeError:
                    pass
                self._git("checkout", original_head)
                self._git("branch", "-D", pick)
                continue
            try:
                self._git("push", self.push_remote, f"{pick}:{branch}")
                h = self._git("rev-parse", pick)
                print(f"Picked into {branch} (hash: {_short_sha(h)})")
                merged.append(branch)
            except RuntimeError as e:
                print(f"Warning: push to {branch} failed: {e}", file=sys.stderr)
            self._git("checkout", original_head)
            self._git("branch", "-D", pick)

        self._comment_merge_summary(merged, sha)

        if self.resolve_jira:
            try:
                self._do_resolve_jira(title, merged)
            except RuntimeError as e:
                print(f"Warning: JIRA resolution failed: {e}", file=sys.stderr)

    def _resolve_fix_version_names(self, title, target_branch):
        if not self.resolve_jira or not self.jira_token:
            return list(self.fix_versions)

        ids = JIRA_ID_RE.findall(title)
        if not ids:
            return list(self.fix_versions)

        try:
            versions = self._jira_unreleased_versions()
            if not versions:
                return list(self.fix_versions)

            vm = {v["name"]: v for v in versions}
            resolved = list(dict.fromkeys(fv for fv in self.fix_versions if fv in vm))

            infer_master = not self.fix_versions
            branches = [target_branch] + self.release_branches
            for iv in self._infer_fix_versions(branches, versions, infer_master):
                if iv["name"] not in resolved:
                    resolved.append(iv["name"])
            return resolved
        except RuntimeError as e:
            print(f"Warning: failed to fetch JIRA versions: {e}", file=sys.stderr)
            return list(self.fix_versions)

    def _comment_merge_summary(self, merged, sha):
        lines = [f"Merged into {merged[0]} ({_short_sha(sha)})."]
        for branch in merged[1:]:
            lines.append(f"Cherry-picked into {branch}.")
        try:
            self._gh_comment_pr(self.pr, "\n".join(lines))
            print("Commented on PR with merge summary.")
        except RuntimeError as e:
            print(f"Warning: failed to comment on PR: {e}", file=sys.stderr)

    def _do_resolve_jira(self, title, merged):
        if not self.jira_token:
            raise RuntimeError("JIRA_ACCESS_TOKEN is not set")

        ids = JIRA_ID_RE.findall(title)
        if not ids:
            print("No JIRA ID found in PR title, skipping.")
            return

        versions = self._jira_unreleased_versions()
        vm = {v["name"]: v for v in versions}

        fix_ver, seen = [], set()
        for fv in self.fix_versions:
            if fv not in vm:
                raise RuntimeError(f'fix version "{fv}" not found')
            fix_ver.append(vm[fv])
            seen.add(fv)
        if versions:
            infer_master = not self.fix_versions
            for iv in self._infer_fix_versions(merged, versions, infer_master):
                if iv["name"] not in seen:
                    fix_ver.append(iv)
                    seen.add(iv["name"])

        for jira_id in ids:
            try:
                issue = self._jira_get_issue(jira_id)
            except RuntimeError as e:
                print(f"Warning: get {jira_id}: {e}", file=sys.stderr)
                continue
            status = issue.get("fields", {}).get("status", {}).get("name", "")
            if status in ("Resolved", "Closed"):
                print(f'JIRA {jira_id} already "{status}", skipping.')
                continue

            print(f"=== JIRA {jira_id} ===")
            print(f"Summary:  {issue.get('fields', {}).get('summary', '')}")
            print(f"Status:   {status}")

            transitions = self._jira_transitions(jira_id)
            resolve_id = next((t["id"] for t in transitions if t["name"] == "Resolve Issue"), None)
            if not resolve_id:
                print(f"Warning: no 'Resolve Issue' transition for {jira_id}", file=sys.stderr)
                continue

            jira_comment = (
                f"Issue resolved by pull request {self.pr}"
                f"\n[https://github.com/apache/zeppelin/pull/{self.pr}]"
            )
            try:
                self._jira_resolve(jira_id, resolve_id, fix_ver, jira_comment)
                print(f"Resolved {jira_id}!")
            except RuntimeError as e:
                print(f"Warning: resolve {jira_id}: {e}", file=sys.stderr)


# ── Module-level utilities ───────────────────────────────────────────────

def _parse_csv(value):
    return [s.strip() for s in value.split(",") if s.strip()] if value else []


def _ver_tuple(v):
    return tuple(int(x) for x in v.split("."))


def _short_sha(sha):
    return sha[:8] if len(sha) > 8 else sha


def _standardize_title(text):
    text = text.rstrip(".")
    if text.startswith('Revert "') and text.endswith('"'):
        return text
    if TITLE_FORMATTED_RE.match(text):
        return text

    jira_refs = []
    for m in TITLE_REF_RE.finditer(text):
        ref = m.group(1)
        jira_refs.append("[" + WHITESPACE_RE.sub("-", ref.upper()) + "]")
        text = text.replace(ref, "")

    components = []
    for m in COMPONENT_RE.finditer(text):
        comp = m.group(1)
        components.append(comp.upper())
        text = text.replace(comp, "")

    text = LEADING_NON_WORD_RE.sub("", text)
    result = "".join(jira_refs) + "".join(components) + " " + text
    return WHITESPACE_RE.sub(" ", result.strip())


# ── Entry point ──────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Merge Apache Zeppelin pull requests",
        usage="python3 dev/merge_pr.py [flags]",
    )
    parser.add_argument("--pr", type=int, required=True, help="Pull request number")
    parser.add_argument("--target", default="", help="Target branch (default: PR base branch)")
    parser.add_argument("--fix-versions", default="", help="JIRA fix version(s), comma-separated")
    parser.add_argument("--release-branches", default="", help="Release branch(es) to cherry-pick into, comma-separated")
    parser.add_argument("--resolve-jira", action="store_true", help="Resolve associated JIRA issue(s)")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be done without making changes")
    parser.add_argument("--push-remote", default="", help="Git remote for pushing (default: apache)")
    parser.add_argument("--github-token", default="", help="GitHub OAuth token (env: GITHUB_OAUTH_KEY)")
    parser.add_argument("--jira-token", default="", help="JIRA access token (env: JIRA_ACCESS_TOKEN)")

    args = parser.parse_args()
    MergePR(args).run()


if __name__ == "__main__":
    main()
