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
package org.apache.zeppelin.kotlin.script

import kotlin.reflect.KProperty

class KotlinVariableInfo(private val valueGetter: () -> Any, val descriptor: KProperty<*>) {
    val name: String
        get() = descriptor.name
    val type: String
        get() = descriptor.returnType.toString()

    var _value: Any = valueGetter()
    val value: Any
        get() = _value

    fun toString(shortenTypes: Boolean): String {
        var type = type
        if (shortenTypes) {
            type = KotlinReflectUtil.shorten(type)
        }
        return "$name: $type = $value"
    }

    fun update() {
        _value = valueGetter()
    }

    override fun toString(): String {
        return toString(false)
    }
}
