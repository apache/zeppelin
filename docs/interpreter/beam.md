---
layout: page
title: Beam interpreter in Apache Zeppelin
description: Apache Beam is an open source, unified programming model that you can use to create a data processing pipeline.
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

# Beam interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Beam](http://beam.incubator.apache.org) is an open source unified platform for data processing pipelines. A pipeline can be build using one of the Beam SDKs.
The execution of the pipeline is done by different Runners. Currently, Beam supports Apache Flink Runner, Apache Spark Runner, and Google Dataflow Runner.

## How to use
Basically, you can write normal Beam java code where you can determine the Runner. You should write the main method inside a class becuase the interpreter invoke this main to execute the pipeline. Unlike Zeppelin normal pattern, each paragraph is considered as a separate job, there isn't any relation to any other paragraph.

The following is a demonstration of a word count example with data represented in array of strings
But it can read data from files by replacing `Create.of(SENTENCES).withCoder(StringUtf8Coder.of())` with `TextIO.Read.from("path/to/filename.txt")`

```java
%beam

// most used imports
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.transforms.Create;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import org.apache.beam.runners.direct.*;
import org.apache.beam.sdk.runners.*;
import org.apache.beam.sdk.options.*;
import org.apache.beam.runners.flink.*;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.options.PipelineOptions;

public class MinimalWordCount {
  static List<String> s = new ArrayList<>();
  
  static final String[] SENTENCES_ARRAY = new String[] {
    "Hadoop is the Elephant King!",
    "A yellow and elegant thing.",
    "He never forgets",
    "Useful data, or lets",
    "An extraneous element cling!",
    "A wonderful king is Hadoop.",
    "The elephant plays well with Sqoop.",
    "But what helps him to thrive",
    "Are Impala, and Hive,",
    "And HDFS in the group.",
    "Hadoop is an elegant fellow.",
    "An elephant gentle and mellow.",
    "He never gets mad,",
    "Or does anything bad,",
    "Because, at his core, he is yellow",
	};  
  static final List<String> SENTENCES = Arrays.asList(SENTENCES_ARRAY);
  public static void main(String[] args) {
    PipelineOptions options = PipelineOptionsFactory.create().as(PipelineOptions.class);
    options.setRunner(FlinkRunner.class);
    Pipeline p = Pipeline.create(options);
    p.apply(Create.of(SENTENCES).withCoder(StringUtf8Coder.of()))
         .apply("ExtractWords", ParDo.of(new DoFn<String, String>() {
           @ProcessElement
           public void processElement(ProcessContext c) {
             for (String word : c.element().split("[^a-zA-Z']+")) {
               if (!word.isEmpty()) {
                 c.output(word);
               }
             }
           }
         }))
        .apply(Count.<String> perElement())
        .apply("FormatResults", ParDo.of(new DoFn<KV<String, Long>, String>() {
          @ProcessElement
          public void processElement(DoFn<KV<String, Long>, String>.ProcessContext arg0)
            throws Exception {
            s.add("\n" + arg0.element().getKey() + "\t" + arg0.element().getValue());
          	}
        }));
    p.run();
    System.out.println("%table word\tcount");
    for (int i = 0; i < s.size(); i++) {
      System.out.print(s.get(i));
    }

  }
}

```

