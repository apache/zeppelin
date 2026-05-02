#!/usr/bin/env bash
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

# Downloads the sentence-transformer model required for semantic search.
# Run this once before starting Zeppelin with zeppelin.search.semantic.enable=true.
#
# Usage: bin/install-search-model.sh [INDEX_PATH]
#   INDEX_PATH defaults to /tmp/zeppelin-index (matches zeppelin.search.index.path)

set -euo pipefail

MODEL_NAME="all-MiniLM-L6-v2"
MODEL_REVISION="c9745ed1d9f207416be6d2e6f8de32d1f16199bf"
BASE_URL="https://huggingface.co/sentence-transformers/${MODEL_NAME}/resolve/${MODEL_REVISION}"

# Expected SHA256 checksums for integrity verification
MODEL_SHA256="6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452"
TOKENIZER_SHA256="be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037"

INDEX_PATH="${1:-/tmp/zeppelin-index}"
MODEL_DIR="${INDEX_PATH}/models/${MODEL_NAME}"

mkdir -p "${MODEL_DIR}"

verify_sha256() {
  local file="$1" expected="$2"
  local actual
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "${file}" | cut -d' ' -f1)
  elif command -v shasum >/dev/null 2>&1; then
    actual=$(shasum -a 256 "${file}" | cut -d' ' -f1)
  else
    echo "WARNING: Neither sha256sum nor shasum found, skipping integrity check for ${file}"
    return 0
  fi
  if [ "${actual}" != "${expected}" ]; then
    echo "ERROR: SHA256 mismatch for ${file}"
    echo "  Expected: ${expected}"
    echo "  Actual:   ${actual}"
    rm -f "${file}"
    return 1
  fi
  echo "SHA256 verified: ${file}"
}

download() {
  local url="$1" dest="$2" expected_sha="$3"
  if [ -f "${dest}" ]; then
    if verify_sha256 "${dest}" "${expected_sha}"; then
      echo "Already exists and verified: ${dest}"
      return
    fi
    echo "Existing file failed verification, re-downloading..."
  fi
  echo "Downloading ${url} ..."
  curl -fSL --connect-timeout 30 --max-time 300 -o "${dest}.tmp" "${url}"
  mv "${dest}.tmp" "${dest}"
  verify_sha256 "${dest}" "${expected_sha}"
  echo "Saved: ${dest}"
}

download "${BASE_URL}/onnx/model.onnx" "${MODEL_DIR}/model.onnx" "${MODEL_SHA256}"
download "${BASE_URL}/tokenizer.json" "${MODEL_DIR}/tokenizer.json" "${TOKENIZER_SHA256}"

echo "Model installed to ${MODEL_DIR}"
