---
layout: page
title: Java interpreter in Apache Zeppelin
description: Run Java code and any distributed java computation library by importing the dependencies in the interpreter configuration.
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

# Java interpreter for Apache Zeppelin

<div id="toc"></div>

## How to use
Basically, you can write normal java code. You should write the main method inside a class because the interpreter invoke this main to execute the code. Unlike Zeppelin normal pattern, each paragraph is considered as a separate job, there isn't any relation to any other paragraph. For example, a variable defined in one paragraph cannot be used in another one as each paragraph is a self contained java main class that is executed and the output returned to Zeppelin.


The following is a demonstration of a word count example with data represented as a java Map and displayed leveraging Zeppelin's built in visualization using the utility method `JavaInterpreterUtils.displayTableFromSimpleMap`.


```java
%java
import java.util.HashMap;
import java.util.Map;
import org.apache.zeppelin.java.JavaInterpreterUtils;

public class HelloWorld {

    public static void main(String[] args) {
    
        Map<String, Long> counts = new HashMap<>();
        counts.put("hello",4L);
        counts.put("world",5L);

        System.out.println(JavaInterpreterUtils.displayTableFromSimpleMap("Word","Count", counts));
        
    }

}
```

