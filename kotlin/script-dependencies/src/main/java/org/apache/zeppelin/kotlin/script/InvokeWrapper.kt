package org.apache.zeppelin.kotlin.script

interface InvokeWrapper {
    operator fun <T> invoke(body: () -> T): T // e.g. for capturing io
}