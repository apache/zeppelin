#! /usr/bin/env python

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import sys, argparse
from urllib2 import urlopen
from json import loads


class UsageOnErrorParser(argparse.ArgumentParser):
    def error(self, message):
        sys.stderr.write('argument error: %s\n' % message)
        self.print_help()
        sys.exit(2)

parser = UsageOnErrorParser(description='Print preferred Apache mirror URL.')
parser.add_argument('url', type=str, help='Apache mirror selector url.')

args = parser.parse_args()

jsonurl = args.url + '&asjson=1'

body = urlopen(jsonurl).read().decode('utf-8')
mirrors = loads(body)
print(mirrors['preferred'] + mirrors['path_info'])

