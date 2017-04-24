# /**
#  * Licensed to the Apache Software Foundation (ASF) under one
#  * or more contributor license agreements.  See the NOTICE file
#  * distributed with this work for additional information
#  * regarding copyright ownership.  The ASF licenses this file
#  * to you under the Apache License, Version 2.0 (the
#  * "License"); you may not use this file except in compliance
#  * with the License.  You may obtain a copy of the License at
#  *
#  *     http://www.apache.org/licenses/LICENSE-2.0
#  *
#  * Unless required by applicable law or agreed to in writing, software
#  * distributed under the License is distributed on an "AS IS" BASIS,
#  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  * See the License for the specific language governing permissions and
#  * limitations under the License.
#  */

import argparse
import json

from os.path import isfile
from os import getcwd

from subprocess import call, check_call


#######################################################################################################################
# I put these here so it will (hopeully) be easy(er) to bump versions / maintain
# If there is demand, we could easily make parts or all comand line arguments as well
#######################################################################################################################
tar_name = "apache-mahout-distribution-0.12.2.tar.gz"
mahout_bin_url =  "http://apache.osuosl.org/mahout/0.12.2/%s" % tar_name
mahout_version = "0.12.2"

parser = argparse.ArgumentParser()

parser.add_argument("--force_download", help="force download Apache Mahout", action="store_true")
parser.add_argument("--restart_later", help="force download Apache Mahout", action="store_true")
parser.add_argument("--zeppelin_home", help="path to ZEPPELIN_HOME")
parser.add_argument("--mahout_home", help="path to MAHOUT_HOME, use this if you have already installed Apache Mahout")
parser.add_argument("--overwrite_existing", help="if %sparkMahout or %flinkMahout exist, delete them and create new ones. Otherwise Fail.", action="store_true")

args = parser.parse_args()



class ZeppelinTerpWrangler:
    def __init__(self, interpreter_json_path):
        self.interpreter_json_path = interpreter_json_path

    def _getTerpID(self, terpName):
        terp_id = None
        for k, v in self.interpreter_json['interpreterSettings'].iteritems():
            if v['name'] == terpName:
                terp_id = k
                break

        return terp_id

    def _terpExists(self, terpName):
        terp_id = self._getTerpID(terpName)
        if terp_id == None:
            return False
        return True

    def createTerp(self, original_terp_name, new_terp_name, overwrite_existing=True ):

        new_terp_id = new_terp_name
        if self._terpExists(new_terp_name):
            print "Found existing '%s' interpreter..." % new_terp_name
            if overwrite_existing:
                print "deleting %s from interpreter.json" %new_terp_name
                del self.interpreter_json['interpreterSettings'][self._getTerpID(new_terp_name)]
            else:
                print "exiting program."
                exit(1)

        orig_terp_id = self._getTerpID(original_terp_name)

        from copy import deepcopy
        self.interpreter_json['interpreterSettings'][new_terp_id] = deepcopy(
        self.interpreter_json['interpreterSettings'][orig_terp_id])
        self.interpreter_json['interpreterSettings'][new_terp_id]['name'] = new_terp_name
        self.interpreter_json['interpreterSettings'][new_terp_id]['id'] = new_terp_id
        print "created new interpreter '%s' from interpreter '%s" % (new_terp_name, original_terp_name)

    def _readTerpJson(self):
        with open(self.interpreter_json_path) as f:
            self.interpreter_json = json.load(f)

    def _writeTerpJson(self):
        with open(self.interpreter_json_path, 'wb') as f:
            json.dump(self.interpreter_json, f, sort_keys=True, indent=4)

    def _updateTerpProp(self, terpName, property, value):
        terp_id = self._getTerpID(terpName)
        self.interpreter_json['interpreterSettings'][terp_id]['properties'][property] = value

    def _addTerpDep(self, terpName="", dep="", exclusions=None):
        if self.interpreter_json == {}:
            print "no interpreter.json loaded, reading last one downloaded"
            self._readTerpJson()
        terp_id = self._getTerpID(terpName)
        deps = self.interpreter_json['interpreterSettings'][terp_id]['dependencies']

        dep_dict = {
            u'groupArtifactVersion': dep,
            u'local': False

        }
        if exclusions != None:
            dep_dict["exclusions"] = exclusions
        deps.append(dep_dict)

        ## Remove Duplicate Dependencies
        seen = set()
        new_deps = list()
        for d in deps:
            t = d.items()
            if t[0] not in seen:
                seen.add(t[0])
                new_deps.append(d)

        self.interpreter_json['interpreterSettings'][terp_id]['dependencies'] = new_deps

    def addMahoutConfig(self, terpName, mahout_home, mahout_version = "0.12.2"):

        print "updating '%s' with Apache Mahout dependencies and settings" % terpName

        terpDeps = ["%s/mahout-math-%s.jar" %  (mahout_home, mahout_version),
                    "%s/mahout-math-scala_2.10-%s.jar" %  (mahout_home, mahout_version)]

        if "spark" in terpName.lower():
            configs = {
                "spark.kryo.referenceTracking": "false",
                "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
                "spark.kryoserializer.buffer": "32k",
                "spark.kryoserializer.buffer.max": "600m",
                "spark.serializer": "org.apache.spark.serializer.KryoSerializer"
            }
            terpDeps.append('%s/mahout-spark_2.10-%s-dependency-reduced.jar' % (mahout_home, mahout_version))
            terpDeps.append("%s/mahout-spark_2.10-%s.jar" % (mahout_home, mahout_version))
            terpDeps.append("%s/mahout-spark-shell_2.10-%s.jar" % (mahout_home, mahout_version))

        if "flink" in terpName.lower():
            configs = {
                "taskmanager.numberOfTaskSlots" : "12"
            }
            addlDeps = [
                "%s/mahout-flink_2.10-%s.jar" % (mahout_home, mahout_version),
                "%s/mahout-hdfs-%s.jar" % (mahout_home, mahout_version),
                "com.google.guava:guava:14.0.1"
                #"%s/guava-14.0.1.jar" % mahout_home  ## reuired in lib dir if running against cluster
            ]
            for t in addlDeps:
                terpDeps.append(t)

        for k, v in configs.iteritems():
            self._updateTerpProp(terpName, k, v)

        for t in terpDeps:
            self._addTerpDep(terpName, t)

#######################################################################################################################
# Need to be sure we know where Zeppelin Top directory is so we can edit conf files
#
#######################################################################################################################

def valid_zeppelin_home(path):
    return isfile(path + "/bin/zeppelin-daemon.sh")

if args.zeppelin_home == None:
    zeppelin_home = getcwd()
    if (zeppelin_home.split("/")[-1] == "bin") and (isfile("zeppelin-daemon.sh")):
        print "we're in the zeppelin/bin"
        zeppelin_home = "/".join(zeppelin_home.split("/")[:-1])
    print "--zeppelin_home not specified, using %s" % zeppelin_home
else:
    zeppelin_home = args.zeppelin_home


if not valid_zeppelin_home(zeppelin_home):
    print "%s does not appear to be a valid ZEPPELIN_HOME - e.g. the top level directory of the ZEPPELIN install" % zeppelin_home
    exit(1)
else:
    print "ZEPPELIN_HOME validated"

interpreter_json_path = zeppelin_home + "/conf/interpreter.json"

if not isfile(interpreter_json_path):
    print "interpreter.json doesn't exist. Checking weather Zeppelin is running."
    status = call(["bin/zeppelin-daemon.sh", 'status'], cwd=zeppelin_home)
    if status == 1:
        print "Zeppelin doesn't appear to be running- it is possible that Zeppelin has never been run (interpreter.json is created when Zeppelin is run)"
        print "I'm going to try to start Zeppelin to create interpreter.json"
        call(["bin/zeppelin-daemon.sh", 'start'], cwd=zeppelin_home)
        from time import sleep
        sleep(3)
    else:
        print "We're in the correct top-level directory, Zeppelin appears to be running, but there is no 'interpreter.json'. \
          \nThis is a confusing case.  Please try restarting Zeppelin, but if that doesn't work reach out on the mailing list."

if isfile(interpreter_json_path):
    z = ZeppelinTerpWrangler(interpreter_json_path)
else:
    print "'interpreter.json' not found in %s/conf" % args.zeppelin_home
    exit(1)

#######################################################################################################################
# If --mahout_home not set, download and untar Mahout in to ZEPPELIN_HOME
# Set MAHOUT_HOME to ZEPPELIN_HOME/<mahout_untar_dir>
#######################################################################################################################

def download_mahout():
    if args.force_download:
        print "--force_download: OK, deleting existing tar if it exists."
        call(["rm", "%s/%s" % (zeppelin_home, tar_name)])
        return True
    elif isfile("%s/%s" % (zeppelin_home, tar_name)):
        print "%s found, skipping download" % tar_name
        return False
    elif args.mahout_home:
        print "--mahout_home set, skipping download"
        return False
    else:
        return True

if download_mahout():
    check_call(['wget', mahout_bin_url], cwd= zeppelin_home)
    check_call(['tar', 'xzf', tar_name], cwd= zeppelin_home)



if args.mahout_home:
    mahout_home = args.mahout_home
else:
  mahout_home = zeppelin_home + "/" + ".".join(tar_name.split(".")[:-2])

#######################################################################################################################
# Create new interpreters
#######################################################################################################################

z._readTerpJson()
z.createTerp("spark", "sparkMahout", args.overwrite_existing)
z.createTerp("flink", "flinkMahout", args.overwrite_existing)
z.addMahoutConfig("sparkMahout", mahout_home, mahout_version)
z.addMahoutConfig("flinkMahout", mahout_home, mahout_version)
z._writeTerpJson()

#######################################################################################################################
# Add "export MAHOUT_HOME=... to conf/zeppelin-env.sh
# Create if doesn't exist.
#######################################################################################################################

mahout_home_str = '\nexport MAHOUT_HOME=%s\n' % (mahout_home)

zeppelin_env_sh_path = '%s/conf/zeppelin-env.sh' % zeppelin_home
if isfile(zeppelin_env_sh_path):
    with open(zeppelin_env_sh_path, 'rb') as f:
        zeppelin_env_sh = f.readlines()
    if any(["export MAHOUT_HOME=" in line for line in zeppelin_env_sh]):
        print "'export MAHOUT_HOME=...' already exists in zeppelin_env.sh, not appending"
    else:
        print "appending '%s' to conf/zeppelin-env.sh" % mahout_home_str
        with open(zeppelin_env_sh_path, 'a') as f:
            f.write(mahout_home_str)
else:
    print "appending '%s' to conf/zeppelin-env.sh" % mahout_home_str
    with open(zeppelin_env_sh_path, 'wb') as f:
        f.write(mahout_home_str)


#######################################################################################################################
# You have to restart Apache Zeppelin for new terps to show up... do this for user unless the specified otherwise
#
#######################################################################################################################
if not args.restart_later:
    print "restarting Apache Zeppelin to load new interpreters..."
    check_call(["bin/zeppelin-daemon.sh", 'restart'], cwd= zeppelin_home)
else:
    print "--restart_later flag detected: remember to restart Zeppelin to see new Mahout interpreters!!"

#######################################################################################################################
# Good bye
#######################################################################################################################

print "---------------------------------------------------------------------------------------------------------------"
print "all done! Thanks for using Apache Mahout"
print "bye"
