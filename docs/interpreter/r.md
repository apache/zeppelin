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

To run R code and visualize plots in Apache Zeppelin, you will need R on your master node (or your dev laptop).

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
    <td>Vanilla r interpreter, with least dependencies, only R environment installed is required.
    It is always recommended to use the fully qualified interpreter name <code>%r.r</code>code>, because <code>%r</code> is ambiguous, 
    it could mean both <code>%spark.r</code> and <code>%r.r</code></td>
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

If you want to use R with Spark, it is almost the same via `%spark.r`, `%spark.ir` & `%spark.shiny` . You can refer Spark Interpreter docs for more details.

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
</table>

## Using the R Interpreter(`%r.r` & `%r.ir`)

By default, the R Interpreter appears as two Zeppelin Interpreters, `%r.r` and `%r.ir`.

`%r.r` behaves like an ordinary REPL and use SparkR to communicate between R process and JVM process.
`%r.ir` use IRKernel underneath, it behaves like using IRKernel in Jupyter notebook.  

R basic expression

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_basic.png" width="800px"/>

R base plotting is fully supported

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_plotting.png" width="800px"/>

Besides R base plotting, you can use other visualization library, e.g. `ggplot` and `googlevis` 

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_ggplot.png" width="800px"/>

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_googlevis.png" width="800px"/>


## Make Shiny App in Zeppelin

[Shiny](https://shiny.rstudio.com/tutorial/) is an R package that makes it easy to build interactive web applications (apps) straight from R.
For developing one Shiny App in Zeppelin, you need to at least 3 paragraphs (server paragraph, ui paragraph and run type paragraph)

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

After executing the run type R shiny paragraph, the shiny app will be launched and embedded as Iframe in paragraph.

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/r_shiny.png" width="800px"/>

### Run multiple shiny app

If you want to run multiple shiny app, you can specify `app` in paragraph local property to differentiate shiny app.

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