import sbt._

object Dependencies {

  lazy val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % "3.1.3"
  )

  lazy val cats = Seq(
    "org.typelevel" %% "cats-core" % "2.1.1",
    "org.typelevel" %% "cats-effect" % "2.1.4",
    "org.typelevel" %% "kittens" % "2.1.0",
    "org.typelevel" %% "mouse" % "0.25"
  )

  lazy val fs2 = Seq(
    "co.fs2" %% "fs2-core" % "2.4.0",
    "co.fs2" %% "fs2-io" % "2.4.0"
  )

  lazy val fs2Process = Seq(
    "eu.monniot" %% "fs2-process" % "0.3.0"
  )

  lazy val contextApplied = "org.augustjune" %% "context-applied" % "0.1.4"

  lazy val shapeless = Seq(
    "com.chuusai" %% "shapeless" % "2.3.3"
  )

  lazy val logback = Seq("ch.qos.logback" % "logback-classic" % "1.2.3")

  lazy val slf4j = Seq("org.slf4j" % "slf4j-simple" % "1.7.30")

  lazy val scodec = Seq(
    "org.scodec" %% "scodec-core" % "1.11.7", 
    "org.scodec" %% "scodec-bits" % "1.1.20",
    "org.scodec" %% "scodec-stream" % "2.0.0"
  )

}
