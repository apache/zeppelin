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

import org.apache.zeppelin.kotlin.script.DependsOn
import java.io.File
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

/**
 * Class that holds properties for Kotlin REPL creation,
 * namely implicit receiver, classpath, preloaded code, directory for class bytecode output,
 * max result limit and shortening types flag.
 *
 * Set its parameters by chaining corresponding methods, e.g.
 * properties.outputDir(dir).shortenTypes(false)
 *
 * Get its parameters via getters.
 */
class KotlinReplProperties {
    private val classpath: MutableSet<String> = HashSet()
    private val codeOnLoadSet: MutableList<String> = mutableListOf()
    private val receivers: MutableList<Any> = mutableListOf()

    val codeOnLoad: List<String>
        get() = codeOnLoadSet
    var outputDir: String? = null
        private set
    var maxResult = 1000
        private set
    var shortenTypes = true
        private set
    val implicitReceivers: List<Any>
        get() = receivers

    fun classPath(path: String): KotlinReplProperties {
        classpath.add(path)
        return this
    }

    fun classPath(paths: Collection<String>): KotlinReplProperties {
        classpath.addAll(paths)
        return this
    }

    fun codeOnLoad(code: String): KotlinReplProperties {
        codeOnLoadSet.add(code)
        return this
    }

    fun codeOnLoad(code: Collection<String>?): KotlinReplProperties {
        codeOnLoadSet.addAll(code!!)
        return this
    }

    fun outputDir(outputDir: String?): KotlinReplProperties {
        this.outputDir = outputDir
        return this
    }

    fun maxResult(maxResult: Int): KotlinReplProperties {
        this.maxResult = maxResult
        return this
    }

    fun shortenTypes(shortenTypes: Boolean): KotlinReplProperties {
        this.shortenTypes = shortenTypes
        return this
    }

    fun addImplicitReceiver(receiver: Any): KotlinReplProperties {
        receivers.add(receiver)
        return this
    }

    fun getClasspath(): List<File> {
        println("Current classpath in Kotlin interpreter:")
        println(classpath.joinToString("\n"))
        return classpath.map { File(it) }
    }

    private fun getTestClasspath(): List<String> {
        val javaClasspath = System.getProperty("java.class.path").split(File.pathSeparator)
        return javaClasspath.filter { s ->
            val sep = File.separator
            listOf(
                    "spark${sep}interpreter",
                    "kotlin${sep}script-dependencies",
                    "zeppelin-interpreter${sep}",
                    "org${sep}jetbrains${sep}kotlin${sep}kotlin",
                    "org${sep}jetbrains${sep}annotations",
                    "org${sep}apache${sep}spark${sep}spark-",
                    "org${sep}scala-lang"
            ).any {
                s.contains(it)
            }
        }
        /*
        return scriptCompilationClasspathFromContext(
                "spark-interpreter",
                "kotlin-stdlib",
                "kotlin-reflect",
                "kotlin-script-runtime",
                classLoader = DependsOn::class.java.classLoader
        ).map { it.absolutePath }
         */
    }

    init {
        val javaClasspath = getTestClasspath() // System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray()
        println("Initial Java classpath in Kotlin interpreter:")
        println(javaClasspath.joinToString("\n"))

        val isKotlinJar = Regex("kotlin-[a-z]+(-[a-z]+)*(-.*)?\\.jar")
        classpath.addAll(javaClasspath) // .filter { !it.matches(isKotlinJar) }
    }
}