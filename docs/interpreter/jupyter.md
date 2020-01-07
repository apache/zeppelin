---
layout: page
title: "Jupyter Interpreter for Apache Zeppelin"
description: "Project Jupyter exists to develop open-source software, open-standards, and services for interactive computing across dozens of programming languages."
group: interpreter
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Jupyter Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview

Project [Jupyter](https://jupyter.org/) exists to develop open-source software, open-standards, and services for interactive computing across dozens of programming languages.
Zeppelin's Jupyter interpreter is a bridge/adapter between Zeppelin interpreter and Jupyter kernel. You can use any of jupyter kernel as long as you installed the necessary dependencies.

## Configuration

To run any Jupyter kernel in Zeppelin you first need to install the following prerequisite:

* pip install jupyter-client
* pip install grpcio
* pip install protobuf

Then you need install the jupyter kernel you want to use. In the following sections, we will talk about how to use the following 3 jupyter kernels in Zeppelin:

* ipython
* ir
* julia

## Jupyter Python kernel

In order to use Jupyter Python kernel in Zeppelin, you need to install `ipykernel` first. 

```bash

pip install ipykernel
```

Then you can run python code in Jupyter interpreter like following. 

```python

%jupyter(kernel=python)

%matplotlib inline
import matplotlib.pyplot as plt
plt.plot([1, 2, 3])
```

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/ipython_kernel.png" width="80%"/>

## Jupyter R kernel

In order to use [IRKernel](https://github.com/IRkernel/IRkernel), you need to first install `IRkernel` package in R.

```r
install.packages('IRkernel')
IRkernel::installspec()  # to register the kernel in the current R installation
```

Then you can run r code in Jupyter interpreter like following. 

```r
%jupyter(kernel=ir)

library(ggplot2)
ggplot(mpg, aes(x = displ, y = hwy)) +
  geom_point()
```

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/ir_kernel.png" width="80%"/>


## Jupyter Julia kernel

In order to use Julia in Zeppelin, you first need to install [IJulia](https://github.com/JuliaLang/IJulia.jl) first

```julia
using Pkg
Pkg.add("IJulia")

```

Then you can run julia code in Jupyter interpreter like following. 

```julia

%jupyter(kernel=julia-1.3)

using Pkg
Pkg.add("Plots")
using Plots
plotly() # Choose the Plotly.jl backend for web interactivity
plot(rand(5,5),linewidth=2,title="My Plot")
Pkg.add("PyPlot") # Install a different backend
pyplot() # Switch to using the PyPlot.jl backend
plot(rand(5,5),linewidth=2,title="My Plot")
```

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/julia_kernel.png" width="80%"/>


## Use any other kernel

For any other jupyter kernel, you can follow the below steps to use it in Zeppelin.

1. Install the specified jupyter kernel. you can find all the available jupyter kernels [here](https://github.com/jupyter/jupyter/wiki/Jupyter-kernels) 
2. Find its kernel name by run the following command
   ```bash
   jupyter kernelspec list
   ```
3. Run the kernel as following

```python

%jupyter(kernel=kernel_name)

code
```