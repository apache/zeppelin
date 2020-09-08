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

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed
import java.util.Collections
import scala.collection.JavaConversions._

senv.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
senv.enableCheckpointing(5000)

val data = senv.addSource(new SourceFunction[(Long, String)] with ListCheckpointed[java.lang.Long] {

  val pages = Seq("home", "search", "search", "product", "product", "product")
  var count: Long = 0
  var running : Boolean = true
  // startTime is 2018/1/1
  var startTime: Long = new java.util.Date(2018 - 1900,0,1).getTime
  var sleepInterval = 500

  override def run(ctx: SourceFunction.SourceContext[(Long, String)]): Unit = {
    val lock = ctx.getCheckpointLock

    while (count < 60 && running) {
      lock.synchronized({
        ctx.collect((startTime + count * sleepInterval, pages(count.toInt % pages.size)))
        count += 1
        Thread.sleep(sleepInterval)
      })
    }
  }

  override def cancel(): Unit = {
    running = false
  }

  override def snapshotState(checkpointId: Long, timestamp: Long): java.util.List[java.lang.Long] = {
    Collections.singletonList(count)
  }

  override def restoreState(state: java.util.List[java.lang.Long]): Unit = {
    state.foreach(s => count = s)
  }

}).assignAscendingTimestamps(_._1)

stenv.registerDataStream("log", data, 'time, 'url, 'rowtime.rowtime)
