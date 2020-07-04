package org.apache.zeppelin.kotlin.repl

import kotlin.script.experimental.api.SourceCode

class SourceCodeImpl(number: Int, override val text: String) : SourceCode {
    override val name: String? = "Line_$number"
    override val locationId: String? = "location_$number"
}