/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// merge-pr.go merges Apache Zeppelin pull requests via the GitHub API,
// optionally cherry-picks into release branches, and resolves JIRA issues.
//
// Usage:
//
//	go run dev/merge-pr.go --pr 5167 --dry-run
//	go run dev/merge-pr.go --pr 5167 --resolve-jira --fix-versions 0.13.0
//	go run dev/merge-pr.go --pr 5167 --resolve-jira --release-branches branch-0.12,branch-0.11
package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

const (
	githubAPIBase = "https://api.github.com/repos/apache/zeppelin"
	jiraAPIBase   = "https://issues.apache.org/jira/rest/api/2"
)

// ── CSV flag type ───────────────────────────────────────────────────────────

type csvFlag []string

func (f *csvFlag) String() string { return strings.Join(*f, ",") }
func (f *csvFlag) Set(v string) error {
	for _, s := range strings.Split(v, ",") {
		if t := strings.TrimSpace(s); t != "" {
			*f = append(*f, t)
		}
	}
	return nil
}

// ── Flags ───────────────────────────────────────────────────────────────────

var (
	flagPR              int
	flagTarget          string
	flagFixVersions     csvFlag
	flagReleaseBranches csvFlag
	flagResolveJira     bool
	flagDryRun          bool
	flagPushRemote      string
	flagGithubToken     string
	flagJiraToken       string
)

func init() {
	flag.IntVar(&flagPR, "pr", 0, "Pull request number (required)")
	flag.StringVar(&flagTarget, "target", "", "Target branch (default: PR base branch)")
	flag.Var(&flagFixVersions, "fix-versions", "JIRA fix version(s), comma-separated")
	flag.Var(&flagReleaseBranches, "release-branches", "Release branch(es) to cherry-pick into, comma-separated")
	flag.BoolVar(&flagResolveJira, "resolve-jira", false, "Resolve associated JIRA issue(s)")
	flag.BoolVar(&flagDryRun, "dry-run", false, "Show what would be done without making changes")
	flag.StringVar(&flagPushRemote, "push-remote", envOrDefault("PUSH_REMOTE_NAME", "apache"), "Git remote for pushing")
	flag.StringVar(&flagGithubToken, "github-token", "", "GitHub OAuth token (env: GITHUB_OAUTH_KEY)")
	flag.StringVar(&flagJiraToken, "jira-token", "", "JIRA access token (env: JIRA_ACCESS_TOKEN)")
}

func envOrDefault(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

// ── Git ─────────────────────────────────────────────────────────────────────

func gitRun(args ...string) (string, error) {
	out, err := exec.Command("git", args...).CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("git %s: %w\n%s", strings.Join(args, " "), err, out)
	}
	return strings.TrimSpace(string(out)), nil
}

func gitCurrentRef() (string, error) {
	ref, err := gitRun("rev-parse", "--abbrev-ref", "HEAD")
	if err != nil {
		return "", err
	}
	if ref == "HEAD" {
		return gitRun("rev-parse", "HEAD")
	}
	return ref, nil
}

// ── HTTP ────────────────────────────────────────────────────────────────────

func httpDo(method, url string, body interface{}, auth string) ([]byte, int, error) {
	var r io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, 0, err
		}
		r = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, url, r)
	if err != nil {
		return nil, 0, err
	}
	if auth != "" {
		req.Header.Set("Authorization", auth)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/vnd.github.v3+json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	return data, resp.StatusCode, err
}

// ── GitHub ───────────────────────────────────────────────────────────────────

type pullRequest struct {
	URL       string `json:"url"`
	Title     string `json:"title"`
	Body      string `json:"body"`
	Mergeable bool   `json:"mergeable"`
	Base      struct{ Ref string `json:"ref"` } `json:"base"`
	Head      struct{ Ref string `json:"ref"` } `json:"head"`
	User      struct{ Login string `json:"login"` } `json:"user"`
}

type mergeResponse struct{ SHA string `json:"sha"` }

func ghAuth() string {
	if flagGithubToken != "" {
		return "token " + flagGithubToken
	}
	return ""
}

func ghGetPR(num int) (*pullRequest, error) {
	data, code, err := httpDo("GET", fmt.Sprintf("%s/pulls/%d", githubAPIBase, num), nil, ghAuth())
	if err != nil {
		return nil, err
	}
	if code != 200 {
		return nil, fmt.Errorf("GET PR #%d: HTTP %d: %s", num, code, data)
	}
	var pr pullRequest
	return &pr, json.Unmarshal(data, &pr)
}

func ghMergePR(num int, title, msg string) (*mergeResponse, error) {
	body := map[string]string{"commit_title": title, "commit_message": msg, "merge_method": "squash"}
	data, code, err := httpDo("PUT", fmt.Sprintf("%s/pulls/%d/merge", githubAPIBase, num), body, ghAuth())
	if err != nil {
		return nil, err
	}
	if code == 405 {
		return nil, fmt.Errorf("merge PR #%d is not allowed", num)
	}
	if code != 200 {
		return nil, fmt.Errorf("merge PR #%d: HTTP %d: %s", num, code, data)
	}
	var resp mergeResponse
	return &resp, json.Unmarshal(data, &resp)
}

// ── JIRA ────────────────────────────────────────────────────────────────────

type jiraIssue struct {
	Key    string `json:"key"`
	Fields struct {
		Summary  string     `json:"summary"`
		Status   struct{ Name string `json:"name"` } `json:"status"`
		Assignee *struct{ DisplayName string `json:"displayName"` } `json:"assignee"`
	} `json:"fields"`
}

type jiraVersion struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Released bool   `json:"released"`
	Archived bool   `json:"archived"`
}

type jiraTransition struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

func jiraAuth() string {
	if flagJiraToken != "" {
		return "Bearer " + flagJiraToken
	}
	return ""
}

func jiraGetIssue(key string) (*jiraIssue, error) {
	data, code, err := httpDo("GET", fmt.Sprintf("%s/issue/%s", jiraAPIBase, key), nil, jiraAuth())
	if err != nil {
		return nil, err
	}
	if code != 200 {
		return nil, fmt.Errorf("GET %s: HTTP %d: %s", key, code, data)
	}
	var issue jiraIssue
	return &issue, json.Unmarshal(data, &issue)
}

func jiraUnreleasedVersions() ([]jiraVersion, error) {
	data, code, err := httpDo("GET", jiraAPIBase+"/project/ZEPPELIN/versions", nil, jiraAuth())
	if err != nil {
		return nil, err
	}
	if code != 200 {
		return nil, fmt.Errorf("GET versions: HTTP %d: %s", code, data)
	}
	var all []jiraVersion
	if err := json.Unmarshal(data, &all); err != nil {
		return nil, err
	}
	var out []jiraVersion
	for _, v := range all {
		if !v.Released && !v.Archived && reSemanticVer.MatchString(v.Name) {
			out = append(out, v)
		}
	}
	sort.Slice(out, func(i, j int) bool { return cmpVer(out[i].Name, out[j].Name) > 0 })
	return out, nil
}

func jiraTransitions(key string) ([]jiraTransition, error) {
	data, code, err := httpDo("GET", fmt.Sprintf("%s/issue/%s/transitions", jiraAPIBase, key), nil, jiraAuth())
	if err != nil {
		return nil, err
	}
	if code != 200 {
		return nil, fmt.Errorf("GET transitions %s: HTTP %d: %s", key, code, data)
	}
	var r struct{ Transitions []jiraTransition `json:"transitions"` }
	if err := json.Unmarshal(data, &r); err != nil {
		return nil, err
	}
	return r.Transitions, nil
}

func jiraResolve(key, tid string, fv []jiraVersion, comment string) error {
	var fvu []map[string]interface{}
	for _, v := range fv {
		fvu = append(fvu, map[string]interface{}{"add": map[string]string{"id": v.ID, "name": v.Name}})
	}
	body := map[string]interface{}{
		"transition": map[string]string{"id": tid},
		"update": map[string]interface{}{
			"comment":     []map[string]interface{}{{"add": map[string]string{"body": comment}}},
			"fixVersions": fvu,
		},
	}
	_, code, err := httpDo("POST", fmt.Sprintf("%s/issue/%s/transitions", jiraAPIBase, key), body, jiraAuth())
	if err != nil {
		return err
	}
	if code != 204 {
		return fmt.Errorf("resolve %s: HTTP %d", key, code)
	}
	return nil
}

func cmpVer(a, b string) int {
	ap, bp := strings.Split(a, "."), strings.Split(b, ".")
	for i := 0; i < len(ap) && i < len(bp); i++ {
		ai, _ := strconv.Atoi(ap[i])
		bi, _ := strconv.Atoi(bp[i])
		if ai != bi {
			return ai - bi
		}
	}
	return len(ap) - len(bp)
}

// ── Helpers ──────────────────────────────────────────────────────────────────

var (
	jiraIDRe         = regexp.MustCompile(`ZEPPELIN-\d{3,6}`)
	reTitleFormatted = regexp.MustCompile(`^\[ZEPPELIN-\d{3,6}\](\[[A-Z0-9_\s,]+\] )+\S+`)
	reTitleRef       = regexp.MustCompile(`(?i)(ZEPPELIN[-\s]*\d{3,6})`)
	reComponent      = regexp.MustCompile(`(?i)(\[[\w\s,.\-]+\])`)
	reWhitespace     = regexp.MustCompile(`\s+`)
	reLeadingNonWord = regexp.MustCompile(`^\W+`)
	reSemanticVer    = regexp.MustCompile(`^\d+\.\d+\.\d+$`)
)

func shortSHA(s string) string {
	if len(s) > 8 {
		return s[:8]
	}
	return s
}

func standardizeTitle(text string) string {
	text = strings.TrimRight(text, ".")
	if strings.HasPrefix(text, `Revert "`) && strings.HasSuffix(text, `"`) {
		return text
	}
	if reTitleFormatted.MatchString(text) {
		return text
	}
	// Extract JIRA ref(s)
	var jiraRefs []string
	for _, ref := range reTitleRef.FindAllString(text, -1) {
		jiraRefs = append(jiraRefs, "["+strings.ToUpper(reWhitespace.ReplaceAllString(ref, "-"))+"]")
		text = strings.Replace(text, ref, "", 1)
	}
	// Extract component(s): [SPARK], [FLINK], etc.
	var components []string
	for _, comp := range reComponent.FindAllString(text, -1) {
		components = append(components, strings.ToUpper(comp))
		text = strings.Replace(text, comp, "", 1)
	}
	// Cleanup remaining leading symbols
	text = reLeadingNonWord.ReplaceAllString(text, "")
	// Assemble: [ZEPPELIN-XXXX][COMPONENT] remaining text
	result := strings.Join(jiraRefs, "") + strings.Join(components, "") + " " + text
	return reWhitespace.ReplaceAllString(strings.TrimSpace(result), " ")
}

// ── Main ────────────────────────────────────────────────────────────────────

func main() {
	flag.Parse()
	if flagPR == 0 {
		fmt.Fprintln(os.Stderr, "Error: --pr is required")
		flag.Usage()
		os.Exit(1)
	}
	if flagGithubToken == "" {
		flagGithubToken = os.Getenv("GITHUB_OAUTH_KEY")
	}
	if flagJiraToken == "" {
		flagJiraToken = os.Getenv("JIRA_ACCESS_TOKEN")
	}

	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	originalHead, err := gitCurrentRef()
	if err != nil {
		return fmt.Errorf("get current ref: %w", err)
	}

	pr, err := ghGetPR(flagPR)
	if err != nil {
		return err
	}
	if !pr.Mergeable {
		return fmt.Errorf("PR #%d is not mergeable", flagPR)
	}
	if strings.Contains(pr.Title, "[WIP]") {
		fmt.Fprintf(os.Stderr, "WARNING: PR title contains [WIP]: %s\n", pr.Title)
	}

	target := flagTarget
	if target == "" {
		target = pr.Base.Ref
	}
	title := standardizeTitle(pr.Title)
	src := fmt.Sprintf("%s/%s", pr.User.Login, pr.Head.Ref)

	fmt.Printf("=== Pull Request #%d ===\n", flagPR)
	fmt.Printf("title:  %s\n", title)
	fmt.Printf("source: %s\n", src)
	fmt.Printf("target: %s\n", target)
	fmt.Printf("url:    %s\n", pr.URL)
	if len(flagReleaseBranches) > 0 {
		fmt.Printf("release-branches: %s\n", strings.Join(flagReleaseBranches, ", "))
	}

	if flagDryRun {
		fmt.Println("\n[dry-run] Would merge PR and stop here.")
		return nil
	}

	// Merge
	body := strings.ReplaceAll(pr.Body, "@", "<at>")
	name, _ := gitRun("config", "--get", "user.name")
	email, _ := gitRun("config", "--get", "user.email")
	msg := fmt.Sprintf("%s\n\nCloses #%d from %s.\n\nSigned-off-by: %s <%s>", body, flagPR, src, name, email)

	resp, err := ghMergePR(flagPR, title, msg)
	if err != nil {
		return err
	}
	fmt.Printf("\nPR #%d merged! (hash: %s)\n", flagPR, shortSHA(resp.SHA))

	gitRun("fetch", flagPushRemote, target)

	// Cherry-pick into release branches
	merged := []string{target}
	for _, branch := range flagReleaseBranches {
		pick := fmt.Sprintf("PR_TOOL_PICK_PR_%d_%s", flagPR, strings.ToUpper(branch))
		cleanup := func() {
			gitRun("checkout", originalHead)
			gitRun("branch", "-D", pick)
		}
		if _, err := gitRun("fetch", flagPushRemote, branch+":"+pick); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: fetch %s failed: %v\n", branch, err)
			continue
		}
		gitRun("checkout", pick)
		if _, err := gitRun("cherry-pick", "-sx", resp.SHA); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: cherry-pick into %s failed: %v\n", branch, err)
			gitRun("cherry-pick", "--abort")
			cleanup()
			continue
		}
		if _, err := gitRun("push", flagPushRemote, pick+":"+branch); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: push to %s failed: %v\n", branch, err)
		} else {
			h, _ := gitRun("rev-parse", pick)
			fmt.Printf("Picked into %s (hash: %s)\n", branch, shortSHA(h))
			merged = append(merged, branch)
		}
		cleanup()
	}

	// Resolve JIRA
	if flagResolveJira {
		if err := doResolveJira(title, merged); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: JIRA resolution failed: %v\n", err)
		}
	}
	return nil
}

func doResolveJira(title string, merged []string) error {
	if flagJiraToken == "" {
		return fmt.Errorf("JIRA_ACCESS_TOKEN is not set")
	}
	ids := jiraIDRe.FindAllString(title, -1)
	if len(ids) == 0 {
		fmt.Println("No JIRA ID found in PR title, skipping.")
		return nil
	}

	versions, err := jiraUnreleasedVersions()
	if err != nil {
		return err
	}

	vm := make(map[string]jiraVersion)
	for _, v := range versions {
		vm[v.Name] = v
	}
	// Start with explicitly specified fix versions
	var fixVer []jiraVersion
	has := make(map[string]bool)
	for _, fv := range flagFixVersions {
		v, ok := vm[fv]
		if !ok {
			return fmt.Errorf("fix version %q not found", fv)
		}
		fixVer = append(fixVer, v)
		has[v.Name] = true
	}
	// Auto-infer: master → latest version only when no --fix-versions given;
	// release branches → matching version always
	if len(versions) > 0 {
		inferMaster := len(flagFixVersions) == 0
		for _, iv := range inferFixVersions(merged, versions, inferMaster) {
			if !has[iv.Name] {
				fixVer = append(fixVer, iv)
				has[iv.Name] = true
			}
		}
	}

	for _, id := range ids {
		issue, err := jiraGetIssue(id)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Warning: get %s: %v\n", id, err)
			continue
		}
		st := issue.Fields.Status.Name
		if st == "Resolved" || st == "Closed" {
			fmt.Printf("JIRA %s already %q, skipping.\n", id, st)
			continue
		}

		fmt.Printf("=== JIRA %s ===\n", issue.Key)
		fmt.Printf("Summary:  %s\n", issue.Fields.Summary)
		fmt.Printf("Status:   %s\n", st)
		if issue.Fields.Assignee != nil {
			fmt.Printf("Assignee: %s\n", issue.Fields.Assignee.DisplayName)
		}

		ts, err := jiraTransitions(id)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Warning: transitions %s: %v\n", id, err)
			continue
		}
		var rid string
		for _, t := range ts {
			if t.Name == "Resolve Issue" {
				rid = t.ID
				break
			}
		}
		if rid == "" {
			fmt.Fprintf(os.Stderr, "Warning: no 'Resolve Issue' transition for %s\n", id)
			continue
		}

		comment := fmt.Sprintf("Issue resolved by pull request %d\n[https://github.com/apache/zeppelin/pull/%d]", flagPR, flagPR)
		if err := jiraResolve(id, rid, fixVer, comment); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: resolve %s: %v\n", id, err)
			continue
		}
		fmt.Printf("Resolved %s!\n", id)
	}
	return nil
}

// inferFixVersions maps merge branches to JIRA fix versions.
// For "master", picks the latest unreleased version.
// For release branches like "branch-0.12", finds the smallest matching 0.12.x version.
// Then removes redundant X.Y.0 if a previous minor X.(Y-1).0 is also selected.
func inferFixVersions(merged []string, versions []jiraVersion, inferMaster bool) []jiraVersion {
	var names []string
	has := make(map[string]bool)
	for _, branch := range merged {
		if branch == "master" {
			if inferMaster && !has[versions[0].Name] {
				names = append(names, versions[0].Name)
				has[versions[0].Name] = true
			}
		} else {
			// "branch-0.12" → prefix "0.12"
			prefix := strings.TrimPrefix(branch, "branch-")
			// Find all matching versions, pick the smallest (last in desc-sorted list)
			var found []string
			for _, v := range versions {
				if strings.HasPrefix(v.Name, prefix+".") || v.Name == prefix {
					found = append(found, v.Name)
				}
			}
			if len(found) > 0 {
				pick := found[len(found)-1]
				if !has[pick] {
					names = append(names, pick)
					has[pick] = true
				}
			} else {
				fmt.Fprintf(os.Stderr, "Warning: no version found for %s, skipping\n", branch)
			}
		}
	}
	// Remove redundant X.Y.0 when X.(Y-1).0 is also present
	filtered := make([]string, 0, len(names))
	for _, v := range names {
		parts := strings.Split(v, ".")
		if len(parts) == 3 && parts[2] == "0" {
			minor, _ := strconv.Atoi(parts[1])
			if minor > 0 {
				prev := fmt.Sprintf("%s.%d.0", parts[0], minor-1)
				if has[prev] {
					continue
				}
			}
		}
		filtered = append(filtered, v)
	}
	// Map names back to jiraVersion structs
	vm := make(map[string]jiraVersion)
	for _, v := range versions {
		vm[v.Name] = v
	}
	var result []jiraVersion
	for _, name := range filtered {
		if v, ok := vm[name]; ok {
			result = append(result, v)
		}
	}
	if len(result) > 0 {
		fmt.Printf("Auto-inferred fix version(s): %s\n", strings.Join(filtered, ", "))
	}
	return result
}
