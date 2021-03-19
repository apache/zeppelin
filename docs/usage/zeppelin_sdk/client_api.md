---
layout: page
title: "Apache Zeppelin SDK - ZeppelinClient API"
description: "This page contains Apache Zeppelin Client API."
group: usage/zeppelin_sdk 
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

# Apache Zeppelin SDK - Zeppelin Client API

<div id="toc"></div>

## Overview

Zeppelin client api is a lower level java api which encapsulates Zeppelin's rest api so that you can easily integrate Zeppelin
with your system. You can use zeppelin client api to do most of the things in notebook ui in a programmatic way, such as create/delete note/paragraph, 
run note/paragraph and etc.


## How to use Zeppelin Client API

The entry point of zeppelin client api is class `ZeppelinClient`. All the operations is via this class, e.g. in the following example, we use `ZeppelinClient` to run the spark tutorial note programmatically.

{% highlight java %}
ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
ZeppelinClient zClient = new ZeppelinClient(clientConfig);

String zeppelinVersion = zClient.getVersion();
System.out.println("Zeppelin version: " + zeppelinVersion);

// execute note 2A94M5J1Z paragraph by paragraph
try {
  ParagraphResult paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150210-015259_1403135953");
  System.out.println("Execute the 1st spark tutorial paragraph, paragraph result: " + paragraphResult);

  paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150210-015302_1492795503");
  System.out.println("Execute the 2nd spark tutorial paragraph, paragraph result: " + paragraphResult);

  Map<String, String> parameters = new HashMap<>();
  parameters.put("maxAge", "40");
  paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150212-145404_867439529", parameters);
  System.out.println("Execute the 3rd spark tutorial paragraph, paragraph result: " + paragraphResult);

  parameters = new HashMap<>();
  parameters.put("marital", "married");
  paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150213-230422_1600658137", parameters);
  System.out.println("Execute the 4th spark tutorial paragraph, paragraph result: " + paragraphResult);
} finally {
  // you need to stop interpreter explicitly if you are running paragraph separately.
  zClient.stopInterpreter("2A94M5J1Z", "spark");
}
{% endhighlight %}

Here we list some importance apis of ZeppelinClient, for the completed api, please refer its javadoc.

{% highlight java %}

public String createNote(String notePath) throws Exception 

public void deleteNote(String noteId) throws Exception 

public NoteResult executeNote(String noteId) throws Exception 

public NoteResult executeNote(String noteId, 
                              Map<String, String> parameters) throws Exception
                              
public NoteResult queryNoteResult(String noteId) throws Exception 

public NoteResult submitNote(String noteId) throws Exception

public NoteResult submitNote(String noteId, 
                             Map<String, String> parameters) throws Exception 
                             
public NoteResult waitUntilNoteFinished(String noteId) throws Exception

public String addParagraph(String noteId, 
                           String title, 
                           String text) throws Exception
                           
public void updateParagraph(String noteId, 
                            String paragraphId, 
                            String title, 
                            String text) throws Exception
                            
public ParagraphResult executeParagraph(String noteId,
                                        String paragraphId,
                                        String sessionId,
                                        Map<String, String> parameters) throws Exception
                                        
public ParagraphResult submitParagraph(String noteId,
                                       String paragraphId,
                                       String sessionId,
                                       Map<String, String> parameters) throws Exception
                                       
public void cancelParagraph(String noteId, String paragraphId)
    
public ParagraphResult queryParagraphResult(String noteId, String paragraphId) 
    
public ParagraphResult waitUtilParagraphFinish(String noteId, String paragraphId)

{% endhighlight %}


## Examples

For more detailed usage of zeppelin client api, you can check the examples in module `zeppelin-client-examples`

* [ZeppelinClientExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/ZeppelinClientExample.java)]
* [ZeppelinClientExample2](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/ZeppelinClientExample2.java)]
