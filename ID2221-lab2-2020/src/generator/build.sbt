name := "Producer"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.apache.kafka" % "kafka_2.11" % "1.0.0"
libraryDependencies ++= Seq(
("com.datastax.spark" %% "spark-cassandra-connector" % "2.4.0").exclude("io.netty", "netty-handler"),
("com.datastax.cassandra" % "cassandra-driver-core" % "3.0.0").exclude("io.netty", "netty-handler")
)
