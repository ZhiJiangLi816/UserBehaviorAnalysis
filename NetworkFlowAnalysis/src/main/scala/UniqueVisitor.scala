import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.AllWindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/** *
 *
 * @author Zhi-jiang li
 * @date 2020/2/28 0028 14:37
 **/

case class UvCount(windowEnd: Long, uvCount: Long)

object UniqueVisitor {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    val dataStream = env.readTextFile("H:\\workspace\\ideaDemo\\SparkWorkSpace\\UserBehaviorAnalysis\\NetworkFlowAnalysis\\src\\main\\resources\\UserBehavior.csv")
      .map(data => {
        val dataArray = data.split(",")
        UserBehavior(dataArray(0).trim.toLong, dataArray(1).trim.toLong, dataArray(2).trim.toInt, dataArray(3).trim.toString, dataArray(4).trim.toLong)
      })
      .assignAscendingTimestamps(_.timestamp * 1000L)
      //只统计PV操作
      .filter(_.behavior == "pv")
      .timeWindowAll(Time.hours(1))
      .apply(new UvCountByWindow())

    dataStream.print()
    env.execute("uv job")
  }
}

class UvCountByWindow() extends AllWindowFunction[UserBehavior, UvCount, TimeWindow] {
  override def apply(window: TimeWindow, input: Iterable[UserBehavior], out: Collector[UvCount]): Unit = {
    //定义一个scala set,用于保存所有的数据userId,并去重
    var idSet = Set[Long]()
    //把当前窗口所有数据的ID收集到Set中,最后输出的Set的大小
    for( userBehavior <- input){
      idSet += userBehavior.userId
    }
    out.collect(UvCount(window.getEnd,idSet.size))
  }
}
