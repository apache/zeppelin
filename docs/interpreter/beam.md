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

The following is a demonstration of a word count example

```java
%beam

// imports are omitted to save space

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

