#!/usr/bin/env python
#
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
import sys, os, json, time, shutil
from subprocess import check_output

SERVING_PORT=8090
TMP_FILE_PATH="/tmp/z_api_route_location"

def genLocation(namespace, serviceName, noteId, revId, endpoint):
  location = ""
  # print("serviceName=" + serviceName + ", noteId=" + noteId + ", revId=" + revId + ", endpoint=" + endpoint)
  location += "location /serving/{}/{}/{} {{".format(noteId, revId, endpoint)
  location += "\n  resolver 127.0.0.1:53 ipv6=off;"
  location += "\n  rewrite ^/serving/[^/]*/[^/]*/(.*) /$1 break;"
  location += "\n  proxy_pass http://{}.{}.svc.cluster.local:{};".format(serviceName, namespace, SERVING_PORT)
  location += "\n  proxy_set_header Host $host;"
  location += "\n  proxy_http_version 1.1;"
  location += "\n  proxy_set_header Upgrade $http_upgrade;"
  location += "\n  proxy_set_header Connection $connection_upgrade;"
  location += "\n}\n"
  return location

def main(locationWritePath):
  # get all Services has 'noteId' in label key.
  out = check_output(["kubectl", "get", "services", "-l", "noteId", "-o", "json"])
  services = json.loads(out)

  locations = []

  for service in services['items']:
    labels = service["metadata"]["labels"]
    noteId = labels["noteId"]
    revId = labels["revId"]
    serviceName = service["metadata"]["name"]
    namespace = service["metadata"]["namespace"]

    # iterate all endpoints
    for key in labels:
      if key.startswith("endpoint-"):
        endpoint = labels[key]
        locations.append(genLocation(namespace, serviceName, noteId, revId, endpoint))

  # write generated nginx location block to file
  locationConf = "\n".join(locations)

  locationFile = open(TMP_FILE_PATH, "w")
  locationFile.write(locationConf)
  locationFile.close()

  shutil.copyfile(TMP_FILE_PATH, locationWritePath)

if __name__== "__main__":
  intervalSec = float(sys.argv[1])
  locationWritePath = sys.argv[2]
  while True:
    main(locationWritePath)
    time.sleep(intervalSec)
