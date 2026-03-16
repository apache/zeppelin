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

package github

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

const apiBase = "https://api.github.com/repos/apache/zeppelin"

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

type PullRequest struct {
	URL       string `json:"url"`
	Title     string `json:"title"`
	Body      string `json:"body"`
	Mergeable bool   `json:"mergeable"`
	Base      struct {
		Ref string `json:"ref"`
	} `json:"base"`
	Head struct {
		Ref string `json:"ref"`
	} `json:"head"`
	User struct {
		Login string `json:"login"`
	} `json:"user"`
}

type Branch struct {
	Name string `json:"name"`
}

type MergeResponse struct {
	SHA     string `json:"sha"`
	Message string `json:"message"`
	Merged  bool   `json:"merged"`
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
	if c.token != "" {
		req.Header.Set("Authorization", "token "+c.token)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/vnd.github.v3+json")

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

func (c *Client) GetPullRequest(prNum int) (*PullRequest, error) {
	data, status, err := c.doRequest("GET", fmt.Sprintf("%s/pulls/%d", apiBase, prNum), nil)
	if err != nil {
		return nil, err
	}
	if status != 200 {
		return nil, fmt.Errorf("failed to get PR #%d: HTTP %d: %s", prNum, status, string(data))
	}

	var pr PullRequest
	if err := json.Unmarshal(data, &pr); err != nil {
		return nil, err
	}
	return &pr, nil
}

func (c *Client) GetBranches() ([]Branch, error) {
	data, status, err := c.doRequest("GET", fmt.Sprintf("%s/branches", apiBase), nil)
	if err != nil {
		return nil, err
	}
	if status != 200 {
		return nil, fmt.Errorf("failed to get branches: HTTP %d: %s", status, string(data))
	}

	var branches []Branch
	if err := json.Unmarshal(data, &branches); err != nil {
		return nil, err
	}
	return branches, nil
}

func (c *Client) MergePullRequest(prNum int, title, message string) (*MergeResponse, error) {
	body := map[string]string{
		"commit_title":   title,
		"commit_message": message,
		"merge_method":   "squash",
	}

	data, status, err := c.doRequest("PUT", fmt.Sprintf("%s/pulls/%d/merge", apiBase, prNum), body)
	if err != nil {
		return nil, err
	}
	if status == 405 {
		return nil, fmt.Errorf("merge pull request #%d is not allowed", prNum)
	}
	if status != 200 {
		return nil, fmt.Errorf("failed to merge PR #%d: HTTP %d: %s", prNum, status, string(data))
	}

	var resp MergeResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}
