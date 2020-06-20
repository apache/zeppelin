#!/usr/bin/env bash
# Install instruction from here https://cran.r-project.org/bin/linux/ubuntu/README.html

sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys E298A3A825C0D65DFD57CBB651716619E084DAB9
echo "deb https://cloud.r-project.org/bin/linux/ubuntu xenial-cran35/" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
sudo apt-get install -y r-base
