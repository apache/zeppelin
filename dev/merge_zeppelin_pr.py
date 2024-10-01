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

# Utility for creating well-formed pull request merges and pushing them to Apache
# Zeppelin.
#   usage: ./merge_zeppelin_pr.py    (see config env vars below)
#
# This utility assumes you already have a local Zeppelin git folder and that you
# have added remotes corresponding to the git@github.com:apache/zeppelin.git

import json
import os
import re
import subprocess
import sys
import traceback
from urllib.request import urlopen
from urllib.request import Request
from urllib.error import HTTPError

try:
    import jira.client
    JIRA_IMPORTED = True
except ImportError:
    JIRA_IMPORTED = False

# Location of your Zeppelin git development area
ZEPPELIN_HOME = os.environ.get("ZEPPELIN_HOME", os.getcwd())
# Remote name which points to the Github site
PR_REMOTE_NAME = os.environ.get("PR_REMOTE_NAME", "apache")
# Remote name which points to Apache git
PUSH_REMOTE_NAME = os.environ.get("PUSH_REMOTE_NAME", "apache")
# ASF JIRA username
JIRA_USERNAME = os.environ.get("JIRA_USERNAME", "")
# ASF JIRA password
JIRA_PASSWORD = os.environ.get("JIRA_PASSWORD", "")
# ASF JIRA access token
# If it is configured, username and password are dismissed
# Go to https://issues.apache.org/jira/secure/ViewProfile.jspa -> Personal Access Tokens for
# your own token management.
JIRA_ACCESS_TOKEN = os.environ.get("JIRA_ACCESS_TOKEN")
# OAuth key used for issuing requests against the GitHub API. If this is not defined, then requests
# will be unauthenticated. You should only need to configure this if you find yourself regularly
# exceeding your IP's unauthenticated request rate limit. You can create an OAuth key at
# https://github.com/settings/tokens. This script only requires the "public_repo" scope.
GITHUB_OAUTH_KEY = os.environ.get("GITHUB_OAUTH_KEY")


GITHUB_BASE = "https://github.com/apache/zeppelin/pull"
GITHUB_API_BASE = "https://api.github.com/repos/apache/zeppelin"
JIRA_BASE = "https://issues.apache.org/jira/browse"
JIRA_API_BASE = "https://issues.apache.org/jira"
# Prefix added to temporary branches
BRANCH_PREFIX = "PR_TOOL"


def print_error(msg):
    print("\033[91m%s\033[0m" % msg)


def bold_input(prompt) -> str:
    return input("\033[1m%s\033[0m" % prompt)


def http_req_and_return_json(req):
    try:
        if GITHUB_OAUTH_KEY:
            req.add_header("Authorization", "token %s" % GITHUB_OAUTH_KEY)
        return json.load(urlopen(req))
    except HTTPError as e:
        if "X-RateLimit-Remaining" in e.headers and e.headers["X-RateLimit-Remaining"] == "0":
            print_error(
                "Exceeded the GitHub API rate limit; see the instructions in "
                + "dev/merge_zeppelin_pr.py to configure an OAuth token for making authenticated "
                + "GitHub requests."
            )
            sys.exit(-1)
        elif e.code == 401:
            print_error(
                "GITHUB_OAUTH_KEY is invalid or expired. Please regenerate a new one with "
                + "at least the 'public_repo' scope on https://github.com/settings/tokens and "
                + "update your local settings before you try again."
            )
            sys.exit(-1)
        else:
            raise e


def http_put(url, data):
    req = Request(url, data=json.dumps(data).encode('utf-8'), method="PUT")
    return http_req_and_return_json(req)


def http_get(url):
    req = Request(url)
    return http_req_and_return_json(req)


def fail(msg):
    print_error(msg)
    clean_up()
    sys.exit(-1)


def run_cmd(cmd):
    print(cmd)
    if isinstance(cmd, list):
        return subprocess.check_output(cmd).decode("utf-8")
    else:
        return subprocess.check_output(cmd.split(" ")).decode("utf-8")


def continue_maybe(prompt, cherry=False):
    result = bold_input("%s (y/N): " % prompt)
    if result.lower() != "y":
        if cherry:
            try:
                run_cmd("git cherry-pick --abort")
            except Exception:
                print_error("Unable to abort and get back to the state before cherry-pick")
        fail("Okay, exiting")


def clean_up():
    if "original_head" in globals():
        print("Restoring head pointer to %s" % original_head)
        run_cmd("git checkout %s" % original_head)

        branches = run_cmd("git branch").replace(" ", "").split("\n")

        for branch in list(filter(lambda x: x.startswith(BRANCH_PREFIX), branches)):
            print("Deleting local branch %s" % branch)
            run_cmd("git branch -D %s" % branch)


# merge the requested PR and return the merge hash
def merge_pr(pr_num, target_ref, title, body, pr_repo_desc):
    # We replace @ symbols with <at> from the body to avoid triggering e-mails
    # to people every time someone creates a public fork of Zeppelin.
    message = body.replace("@", "<at>")

    committer_name = run_cmd("git config --get user.name").strip()
    committer_email = run_cmd("git config --get user.email").strip()

    # The string "Closes #%s" string is required for GitHub to correctly close the PR
    message = "%s\n\nCloses #%s from %s." % (message, pr_num, pr_repo_desc)
    message = "%s\n\nSigned-off-by: %s <%s>" % (message, committer_name, committer_email)

    merge_pr_resp = None
    try:
        merge_pr_resp = http_put(
            "%s/pulls/%s/merge" % (GITHUB_API_BASE, pr_num),
            {"commit_title": title, "commit_message": message, "merge_method": "squash"})
    except HTTPError as e:
        if e.code == 405:
            fail("Merge pull request #%s is not allowed." % pr_num)

    merge_hash = merge_pr_resp["sha"][:8]
    print("Pull request #%s merged!" % pr_num)
    print("Merge hash: %s" % merge_hash)

    # we must do a git fetch to make the merged commit visible in local
    run_cmd("git fetch %s %s" % (PUSH_REMOTE_NAME, target_ref))
    return merge_hash


def cherry_pick(pr_num, merge_hash, default_branch):
    pick_ref = bold_input("Enter a branch name [%s]: " % default_branch)
    if pick_ref == "":
        pick_ref = default_branch

    pick_branch_name = "%s_PICK_PR_%s_%s" % (BRANCH_PREFIX, pr_num, pick_ref.upper())

    run_cmd("git fetch %s %s:%s" % (PUSH_REMOTE_NAME, pick_ref, pick_branch_name))
    run_cmd("git checkout %s" % pick_branch_name)

    try:
        run_cmd("git cherry-pick -sx %s" % merge_hash)
    except Exception as e:
        msg = "Error cherry-picking: %s\nWould you like to manually fix-up this merge?" % e
        continue_maybe(msg, True)
        msg = "Okay, please fix any conflicts and finish the cherry-pick. Finished?"
        continue_maybe(msg, True)

    continue_maybe(
        "Pick complete (local ref %s). Push to %s?" % (pick_branch_name, PUSH_REMOTE_NAME)
    )

    try:
        run_cmd("git push %s %s:%s" % (PUSH_REMOTE_NAME, pick_branch_name, pick_ref))
    except Exception as e:
        clean_up()
        fail("Exception while pushing: %s" % e)

    pick_hash = run_cmd("git rev-parse %s" % pick_branch_name)[:8]
    clean_up()

    print("Pull request #%s picked into %s!" % (pr_num, pick_ref))
    print("Pick hash: %s" % pick_hash)
    return pick_ref


def print_jira_issue_summary(issue):
    summary = "Summary\t\t%s\n" % issue.fields.summary
    assignee = issue.fields.assignee
    if assignee is not None:
        assignee = assignee.displayName
    assignee = "Assignee\t%s\n" % assignee
    status = "Status\t\t%s\n" % issue.fields.status.name
    url = "Url\t\t%s/%s\n" % (JIRA_BASE, issue.key)
    target_versions = "Affected\t%s\n" % [x.name for x in issue.fields.versions]
    fix_versions = ""
    if len(issue.fields.fixVersions) > 0:
        fix_versions = "Fixed\t\t%s\n" % [x.name for x in issue.fields.fixVersions]
    print("=== JIRA %s ===" % issue.key)
    print("%s%s%s%s%s%s" % (summary, assignee, status, url, target_versions, fix_versions))


def get_jira_issue(prompt, default_jira_id=""):
    jira_id = bold_input("%s [%s]: " % (prompt, default_jira_id))
    if jira_id == "":
        jira_id = default_jira_id
        if jira_id == "":
            print("JIRA ID not found, skipping.")
            return None
    try:
        issue = asf_jira.issue(jira_id)
        print_jira_issue_summary(issue)
        status = issue.fields.status.name
        if status == "Resolved" or status == "Closed":
            print("JIRA issue %s already has status '%s'" % (jira_id, status))
            return None
        if bold_input("Check if the JIRA information is as expected (y/N): ").lower() == "y":
            return issue
        else:
            return get_jira_issue("Enter the revised JIRA ID again or leave blank to skip")
    except Exception as e:
        print_error("ASF JIRA could not find %s: %s" % (jira_id, e))
        return get_jira_issue("Enter the revised JIRA ID again or leave blank to skip")


def resolve_jira_issue(merge_branches, comment, default_jira_id=""):
    issue = get_jira_issue("Enter a JIRA id", default_jira_id)
    if issue is None:
        return

    if issue.fields.assignee is None:
        choose_jira_assignee(issue)

    versions = asf_jira.project_versions("ZEPPELIN")
    # Consider only x.y.z, unreleased, unarchived versions
    versions = [
        x
        for x in versions
        if not x.raw["released"] and not x.raw["archived"] and re.match(r"\d+\.\d+\.\d+", x.name)
    ]
    versions = sorted(versions, key=lambda x: list(map(int, x.name.split('.'))), reverse=True)

    default_fix_versions = []
    for b in merge_branches:
        if b == "master":
            default_fix_versions.append(versions[0].name)
        else:
            found = False
            found_versions = []
            for v in versions:
                if v.name.startswith(b.replace("branch-", "")):
                    found_versions.append(v.name)
                    found = True
            if found:
                # There might be several unreleased versions for specific branches
                # For example, assuming
                # versions = ['4.0.0', '3.5.1', '3.5.0', '3.4.2', '3.3.4', '3.3.3']
                # we've found two candidates for branch-3.5, we pick the last/smallest one
                if found_versions[-1] not in default_fix_versions:
                    default_fix_versions.append(found_versions[-1])
            else:
                print_error(
                    "Target version for %s is not found on JIRA, it may be archived or "
                    "not created. Skipping it." % b
                )

    for v in default_fix_versions:
        # Handles the case where we have forked a release branch but not yet made the release.
        # In this case, if the PR is committed to the master branch and the release branch, we
        # only consider the release branch to be the fix version. E.g. it is not valid to have
        # both 1.1.0 and 1.0.0 as fix versions.
        (major, minor, patch) = v.split(".")
        if patch == "0":
            previous = "%s.%s.%s" % (major, int(minor) - 1, 0)
            if previous in default_fix_versions:
                default_fix_versions = list(filter(lambda x: x != v, default_fix_versions))
    default_fix_versions = ",".join(default_fix_versions)

    available_versions = set(list(map(lambda v: v.name, versions)))
    while True:
        try:
            fix_versions = bold_input(
                "Enter comma-separated fix version(s) [%s]: " % default_fix_versions
            )
            if fix_versions == "":
                fix_versions = default_fix_versions
            fix_versions = fix_versions.replace(" ", "").split(",")
            if set(fix_versions).issubset(available_versions):
                break
            else:
                print(
                    "Specified version(s) [%s] not found in the available versions, try "
                    "again (or leave blank and fix manually)." % (", ".join(fix_versions))
                )
        except KeyboardInterrupt:
            raise
        except BaseException:
            traceback.print_exc()
            print("Error setting fix version(s), try again (or leave blank and fix manually)")

    def get_version_json(version_str):
        return list(filter(lambda v: v.name == version_str, versions))[0].raw

    jira_fix_versions = list(map(lambda v: get_version_json(v), fix_versions))

    resolve = list(filter(lambda a: a["name"] == "Resolve Issue", asf_jira.transitions(issue.key)))[
        0
    ]
    resolution = list(filter(lambda r: r.raw["name"] == "Fixed", asf_jira.resolutions()))[0]
    asf_jira.transition_issue(
        issue.key,
        resolve["id"],
        fixVersions=jira_fix_versions,
        comment=comment,
        resolution={"id": resolution.raw["id"]},
    )

    try:
        print_jira_issue_summary(asf_jira.issue(issue.key))
    except Exception:
        print("Unable to fetch JIRA issue %s after resolving" % issue.key)
    print("Successfully resolved %s with fixVersions=%s!" % (issue.key, fix_versions))


def choose_jira_assignee(issue):
    """
    Prompt the user to choose who to assign the issue to in jira, given a list of candidates,
    including the original reporter and all commentators
    """
    while True:
        try:
            reporter = issue.fields.reporter
            commentators = list(map(lambda x: x.author, issue.fields.comment.comments))
            candidates = set(commentators)
            candidates.add(reporter)
            candidates = list(candidates)
            print("JIRA is unassigned, choose assignee")
            for idx, author in enumerate(candidates):
                if author.key == "apachezeppelin":
                    continue
                annotations = ["Reporter"] if author == reporter else []
                if author in commentators:
                    annotations.append("Commentator")
                print("[%d] %s (%s)" % (idx, author.displayName, ",".join(annotations)))
            raw_assignee = bold_input(
                "Enter number of user, or userid, to assign to (blank to leave unassigned):"
            )
            if raw_assignee == "":
                return None
            else:
                try:
                    id = int(raw_assignee)
                    assignee = candidates[id]
                except BaseException:
                    # assume it's a user id, and try to assign (might fail, we just prompt again)
                    assignee = asf_jira.user(raw_assignee)
                try:
                    assign_issue(issue.key, assignee.name)
                except Exception as e:
                    if (
                        e.__class__.__name__ == "JIRAError"
                        and ("'%s' cannot be assigned" % assignee.name)
                        in getattr(e, "response").text
                    ):
                        continue_maybe(
                            "User '%s' cannot be assigned, add to contributors role and try again?"
                            % assignee.name
                        )
                        grant_contributor_role(assignee.name)
                        assign_issue(issue.key, assignee.name)
                    else:
                        raise e
                return assignee
        except KeyboardInterrupt:
            raise
        except BaseException:
            traceback.print_exc()
            print("Error assigning JIRA, try again (or leave blank and fix manually)")


def grant_contributor_role(user: str):
    role = asf_jira.project_role("ZEPPELIN", 10010)
    role.add_user(user)
    print("Successfully added user '%s' to contributors role" % user)


def assign_issue(issue: int, assignee: str) -> bool:
    """
    Assign an issue to a user, which is a shorthand for jira.client.JIRA.assign_issue.
    The original one has an issue that it will search users again and only choose the assignee
    from 20 candidates. If it's unmatched, it picks the head blindly. In our case, the assignee
    is already resolved.
    """
    url = getattr(asf_jira, "_get_latest_url")(f"issue/{issue}/assignee")
    payload = {"name": assignee}
    getattr(asf_jira, "_session").put(url, data=json.dumps(payload))
    return True


def resolve_jira_issues(title, merge_branches, comment):
    jira_ids = re.findall("ZEPPELIN-[0-9]{3,6}", title)

    if len(jira_ids) == 0:
        resolve_jira_issue(merge_branches, comment)
    for jira_id in jira_ids:
        resolve_jira_issue(merge_branches, comment, jira_id)


def standardize_jira_ref(text):
    """
    Standardize the [ZEPPELIN-XXXX][MODULE] prefix
    Convert
        "[ZEPPELIN-XXXX][spark] Issue" or
        "[Spark] ZEPPELIN-XXXX. Issue" or
        "ZEPPELIN XXXX [SPARK]: Issue"
    to
        "[ZEPPELIN-XXXX][SPARK] Issue"
    """
    jira_refs = []
    components = []

    # If this is a Revert PR, no need to process any further
    if text.startswith('Revert "') and text.endswith('"'):
        return text

    # If the string is compliant, no need to process any further
    if re.search(r"^\[ZEPPELIN-[0-9]{3,6}\](\[[A-Z0-9_\s,]+\] )+\S+", text):
        return text

    # Extract JIRA ref(s):
    pattern = re.compile(r"(ZEPPELIN[-\s]*[0-9]{3,6})+", re.IGNORECASE)
    for ref in pattern.findall(text):
        # Add brackets, replace spaces with a dash, & convert to uppercase
        jira_refs.append("[" + re.sub(r"\s+", "-", ref.upper()) + "]")
        text = text.replace(ref, "")

    # Extract zeppelin component(s):
    # Look for alphanumeric chars, spaces, dashes, periods, and/or commas
    pattern = re.compile(r"(\[[\w\s,.-]+\])", re.IGNORECASE)
    for component in pattern.findall(text):
        components.append(component.upper())
        text = text.replace(component, "")

    # Cleanup any remaining symbols:
    pattern = re.compile(r"^\W+(.*)", re.IGNORECASE)
    if pattern.search(text) is not None:
        text = pattern.search(text).groups()[0]

    # Assemble full text (JIRA ref(s), module(s), remaining text)
    clean_text = "".join(jira_refs).strip() + "".join(components).strip() + " " + text.strip()

    # Replace multiple spaces with a single space, e.g. if no jira refs and/or components were
    # included
    clean_text = re.sub(r"\s+", " ", clean_text.strip())

    return clean_text


def get_current_ref():
    ref = run_cmd("git rev-parse --abbrev-ref HEAD").strip()
    if ref == "HEAD":
        # The current ref is a detached HEAD, so grab its SHA.
        return run_cmd("git rev-parse HEAD").strip()
    else:
        return ref


def initialize_jira():
    global asf_jira
    jira_server = {"server": JIRA_API_BASE}

    if not JIRA_IMPORTED:
        print_error("ERROR finding jira library. Run 'pip3 install jira' to install.")
        continue_maybe("Continue without jira?")
    elif JIRA_ACCESS_TOKEN:
        client = jira.client.JIRA(jira_server, token_auth=JIRA_ACCESS_TOKEN)
        try:
            # Eagerly check if the token is valid to align with the behavior of username/password
            # authn
            client.current_user()
            asf_jira = client
        except Exception as e:
            if e.__class__.__name__ == "JIRAError" and getattr(e, "status_code", None) == 401:
                msg = (
                    "ASF JIRA could not authenticate with the invalid or expired token '%s'"
                    % JIRA_ACCESS_TOKEN
                )
                fail(msg)
            else:
                raise e
    elif JIRA_USERNAME and JIRA_PASSWORD:
        print("You can use JIRA_ACCESS_TOKEN instead of JIRA_USERNAME/JIRA_PASSWORD.")
        print("Visit https://issues.apache.org/jira/secure/ViewProfile.jspa ")
        print("and click 'Personal Access Tokens' menu to manage your own tokens.")
        asf_jira = jira.client.JIRA(jira_server, basic_auth=(JIRA_USERNAME, JIRA_PASSWORD))
    else:
        print("Neither JIRA_ACCESS_TOKEN nor JIRA_USERNAME/JIRA_PASSWORD are set.")
        continue_maybe("Continue without jira?")


def main():
    initialize_jira()
    global original_head

    os.chdir(ZEPPELIN_HOME)
    original_head = get_current_ref()

    branches = http_get("%s/branches" % GITHUB_API_BASE)
    branch_names = list(filter(lambda x: x.startswith("branch-"), [x["name"] for x in branches]))
    branch_names = sorted(branch_names, key=lambda x: list(map(int, x.removeprefix("branch-").split('.'))), reverse=True)
    branch_iter = iter(branch_names)

    pr_num = bold_input("Which pull request would you like to merge? (e.g. 34): ")
    pr = http_get("%s/pulls/%s" % (GITHUB_API_BASE, pr_num))
    pr_events = http_get("%s/issues/%s/events" % (GITHUB_API_BASE, pr_num))

    url = pr["url"]

    # Warn if the PR is WIP
    if "[WIP]" in pr["title"]:
        msg = "The PR title has `[WIP]`:\n%s\nContinue?" % pr["title"]
        continue_maybe(msg)

    # Decide whether to use the modified title or not
    modified_title = standardize_jira_ref(pr["title"]).rstrip(".")
    if modified_title != pr["title"]:
        print("I've re-written the title as follows to match the standard format:")
        print("Original: %s" % pr["title"])
        print("Modified: %s" % modified_title)
        result = bold_input("Would you like to use the modified title? (y/N): ")
        if result.lower() == "y":
            title = modified_title
            print("Using modified title:")
        else:
            title = pr["title"]
            print("Using original title:")
        print(title)
    else:
        title = pr["title"]

    body = pr["body"]
    if body is None:
        body = ""
    modified_body = re.sub(re.compile(r"<!--[^>]*-->\n?", re.DOTALL), "", body).lstrip()
    if modified_body != body:
        print("=" * 80)
        print(modified_body)
        print("=" * 80)
        print("I've removed the comments from PR template like the above:")
        result = bold_input("Would you like to use the modified body? (y/N): ")
        if result.lower() == "y":
            body = modified_body
            print("Using modified body:")
        else:
            print("Using original body:")
        print("=" * 80)
        print(body)
        print("=" * 80)
    target_ref = pr["base"]["ref"]
    user_login = pr["user"]["login"]
    base_ref = pr["head"]["ref"]
    pr_repo_desc = "%s/%s" % (user_login, base_ref)

    if not bool(pr["mergeable"]):
        fail("Pull request %s is not mergeable in its current form." % pr_num)

    if asf_jira is not None:
        jira_ids = re.findall("ZEPPELIN-[0-9]{3,6}", title)
        for jira_id in jira_ids:
            try:
                print_jira_issue_summary(asf_jira.issue(jira_id))
            except Exception:
                print_error("Unable to fetch summary of %s" % jira_id)

    print("\n=== Pull Request #%s ===" % pr_num)
    print("title\t%s\nsource\t%s\ntarget\t%s\nurl\t%s" % (title, pr_repo_desc, target_ref, url))
    continue_maybe("Proceed with merging pull request #%s?" % pr_num)

    merged_refs = [target_ref]

    merge_hash = merge_pr(pr_num, target_ref, title, body, pr_repo_desc)

    pick_prompt = "Would you like to pick %s into another branch?" % merge_hash
    while bold_input("\n%s (y/N): " % pick_prompt).lower() == "y":
        merged_refs = merged_refs + [
            cherry_pick(pr_num, merge_hash, next(branch_iter, branch_names[0]))
        ]

    if asf_jira is not None:
        continue_maybe("Would you like to update an associated JIRA?")
        jira_comment = "Issue resolved by pull request %s\n[%s/%s]" % (
            pr_num,
            GITHUB_BASE,
            pr_num,
        )
        resolve_jira_issues(title, merged_refs, jira_comment)
    else:
        print("Exiting without trying to close the associated JIRA.")


if __name__ == "__main__":
    try:
        main()
    except BaseException:
        clean_up()
        raise
