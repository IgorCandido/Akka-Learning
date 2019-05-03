name := "Akka-Learning"

version := "0.1"

scalaVersion := "2.12.8"

val akkaTest = "com.typesafe.akka" %% "akka-testkit" % "2.5.22" % Test
val akka =  "com.typesafe.akka" %% "akka-actor" % "2.5.22"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.0-SNAP10" % Test
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
val cats = "org.typelevel" %% "cats-core" % "1.6.0"

libraryDependencies ++= Seq(akka, akkaTest, scalaTest, scalaCheck, cats)