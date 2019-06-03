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

ARG repo
ARG version

FROM ${repo}/zeppelin-interpreter-base:${version}
MAINTAINER Apache Software Foundation <dev@zeppelin.apache.org>

# Must declare it after FROM, because it would be reset if it is declared before FROM
ARG interpreter_name

COPY zeppelin-${VERSION}/interpreter/${interpreter_name} ${Z_HOME}/interpreter/${interpreter_name}
