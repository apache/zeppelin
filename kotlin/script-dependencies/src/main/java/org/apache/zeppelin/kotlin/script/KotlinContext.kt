package org.apache.zeppelin.kotlin.script

import java.util.HashMap
import kotlin.reflect.KMutableProperty0

/**
 * Kotlin REPL has built-in context for getting user-declared functions and variables
 * and setting invokeWrapper for additional side effects in evaluation.
 * It can be accessed inside REPL by name `kc`, e.g. kc.showVars()
 */
class KotlinContext(private val shortenTypes: Boolean, private val wrapperProp: KMutableProperty0<InvokeWrapper?>) {
    val vars: MutableMap<String, KotlinVariableInfo> = HashMap()
    val functions: MutableMap<String, KotlinFunctionInfo> = HashMap()

    fun showVars() {
        for (`var` in vars.values) {
            println(`var`.toString(shortenTypes))
        }
    }

    fun showFunctions() {
        for (`fun` in functions.values) {
            println(`fun`.toString(shortenTypes))
        }
    }

    var wrapper: InvokeWrapper?
        set(value) = wrapperProp.set(value)
        get() = wrapperProp.get()
}