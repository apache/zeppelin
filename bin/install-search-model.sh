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

INDEX_PATH="${1:-/tmp/zeppelin-index}"
MODEL_DIR="${INDEX_PATH}/models/${MODEL_NAME}"

mkdir -p "${MODEL_DIR}"

download() {
  local url="$1" dest="$2"
  if [ -f "${dest}" ]; then
    echo "Already exists: ${dest}"
    return
  fi
  echo "Downloading ${url} ..."
  curl -fSL --connect-timeout 30 --max-time 300 -o "${dest}.tmp" "${url}"
  mv "${dest}.tmp" "${dest}"
  echo "Saved: ${dest}"
}

download "${BASE_URL}/onnx/model.onnx" "${MODEL_DIR}/model.onnx"
download "${BASE_URL}/tokenizer.json" "${MODEL_DIR}/tokenizer.json"

echo "Model installed to ${MODEL_DIR}"
