---
layout: page
title: "Writing a new Application(Experimental)"
description: "Apache Zeppelin Application is a package that runs on Interpreter process and displays it's output inside of the notebook. Make your own Application in Apache Zeppelin is quite easy."
group: development
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

# Writing a new Application (Experimental)

<div id="toc"></div>

## What is Apache Zeppelin Application

Apache Zeppelin Application is a package that runs on Interpreter process and displays it's output inside of the notebook. While application runs on Interpreter process, it's able to access resources provided by Interpreter through ResourcePool. Output is always rendered by AngularDisplaySystem. Therefore application provides all the possiblities of making interactive graphical application that uses data and processing power of any Interpreter.


## Make your own Application

Writing Application means extending `org.apache.zeppelin.helium.Application`. You can use your favorite IDE and language while Java class files are packaged into jar. `Application` class looks like

```java

/**
 * Constructor. Invoked when application is loaded
 */
public Application(ApplicationContext context);

/**
 * Invoked when there're (possible) updates in required resource set.
 * i.e. invoked after application load and after paragraph finishes.
 */
public abstract void run(ResourceSet args);

/**
 * Invoked before application unload.
 * Application is automatically unloaded with paragraph/notebook removal
 */
public abstract void unload();
```


You can check example applications under [./zeppelin-examples](https://github.com/apache/incubator-zeppelin/tree/master/zeppelin-examples) directory.


## Development mode

In the development mode, you can run your Application in your IDE as a normal java application and see the result inside of Zeppelin notebook.

`org.apache.zeppelin.interpreter.dev.ZeppelinApplicationDevServer` can run Zeppelin Application in development mode.

```java

// entry point for development mode
public static void main(String[] args) throws Exception {

  // add resources for development mode
  LocalResourcePool pool = new LocalResourcePool("dev");
  pool.put("date", new Date());

  // run application in devlopment mode with given resource
  // in this case, Clock.class.getName() will be the application class name  
  ZeppelinApplicationDevServer devServer = new ZeppelinApplicationDevServer(
    Clock.class.getName(), pool.getAll());

  // start development mode
  devServer.start();
  devServer.join();
}
```


In the Zeppelin notebook, run `%dev run` will connect to application running in development mode.


## Package file

Package file is a json file that provides information about the application.
Json file contains the following information

```
{
  name : "[organization].[name]",
  description : "Description",
  artifact : "groupId:artifactId:version",
  className : "your.package.name.YourApplicationClass",
  resources : [
    ["resource.name", ":resource.class.name"],
    ["alternative.resource.name", ":alternative.class.name"]
  ],
  icon : "<i class="icon"></i>"
}

```

#### name

Name is a string in `[group].[name]` format.
`[group]` and `[name]` allow only `[A-Za-z0-9_]`.
Group is normally the name of an organization who creates this application.

#### description

A short description about the application

#### artifact

Location of the jar artifact.
`"groupId:artifactId:version"` will load artifact from maven repository.
If jar exists in the local filesystem, absolute/relative can be used.

e.g.

When artifact exists in Maven repository

```
artifact: "org.apache.zeppelin:zeppelin-examples:0.6.0"
```

When artifact exists in the local filesystem

```
artifact: "zeppelin-example/target/zeppelin-example-0.6.0.jar"
```

#### className

Entry point. Class that extends `org.apache.zeppelin.helium.Application`

#### resources

Two dimensional array that defines required resources by name or by className. Helium Application launcher will compare resources in the ResourcePool with the information in this field and suggest application only when all required resources are available in the ResourcePool.

Resouce name is a string which will be compared with the name of objects in the ResourcePool. className is a string with ":" prepended, which will be compared with className of the objects in the ResourcePool.

Application may require two or more resources. Required resources can be listed inside of the json array. For example, if the application requires object "name1", "name2" and "className1" type of object to run, resources field can be

```
resources: [
  [ "name1", "name2", ":className1", ...]
]
```

If Application can handle alternative combination of required resources, alternative set can be listed as below.

```
resources: [
  [ "name", ":className"],
  [ "altName", ":altClassName1"],
  ...
]
```

Easier way to understand this scheme is

```
resources: [
   [ 'resource' AND 'resource' AND ... ] OR
   [ 'resource' AND 'resource' AND ... ] OR
   ...
]
```


#### icon

Icon to be used on the application button. String in this field will be rendered as a HTML tag.

e.g.

```
icon: "<i class='fa fa-clock-o'></i>"
```

