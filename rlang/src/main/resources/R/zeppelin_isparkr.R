#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

port <- ${Port}
libPath <- ${libPath}
version <- ${version}
timeout <- ${timeout}
isSparkSupported <- ${isSparkSupported}
authSecret <- ${authSecret}

print(paste("Port ", toString(port)))
print(paste("LibPath ", libPath))

.libPaths(c(file.path(libPath), .libPaths()))
library(SparkR)

if (is.null(authSecret) || authSecret == '') {
  SparkR:::connectBackend("localhost", port, timeout)
} else {
  SparkR:::connectBackend("localhost", port, timeout, authSecret)
}

# scStartTime is needed by R/pkg/R/sparkR.R
assign(".scStartTime", as.integer(Sys.time()), envir = SparkR:::.sparkREnv)
assign(".maxRows", as.integer())

if (isSparkSupported == "true") {
  # setup spark env
  assign(".sc", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getSparkContext"), envir = SparkR:::.sparkREnv)
  assign("sc", get(".sc", envir = SparkR:::.sparkREnv), envir=.GlobalEnv)
  if (version >= 20000) {
   assign(".sparkRsession", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getSparkSession"), envir = SparkR:::.sparkREnv)
   assign("spark", get(".sparkRsession", envir = SparkR:::.sparkREnv), envir = .GlobalEnv)
   assign(".sparkRjsc", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getJavaSparkContext"), envir = SparkR:::.sparkREnv)
  }
  assign(".sqlc", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getSqlContext"), envir = SparkR:::.sparkREnv)
  assign("sqlContext", get(".sqlc", envir = SparkR:::.sparkREnv), envir = .GlobalEnv)
  assign(".zeppelinContext", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getZeppelinContext"), envir = .GlobalEnv)
} else {
  assign(".zeppelinContext", SparkR:::callJStatic("org.apache.zeppelin.r.RInterpreter", "getRZeppelinContext"), envir = .GlobalEnv)
}

z.put <- function(name, object) {
  SparkR:::callJMethod(.zeppelinContext, "put", name, object)
}

z.get <- function(name) {
  SparkR:::callJMethod(.zeppelinContext, "get", name)
}

z.getAsDataFrame <- function(name) {
  stringValue <- z.get(name)
  read.table(text=stringValue, header=TRUE, sep="\t")
}

z.angular <- function(name, noteId=NULL, paragraphId=NULL) {
  SparkR:::callJMethod(.zeppelinContext, "angular", name, noteId, paragraphId)
}

z.angularBind <- function(name, value, noteId=NULL, paragraphId=NULL) {
  SparkR:::callJMethod(.zeppelinContext, "angularBind", name, value, noteId, paragraphId)
}

z.textbox <- function(name, value) {
  SparkR:::callJMethod(.zeppelinContext, "textbox", name, value)
}

z.noteTextbox <- function(name, value) {
  SparkR:::callJMethod(.zeppelinContext, "noteTextbox", name, value)
}

z.password <- function(name) {
  SparkR:::callJMethod(.zeppelinContext, "password", name)
}

z.notePassword <- function(name) {
  SparkR:::callJMethod(.zeppelinContext, "notePassword", name)
}

z.run <- function(paragraphId) {
  SparkR:::callJMethod(.zeppelinContext, "run", paragraphId)
}

z.runNote <- function(noteId) {
  SparkR:::callJMethod(.zeppelinContext, "runNote", noteId)
}

z.runAll <- function() {
  SparkR:::callJMethod(.zeppelinContext, "runAll")
}

z.angular <- function(name) {
  SparkR:::callJMethod(.zeppelinContext, "angular", name)
}

z.angularBind <- function(name, value) {
  SparkR:::callJMethod(.zeppelinContext, "angularBind", name, value)
}

z.angularUnbind <- function(name, value) {
  SparkR:::callJMethod(.zeppelinContext, "angularUnbind", name)
}

z.show <- function(data, maxRows=1000) {
  if (is.data.frame(data)) {
    resultString = c(paste(colnames(data),  collapse ="\t"))
    for (row in 1: min(nrow(data), maxRows)) {
      rowString <- paste(data[row,], collapse ="\t")
      resultString = c(resultString, rowString)
    }
    a=paste(resultString, collapse="\n")
    cat("\n%table ", a, "\n\n%text ", sep="")
    if (nrow(data) > maxRows) {
      cat("\n%html <font color=red>Results are limited by ", maxRows, " rows.</font>", "\n%text ", sep="")
    }
  } else {
    cat(data)
  }
}