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

import java.io.File

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

    val codeOnLoad: List<String>
        get() = codeOnLoadSet
    var outputDir: String? = null
        private set
    var maxResult = 1000
        private set
    var shortenTypes = true
        private set

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

    fun getClasspath(): List<File> {
        return classpath.map { File(it) }
    }

    init {
        val javaClasspath = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray()
        classpath.addAll(javaClasspath)
    }
}