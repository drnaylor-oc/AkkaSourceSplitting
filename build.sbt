ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.15"

lazy val root = (project in file("."))
  .settings(
    name := "SourceSplitting"
  )

lazy val akkaVersion = "2.6.19"
lazy val scalaTestVersion = "3.2.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-contrib" % "0.11+4-91b2f9fa",
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.1.10",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test
)