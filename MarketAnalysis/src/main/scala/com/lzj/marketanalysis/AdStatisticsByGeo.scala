package com.lzj.marketanalysis

import java.sql.Timestamp

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/** *
 *
 * @author Zhi-jiang li
 * @date 2020/3/1 0001 21:13
 **/

//输入的广告点击事件样例类
case class AdClickEvent(userId: Long, adId: Long, province: String, city: String, timestamp: Long)

//输出样例类,按照省份统计的输出结果样例类
case class CountByProvince(windowEnd: String, province: String, count: Long)

//输出的黑名单报警信息
case class BlackListWaring(userId: Long, adId: Long, msg: String)

object AdStatisticsByGeo {
  //定义测输出流的标签
  val blackListOutputTag: OutputTag[BlackListWaring] = new OutputTag[BlackListWaring]("blackList")

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    //读取数据并转换成AdClickEvent
    val resource = getClass.getResource("/AdClickLog.csv")
    val adEventStream = env.readTextFile(resource.getPath)
      .map(data => {
        val dataArray = data.split(",")
        AdClickEvent(dataArray(0).trim.toLong, dataArray(1).trim.toLong, dataArray(2).trim, dataArray(3).trim, dataArray(4).trim.toLong)
      })
      .assignAscendingTimestamps(_.timestamp * 1000L)

    //定义process function,过滤大量刷点击的行为
    val filterBlackListStream = adEventStream
      .keyBy((data => (data.userId, data.adId)))
      .process(new FilterBlackListUser(100))

    //根据省份做分组,开窗聚合
    val adCountStream = filterBlackListStream
      .keyBy(_.province)
      .timeWindow(Time.hours(1), Time.seconds(5))
      .aggregate(new AdCountAgg(), new AdCountResult())

    adCountStream.print("count")
    filterBlackListStream.getSideOutput(blackListOutputTag).print("blackList")

    env.execute("ad statistics job")
  }


  class FilterBlackListUser(maxCount: Int) extends KeyedProcessFunction[(Long, Long), AdClickEvent, AdClickEvent] {
    //定义状态,保存当前用户对当前广告的点击量
    lazy val countState: ValueState[Long] = getRuntimeContext.getState(new ValueStateDescriptor[Long]("count-state", classOf[Long]))

    //保存是否发送过黑名单的状态
    lazy val isSentBlackList: ValueState[Boolean] = getRuntimeContext.getState(new ValueStateDescriptor[Boolean]("isSent-state", classOf[Boolean]))

    //保存定时器触发的时间戳
    lazy val resetTimer: ValueState[Long] = getRuntimeContext.getState(new ValueStateDescriptor[Long]("reSetTime-state", classOf[Long]))

    override def processElement(value: AdClickEvent, ctx: KeyedProcessFunction[(Long, Long), AdClickEvent, AdClickEvent]#Context, out: Collector[AdClickEvent]): Unit = {
      //取出Count状态
      val curCount = countState.value()

      //如果是第一次来,注册定时器,每天00:00触发
      if (curCount == 0) {
        val ts = (ctx.timerService().currentProcessingTime() / (1000 * 60 * 60 * 24) + 1) * (1000 * 60 * 60 * 24)
        resetTimer.update(ts)
        ctx.timerService().registerProcessingTimeTimer(ts)
      }

      //判断计数是否达到上限,如果达到则假如黑名单
      if (curCount >= maxCount) {
        //判断是否发送过黑名单,只发送一次
        if (!isSentBlackList.value()) {
          isSentBlackList.update(true)
          //输出到测输出流
          ctx.output(blackListOutputTag, BlackListWaring(value.userId, value.adId, "Click over maxCount" + maxCount + "times today"))
        }
        return
      }
      //计算状态+1,输出数据到主流
      countState.update(curCount + 1)
      out.collect(value)
    }

    override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[(Long, Long), AdClickEvent, AdClickEvent]#OnTimerContext, out: Collector[AdClickEvent]): Unit = {
      //定时器触发时,清空状态
      if (timestamp == resetTimer.value()) {
        isSentBlackList.clear()
        countState.clear()
        resetTimer.clear()
      }
    }
  }

}


//自定义预聚合函数
class AdCountAgg() extends AggregateFunction[AdClickEvent, Long, Long] {
  override def createAccumulator(): Long = 0L

  override def add(in: AdClickEvent, acc: Long): Long = acc + 1

  override def getResult(acc: Long): Long = acc

  override def merge(acc: Long, acc1: Long): Long = acc + acc1
}

//自定义窗口处理函数
class AdCountResult() extends WindowFunction[Long, CountByProvince, String, TimeWindow] {
  override def apply(key: String, window: TimeWindow, input: Iterable[Long], out: Collector[CountByProvince]): Unit = {
    out.collect(CountByProvince(new Timestamp(window.getEnd).toString, key, input.iterator.next()))
  }
}