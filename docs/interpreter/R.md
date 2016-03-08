---
layout: page
title: "R Interpreter"
description: ""
group: manual
---
{% include JB/setup %}

## R Interpreter for Apache Zeppelin

[R](https://www.r-project.org) is a free software environment for statistical computing and graphics.

To run R code and visualize plots in Apache Zeppelin, you will need R on your master node (or your dev laptop).

+ For Centos: `yum install R R-devel libcurl-devel openssl-devel`
+ For Ubuntu: `apt-get install r-base`
    
Validate your installation with a simple R command:

```
R -e "print(1+1)"
```

Then install the required R libraries;

+ devtools with `R -e "install.packages('devtools', repos = 'http://cran.us.r-project.org')"`
+ knitr with `R -e "install.packages('knitr', repos = 'http://cran.us.r-project.org')"`
+ ggplot2 with `R -e "install.packages('ggplot2', repos = 'http://cran.us.r-project.org')"`
+ Other vizualisation librairies with `R -e "install.packages(c('devtools','mplot', 'googleVis'), repos = 'http://cran.us.r-project.org'); require(devtools); install_github('ramnathv/rCharts')"`
+ rscala: You need version 1.0.6 of RScala, so the commands will be [1]

```
[1]
curl https://cran.r-project.org/src/contrib/Archive/rscala/rscala_1.0.6.tar.gz -o /tmp/rscala_1.0.6.tar.gz
R CMD INSTALL /tmp/rscala_1.0.6.tar.gz
```

We recommend you to also install the following optional R libraries to enjoy data analytics:

+ glmnet
+ pROC
+ data.table
+ caret
+ sqldf
+ wordcloud
