import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed
import java.util.Collections
import scala.collection.JavaConversions._

senv.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
senv.enableCheckpointing(15000)

val data = senv.addSource(new SourceFunction[(Long, String)] with ListCheckpointed[java.lang.Long] {

  val pages = Seq("home", "search", "search", "product", "product", "product")
  var count: Long = 0
  var running : Boolean = true
  // startTime is 2018/1/1
  var startTime: Long = new java.util.Date(2018 - 1900,0,1).getTime
  var sleepInterval = {{sleep_interval}}

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
