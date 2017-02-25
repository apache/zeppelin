## Groovy Interpreter


### Samples

```groovy
%groovy
//get a parameter defined as z.angularBind('ngSearchParam', value, 'paragraph_id')
//g is a context object for groovy to avoid mix with z object
def param = g.angular('ngSearchParam')
//send request https://www.googleapis.com/customsearch/v1?q=ngSearchParam_value
def r = HTTP.get(
  //assume you defined groovy interpreter property `search_baseurl`='https://www.googleapis.com/customsearch/v1'
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
