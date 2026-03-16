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

package jira

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

const apiBase = "https://issues.apache.org/jira/rest/api/2"

type Client struct {
	token      string
	httpClient *http.Client
}

func NewClient(token string) *Client {
	return &Client{
		token:      token,
		httpClient: &http.Client{},
	}
}

type Issue struct {
	Key    string `json:"key"`
	Fields struct {
		Summary  string `json:"summary"`
		Status   Status `json:"status"`
		Assignee *User  `json:"assignee"`
	} `json:"fields"`
}

type Status struct {
	Name string `json:"name"`
}

type User struct {
	DisplayName string `json:"displayName"`
	Name        string `json:"name"`
}

type Version struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Released bool   `json:"released"`
	Archived bool   `json:"archived"`
}

type Transition struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

func (c *Client) doRequest(method, url string, body interface{}) ([]byte, int, error) {
	var reqBody io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, 0, err
		}
		reqBody = bytes.NewReader(data)
	}

	req, err := http.NewRequest(method, url, reqBody)
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, err
	}

	return data, resp.StatusCode, nil
}

func (c *Client) GetIssue(issueKey string) (*Issue, error) {
	data, status, err := c.doRequest("GET", fmt.Sprintf("%s/issue/%s", apiBase, issueKey), nil)
	if err != nil {
		return nil, err
	}
	if status != 200 {
		return nil, fmt.Errorf("failed to get issue %s: HTTP %d: %s", issueKey, status, string(data))
	}

	var issue Issue
	if err := json.Unmarshal(data, &issue); err != nil {
		return nil, err
	}
	return &issue, nil
}

func (c *Client) GetProjectVersions(project string) ([]Version, error) {
	data, status, err := c.doRequest("GET", fmt.Sprintf("%s/project/%s/versions", apiBase, project), nil)
	if err != nil {
		return nil, err
	}
	if status != 200 {
		return nil, fmt.Errorf("failed to get versions: HTTP %d: %s", status, string(data))
	}

	var versions []Version
	if err := json.Unmarshal(data, &versions); err != nil {
		return nil, err
	}
	return versions, nil
}

func (c *Client) GetUnreleasedVersions(project string) ([]Version, error) {
	versions, err := c.GetProjectVersions(project)
	if err != nil {
		return nil, err
	}

	versionPattern := regexp.MustCompile(`^\d+\.\d+\.\d+$`)
	var filtered []Version
	for _, v := range versions {
		if !v.Released && !v.Archived && versionPattern.MatchString(v.Name) {
			filtered = append(filtered, v)
		}
	}

	sort.Slice(filtered, func(i, j int) bool {
		return compareVersions(filtered[i].Name, filtered[j].Name) > 0
	})

	return filtered, nil
}

func (c *Client) GetTransitions(issueKey string) ([]Transition, error) {
	data, status, err := c.doRequest("GET", fmt.Sprintf("%s/issue/%s/transitions", apiBase, issueKey), nil)
	if err != nil {
		return nil, err
	}
	if status != 200 {
		return nil, fmt.Errorf("failed to get transitions: HTTP %d: %s", status, string(data))
	}

	var result struct {
		Transitions []Transition `json:"transitions"`
	}
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, err
	}
	return result.Transitions, nil
}

func (c *Client) ResolveIssue(issueKey string, transitionID string, fixVersions []Version, comment string) error {
	body := map[string]interface{}{
		"transition": map[string]string{"id": transitionID},
		"update": map[string]interface{}{
			"comment": []map[string]interface{}{
				{"add": map[string]string{"body": comment}},
			},
			"fixVersions": func() []map[string]interface{} {
				var fvs []map[string]interface{}
				for _, v := range fixVersions {
					fvs = append(fvs, map[string]interface{}{"add": map[string]string{"id": v.ID, "name": v.Name}})
				}
				return fvs
			}(),
		},
	}

	_, status, err := c.doRequest("POST", fmt.Sprintf("%s/issue/%s/transitions", apiBase, issueKey), body)
	if err != nil {
		return err
	}
	if status != 204 {
		return fmt.Errorf("failed to resolve issue %s: HTTP %d", issueKey, status)
	}
	return nil
}

func compareVersions(a, b string) int {
	aParts := strings.Split(a, ".")
	bParts := strings.Split(b, ".")
	for i := 0; i < len(aParts) && i < len(bParts); i++ {
		ai, _ := strconv.Atoi(aParts[i])
		bi, _ := strconv.Atoi(bParts[i])
		if ai != bi {
			return ai - bi
		}
	}
	return len(aParts) - len(bParts)
}

// ExtractJIRAIDs extracts ZEPPELIN-XXXX IDs from a string.
func ExtractJIRAIDs(text string) []string {
	pattern := regexp.MustCompile(`ZEPPELIN-\d{3,6}`)
	return pattern.FindAllString(text, -1)
}
