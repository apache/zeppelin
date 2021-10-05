# Developer guide to Kotlin interpreter

The following module adds Kotlin language support to Apache Zeppelin.
Here is the guide to its implementation and how it can be improved and tested. 

## Implementation details
### Kotlin REPL
For interactive Kotlin execution, an instance of `KotlinRepl` is created.
To set REPL properties (such as classpath, generated classes output directory, max result, etc.),
pass `KotlinReplProperties` to its constructor. For example:
```java
KotlinReplProperties replProperties = new KotlinReplProperties()
    .maxResult(1000)
    .shortenTypes(true);
KotlinRepl repl = new KotlinRepl(replProperties);
```

### Variable/function binding
You can also bind variables and functions on REPL creation using implicit receiver language feature.
This means that all code run in REPL will be executed in Kotlin's `with` block with the receiver, 
making the receiver's fields and methods accessible.
   
To add your variables/functions, extend `KotlinReceiver` class (in separate file), declare your fields and methods, and pass an instance of it to
`KotlinReplProperties`. Example:
```java
// In separate file:
class CustomReceiver extends KotlinReceiver {
    public int myValue = 1 // will be converted to Kotlin "var myValue: Int"
    public final String messageTemplate = "Value = %VALUE%" // "val messageTemplate: String"
    
    public String getMessage() {
        return messageTemplate.replace("%VALUE%", String.valueOf(myValue));
    }
}

// In intepreter creation:
replProperties.receiver(new CustomReceiver);
KotlinRepl repl = new KotlinRepl(replProperties);
repl.eval("getMessage()"); // will return interpreterResult with "Value = 1" string
``` 

In `KotlinInterpreter` REPL properties are created on construction, are accessible via `getKotlinReplProperties` method,
and are used in REPL creation on `open()`.

### Generated class files
Each code snippet run in REPL is registered as a separate class and saved in location 
specified by `outputDir` REPL property. Anonymous classes and lambdas also get saved there under specific names. 

This is needed for Spark to send classes to remote executors, and in Spark Kotlin interpreter this directory is the same 
as in `sparkContext` option `spark.repl.class.outputDir`.

### Kotlin Spark Interpreter
Kotlin interpreter in Spark intepreter group takes `SparkSession`, `JavaSparkContext`, `SQLContext` 
and `ZeppelinContext` from `SparkInterpreter` in the same session and binds them in its scope.
  
## Testing
Kotlin Interpreter and Spark Kotlin Interpreter come with unit tests. 
They can be run with \
`./mvnw clean test` \
in `$ZEPPELIN_HOME/kotlin` for base Kotlin Interpreter and \
`./mvnw -Dtest=KotlinSparkInterpreterTest test` \
in `$ZEPPELIN_HOME/spark/interpreter` for Spark Kotlin Interpreter.

To test manually, build Zeppelin with \
`./mvnw clean package -DskipTests` \
and create a note with `kotlin` interpreter for base or `spark` for Spark. 
In Spark interpreter, add `%spark.kotlin` in the start of paragraph to use Kotlin Spark Interpreter.

Example:
```$kotlin
%spark.kotlin
val df = spark.range(10)
df.show()
```
## Possible Improvements
* It would be great to bind `ZeppelinContext` to base Kotlin interpreter, but for now I had trouble instantiating it 
inside KotlinInterpreter.
* When Kotlin has its own Spark API, it will be good to move to it. Currently in Java Spark API Kotlin 
can not use things like `forEach` because of ambiguity between `Iterable<?>.forEach` and `Map<?, ?>.forEach` 
(`foreach` from Spark's API does work, though).
* The scoped mode for Kotlin Spark Interpreter currently has issues with having the same class output directory 
for different intepreters, leading to overwriting classes. Adding prefixes to generated classes or putting them
 in separate directories leads to `ClassNotFoundException` on Spark executors.
