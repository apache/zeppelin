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
import os, sys, getopt, traceback, json, requests, time

author = sys.argv[1]
branch = sys.argv[2]
commit = sys.argv[3]

#check = [5, 60, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300]
check = [5, 10, 10]

def info(msg):
    print("[" + time.strftime("%Y-%m-%d %H:%M:%S") + "] " + msg)


info("Author: " + author + ", branch: " + branch + ", commit: " + commit)


def getBuildStatus(author, branch, commit):
    travisApi = "https://api.travis-ci.org/"

    # get latest 25 builds
    resp = requests.get(url=travisApi + "/repos/" + author + "/zeppelin/builds")
    data = json.loads(resp.text)

    build = None
    for b in data:
        if b["branch"] == branch and b["commit"][:len(commit)] == commit:
            resp = requests.get(url=travisApi + "/repos/" + author + "/zeppelin/builds/" + str(b["id"]))
            build = json.loads(resp.text)
            break

    return build


def printBuildStatus(build):
    failure = 0
    running = 0
    for index, job in enumerate(build["matrix"]):
        result = job["result"]
        if job["started_at"] == None and result == None:
            print("[" + str(index+1) + "] Not started")
            running = running + 1
        elif job["started_at"] != None and job["finished_at"] == None:
            print("[" + str(index+1) + "] Running ...")
            running = running + 1
        elif job["started_at"] != None and job["finished_at"] != None:
            if result == None:
                print("[" + str(index+1) + "] Not completed")
                failure = failure + 1
            elif result == 0:
                print("[" + str(index+1) + "] OK")
            else:
                print("[" + str(index+1) + "] Error " + str(result))
                failure = failure + 1
        else:
            print("[" + str(index+1) + "] Unknown state")
            
    return failure, running



for sleep in check:
    info("--------------------------------")
    time.sleep(sleep);
    info("Get build status ...")
    build = getBuildStatus(author, branch, commit)
    if build == None:
        info("Can't find build for branch=" + branch + ", commit= " + commit)
        sys.exit(1)

    print("https://travis-ci.org/" + author + "/zeppelin/builds/" + str(build["id"]))
    failure, running = printBuildStatus(build)

    print(str(failure) + " job(s) failed, " + str(running) + " job(s) running")

    if failure != 0:
        sys.exit(1)

    if failure == 0 and running == 0:
        info("CI Green!")
        sys.exit(0)

info("Timeout")
sys.exit(1)

