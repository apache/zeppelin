---
layout: page
title: "Beam Interpreter"
description: ""
group: interpreter
---
{% include JB/setup %}

# Beam interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Beam](http://beam.incubator.apache.org) is an open source, unified programming model that you can use to create a data processing pipeline. You start by building a program that defines the pipeline using one of the open source Beam SDKs. The pipeline is then executed by one of Beam’s supported distributed processing back-ends, which include Apache Flink, Apache Spark, and Google Cloud Dataflow.

Beam is particularly useful for Embarrassingly Parallel data processing tasks, in which the problem can be decomposed into many smaller bundles of data that can be processed independently and in parallel. You can also use Beam for Extract, Transform, and Load (ETL) tasks and pure data integration. These tasks are useful for moving data between different storage media and data sources, transforming data into a more desirable format, or loading data onto a new system.

# Apache Beam Pipeline Runners
The Beam Pipeline Runners translate the data processing pipeline you define with your Beam program into the API compatible with the distributed processing back-end of your choice. When you run your Beam program, you’ll need to specify the appropriate runner for the back-end where you want to execute your pipeline.

* Beam currently supports Runners that work with the following distributed processing back-ends:
- Google Cloud Dataflow
- Apache Flink
- Apache Spark

## How to use
Basically, You can write normal java code and determine the runner inside the code.
You should write the main method inside the main class beacuase the interpreter invoke this main to execute pipline.
Each paragraph is considered as separate job, there isn't any relate to any another job, Beacuse the interpreter is a static repl of java, we compile and run each paragraph apart.

**Example for Flink Runner**

```
%beam

import java.util.ArrayList;
import java.util.List;
import org.apache.spark.api.java.*;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.SparkConf;
import org.apache.spark.streaming.*;
import org.apache.spark.SparkContext;
import org.apache.beam.runners.direct.*;
import org.apache.beam.sdk.runners.*;
import org.apache.beam.sdk.options.*;
import org.apache.beam.runners.spark.*;
import org.apache.beam.runners.spark.io.ConsoleIO;
import org.apache.beam.runners.flink.*;
import org.apache.beam.runners.flink.examples.WordCount.Options;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.KV;


public class MinimalWordCount {
  static List<String> s = new ArrayList<>();
  public static void main(String[] args) {

    Options options = PipelineOptionsFactory.create().as(Options.class);

    options.setRunner(FlinkPipelineRunner.class);


    Pipeline p = Pipeline.create(options);

    p.apply(TextIO.Read.from("/home/admin/mahmoud/work/bigdata/beam/shakespeare/input/file1.txt"))

        .apply(ParDo.named("ExtractWords").of(new DoFn<String, String>() {
          @Override
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

          @Override
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
