import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time

/** *
 *
 * @author Zhi-jiang li
 * @date 2020/2/28 0028 14:01
 * */

//定义输入数据的样例类
case class UserBehavior(userId: Long, itemId: Long, categoryId: Int, behavior: String, timestamp: Long)

object PageView {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    //用相对路径定义数据源
    //val resource = getClass.getResource("/UserBehavior.csv")
    //print(resource.getPath)
    val dataStream = env.readTextFile("H:\\workspace\\ideaDemo\\SparkWorkSpace\\UserBehaviorAnalysis\\NetworkFlowAnalysis\\src\\main\\resources\\UserBehavior.csv")
      .map(data=>{
        val dataArray = data.split(",")
        UserBehavior(dataArray(0).trim.toLong, dataArray(1).trim.toLong, dataArray(2).trim.toInt, dataArray(3).trim.toString, dataArray(4).trim.toLong)
      })
      .assignAscendingTimestamps(_.timestamp * 1000L)
      //只统计PV操作
      .filter(_.behavior == "pv")
      .map(data => ("pv",1))
      .keyBy(_._1)
      .timeWindow(Time.hours(1))
      .sum(1)

    dataStream.print("pv count")

    env.execute("page view job")
  }
}
