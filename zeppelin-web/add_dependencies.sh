#!/bin/bash
( sudo apt-get install libfontconfig -y || sudo yum install fontconfig -y ) || exit 0;
