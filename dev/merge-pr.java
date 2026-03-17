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

// merge-pr.java merges Apache Zeppelin pull requests via the GitHub API,
// optionally cherry-picks into release branches, and resolves JIRA issues.
//
// Usage:
//   java dev/merge-pr.java --pr 5167 --dry-run
//   java dev/merge-pr.java --pr 5167 --resolve-jira --fix-versions 0.13.0
//   java dev/merge-pr.java --pr 5167 --resolve-jira --release-branches branch-0.12,branch-0.11

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single-file Java CLI for merging Apache Zeppelin pull requests.
 * Requires Java 11+. No external dependencies.
 * Run with: java dev/merge-pr.java --pr NUMBER [flags]
 */
public class MergePr {

    static final String GITHUB_API_BASE = "https://api.github.com/repos/apache/zeppelin";
    static final String JIRA_API_BASE = "https://issues.apache.org/jira/rest/api/2";
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static final Pattern JIRA_ID_RE = Pattern.compile("ZEPPELIN-\\d{3,6}");
    static final Pattern TITLE_FORMATTED_RE = Pattern.compile("^\\[ZEPPELIN-\\d{3,6}](\\[[A-Z0-9_\\s,]+] )+\\S+");
    static final Pattern TITLE_REF_RE = Pattern.compile("(?i)(ZEPPELIN[-\\s]*\\d{3,6})");
    static final Pattern COMPONENT_RE = Pattern.compile("(?i)(\\[[\\w\\s,.\\-]+])");
    static final Pattern WHITESPACE_RE = Pattern.compile("\\s+");
    static final Pattern LEADING_NON_WORD_RE = Pattern.compile("^\\W+");
    static final Pattern SEMANTIC_VER_RE = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    // ── Flags ──────────────────────────────────────────────────────────────

    static int flagPR;
    static String flagTarget = "";
    static List<String> flagFixVersions = new ArrayList<>();
    static List<String> flagReleaseBranches = new ArrayList<>();
    static boolean flagResolveJira;
    static boolean flagDryRun;
    static String flagPushRemote;
    static String flagGithubToken;
    static String flagJiraToken;

    static void parseArgs(String[] args) {
        flagPushRemote = envOrDefault("PUSH_REMOTE_NAME", "apache");
        flagGithubToken = envOrDefault("GITHUB_OAUTH_KEY", "");
        flagJiraToken = envOrDefault("JIRA_ACCESS_TOKEN", "");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pr": flagPR = Integer.parseInt(args[++i]); break;
                case "--target": flagTarget = args[++i]; break;
                case "--fix-versions": flagFixVersions = parseCsv(args[++i]); break;
                case "--release-branches": flagReleaseBranches = parseCsv(args[++i]); break;
                case "--resolve-jira": flagResolveJira = true; break;
                case "--dry-run": flagDryRun = true; break;
                case "--push-remote": flagPushRemote = args[++i]; break;
                case "--github-token": flagGithubToken = args[++i]; break;
                case "--jira-token": flagJiraToken = args[++i]; break;
                case "--help": case "-h": printUsage(); System.exit(0); break;
                default:
                    System.err.println("Unknown flag: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }
    }

    static void printUsage() {
        System.err.println("Usage: java dev/merge-pr.java [flags]");
        System.err.println("  --pr int              Pull request number (required)");
        System.err.println("  --target string        Target branch (default: PR base branch)");
        System.err.println("  --fix-versions value   JIRA fix version(s), comma-separated");
        System.err.println("  --release-branches value  Release branch(es) to cherry-pick into, comma-separated");
        System.err.println("  --resolve-jira         Resolve associated JIRA issue(s)");
        System.err.println("  --dry-run              Show what would be done without making changes");
        System.err.println("  --push-remote string   Git remote for pushing (default: apache)");
        System.err.println("  --github-token string  GitHub OAuth token (env: GITHUB_OAUTH_KEY)");
        System.err.println("  --jira-token string    JIRA access token (env: JIRA_ACCESS_TOKEN)");
    }

    static List<String> parseCsv(String value) {
        List<String> result = new ArrayList<>();
        for (String s : value.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : def;
    }

    // ── Git ────────────────────────────────────────────────────────────────

    static String gitRun(String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            output = r.lines().collect(Collectors.joining("\n")).trim();
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("git " + String.join(" ", args) + " failed:\n" + output);
        }
        return output;
    }

    static String gitCurrentRef() throws Exception {
        String ref = gitRun("rev-parse", "--abbrev-ref", "HEAD");
        return "HEAD".equals(ref) ? gitRun("rev-parse", "HEAD") : ref;
    }

    // ── HTTP ───────────────────────────────────────────────────────────────

    static HttpResponse<String> httpDo(String method, String url, String body, String auth)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (auth != null && !auth.isEmpty()) {
            builder.header("Authorization", auth);
        }
        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ── Simple JSON parser (no external deps) ──────────────────────────────

    // Minimal JSON helpers — we only need to read specific fields from GitHub/JIRA responses.
    // For writing, we build JSON strings directly.

    static String jsonStr(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*?)\"";
        Matcher m = Pattern.compile(pattern).matcher(json);
        return m.find() ? m.group(1) : "";
    }

    static boolean jsonBool(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        Matcher m = Pattern.compile(pattern).matcher(json);
        return m.find() && "true".equals(m.group(1));
    }

    static String jsonObj(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\\{";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (!m.find()) return "{}";
        int start = m.end() - 1;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') { depth--; if (depth == 0) return json.substring(start, i + 1); }
        }
        return "{}";
    }

    static List<String> jsonArray(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\\[";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (!m.find()) return List.of();
        int start = m.end() - 1;
        int depth = 0;
        int arrEnd = json.length();
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') { depth--; if (depth == 0) { arrEnd = i + 1; break; } }
        }
        String arr = json.substring(start, arrEnd);
        // Split top-level objects
        List<String> items = new ArrayList<>();
        depth = 0;
        int itemStart = -1;
        for (int i = 1; i < arr.length() - 1; i++) {
            char c = arr.charAt(i);
            if (c == '{' && depth == 0) itemStart = i;
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (c == '}' && depth == 0 && itemStart >= 0) {
                items.add(arr.substring(itemStart, i + 1));
                itemStart = -1;
            }
        }
        return items;
    }

    // ── GitHub ──────────────────────────────────────────────────────────────

    static String ghAuth() {
        return flagGithubToken.isEmpty() ? "" : "token " + flagGithubToken;
    }

    static String ghGetPR(int num) throws Exception {
        HttpResponse<String> r = httpDo("GET", GITHUB_API_BASE + "/pulls/" + num, null, ghAuth());
        if (r.statusCode() != 200) throw new RuntimeException("GET PR #" + num + ": HTTP " + r.statusCode());
        return r.body();
    }

    static String ghMergePR(int num, String title, String msg) throws Exception {
        String body = String.format("{\"commit_title\":%s,\"commit_message\":%s,\"merge_method\":\"squash\"}",
                jsonEscape(title), jsonEscape(msg));
        HttpResponse<String> r = httpDo("PUT", GITHUB_API_BASE + "/pulls/" + num + "/merge", body, ghAuth());
        if (r.statusCode() == 405) throw new RuntimeException("Merge PR #" + num + " is not allowed");
        if (r.statusCode() != 200) throw new RuntimeException("Merge PR #" + num + ": HTTP " + r.statusCode());
        return r.body();
    }

    static void ghCommentPR(int num, String comment) throws Exception {
        String body = String.format("{\"body\":%s}", jsonEscape(comment));
        HttpResponse<String> r = httpDo("POST", GITHUB_API_BASE + "/issues/" + num + "/comments", body, ghAuth());
        if (r.statusCode() != 201) {
            System.err.println("Warning: comment PR #" + num + ": HTTP " + r.statusCode());
        }
    }

    static String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    // ── JIRA ───────────────────────────────────────────────────────────────

    static String jiraAuth() {
        return flagJiraToken.isEmpty() ? "" : "Bearer " + flagJiraToken;
    }

    static String jiraGetIssue(String key) throws Exception {
        HttpResponse<String> r = httpDo("GET", JIRA_API_BASE + "/issue/" + key, null, jiraAuth());
        if (r.statusCode() != 200) throw new RuntimeException("GET " + key + ": HTTP " + r.statusCode());
        return r.body();
    }

    static List<Map<String, String>> jiraUnreleasedVersions() throws Exception {
        HttpResponse<String> r = httpDo("GET", JIRA_API_BASE + "/project/ZEPPELIN/versions", null, jiraAuth());
        if (r.statusCode() != 200) throw new RuntimeException("GET versions: HTTP " + r.statusCode());
        List<String> all = jsonArray(r.body(), "dummy"); // won't work — top level is array
        // Parse top-level array manually
        String body = r.body().trim();
        all = new ArrayList<>();
        int depth = 0; int start = -1;
        for (int i = 1; i < body.length() - 1; i++) {
            if (body.charAt(i) == '{' && depth == 0) start = i;
            if (body.charAt(i) == '{') depth++;
            if (body.charAt(i) == '}') depth--;
            if (body.charAt(i) == '}' && depth == 0 && start >= 0) {
                all.add(body.substring(start, i + 1));
                start = -1;
            }
        }
        List<Map<String, String>> versions = new ArrayList<>();
        for (String v : all) {
            String name = jsonStr(v, "name");
            boolean released = jsonBool(v, "released");
            boolean archived = jsonBool(v, "archived");
            if (!released && !archived && SEMANTIC_VER_RE.matcher(name).matches()) {
                Map<String, String> ver = new HashMap<>();
                ver.put("id", jsonStr(v, "id"));
                ver.put("name", name);
                versions.add(ver);
            }
        }
        versions.sort((a, b) -> cmpVer(b.get("name"), a.get("name")));
        return versions;
    }

    static List<Map<String, String>> jiraTransitions(String key) throws Exception {
        HttpResponse<String> r = httpDo("GET", JIRA_API_BASE + "/issue/" + key + "/transitions", null, jiraAuth());
        if (r.statusCode() != 200) throw new RuntimeException("GET transitions " + key + ": HTTP " + r.statusCode());
        List<Map<String, String>> result = new ArrayList<>();
        for (String t : jsonArray(r.body(), "transitions")) {
            Map<String, String> tr = new HashMap<>();
            tr.put("id", jsonStr(t, "id"));
            tr.put("name", jsonStr(t, "name"));
            result.add(tr);
        }
        return result;
    }

    static void jiraResolve(String key, String transitionId, List<Map<String, String>> fixVersions, String comment)
            throws Exception {
        StringBuilder fvJson = new StringBuilder("[");
        for (int i = 0; i < fixVersions.size(); i++) {
            if (i > 0) fvJson.append(",");
            fvJson.append(String.format("{\"add\":{\"id\":\"%s\",\"name\":\"%s\"}}",
                    fixVersions.get(i).get("id"), fixVersions.get(i).get("name")));
        }
        fvJson.append("]");
        String body = String.format(
                "{\"transition\":{\"id\":\"%s\"},\"update\":{\"comment\":[{\"add\":{\"body\":%s}}],\"fixVersions\":%s}}",
                transitionId, jsonEscape(comment), fvJson);
        HttpResponse<String> r = httpDo("POST", JIRA_API_BASE + "/issue/" + key + "/transitions", body, jiraAuth());
        if (r.statusCode() != 204) throw new RuntimeException("Resolve " + key + ": HTTP " + r.statusCode());
    }

    static int cmpVer(String a, String b) {
        String[] ap = a.split("\\."), bp = b.split("\\.");
        for (int i = 0; i < Math.min(ap.length, bp.length); i++) {
            int d = Integer.parseInt(ap[i]) - Integer.parseInt(bp[i]);
            if (d != 0) return d;
        }
        return ap.length - bp.length;
    }

    // ── Title normalization ────────────────────────────────────────────────

    static String standardizeTitle(String text) {
        text = text.replaceAll("\\.+$", "");
        if (text.startsWith("Revert \"") && text.endsWith("\"")) return text;
        if (TITLE_FORMATTED_RE.matcher(text).find()) return text;

        List<String> jiraRefs = new ArrayList<>();
        Matcher refMatcher = TITLE_REF_RE.matcher(text);
        while (refMatcher.find()) {
            String ref = refMatcher.group(1);
            jiraRefs.add("[" + WHITESPACE_RE.matcher(ref.toUpperCase()).replaceAll("-") + "]");
            text = text.replace(ref, "");
        }
        List<String> components = new ArrayList<>();
        Matcher compMatcher = COMPONENT_RE.matcher(text);
        while (compMatcher.find()) {
            String comp = compMatcher.group(1);
            components.add(comp.toUpperCase());
            text = text.replace(comp, "");
        }
        text = LEADING_NON_WORD_RE.matcher(text).replaceAll("");
        String result = String.join("", jiraRefs) + String.join("", components) + " " + text;
        return WHITESPACE_RE.matcher(result.trim()).replaceAll(" ");
    }

    static String shortSHA(String sha) {
        return sha.length() > 8 ? sha.substring(0, 8) : sha;
    }

    // ── Fix version inference ──────────────────────────────────────────────

    static List<Map<String, String>> inferFixVersions(List<String> merged,
            List<Map<String, String>> versions, boolean inferMaster) {
        List<String> names = new ArrayList<>();
        LinkedHashSet<String> has = new LinkedHashSet<>();
        for (String branch : merged) {
            if ("master".equals(branch)) {
                if (inferMaster && !has.contains(versions.get(0).get("name"))) {
                    String name = versions.get(0).get("name");
                    names.add(name);
                    has.add(name);
                }
            } else {
                String prefix = branch.startsWith("branch-") ? branch.substring(7) : branch;
                List<String> found = new ArrayList<>();
                for (Map<String, String> v : versions) {
                    String vn = v.get("name");
                    if (vn.startsWith(prefix + ".") || vn.equals(prefix)) found.add(vn);
                }
                if (!found.isEmpty()) {
                    String pick = found.get(found.size() - 1);
                    if (!has.contains(pick)) { names.add(pick); has.add(pick); }
                } else {
                    System.err.println("Warning: no version found for " + branch + ", skipping");
                }
            }
        }
        // Remove redundant X.Y.0 when X.(Y-1).0 is also present
        List<String> filtered = new ArrayList<>();
        for (String v : names) {
            String[] parts = v.split("\\.");
            if (parts.length == 3 && "0".equals(parts[2])) {
                int minor = Integer.parseInt(parts[1]);
                if (minor > 0 && has.contains(parts[0] + "." + (minor - 1) + ".0")) continue;
            }
            filtered.add(v);
        }
        Map<String, Map<String, String>> vm = new HashMap<>();
        for (Map<String, String> v : versions) vm.put(v.get("name"), v);
        List<Map<String, String>> result = new ArrayList<>();
        for (String name : filtered) {
            if (vm.containsKey(name)) result.add(vm.get(name));
        }
        if (!result.isEmpty()) {
            System.out.println("Auto-inferred fix version(s): " + String.join(", ", filtered));
        }
        return result;
    }

    // ── Effective command ──────────────────────────────────────────────────

    static void printEffectiveCommand(String target, List<String> fixVersions) {
        List<String> parts = new ArrayList<>();
        parts.add("java dev/merge-pr.java");
        parts.add("--pr " + flagPR);
        if (!target.isEmpty() && !"master".equals(target)) parts.add("--target " + target);
        if (!flagReleaseBranches.isEmpty()) parts.add("--release-branches " + String.join(",", flagReleaseBranches));
        if (flagResolveJira) parts.add("--resolve-jira");
        if (!fixVersions.isEmpty()) parts.add("--fix-versions " + String.join(",", fixVersions));
        if (!"apache".equals(flagPushRemote)) parts.add("--push-remote " + flagPushRemote);
        System.out.println("[dry-run] Effective command:\n  " + String.join(" ", parts));
    }

    // ── Main ───────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        parseArgs(args);
        if (flagPR == 0) {
            System.err.println("Error: --pr is required");
            printUsage();
            System.exit(1);
        }
        run();
    }

    static void run() throws Exception {
        String originalHead = gitCurrentRef();

        String prJson = ghGetPR(flagPR);
        if (!jsonBool(prJson, "mergeable")) {
            throw new RuntimeException("PR #" + flagPR + " is not mergeable");
        }
        String prTitle = jsonStr(prJson, "title");
        if (prTitle.contains("[WIP]")) {
            System.err.println("WARNING: PR title contains [WIP]: " + prTitle);
        }

        String target = flagTarget.isEmpty() ? jsonStr(jsonObj(prJson, "base"), "ref") : flagTarget;
        String title = standardizeTitle(prTitle);
        String headRef = jsonStr(jsonObj(prJson, "head"), "ref");
        String userLogin = jsonStr(jsonObj(prJson, "user"), "login");
        String src = userLogin + "/" + headRef;
        String prBody = jsonStr(prJson, "body");

        System.out.println("=== Pull Request #" + flagPR + " ===");
        System.out.println("title:  " + title);
        System.out.println("source: " + src);
        System.out.println("target: " + target);
        System.out.println("url:    " + jsonStr(prJson, "url"));
        if (!flagReleaseBranches.isEmpty()) {
            System.out.println("release-branches: " + String.join(", ", flagReleaseBranches));
        }

        // Resolve fix versions for effective command display
        List<String> resolvedFixVersions = new ArrayList<>();
        if (flagResolveJira && !flagJiraToken.isEmpty()) {
            List<String> ids = new ArrayList<>();
            Matcher idm = JIRA_ID_RE.matcher(title);
            while (idm.find()) ids.add(idm.group());
            if (!ids.isEmpty()) {
                try {
                    List<Map<String, String>> versions = jiraUnreleasedVersions();
                    if (!versions.isEmpty()) {
                        Map<String, Map<String, String>> vm = new HashMap<>();
                        for (Map<String, String> v : versions) vm.put(v.get("name"), v);
                        LinkedHashSet<String> has = new LinkedHashSet<>();
                        for (String fv : flagFixVersions) {
                            if (vm.containsKey(fv)) { resolvedFixVersions.add(fv); has.add(fv); }
                        }
                        boolean inferMaster = flagFixVersions.isEmpty();
                        List<String> branches = new ArrayList<>();
                        branches.add(target);
                        branches.addAll(flagReleaseBranches);
                        for (Map<String, String> iv : inferFixVersions(branches, versions, inferMaster)) {
                            if (!has.contains(iv.get("name"))) {
                                resolvedFixVersions.add(iv.get("name"));
                                has.add(iv.get("name"));
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: failed to fetch JIRA versions: " + e.getMessage());
                }
            }
        }

        if (flagDryRun) {
            System.out.println();
            printEffectiveCommand(target, resolvedFixVersions);
            return;
        }

        // Merge
        String body = prBody.replace("@", "<at>");
        String name = "", email = "";
        try { name = gitRun("config", "--get", "user.name"); } catch (Exception ignored) {}
        try { email = gitRun("config", "--get", "user.email"); } catch (Exception ignored) {}
        String msg = body + "\n\nCloses #" + flagPR + " from " + src + ".\n\nSigned-off-by: " + name + " <" + email + ">";

        String mergeJson = ghMergePR(flagPR, title, msg);
        String sha = jsonStr(mergeJson, "sha");
        System.out.println("\nPR #" + flagPR + " merged! (hash: " + shortSHA(sha) + ")");

        try { gitRun("fetch", flagPushRemote, target); } catch (Exception ignored) {}

        // Cherry-pick into release branches
        List<String> merged = new ArrayList<>();
        merged.add(target);
        for (String branch : flagReleaseBranches) {
            String pick = "PR_TOOL_PICK_PR_" + flagPR + "_" + branch.toUpperCase();
            try {
                gitRun("fetch", flagPushRemote, branch + ":" + pick);
            } catch (Exception e) {
                System.err.println("Warning: fetch " + branch + " failed: " + e.getMessage());
                continue;
            }
            gitRun("checkout", pick);
            try {
                gitRun("cherry-pick", "-sx", sha);
            } catch (Exception e) {
                System.err.println("Warning: cherry-pick into " + branch + " failed: " + e.getMessage());
                try { gitRun("cherry-pick", "--abort"); } catch (Exception ignored) {}
                gitRun("checkout", originalHead);
                gitRun("branch", "-D", pick);
                continue;
            }
            try {
                gitRun("push", flagPushRemote, pick + ":" + branch);
                String h = gitRun("rev-parse", pick);
                System.out.println("Picked into " + branch + " (hash: " + shortSHA(h) + ")");
                merged.add(branch);
            } catch (Exception e) {
                System.err.println("Warning: push to " + branch + " failed: " + e.getMessage());
            }
            gitRun("checkout", originalHead);
            gitRun("branch", "-D", pick);
        }

        // Comment on PR
        StringBuilder comment = new StringBuilder();
        comment.append("Merged into ").append(target).append(" (").append(shortSHA(sha)).append(").");
        for (int i = 1; i < merged.size(); i++) {
            comment.append("\nCherry-picked into ").append(merged.get(i)).append(".");
        }
        try {
            ghCommentPR(flagPR, comment.toString());
            System.out.println("Commented on PR with merge summary.");
        } catch (Exception e) {
            System.err.println("Warning: failed to comment on PR: " + e.getMessage());
        }

        // Resolve JIRA
        if (flagResolveJira) {
            try {
                doResolveJira(title, merged);
            } catch (Exception e) {
                System.err.println("Warning: JIRA resolution failed: " + e.getMessage());
            }
        }
    }

    static void doResolveJira(String title, List<String> merged) throws Exception {
        if (flagJiraToken.isEmpty()) throw new RuntimeException("JIRA_ACCESS_TOKEN is not set");

        List<String> ids = new ArrayList<>();
        Matcher m = JIRA_ID_RE.matcher(title);
        while (m.find()) ids.add(m.group());
        if (ids.isEmpty()) { System.out.println("No JIRA ID found in PR title, skipping."); return; }

        List<Map<String, String>> versions = jiraUnreleasedVersions();

        Map<String, Map<String, String>> vm = new HashMap<>();
        for (Map<String, String> v : versions) vm.put(v.get("name"), v);

        List<Map<String, String>> fixVer = new ArrayList<>();
        LinkedHashSet<String> has = new LinkedHashSet<>();
        for (String fv : flagFixVersions) {
            if (!vm.containsKey(fv)) throw new RuntimeException("fix version \"" + fv + "\" not found");
            fixVer.add(vm.get(fv));
            has.add(fv);
        }
        if (!versions.isEmpty()) {
            boolean inferMaster = flagFixVersions.isEmpty();
            for (Map<String, String> iv : inferFixVersions(merged, versions, inferMaster)) {
                if (!has.contains(iv.get("name"))) {
                    fixVer.add(iv);
                    has.add(iv.get("name"));
                }
            }
        }

        for (String id : ids) {
            String issueJson;
            try { issueJson = jiraGetIssue(id); } catch (Exception e) {
                System.err.println("Warning: get " + id + ": " + e.getMessage()); continue;
            }
            String status = jsonStr(jsonObj(jsonObj(issueJson, "fields"), "status"), "name");
            if ("Resolved".equals(status) || "Closed".equals(status)) {
                System.out.println("JIRA " + id + " already \"" + status + "\", skipping.");
                continue;
            }

            System.out.println("=== JIRA " + id + " ===");
            System.out.println("Summary:  " + jsonStr(jsonObj(issueJson, "fields"), "summary"));
            System.out.println("Status:   " + status);

            List<Map<String, String>> transitions = jiraTransitions(id);
            String resolveId = null;
            for (Map<String, String> t : transitions) {
                if ("Resolve Issue".equals(t.get("name"))) { resolveId = t.get("id"); break; }
            }
            if (resolveId == null) {
                System.err.println("Warning: no 'Resolve Issue' transition for " + id);
                continue;
            }

            String jiraComment = "Issue resolved by pull request " + flagPR
                    + "\n[https://github.com/apache/zeppelin/pull/" + flagPR + "]";
            try {
                jiraResolve(id, resolveId, fixVer, jiraComment);
                System.out.println("Resolved " + id + "!");
            } catch (Exception e) {
                System.err.println("Warning: resolve " + id + ": " + e.getMessage());
            }
        }
    }
}
