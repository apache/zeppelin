# Developer guide to Kotlin interpreter

The following module adds Kotlin language support to Apache Zeppelin.
Here is the guide to its implementation and how it can be improved and tested. 

## Implementation details
### Kotlin Repl
For interactive Kotlin execution, an instance of `KotlinRepl` is created.
To set REPL properties (such as classpath, generated classes output directory, max result, etc.),
pass `KotlinReplProperties` to its constructor. For example:
```$java
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
```$java
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
