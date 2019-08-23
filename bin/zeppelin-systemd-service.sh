#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# description: Enable/disable the Zeppelin systemd service.
#

# Directory in which the systemd unit files sit.
SYSTEMD_DIR=/etc/systemd/system

function enable_systemd_service()
{
    # Where are we in the fs?
    OLD_PWD=$(pwd)

    # Work out where the script is run from and cd into said directory.
    cd "$(dirname "${BASH_SOURCE[0]}")"

    # Work out the current directory.
    MY_PWD=$(readlink -f .)

    # Work out the Zeppelin source directory (go up a directory actually).
    ZEPPELIN_DIR=$(dirname "${MY_PWD}")

    # Copy the unit file.
    cp "${ZEPPELIN_DIR}"/scripts/systemd/zeppelin.systemd "${SYSTEMD_DIR}"

    # Swap the template variable with the right directory path.
    sed -i -e "s#%ZEPPELIN_DIR%#${ZEPPELIN_DIR}#g;" \
        "${SYSTEMD_DIR}"/zeppelin.systemd

    # Set up the unit file.
    systemctl daemon-reload
    systemctl enable zeppelin.service

    # Display a help message.
    echo "To start Zeppelin using systemd, simply type:
# systemctl start zeppelin

To check the service health:
# systemctl status zeppelin"

    # Go back where we came from.
    cd "${OLD_PWD}"
}

function disable_systemd_service()
{
    # Let's mop up.
    systemctl stop zeppelin.service
    systemctl disable zeppelin.service
    rm "${SYSMTED_DIR}"/zeppelin.systemd
    systemctl daemon-reload
    systemctl reset-failed

    # We're done. Explain what's just happened.
    echo "Zeppelin systemd service has been disabled and removed from your system."
}

function check_user()
{
    # Are we root?
    if [[ $(id -u) -ne 0 ]]; then
        echo "Please run this script as root!"
        exit -1
    fi
}

function check_systemctl()
{
    # Is the systemctl command available?
    type -P systemctl > /dev/null
    if [[ $? -ne 0 ]]; then
        echo "ERROR! the 'systemctl' command has not been found!
Please install systemd if you want to use this script."
        exit -1
    fi
}

USAGE="usage: zeppelin-systemd-service.sh {enable|disable}

  enable:    enable Zeppelin systemd service.
  disable:   disable Zeppelin systemd service.
"

# Main method starts from here downwards.
check_user
check_systemctl

case "${1}" in
  enable)
    enable_systemd_service
    ;;
  disable)
    disable_systemd_service
    ;;
  *)
    echo "${USAGE}"
esac
