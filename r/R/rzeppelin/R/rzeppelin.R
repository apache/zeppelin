#
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

.zeppenv <- new.env()

.z.ohandler = evaluate:::new_output_handler(
	value = function(x) {
		if (is.data.frame(x)) return(x)
		if ("html" %in% class(x)) return(x)
		if (require("htmltools") & require("knitr")) {
			if ("htmlwidget" %in% class(x)) {
				return(.z.show.htmlwidget(x))
			}
		}
		if (isS4(x)) show(x)
		else {
			if (require("repr")) {
				return(repr:::repr(x))
			} else return(x)
		}
	}
)

# wrapper for evaluate
.z.valuate <- function(input) evaluate:::evaluate(
	input = input,
	envir =.zeppenv,
	debug = FALSE,
	output_handler =.z.ohandler,
	stop_on_error = 0
)

# converts data.tables to the format needed for display in zeppelin

.z.table <- function(i) {

	.zdfoutcon <- textConnection(".zdfout", open="w")
	write.table(i,
							col.names=TRUE, row.names=FALSE, sep="\t",
							eol="\n", quote = FALSE, file = .zdfoutcon)
	close(.zdfoutcon)
	rm(.zdfoutcon)
	.zdfout
}

.z.completion <- function(buf, cursor) {
	utils:::.assignLinebuffer(buf)
	utils:::.assignEnd(cursor)
	utils:::.guessTokenFromLine()
	utils:::.completeToken()
	utils:::.retrieveCompletions()
}

.z.setProgress <- function(progress)  SparkR:::callJMethod(.rContext, "setProgress", progress %% 100)
.z.incrementProgress <- function(increment = 1) SparkR:::callJMethod(.rContext, "incrementProgress", increment)

.z.input <- function(name) SparkR:::callJMethod(.zeppelinContext, "input", name)

.z.get <- function(name) {
  isRDD <- SparkR:::callJStatic("org.apache.zeppelin.rinterpreter.RStatics", "testRDD", name)
  obj <- SparkR:::callJStatic("org.apache.zeppelin.rinterpreter.RStatics", "getZ", name)
  if (isRDD) SparkR:::RDD(obj)
  else obj
 }

.z.put <- function(name, object) {
  if ("RDD" %in% class(object)) object <- SparkR:::getJRDD(object)
  SparkR:::callJStatic("org.apache.zeppelin.rinterpreter.RStatics", "putZ", name, object)
 }

.z.repr <- function(x) {
    if (require(repr)) repr:::repr(x)
    else toString(x)
 }

progress_zeppelin <- function(...) {
  list(init = function(x) .z.setProgress(0),
    step = function() .z.incrementProgress,
    term = function() {})
 }

