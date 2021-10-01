#!/bin/bash

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

# Script for installing R / Python dependencies for Travis CI
set -ev
touch ~/.environ

# Install R dependencies if SPARKR is true
if [[ "${SPARKR}" = "true" ]] ; then
  echo "R_LIBS=~/R" > ~/.Renviron
  echo "export R_LIBS=~/R" >> ~/.environ
  source ~/.environ
  if [[ ! -d "$HOME/R/knitr" ]] ; then
    mkdir -p ~/R
    R -e "install.packages('evaluate', repos = 'http://cran.us.r-project.org', lib='~/R')"  > /dev/null 2>&1
    R -e "install.packages('base64enc', repos = 'http://cran.us.r-project.org', lib='~/R')"  > /dev/null 2>&1
    R -e "install.packages('knitr', repos = 'http://cran.us.r-project.org', lib='~/R')"  > /dev/null 2>&1
    R -e "install.packages('ggplot2', repos = 'http://cran.us.r-project.org', lib='~/R')"  > /dev/null 2>&1
  fi
fi

# Install Python dependencies for Python specific tests
if [[ -n "$PYTHON" ]] ; then
  wget https://repo.anaconda.com/miniconda/Miniconda3-py38_4.10.3-Linux-aarch64.sh -O miniconda.sh
  bash miniconda.sh -b -p $HOME/miniconda
  echo "export PATH='$HOME/miniconda/bin:$PATH'" >> ~/.environ
  source ~/.environ
  hash -r
  conda config --set always_yes yes --set changeps1 no
  conda update -q conda
  conda info -a
  conda config --add channels conda-forge
  conda install -q pycodestyle scipy numpy=1.19.5 grpcio protobuf pandasql ipython ipykernel jupyter_client=5 hvplot plotnine seaborn intake \
  intake-parquet intake-xarray altair vega_datasets plotly pip r-base=3 r-data.table r-evaluate r-base64enc r-knitr r-ggplot2 r-irkernel r-shiny r-googlevis
  pip install -q scipy==1.7.1 ggplot==0.11.5 grpcio==1.41.0 bkzep==0.6.1
fi
