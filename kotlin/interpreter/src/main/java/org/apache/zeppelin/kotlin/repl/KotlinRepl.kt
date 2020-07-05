/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.kotlin.repl

import kotlinx.coroutines.runBlocking
import org.apache.zeppelin.interpreter.InterpreterResult
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.kotlin.reflect.ContextUpdater
import org.apache.zeppelin.kotlin.script.BaseScriptClass
import org.apache.zeppelin.kotlin.script.InvokeWrapper
import org.apache.zeppelin.kotlin.script.KotlinContext
import org.apache.zeppelin.kotlin.script.KotlinReflectUtil.SCRIPT_PREFIX
import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import org.jetbrains.kotlin.scripting.resolve.skipExtensionsResolutionForImplicitsExceptInnermost
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.KJvmEvaluatedSnippet
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.isIncomplete
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext
import kotlin.script.experimental.jvm.util.toSourceCodePosition
import kotlin.script.experimental.util.LinkedSnippet

/**
 * Read-evaluate-print loop for Kotlin code.
 * Each code snippet is compiled into Line_N class and evaluated.
 */
class KotlinRepl(properties: KotlinReplProperties) {
    private val compiler: KJvmReplCompilerWithIdeServices = KJvmReplCompilerWithIdeServices()
    private val evaluator: BasicJvmReplEvaluator = BasicJvmReplEvaluator()
    private val counter: AtomicInteger = AtomicInteger(0)

    private val maxResult = properties.maxResult
    private val shortenTypes = properties.shortenTypes
    private var wrapper: InvokeWrapper? = null

    val kotlinContext: KotlinContext = KotlinContext(shortenTypes, ::wrapper)

    private val compilationConfiguration = ScriptCompilationConfiguration {
        hostConfiguration.update { it.withDefaultsFrom(defaultJvmScriptingHostConfiguration) }
        baseClass.put(KotlinType(BaseScriptClass::class))
        fileExtension.put("$SCRIPT_PREFIX.kts")
        defaultImports(BaseScriptClass::class)

        jvm {
            val classpath = scriptCompilationClasspathFromContext("script-dependencies", classLoader = BaseScriptClass::class.java.classLoader)
            updateClasspath(classpath)
            updateClasspath(properties.getClasspath())
        }

        val receiversTypes = mutableListOf<KotlinType>()
        receiversTypes.addAll(properties.implicitReceivers.map { KotlinType(it::class) })
        implicitReceivers(receiversTypes)
        skipExtensionsResolutionForImplicitsExceptInnermost(receiversTypes)
        compilerOptions(listOf("-jvm-target", "1.8"))
    }

    private val evaluationConfiguration = ScriptEvaluationConfiguration {
        implicitReceivers.invoke(v = properties.implicitReceivers)
        constructorArgs.invoke(kotlinContext)
    }

    private val writer: ClassWriter = ClassWriter(properties.outputDir)
    private val contextUpdater = ContextUpdater(kotlinContext, evaluator)

    init {
        //properties.receiver?.kc = kotlinContext
        for (line in properties.codeOnLoad) {
            eval(line)
        }
    }

    val variables: List<org.apache.zeppelin.kotlin.script.KotlinVariableInfo>
        get() = ArrayList(kotlinContext.vars.values)
    val functions: List<org.apache.zeppelin.kotlin.script.KotlinFunctionInfo>
        get() = ArrayList(kotlinContext.functions.values)

    /**
     * Evaluates code snippet and returns interpreter result.
     * REPL evaluation consists of:
     * - Compiling code in JvmReplCompiler
     * - Writing compiled classes to disk
     * - Evaluating compiled classes inside InvokeWrapper
     * - Updating list of user-defined functions and variables
     * - Formatting result
     * @param code Kotlin code to execute
     * @return result of interpretation
     */
    fun eval(code: String): InterpreterResult {
        val snippet: SourceCode = SourceCodeImpl(counter.getAndIncrement(), code)
        val compileResult = runBlocking { compiler.compile(snippet, compilationConfiguration) }
        val compileError = checkCompileError(compileResult)

        val classesList = compileError?.let { return it } ?: (compileResult as ResultWithDiagnostics.Success).value
        val classes = classesList.get()

        writer.writeClasses(snippet, classes)
        val runnable = { runBlocking { evaluator.eval(classesList, evaluationConfiguration) } }
        val evalResult = wrapper?.let { it(runnable) } ?: runnable()

        val interpreterResult = checkEvalError(evalResult)
        contextUpdater.update()
        return interpreterResult
    }

    fun complete(code: String, cursor: Int): List<InterpreterCompletion> {
        val snippet: SourceCode = SourceCodeImpl(counter.getAndIncrement(), code)
        val codePos = cursor.toSourceCodePosition(snippet)
        val completionResult = runBlocking {
            compiler.complete(snippet, codePos, compilationConfiguration)
        }
        return when(completionResult) {
            is ResultWithDiagnostics.Success -> {
                val result = completionResult.value
                result.map { variant ->
                    InterpreterCompletion(variant.text, variant.displayText, variant.tail)
                }.toList()
            }
            else -> {
                emptyList()
            }
        }
    }

    private fun checkCompileError(compileResult: ResultWithDiagnostics<LinkedSnippet<KJvmCompiledScript>>): InterpreterResult? {
        return when(compileResult) {
            is ResultWithDiagnostics.Failure -> {
                when {
                    compileResult.isIncomplete() -> InterpreterResult(InterpreterResult.Code.INCOMPLETE)
                    else -> InterpreterResult(InterpreterResult.Code.ERROR, compileResult.getErrors())
                }
            }
            is ResultWithDiagnostics.Success -> {
                null
            }
        }
    }

    private fun checkEvalError(evalResult: ResultWithDiagnostics<LinkedSnippet<KJvmEvaluatedSnippet>>): InterpreterResult {
        return when(evalResult) {
            is ResultWithDiagnostics.Success -> {
                val pureResult = evalResult.value.get()
                when (val resultValue = pureResult.result) {
                    is ResultValue.Error -> InterpreterResult(InterpreterResult.Code.ERROR, resultValue.error.stackTraceToString())
                    is ResultValue.Unit -> {
                        return InterpreterResult(InterpreterResult.Code.SUCCESS)
                    }
                    is ResultValue.Value -> {
                        val typeString = (if (shortenTypes) org.apache.zeppelin.kotlin.script.KotlinReflectUtil.shorten(resultValue.type) else resultValue.type)!!
                        val valueString = prepareValueString(resultValue.value)
                        InterpreterResult(
                                InterpreterResult.Code.SUCCESS,
                                resultValue.name + ": " + typeString + " = " + valueString)
                    }
                    is ResultValue.NotEvaluated -> {
                        InterpreterResult(InterpreterResult.Code.ERROR, buildString {
                            val cause = evalResult.reports.firstOrNull()?.exception
                            append("This snippet was not evaluated: ")
                            appendLine(cause.toString())
                            cause?.stackTraceToString()?.let { appendLine(it) }
                        })
                    }
                }
            }
            is ResultWithDiagnostics.Failure -> InterpreterResult(InterpreterResult.Code.ERROR, evalResult.getErrors())
        }
    }

    private fun prepareValueString(value: Any?): String {
        if (value == null) {
            return "null"
        }
        if (value !is Collection<*>) {
            return value.toString()
        }
        return if (value.size <= maxResult) value.toString()
        else "[" + value.stream()
                .limit(maxResult.toLong())
                .map { it.toString() }
                .collect(Collectors.joining(",")) +
                " ... " + (value.size - maxResult) + " more]"
    }
}