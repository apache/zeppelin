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

package cmd

import (
	"fmt"
	"os"
	"regexp"
	"strings"

	"github.com/apache/zeppelin/dev/merge-pr/git"
	"github.com/apache/zeppelin/dev/merge-pr/github"
	"github.com/apache/zeppelin/dev/merge-pr/jira"
	"github.com/spf13/cobra"
)

var (
	prNum        int
	targetRef    string
	fixVersions  []string
	resolveJira  bool
	cherryPick   string
	dryRun       bool
	prRemote     string
	pushRemote   string
	githubToken  string
	jiraToken    string
)

func Execute() error {
	return rootCmd.Execute()
}

var rootCmd = &cobra.Command{
	Use:   "merge-pr",
	Short: "Merge Apache Zeppelin pull requests",
	Long:  "A CLI tool for merging Apache Zeppelin pull requests with squash merge, optional cherry-pick, and JIRA resolution.",
	RunE:  runMerge,
}

func init() {
	rootCmd.Flags().IntVar(&prNum, "pr", 0, "Pull request number (required)")
	rootCmd.Flags().StringVar(&targetRef, "target", "", "Target branch (default: PR base branch)")
	rootCmd.Flags().StringSliceVar(&fixVersions, "fix-version", nil, "JIRA fix version(s)")
	rootCmd.Flags().BoolVar(&resolveJira, "resolve-jira", false, "Resolve associated JIRA issue(s)")
	rootCmd.Flags().StringVar(&cherryPick, "cherry-pick", "", "Cherry-pick merged commit into this branch")
	rootCmd.Flags().BoolVar(&dryRun, "dry-run", false, "Show what would be done without making changes")
	rootCmd.Flags().StringVar(&prRemote, "pr-remote", envOrDefault("PR_REMOTE_NAME", "apache"), "Git remote for pull requests")
	rootCmd.Flags().StringVar(&pushRemote, "push-remote", envOrDefault("PUSH_REMOTE_NAME", "apache"), "Git remote for pushing")
	rootCmd.Flags().StringVar(&githubToken, "github-token", "", "GitHub OAuth token (env: GITHUB_OAUTH_KEY)")
	rootCmd.Flags().StringVar(&jiraToken, "jira-token", "", "JIRA access token (env: JIRA_ACCESS_TOKEN)")

	rootCmd.MarkFlagRequired("pr")
}

func envOrDefault(key, defaultVal string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultVal
}

func runMerge(cmd *cobra.Command, args []string) error {
	// Fall back to environment variables for tokens
	if githubToken == "" {
		githubToken = os.Getenv("GITHUB_OAUTH_KEY")
	}
	if jiraToken == "" {
		jiraToken = os.Getenv("JIRA_ACCESS_TOKEN")
	}

	// Save current HEAD for cleanup
	originalHead, err := git.CurrentRef()
	if err != nil {
		return fmt.Errorf("failed to get current ref: %w", err)
	}

	ghClient := github.NewClient(githubToken)

	// Fetch PR info
	pr, err := ghClient.GetPullRequest(prNum)
	if err != nil {
		return fmt.Errorf("failed to get PR #%d: %w", prNum, err)
	}

	if !pr.Mergeable {
		return fmt.Errorf("PR #%d is not mergeable in its current form", prNum)
	}

	if strings.Contains(pr.Title, "[WIP]") {
		fmt.Fprintf(os.Stderr, "WARNING: PR title contains [WIP]: %s\n", pr.Title)
	}

	// Determine target
	if targetRef == "" {
		targetRef = pr.Base.Ref
	}

	title := standardizeJiraRef(pr.Title)
	prRepoDesc := fmt.Sprintf("%s/%s", pr.User.Login, pr.Head.Ref)

	// Print PR info
	fmt.Printf("=== Pull Request #%d ===\n", prNum)
	fmt.Printf("title:  %s\n", title)
	fmt.Printf("source: %s\n", prRepoDesc)
	fmt.Printf("target: %s\n", targetRef)
	fmt.Printf("url:    %s\n", pr.URL)

	if dryRun {
		fmt.Println("\n[dry-run] Would merge PR and stop here.")
		return nil
	}

	// Build merge message
	body := strings.ReplaceAll(pr.Body, "@", "<at>")

	committerName, _ := git.ConfigGet("user.name")
	committerEmail, _ := git.ConfigGet("user.email")

	message := fmt.Sprintf("%s\n\nCloses #%d from %s.\n\nSigned-off-by: %s <%s>",
		body, prNum, prRepoDesc, committerName, committerEmail)

	// Squash merge via GitHub API
	mergeResp, err := ghClient.MergePullRequest(prNum, title, message)
	if err != nil {
		return fmt.Errorf("failed to merge: %w", err)
	}

	mergeHash := mergeResp.SHA[:8]
	fmt.Printf("\nPull request #%d merged!\n", prNum)
	fmt.Printf("Merge hash: %s\n", mergeHash)

	// Fetch to make merged commit visible
	if err := git.Fetch(pushRemote, targetRef); err != nil {
		fmt.Fprintf(os.Stderr, "Warning: failed to fetch after merge: %v\n", err)
	}

	// Cherry-pick if requested
	mergedRefs := []string{targetRef}
	if cherryPick != "" {
		pickRef, err := doCherryPick(prNum, mergeResp.SHA, cherryPick, originalHead)
		if err != nil {
			return fmt.Errorf("cherry-pick failed: %w", err)
		}
		mergedRefs = append(mergedRefs, pickRef)
	}

	// Resolve JIRA
	if resolveJira {
		if err := doResolveJira(title, mergedRefs); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: JIRA resolution failed: %v\n", err)
		}
	}

	return nil
}

func doCherryPick(prNum int, mergeHash, pickRef, originalHead string) (string, error) {
	branchPrefix := "PR_TOOL"
	pickBranch := fmt.Sprintf("%s_PICK_PR_%d_%s", branchPrefix, prNum, strings.ToUpper(pickRef))

	if _, err := git.Run("fetch", pushRemote, fmt.Sprintf("%s:%s", pickRef, pickBranch)); err != nil {
		return "", fmt.Errorf("failed to fetch %s: %w", pickRef, err)
	}

	if err := git.Checkout(pickBranch); err != nil {
		return "", fmt.Errorf("failed to checkout %s: %w", pickBranch, err)
	}

	if err := git.CherryPick(mergeHash); err != nil {
		git.CherryPickAbort()
		git.Checkout(originalHead)
		git.DeleteBranch(pickBranch)
		return "", fmt.Errorf("cherry-pick of %s failed: %w", mergeHash, err)
	}

	if err := git.Push(pushRemote, pickBranch, pickRef); err != nil {
		git.Checkout(originalHead)
		git.DeleteBranch(pickBranch)
		return "", fmt.Errorf("failed to push cherry-pick: %w", err)
	}

	pickHash, _ := git.Run("rev-parse", pickBranch)
	if len(pickHash) > 8 {
		pickHash = pickHash[:8]
	}

	// Cleanup
	git.Checkout(originalHead)
	git.DeleteBranch(pickBranch)

	fmt.Printf("Pull request #%d picked into %s!\n", prNum, pickRef)
	fmt.Printf("Pick hash: %s\n", pickHash)
	return pickRef, nil
}

func doResolveJira(title string, mergedRefs []string) error {
	if jiraToken == "" {
		return fmt.Errorf("JIRA_ACCESS_TOKEN is not set")
	}

	jiraClient := jira.NewClient(jiraToken)
	jiraIDs := jira.ExtractJIRAIDs(title)
	if len(jiraIDs) == 0 {
		fmt.Println("No JIRA ID found in PR title, skipping JIRA resolution.")
		return nil
	}

	versions, err := jiraClient.GetUnreleasedVersions("ZEPPELIN")
	if err != nil {
		return fmt.Errorf("failed to get versions: %w", err)
	}

	// Determine fix versions
	var resolveVersions []jira.Version
	if len(fixVersions) > 0 {
		versionMap := make(map[string]jira.Version)
		for _, v := range versions {
			versionMap[v.Name] = v
		}
		for _, fv := range fixVersions {
			if v, ok := versionMap[fv]; ok {
				resolveVersions = append(resolveVersions, v)
			} else {
				return fmt.Errorf("fix version %q not found in available versions", fv)
			}
		}
	} else if len(versions) > 0 {
		// Default: use the latest version for master
		for _, ref := range mergedRefs {
			if ref == "master" && len(versions) > 0 {
				resolveVersions = append(resolveVersions, versions[0])
				break
			}
		}
	}

	for _, jiraID := range jiraIDs {
		issue, err := jiraClient.GetIssue(jiraID)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Warning: failed to get JIRA %s: %v\n", jiraID, err)
			continue
		}

		if issue.Fields.Status.Name == "Resolved" || issue.Fields.Status.Name == "Closed" {
			fmt.Printf("JIRA %s already has status %q, skipping.\n", jiraID, issue.Fields.Status.Name)
			continue
		}

		fmt.Printf("=== JIRA %s ===\n", issue.Key)
		fmt.Printf("Summary:  %s\n", issue.Fields.Summary)
		fmt.Printf("Status:   %s\n", issue.Fields.Status.Name)
		if issue.Fields.Assignee != nil {
			fmt.Printf("Assignee: %s\n", issue.Fields.Assignee.DisplayName)
		}

		transitions, err := jiraClient.GetTransitions(jiraID)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Warning: failed to get transitions for %s: %v\n", jiraID, err)
			continue
		}

		var resolveID string
		for _, t := range transitions {
			if t.Name == "Resolve Issue" {
				resolveID = t.ID
				break
			}
		}
		if resolveID == "" {
			fmt.Fprintf(os.Stderr, "Warning: no 'Resolve Issue' transition found for %s\n", jiraID)
			continue
		}

		comment := fmt.Sprintf("Issue resolved by pull request %d\n[https://github.com/apache/zeppelin/pull/%d]", prNum, prNum)

		if err := jiraClient.ResolveIssue(jiraID, resolveID, resolveVersions, comment); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: failed to resolve %s: %v\n", jiraID, err)
			continue
		}

		fmt.Printf("Successfully resolved %s!\n", jiraID)
	}

	return nil
}

// standardizeJiraRef normalizes PR titles to [ZEPPELIN-XXXX] format.
func standardizeJiraRef(text string) string {
	text = strings.TrimRight(text, ".")

	if strings.HasPrefix(text, `Revert "`) && strings.HasSuffix(text, `"`) {
		return text
	}

	if matched, _ := regexp.MatchString(`^\[ZEPPELIN-\d{3,6}\]`, text); matched {
		return text
	}

	pattern := regexp.MustCompile(`(?i)(ZEPPELIN[-\s]*\d{3,6})`)
	refs := pattern.FindAllString(text, -1)
	for _, ref := range refs {
		text = strings.Replace(text, ref, "", 1)
		ref = strings.ToUpper(ref)
		ref = regexp.MustCompile(`\s+`).ReplaceAllString(ref, "-")
		text = "[" + ref + "]" + text
	}

	text = regexp.MustCompile(`^\W+`).ReplaceAllString(text, "")
	text = regexp.MustCompile(`\s+`).ReplaceAllString(strings.TrimSpace(text), " ")

	return text
}
