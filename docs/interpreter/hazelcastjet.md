---
layout: page
title: Hazelcast Jet interpreter in Apache Zeppelin
description: Build and execture Hazelcast Jet computation jobs.
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

# Hazelcast Jet interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Hazelcast Jet](https://jet.hazelcast.org) is an open source application embeddable, distributed computing engine for In-Memory Streaming and Fast Batch Processing built on top of Hazelcast In-Memory Data Grid (IMDG). 
With Hazelcast IMDG providing storage functionality, Hazelcast Jet performs parallel execution to enable data-intensive applications to operate in near real-time.

## Why Hazelcast Jet?
There are plenty of solutions which can solve some of these issues, so why choose Hazelcast Jet?
When speed and simplicity is important.

Hazelcast Jet gives you all the infrastructure you need to build a distributed data processing pipeline within one 10Mb Java JAR: processing, storage and clustering.

As it is built on top of Hazelcast IMDG, Hazelcast Jet comes with in-memory operational storage that’s available out-of-the box. This storage is partitioned, distributed and replicated across the Hazelcast Jet cluster for capacity and resiliency. It can be used as an input data buffer, to publish the results of a Hazelcast Jet computation, to connect multiple Hazelcast Jet jobs or as a lookup cache for data enrichment.

## How to use the Hazelcast Jet interpreter
Basically, you can write normal java code. You should write the main method inside a class because the interpreter invoke this main to execute the code. Unlike Zeppelin normal pattern, each paragraph is considered as a separate job, there isn't any relation to any other paragraph. For example, a variable defined in one paragraph cannot be used in another one as each paragraph is a self contained java main class that is executed and the output returned to Zeppelin.

The following is a demonstration of a word count example with the result represented as an Hazelcast IMDG IMap sink and displayed leveraging Zeppelin's built in visualization using the utility method `JavaInterpreterUtils.displayTableFromSimpleMap`.

```java
%hazelcastjet

import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.zeppelin.java.JavaInterpreterUtils;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;

public class DisplayTableFromSimpleMapExample {

    public static void main(String[] args) {
    
        // Create the specification of the computation pipeline. Note
        // it's a pure POJO: no instance of Jet needed to create it.
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<String>list("text"))
                .flatMap(word ->
                        traverseArray(word.toLowerCase().split("\\W+")))
                .filter(word -> !word.isEmpty())
                .groupingKey(wholeItem())
                .aggregate(counting())
                .drainTo(Sinks.map("counts"));

        // Start Jet, populate the input list
        JetInstance jet = Jet.newJetInstance();
        try {
            List<String> text = jet.getList("text");
            text.add("hello world hello hello world");
            text.add("world world hello world");

            // Perform the computation
            jet.newJob(p).join();

            // Diplay the results with Zeppelin %table
            Map<String, Long> counts = jet.getMap("counts");
            System.out.println(JavaInterpreterUtils.displayTableFromSimpleMap("Word","Count", counts));

        } finally {
            Jet.shutdownAll();
        }
        
    }

}
```

The following is a demonstration where the Hazelcast DAG (directed acyclic graph) is displayed as a graph leveraging Zeppelin's built in visualization using the utility method `HazelcastJetInterpreterUtils.displayNetworkFromDAG`.
This is particularly useful to understand how the high level Pipeline is then converted to the Jet’s low-level Core API. 

```java
%hazelcastjet

import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;

import org.apache.zeppelin.hazelcastjet.HazelcastJetInterpreterUtils;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;

public class DisplayNetworkFromDAGExample {

    public static void main(String[] args) {
    
        // Create the specification of the computation pipeline. Note
        // it's a pure POJO: no instance of Jet needed to create it.
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<String>list("text"))
                .flatMap(word ->
                        traverseArray(word.toLowerCase().split("\\W+"))).setName("flat traversing")
                .filter(word -> !word.isEmpty())
                .groupingKey(wholeItem())
                .aggregate(counting())
                .drainTo(Sinks.map("counts"));

        // Diplay the results with Zeppelin %network
        System.out.println(HazelcastJetInterpreterUtils.displayNetworkFromDAG(p.toDag()));
        
    }

}
```

Note
- By clicking on a node of the graph, the node type is displayed (either Source, Sink or Transform). This is also visually represented with colors (Sources and Sinks are blue, Transforms are orange).
- By clicking on an edge of the graph, the following details are shown: routing (UNICAST, PARTITIONED, ISOLATED, BROADCAST), distributed (true or false), priority (int).
