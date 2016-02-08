---
layout: page
title: "R Interpreter"
description: ""
group: manual
---
{% include JB/setup %}

## R Interpreter for Apache Zeppelin

[R](https://www.r-project.org) iR is a free software environment for statistical computing and graphics.

To run R code and visualize plots in Apache Zeppelin, you will need:

+ devtools with `install.packages("devtools", repos = "http://cran.us.r-project.org")`
+ knitr with `install.packages("knitr", repos = "http://cran.us.r-project.org")`
+ ggplot2 with `install.packages("ggplot2", repos = "http://cran.us.r-project.org")`
+ rscala: We need version 1.0.6 of Rscala, so the commands will be [1]

```
[1]
curl https://cran.r-project.org/src/contrib/Archive/rscala/rscala_1.0.6.tar.gz -o /tmp/rscala_1.0.6.tar.gz
R CMD INSTALL /tmp/rscala_1.0.6.tar.gz
```

Validate your installation with a simple R command:

```
print(1+1)
```

We recommend you to also install the following libraries:

+ glmnet
+ pROC
+ data.table
+ caret
+ sqldf
+ wordcloud
