package com.lzj.hotItems_analysis

import java.util.Properties

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

/** *
 *
 * @author Zhi-jiang li
 * @date 2020/2/28 0028 9:01
 * */
object KafkaProducer {
  def main(args: Array[String]): Unit = {
    writeToKafka("hotitems")
  }

  def writeToKafka(topic: String): Unit={
    val properties = new Properties()
    properties.setProperty("bootstrap.servers","192.168.191.144:9092")
    properties.setProperty("key.serializer","org.apache.kafka.common.serialization.StringSerializer")
    properties.setProperty("value.serializer","org.apache.kafka.common.serialization.StringSerializer")

    //定一个Kafka producer
    val producer = new KafkaProducer[String,String](properties)
    //从文件中读取数据,发送
    val bufferedSource = io.Source.fromFile("H:\\workspace\\ideaDemo\\SparkWorkSpace\\UserBehaviorAnalysis\\HotItemsAnalysis\\src\\main\\resources\\UserBehavior.csv")
    for (line <- bufferedSource.getLines()) {
      val record = new ProducerRecord[String,String](topic,line)
      producer.send(record)
    }
    producer.close()
  }
}
