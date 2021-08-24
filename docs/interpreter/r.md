---
layout: page
title: "R Interpreter for Apache Zeppelin"
description: "R is a free software environment for statistical computing and graphics."
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

# R Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview

[R](https://www.r-project.org) is a free software environment for statistical computing and graphics.

To run R code and visualize plots in Apache Zeppelin, you will need R on your zeppelin server node (or your dev laptop).

+ For Centos: `yum install R R-devel libcurl-devel openssl-devel`
+ For Ubuntu: `apt-get install r-base`

Validate your installation with a simple R command:

```
R -e "print(1+1)"
```

To enjoy plots, install additional libraries with:

+ devtools with 

  ```bash
  R -e "install.packages('devtools', repos = 'http://cran.us.r-project.org')"
  ```
  
+ knitr with 
  
  ```bash
  R -e "install.packages('knitr', repos = 'http://cran.us.r-project.org')"
  ```
  
+ ggplot2 with

  ```bash
  R -e "install.packages('ggplot2', repos = 'http://cran.us.r-project.org')"
  ```

+ Other visualization libraries: 
  
  ```bash
  R -e "install.packages(c('devtools','mplot', 'googleVis'), repos = 'http://cran.us.r-project.org'); 
  require(devtools); install_github('ramnathv/rCharts')"
  ```

We recommend you to also install the following optional R libraries for happy data analytics:

+ glmnet
+ pROC
+ data.table
+ caret
+ sqldf
+ wordcloud

## Supported Interpreters

Zeppelin supports R language in 3 interpreters

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%r.r</td>
    <td>RInterpreter</td>
    <td>Vanilla r interpreter, with least dependencies, only R environment and knitr are required.
    It is always recommended to use the fully qualified interpreter name <code>%r.r</code>, because <code>%r</code> is ambiguous, 
    it could mean <code>%spark.r</code> when current note's default interpreter is <code>%spark</code> and <code>%r.r</code> when the default interpreter is <code>%r</code></td>
  </tr>
  <tr>
    <td>%r.ir</td>
    <td>IRInterpreter</td>
    <td>Provide more fancy R runtime via [IRKernel](https://github.com/IRkernel/IRkernel), almost the same experience like using R in Jupyter. It requires more things, but is the recommended interpreter for using R in Zeppelin.</td>
  </tr>
  <tr>
    <td>%r.shiny</td>
    <td>ShinyInterpreter</td>
    <td>Run Shiny app in Zeppelin</td>
  </tr>
</table>

If you want to use R with Spark, it is almost the same via `%spark.r`, `%spark.ir` & `%spark.shiny` . You can refer Spark interpreter docs for more details.

## Configuration

<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>zeppelin.R.cmd</td>
    <td>R</td>
    <td>Path of the installed R binary. You should set this property explicitly if R is not in your <code>$PATH</code>(example: /usr/bin/R).
    </td>
  </tr>
  <tr>
    <td>zeppelin.R.knitr</td>
    <td>true</td>
    <td>Whether to use knitr or not. It is recommended to install [knitr](https://yihui.org/knitr/)</td>
  </tr>
  <tr>
    <td>zeppelin.R.image.width</td>
    <td>100%</td>
    <td>Image width of R plotting</td>
  </tr>
  <tr>
    <td>zeppelin.R.shiny.iframe_width</td>
    <td>100%</td>
    <td>IFrame width of Shiny App</td>
  </tr>
  <tr>
    <td>zeppelin.R.shiny.iframe_height</td>
    <td>500px</td>
    <td>IFrame height of Shiny App</td>
  </tr>
  <tr>
    <td>zeppelin.R.shiny.portRange</td>
    <td>:</td>
    <td>Shiny app would launch a web app at some port, this property is to specify the portRange via format 'start':'end', e.g. '5000:5001'. By default it is ':' which means any port.</td>
  </tr>
  <tr>
    <td>zeppelin.R.maxResult</td>
    <td>1000</td>
    <td>Max number of dataframe rows to display when using z.show</td>
  </tr>
</table>

## Play R in Zeppelin docker

For beginner, we would suggest you to play R in Zeppelin docker first. In the Zeppelin docker image, we have already installed R and lots of useful R libraries including IRKernel's prerequisites, so `%r.ir` is available.

Without any extra configuration, you can run most of tutorial notes under folder `R Tutorial` directly.

```
docker run -u $(id -u) -p 8080:8080 -p:6789:6789 --rm --name zeppelin apache/zeppelin:0.10.0
```

After running the above command, you can open `http://localhost:8080` to play R in Zeppelin.
The port `6789` exposed in the above command is for R shiny app. You need to make the following 2 interpreter properties to enable shiny app accessible as iframe in Zeppelin docker container. 

* `zeppelin.R.shiny.portRange` to be `6789:6789`
* Set `ZEPPELIN_LOCAL_IP` to be `0.0.0.0`


<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_shiny_app.gif" width="800px"/>


## Interpreter binding mode

The default [interpreter binding mode](../usage/interpreter/interpreter_binding_mode.html) is `globally shared`. That means all notes share the same R interpreter. 
So we would recommend you to ues `isolated per note` which means each note has own R interpreter without affecting each other. But it may run out of your machine resource if too many R
interpreters are created. You can [run R in yarn mode](../interpreter/r.html#run-r-in-yarn-cluster) to avoid this problem. 

## How to use R Interpreter

There are two different implementations of R interpreters: `%r.r` and `%r.ir`.

* Vanilla R Interpreter(`%r.r`) behaves like an ordinary REPL and use SparkR to communicate between R process and JVM process. It requires `knitr` to be installed.
* IRKernel R Interpreter(`%r.ir`) behaves like using IRKernel in Jupyter notebook. It is based on [jupyter interpreter](jupyter.html). Besides jupyter interpreter's prerequisites, [IRkernel](https://github.com/IRkernel/IRkernel) needs to be installed as well. 

Take a look at the tutorial note `R Tutorial/1. R Basics` for how to write R code in Zeppelin.

### R basic expressions

R basic expressions are supported in both `%r.r` and `%r.ir`.

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_basic.png" width="800px"/>

### R base plotting

R base plotting is supported in both `%r.r` and `%r.ir`.

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_plotting.png" width="800px"/>

### Other plotting

Besides R base plotting, you can use other visualization libraries in both `%r.r` and `%r.ir`, e.g. `ggplot` and `googleVis` 

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_ggplot.png" width="800px"/>

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_googlevis.png" width="800px"/>

### z.show

`z.show()` is only available in `%r.ir` to visualize R dataframe, e.g.

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_zshow.png" width="800px"/>

By default, `z.show` would only display 1000 rows, you can specify the maxRows via `z.show(df, maxRows=2000)`

## Make Shiny App in Zeppelin

[Shiny](https://shiny.rstudio.com/tutorial/) is an R package that makes it easy to build interactive web applications (apps) straight from R.
`%r.shiny` is used for developing R shiny app in Zeppelin notebook. It only works when IRKernel Interpreter(`%r.ir`) is enabled.
For developing one Shiny App in Zeppelin, you need to write at least 3 paragraphs (server type paragraph, ui type paragraph and run type paragraph)

* Server type R shiny paragraph

```r

%r.shiny(type=server)

# Define server logic to summarize and view selected dataset ----
server <- function(input, output) {

    # Return the requested dataset ----
    datasetInput <- reactive({
        switch(input$dataset,
        "rock" = rock,
        "pressure" = pressure,
        "cars" = cars)
    })

    # Generate a summary of the dataset ----
    output$summary <- renderPrint({
        dataset <- datasetInput()
        summary(dataset)
    })

    # Show the first "n" observations ----
    output$view <- renderTable({
        head(datasetInput(), n = input$obs)
    })
}
```

* UI type R shiny paragraph

```r
%r.shiny(type=ui)

# Define UI for dataset viewer app ----
ui <- fluidPage(

    # App title ----
    titlePanel("Shiny Text"),
    
    # Sidebar layout with a input and output definitions ----
    sidebarLayout(

        # Sidebar panel for inputs ----
        sidebarPanel(
        
        # Input: Selector for choosing dataset ----
        selectInput(inputId = "dataset",
        label = "Choose a dataset:",
        choices = c("rock", "pressure", "cars")),
        
        # Input: Numeric entry for number of obs to view ----
        numericInput(inputId = "obs",
        label = "Number of observations to view:",
        value = 10)
        ),

        # Main panel for displaying outputs ----
        mainPanel(
        
        # Output: Verbatim text for data summary ----
        verbatimTextOutput("summary"),
        
        # Output: HTML table with requested number of observations ----
        tableOutput("view")
        
        )
    )
)
```

* Run type R shiny paragraph

```r

%r.shiny(type=run)

```

After executing the run type R shiny paragraph, the shiny app will be launched and embedded as iframe in paragraph.
Take a look at the tutorial note `R Tutorial/2. Shiny App` for how to develop R shiny app.

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_shiny.png" width="800px"/>

### Run multiple shiny apps

If you want to run multiple shiny apps, you can specify `app` in paragraph local property to differentiate different shiny apps.

e.g.

```r
%r.shiny(type=ui, app=app_1)
```

```r
%r.shiny(type=server, app=app_1)
```

```r
%r.shiny(type=run, app=app_1)
```

## Run R in yarn cluster

Zeppelin support to [run interpreter in yarn cluster](../quickstart/yarn.html). But there's one critical problem to run R in yarn cluster: how to manage the R environment in yarn container. 
Because yarn cluster is a distributed cluster which is composed of many nodes, and your R interpreter can start in any node. 
It is not practical to manage R environment in each node.

So in order to run R in yarn cluster, we would suggest you to use conda to manage your R environment, and Zeppelin can ship your
R conda environment to yarn container, so that each R interpreter can have its own R environment without affecting each other.

To be noticed, you can only run IRKernel interpreter(`%r.ir`) in yarn cluster. So make sure you include at least the following prerequisites in the below conda env:

* python
* jupyter
* grpcio
* protobuf
* r-base
* r-essentials
* r-irkernel

`python`, `jupyter`, `grpcio` and `protobuf` are required for [jupyter interpreter](../interpreter/jupyter.html), because IRKernel interpreter is based on [jupyter interpreter](../interpreter/jupyter.html). Others are for R runtime.

Following are instructions of how to run R in yarn cluster. You can find all the code in the tutorial note `R Tutorial/3. R Conda Env in Yarn Mode`.


### Step 1

We would suggest you to use conda pack to create archive of conda environment.

Here's one example of yaml file which is used to generate a conda environment with R and some useful R libraries.

* Create a yaml file for conda environment, write the following content into file `r_env.yml`

```text
name: r_env
channels:
  - conda-forge
  - defaults
dependencies:
  - python=3.7 
  - jupyter
  - grpcio
  - protobuf
  - r-base=3
  - r-essentials
  - r-evaluate
  - r-base64enc
  - r-knitr
  - r-ggplot2
  - r-irkernel
  - r-shiny
  - r-googlevis
```

* Create conda environment via this yaml file using either `conda` or `mamba`

```bash

conda env create -f r_env.yml
```

```bash

mamba env create -f r_env.yml
```


* Pack the conda environment using `conda`

```bash

conda pack -n r_env
```

### Step 2

Specify the following properties to enable yarn mode for R interpreter via [inline configuration](../usage/interpreter/overview.html#inline-generic-configuration)

```
%r.conf

zeppelin.interpreter.launcher yarn
zeppelin.yarn.dist.archives hdfs:///tmp/r_env.tar.gz#environment
zeppelin.interpreter.conda.env.name environment
```

`zeppelin.yarn.dist.archives` is the R conda environment tar file which is created in step 1. This tar will be shipped to yarn container and untar in the working directory of yarn container.
`hdfs:///tmp/r_env.tar.gz` is the R conda archive file you created in step 2. `environment` in `hdfs:///tmp/r_env.tar.gz#environment` is the folder name after untar. 
This folder name should be the same as `zeppelin.interpreter.conda.env.name`.

### Step 3

Now you can use run R interpreter in yarn container and also use any R libraries you specify in step 1.
