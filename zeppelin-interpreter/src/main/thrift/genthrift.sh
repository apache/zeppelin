#!/usr/bin/env bash
#/**
# * Licensed to the Apache Software Foundation (ASF) under one
# * or more contributor license agreements.  See the NOTICE file
# * distributed with this work for additional information
# * regarding copyright ownership.  The ASF licenses this file
# * to you under the Apache License, Version 2.0 (the
# * "License"); you may not use this file except in compliance
# * with the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */


THRIFT_VERSION="0.13.0"
THRIFT_URL="https://archive.apache.org/dist/thrift/${THRIFT_VERSION}/thrift-${THRIFT_VERSION}.tar.gz"

check_thrift_installed() {
    if command -v thrift &> /dev/null; then
        INSTALLED_VERSION=$(thrift -version 2>&1 | awk '{print $3}')
        if [ "$INSTALLED_VERSION" == "$THRIFT_VERSION" ]; then
            return 0  # Return 0 if the same version is installed
        else
            echo "Installed Thrift version is $INSTALLED_VERSION, but required version is $THRIFT_VERSION."
            return 1  # Return 1 if a different version is installed
        fi
    else
        echo "Thrift is not installed."
        return 2  # Return 2 if Thrift is not installed
    fi
}

check_openssl_installed() {
    if command -v openssl &> /dev/null; then
        OPENSSL_VERSION=$(openssl version | awk '{print $2}')
        if [[ "$OPENSSL_VERSION" == 1.1* ]]; then
            echo "OpenSSL 1.1.x is already installed. Version: $OPENSSL_VERSION"
            return 0
        else
            echo "OpenSSL is installed, but not version 1.1.x. Current version: $OPENSSL_VERSION"
            return 1
        fi
    else
        echo "OpenSSL is not installed."
        return 1
    fi
}

install_openssl_from_source() {
    echo "Installing OpenSSL 1.1.x from source..."

    yum groupinstall -y "Development Tools"
    yum install -y wget perl-core libtool zlib-devel

    cd /usr/local/src
    wget https://www.openssl.org/source/openssl-1.1.1l.tar.gz
    tar -xzvf openssl-1.1.1l.tar.gz
    cd openssl-1.1.1l
    ./config --prefix=/usr/local/openssl --openssldir=/usr/local/openssl shared zlib
    make
    make install

    export PATH="/usr/local/openssl/bin:$PATH"
    export LD_LIBRARY_PATH="/usr/local/openssl/lib:/usr/local/lib64:/usr/lib64:$LD_LIBRARY_PATH"

    echo "OpenSSL 1.1.x installation completed."
}

install_thrift_ubuntu() {
    echo "Installing Thrift ${THRIFT_VERSION} on Ubuntu/Debian..."

    apt-get update
    apt-get install -y software-properties-common
    apt-get update
    apt-get install -y thrift-compiler=${THRIFT_VERSION}*
}

install_thrift_centos() {
    echo "Installing Thrift ${THRIFT_VERSION} on CentOS..."

    if ! check_openssl_installed; then
        install_openssl_from_source
    fi

    yum install -y epel-release
    yum install -y wget gcc-c++ libevent-devel zlib-devel

    cd /usr/local/src
    wget ${THRIFT_URL}
    if [ ! -f "thrift-${THRIFT_VERSION}.tar.gz" ]; then
        echo "Thrift source file download failed."
        exit 1
    fi
    tar -xzf thrift-${THRIFT_VERSION}.tar.gz
    cd thrift-${THRIFT_VERSION}
    ./configure --with-lua=no --with-ruby=no --with-php=no --with-qt4=no --with-qt5=no --with-cpp=no
    make
    make install

    echo "Thrift ${THRIFT_VERSION} installed successfully."
}

install_thrift_macos() {
  echo "Installing Thrift ${THRIFT_VERSION} on macOS..."
  if command -v brew &> /dev/null; then
    brew install thrift@0.13
  else
    echo "Homebrew not found. Please install Homebrew first."
    exit 1
  fi
}

install_thrift() {
  OS="$(uname -s)"

  case $OS in
    Linux)
      if command -v apt-get &> /dev/null; then
        install_thrift_ubuntu
      elif command -v yum &> /dev/null; then
        install_thrift_centos
      else
        echo "Unsupported Linux distribution. Please install Thrift ${THRIFT_VERSION} manually."
        exit 1
      fi
      ;;
    Darwin)
      install_thrift_macos
      ;;
    *)
      echo "Unsupported OS: $OS. Please install Thrift ${THRIFT_VERSION} manually."
      exit 1
      ;;
  esac
}

generate_thrift_java_files() {
    echo "Generating Java files from Thrift IDL files..."

    THRIFT_DIR="$(dirname "$0")"
    GEN_JAVA_DIR="${THRIFT_DIR}/gen-java"
    TARGET_JAVA_DIR="${THRIFT_DIR}/../java/org/apache/zeppelin/interpreter/thrift"

    rm -rf "${GEN_JAVA_DIR}"
    rm -rf "${TARGET_JAVA_DIR}"
    mkdir -p "${GEN_JAVA_DIR}"

    thrift --gen java -out "${GEN_JAVA_DIR}" "${THRIFT_DIR}/RemoteInterpreterService.thrift"
    thrift --gen java -out "${GEN_JAVA_DIR}" "${THRIFT_DIR}/RemoteInterpreterEventService.thrift"

    # add license header
    for file in "${GEN_JAVA_DIR}/org/apache/zeppelin/interpreter/thrift/"* ; do
        cat "${THRIFT_DIR}/java_license_header.txt" "${file}" > "${file}.tmp"
        mv -f "${file}.tmp" "${file}"
    done

    mv "${GEN_JAVA_DIR}/org/apache/zeppelin/interpreter/thrift" "${TARGET_JAVA_DIR}"
    rm -rf "${GEN_JAVA_DIR}"

    echo "Java files generated successfully."
}


if [ "$1" == "install" ]; then
    # Check if Thrift is installed and its version
    check_thrift_installed
    if [ $? -eq 2 ] || [ $? -eq 1 ]; then
        # Install Thrift only if it is not installed or if a different version is installed
        install_thrift
    else
        echo "Thrift $THRIFT_VERSION is already installed. Skipping installation."
    fi
    exit 0
else
    # Check if Thrift is installed
    check_thrift_installed
    if [ $? -eq 0 ] || [ $? -eq 1 ]; then
        # If Thrift is installed, generate Java files
        # Java file generation will proceed even if a different version of Thrift is installed
        generate_thrift_java_files
    else
        # If Thrift is not installed, inform the user and exit
        echo "Thrift is not installed. If you want to build Java files using Thrift, run './genthrift.sh install'."
        exit 0
    fi
fi