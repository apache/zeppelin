---
layout: page
title: "Kotlin interpreter in Apache Zeppelin"
description: "Kotlin is a cross-platform, statically typed, general-purpose programming language with type inference."
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

# Kotlin interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
Kotlin is a cross-platform, statically typed, general-purpose programming language with type inference.
It is designed to interoperate fully with Java, and the JVM version of its standard library depends on the Java Class Library, but type inference allows its syntax to be more concise.

## Configuration
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>zeppelin.kotlin.maxResult</td>
    <td>1000</td>
    <td>Max n
  <tr>
    <td>zeppelin.kotlin.shortenTypes</td>
    <td>true</td>
    <td>Display shortened types instead of full, e.g. Int vs kotlin.Int</td>
  </tr>
</table>

## Using the Kotlin Interpreter
In a paragraph, use %kotlin to select the Kotlin interpreter and then write your script.

## Example

```kotlin
%kotlin 

fun square(n: Int): Int = n * n
```

## Kotlin Context
Kotlin context is accessible via `kc` object bound to the interpreter. 
It holds `vars` and `functions` fields that return all user-defined variables and functions present in the interpreter.
You can also print variables or functions by calling `kc.showVars()` or `kc.showFunctions()`.

### Example


```kotlin
%kotlin 
fun square(n: Int): Int = n * n

val greeter = { s: String -> println("Hello $s!") }
val l = listOf("Drive", "to", "develop")

kc.showVars()
kc.showFunctions()
```
Output:
```
l: List<String> = [Drive, to, develop]
greeter: (String) -> Unit = (kotlin.String) -> kotlin.Unit
fun square(Int): Int
```
