---
layout: page
title: "Interpreter Execution Hooks (Experimental)"
description: "Apache Zeppelin allows for users to specify additional code to be executed by an interpreter at pre and post-paragraph code execution."
group: manual
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

# Interpreter Execution Hooks (Experimental)

<div id="toc"></div>

## Overview

Apache Zeppelin allows for users to specify additional code to be executed by an interpreter at pre and post-paragraph code execution.
This is primarily useful if you need to run the same set of code for all of the paragraphs within your notebook at specific times.
Currently, this feature is only available for the spark and pyspark interpreters.
To specify your hook code, you may use `z.registerHook()`. 
For example, enter the following into one paragraph:

```python
%pyspark
z.registerHook("post_exec", "print 'This code should be executed before the parapgraph code!'")
z.registerHook("pre_exec", "print 'This code should be executed after the paragraph code!'")
```

These calls will not take into effect until the next time you run a paragraph. 


In another paragraph, enter

```python
%pyspark
print "This code should be entered into the paragraph by the user!"
```

The output should be:

```
This code should be executed before the paragraph code!
This code should be entered into the paragraph by the user!
This code should be executed after the paragraph code!
```

If you ever need to know the hook code, use `z.getHook()`:

```python
%pyspark
print z.getHook("post_exec")

print 'This code should be executed after the paragraph code!'
```
Any call to `z.registerHook()` will automatically overwrite what was previously registered.
To completely unregister a hook event, use `z.unregisterHook(eventCode)`.
Currently only `"post_exec"` and `"pre_exec"` are valid event codes for the Zeppelin Hook Registry system.

Finally, the hook registry is internally shared by other interpreters in the same group.
This would allow for hook code for one interpreter REPL to be set by another as follows:

```scala
%spark
z.unregisterHook("post_exec", "pyspark")
```

The API is identical for both the spark (scala) and pyspark (python) implementations.

### Caveats
Calls to `z.registerHook("pre_exec", ...)` should be made with care. If there are errors in your specified hook code, this will cause the interpreter REPL to become unable to execute any code pass the pre-execute stage making it impossible for direct calls to `z.unregisterHook()` to take into effect. Current workarounds include calling `z.unregisterHook()` from a different interpreter REPL in the same interpreter group (see above) or manually restarting the interpreter group in the UI. 
