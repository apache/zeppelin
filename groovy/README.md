## Groovy Interpreter


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
  ] 
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

Starts or continues rendering of `%angular` to output and returns [groovy.xml.MarkupBuilder](https://www.google.com/search?q=groovy.xml.MarkupBuilder)
MarkupBuilder is usefull to generate html (xml)

* `void g.table(obj)`

starts or continues rendering table rows.

obj:  List(rows) of List(columns) where first line is a header 



