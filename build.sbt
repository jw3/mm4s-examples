name := "mm4s-examples"
organization := "com.github.jw3"
description := "Mattermost for Scala examples"
version := "0.1-SNAPSHOT"
licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion := "2.11.7"
scalacOptions += "-target:jvm-1.8"

resolvers += "jw3 at bintray" at "https://dl.bintray.com/jw3/maven"

libraryDependencies ++= {
  val akkaVersion = "2.4.2"
  val scalaTest = "3.0.0-M15"

  Seq(
    "com.rxthings" %% "akka-injects" % "0.4",

    "io.reactivex" %% "rxscala" % "0.26.0",
    "com.shekhargulati.reactivex" % "rx-docker-client" % "0.2.0",

    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaVersion,

    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,

    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Runtime,

    "org.scalactic" %% "scalactic" % scalaTest % Test,
    "org.scalatest" %% "scalatest" % scalaTest % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
  )
}

com.updateimpact.Plugin.apiKey in ThisBuild :=
  sys.env.getOrElse("UPDATEIMPACT_API_KEY", (com.updateimpact.Plugin.apiKey in ThisBuild).value)
