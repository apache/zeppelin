---
layout: page
title: "Apache Groovy Interpreter for Apache Zeppelin"
description: "Apache Groovy is a powerful, optionally typed and dynamic language, with static-typing and static compilation capabilities, for the Java platform aimed at improving developer productivity thanks to a concise, familiar and easy to learn syntax."
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

# Groovy Interpreter for Apache Zeppelin


### Samples

```groovy
%groovy
//get a parameter defined as z.angularBind('ngSearchParam', value, 'paragraph_id')
//g is a context object for groovy to avoid mix with z object
def param = g.angular('ngSearchParam')
//send request https://www.googleapis.com/customsearch/v1?q=ngSearchParam_value
def r = HTTP.get(
  //assume you defined the groovy interpreter property
  //   `search_baseurl`='https://www.googleapis.com/customsearch/v1'
  //in groovy object o.getProperty('A') == o.'A' == o.A == o['A']
  url : g.search_baseurl,
  query: [ q: param ],
  headers: [
    'Accept':'application/json',
    //'Authorization:' : g.getProperty('search_auth'),
  ],
  ssl : g.getProperty('search_ssl') // assume groovy interpreter property search_ssl = HTTP.getNaiveSSLContext()
)
//check response code
if( r.response.code==200 ) {
  g.html().with{ 
    //g.html() renders %angular to output and returns groovy.xml.MarkupBuilder
    h2("the response ${r.response.code}")
    span( r.response.body )
    h2("headers")
    pre( r.response.headers.join('\n') )
  }
} else {
  //just to show that it's possible to use println with multiline groovy string to render output
  println("""%angular
    <script> alert ("code=${r.response.code} \n msg=${r.response.message}") </script>
  """)
}
```


```groovy
%groovy

//renders a table with headers a, b, c  and two rows
g.table(
  [
    ['a','b','c'],
    ['a1','b1','c1'],
    ['a2','b2','c2'],
  ]
)
```

### the `g` object

* `g.angular(String name)`

   Returns angular object by name. Look up notebook scope first and then global scope.


* `g.angularBind(String name, Object value)`
 
   Assign a new `value` into angular object `name`


* `java.util.Properties g.getProperties()`

   returns all properties defined for this interpreter


* `String g.getProperty('PROPERTY_NAME')` 

   ```groovy 
   g.PROPERTY_NAME
   g.'PROPERTY_NAME'
   g['PROPERTY_NAME']
   g.getProperties().getProperty('PROPERTY_NAME')
   ```

   All above the accessor to named property defined in groovy interpreter.
   In this case with name `PROPERTY_NAME`


* `groovy.xml.MarkupBuilder g.html()`

   Starts or continues rendering of `%angular` to output and returns [groovy.xml.MarkupBuilder](http://groovy-lang.org/processing-xml.html#_markupbuilder)
   MarkupBuilder is usefull to generate html (xml)

* `void g.table(obj)`

   starts or continues rendering table rows.

   obj:  List(rows) of List(columns) where first line is a header 


* `g.input(name, value )`

   Creates `text` input with value specified. The parameter `value` is optional.
   
* `g.select(name, default, Map<Object, String> options)`

   Creates `select` input with defined options. The parameter `default` is optional.

   ```g.select('sex', 'm', ['m':'man', 'w':'woman'])```
   
* `g.checkbox(name, Collection checked, Map<Object, String> options)`

   Creates `checkbox` input.
   
* `g.get(name, default)`

   Returns interpreter-based variable. Visibility depends on interpreter scope. The parameter `default` is optional.

* `g.put(name, value)`

   Stores new value into interpreter-based variable. Visibility depends on interpreter scope.
   
