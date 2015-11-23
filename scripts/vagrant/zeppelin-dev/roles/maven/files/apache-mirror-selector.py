#! /usr/bin/env python

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

