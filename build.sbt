ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.5.0"

val http4sVersion = "1.0.0-M41"
val doobieVersion = "1.0.0-RC5"

lazy val root = (project in file("."))
  .settings(
    name := "weather-fs2",
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "3.10.2",
      "org.typelevel" %% "cats-core" % "2.12.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % "0.15.0-M1",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.postgresql" % "postgresql" % "42.7.3",
      "com.typesafe" % "config" % "1.4.3",
      "org.apache.logging.log4j" % "log4j-api" % "2.23.1",
      "org.apache.logging.log4j" % "log4j-core" % "2.23.1",
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.23.1",
      "com.lmax" % "disruptor" % "4.0.0"
    ),
    run / fork := true,
    Compile / scalacOptions := Seq(
      "-deprecation",
      "-unchecked",
      "-feature"
    )
  )
