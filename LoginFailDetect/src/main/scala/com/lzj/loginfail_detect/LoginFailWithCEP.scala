package com.lzj.loginfail_detect

import java.util

import org.apache.flink.cep.PatternSelectFunction
import org.apache.flink.cep.scala.CEP
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time

/** *
 *
 * @author Zhi-jiang li
 * @date 2020/3/3 0003 23:21
 **/
object LoginFailWithCEP {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    // 1.读取数据,创建简单事件流
    val resource = getClass.getResource("/LoginLog.csv")
    val loginEventStream = env.readTextFile(resource.getPath)
      .map(data => {
        val dataArray = data.split(",")
        LoginEvent(dataArray(0).trim.toLong, dataArray(1).trim, dataArray(2).trim, dataArray(3).trim.toLong)
      })
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[LoginEvent](Time.seconds(5)) {
        override def extractTimestamp(element: LoginEvent): Long = element.eventTime * 1000
      })
      .keyBy(_.userId)

    // 2.定义匹配模式
    val loginFailPattern = Pattern.begin[LoginEvent]("begin")
      .where(_.eventType == "fail")
      .next("next")
      .where(_.eventType == "fail")
      .within(Time.seconds(2))

    // 3.在事件流上应用模式,得到一个patternStream
    val patternStream = CEP.pattern(loginEventStream,loginFailPattern)

    // 4.从pattern Stream上应用select function.检查出匹配事件序列
    val loginFailDataStream = patternStream.select(new LoginFailMatch())

    loginFailDataStream.print()
    env.execute("login fall with CEP job")
  }
}


class LoginFailMatch() extends PatternSelectFunction[LoginEvent,Waring]{
  override def select(map: util.Map[String, util.List[LoginEvent]]): Waring = {
    //从map中按照名称取出对应的事件
    val firstFail = map.get("begin").iterator().next()
    val lastFail = map.get("next").iterator().next()
    Waring(firstFail.userId,firstFail.eventTime,lastFail.eventTime,"login fail")
  }
}