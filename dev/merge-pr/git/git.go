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

package git

import (
	"fmt"
	"os/exec"
	"strings"
)

func Run(args ...string) (string, error) {
	cmd := exec.Command("git", args...)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("git %s: %w\n%s", strings.Join(args, " "), err, string(out))
	}
	return strings.TrimSpace(string(out)), nil
}

func CurrentRef() (string, error) {
	ref, err := Run("rev-parse", "--abbrev-ref", "HEAD")
	if err != nil {
		return "", err
	}
	if ref == "HEAD" {
		return Run("rev-parse", "HEAD")
	}
	return ref, nil
}

func Fetch(remote, ref string) error {
	_, err := Run("fetch", remote, ref)
	return err
}

func Checkout(ref string) error {
	_, err := Run("checkout", ref)
	return err
}

func CherryPick(hash string) error {
	_, err := Run("cherry-pick", "-sx", hash)
	return err
}

func CherryPickAbort() error {
	_, err := Run("cherry-pick", "--abort")
	return err
}

func Push(remote, localRef, remoteRef string) error {
	_, err := Run("push", remote, localRef+":"+remoteRef)
	return err
}

func DeleteBranch(branch string) error {
	_, err := Run("branch", "-D", branch)
	return err
}

func Branches() ([]string, error) {
	out, err := Run("branch")
	if err != nil {
		return nil, err
	}
	var branches []string
	for _, line := range strings.Split(out, "\n") {
		branches = append(branches, strings.TrimSpace(strings.TrimPrefix(line, "*")))
	}
	return branches, nil
}

func ConfigGet(key string) (string, error) {
	return Run("config", "--get", key)
}
