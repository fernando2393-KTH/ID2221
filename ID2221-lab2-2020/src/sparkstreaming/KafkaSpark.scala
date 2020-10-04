package sparkstreaming

import java.util.HashMap
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.kafka._
import kafka.serializer.{DefaultDecoder, StringDecoder}
import org.apache.spark.SparkConf
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka._
import org.apache.spark.storage.StorageLevel
import java.util.{Date, Properties}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, ProducerConfig}
import scala.util.Random

import org.apache.spark.sql.cassandra._
import com.datastax.spark.connector._
import com.datastax.driver.core.{Session, Cluster, Host, Metadata}
import com.datastax.spark.connector.streaming._

object KafkaSpark {
  def main(args: Array[String]) {
    // connect to Cassandra and make a keyspace and table as explained in the document
    val cluster = Cluster.builder().addContactPoint("127.0.0.1").build()
    val session = cluster.connect()
    session.execute("CREATE KEYSPACE IF NOT EXISTS avg_space WITH REPLICATION = { ’class’ : ’SimpleStrategy’, ’replication_factor’ : 1 };")
	session.execute("CREATE TABLE IF NOT EXISTS avg_space.avg (word text PRIMARY KEY, count float);")

    // make a connection to Kafka and read (key, value) pairs from it
	val conf = new SparkConf().setMaster("local[2]").setAppName("AverageCount")
	val ssc = new StreamingContext(conf, Seconds(1))
	ssc.checkpoint(".")

    val kafkaConf = Map("metadata.broker.list" -> "localhost:9092", "zookeeper.connect" -> "localhost:2181",
						"group.id" -> "kafka-spark-streaming", "zookeeper.connection.timeout.ms" -> "1000")
	val topic = Set("avg")
    val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaConf, topic)
	var pairs = messages.map(message => message._2)
	pairs = pairs.map(_.split(",")).map(x => (x(0), x(1).toDouble))
	
	var hashMap = new HashMap()
    // measure the average value for each key in a stateful manner
    def mappingFunc(key: String, value: Option[Double], state: State[Double]): (String, Double) = {
		val newAvg = value.getOrElse(0)
		val oldAvg = state.getOption.getOrElse(0)
		var avg = 0
		if (hashMap.keySet.exists(_ == key)) {
			val cnt = hashMap(key)
			avg = (newAvg + oldAvg * cnt) / (cnt + 1)
			hashMap(key) = cnt + 1 
		}
		else {
			hashMap += (key -> 1)
			avg = newAvg
		}
		state.update(avg)
		(key, avg)
    }

    val stateDstream = pairs.mapWithState(StateSpec.function(mappingFunc))

    // store the result in Cassandra
    stateDstream.saveToCassandra("avg_space", "avg", SomeColumns("word", "count"))
	stateDstream.print()

    ssc.start()
    ssc.awaitTermination()
	session.close()
  }
}
