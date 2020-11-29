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

# Install conda for Python and R dependencies
if [[ -n "$PYTHON" ]] ; then
  wget "https://repo.continuum.io/miniconda/Miniconda${PYTHON}-4.6.14-Linux-x86_64.sh" -O miniconda.sh
else
  wget "https://repo.continuum.io/miniconda/Miniconda3-4.6.14-Linux-x86_64.sh" -O miniconda.sh
fi
bash miniconda.sh -b -p "$HOME/miniconda"
rm -fv miniconda.sh
echo "export PATH='$HOME/miniconda/bin:$PATH'" >> ~/.environ
source ~/.environ
hash -r
conda config --set always_yes yes --set changeps1 no
conda update -q conda
conda info -a
conda config --add channels conda-forge

if [[ -n "$PYTHON" ]] ; then
  if [[ "$PYTHON" == "2" ]] ; then
    pip install -q numpy==1.14.5 pandas==0.21.1 matplotlib==2.1.1 scipy==1.2.1 grpcio==1.19.0 bkzep==0.6.1 hvplot==0.5.2 \
    protobuf==3.7.0 pandasql==0.7.3 ipython==5.8.0 ipykernel==4.10.0 bokeh==1.3.4 panel==0.6.0 holoviews==1.12.3
  else
    pip install -q pycodestyle==2.5.0
    pip install -q numpy==1.17.3 pandas==0.25.0 scipy==1.3.1 grpcio==1.19.0 bkzep==0.6.1 hvplot==0.5.2 protobuf==3.10.0 \
    pandasql==0.7.3 ipython==7.8.0 matplotlib==3.0.3 ipykernel==5.1.2 jupyter_client==5.3.4 bokeh==1.3.4 panel==0.6.0 holoviews==1.12.3 pycodestyle==2.5.0
  fi

  if [[ -n "$TENSORFLOW" ]] ; then
    check_results=$(conda search -c conda-forge tensorflow)
    echo "search tensorflow = $check_results"
    pip install -q "tensorflow==${TENSORFLOW}"
  fi

  if [[ -n "${FLINK}" ]]; then
    pip install -q "apache-flink==${FLINK}"
  fi
fi

# Install R dependencies if R is true
if [[ "$R" == "true" ]] ; then
  conda install -y --quiet r-base=3 r-evaluate r-base64enc r-knitr r-ggplot2 r-irkernel r-shiny=1.5.0 r-googlevis
  R -e "IRkernel::installspec()"
  echo "R_LIBS=~/miniconda/lib/R/library" > ~/.Renviron
  echo "export R_LIBS=~/miniconda/lib/R/library" >> ~/.environ
fi
