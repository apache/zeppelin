package org.apache.zeppelin.kotlin.context;

/**
  The execution context for lines in Kotlin REPL.
  It is passed to the script as an implicit receiver, identical to:
  with (context) {
    ...
  }
 */
public class KotlinReceiver {}
