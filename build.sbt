name := "AKKA-STREAMS-WEBSOCKET-chat-v.3"

version := "1.0"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion
)
