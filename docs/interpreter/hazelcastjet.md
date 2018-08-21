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

## How to use
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

public class HelloWorld {

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

