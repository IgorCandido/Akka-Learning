name := "Akka-Learning"

version := "0.1"

scalaVersion := "2.12.8"

val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % "2.5.22"
val akkaTest = "com.typesafe.akka" %% "akka-testkit" % "2.5.22" % Test
val akka = "com.typesafe.akka" %% "akka-actor" % "2.5.22"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.0-SNAP10" % Test
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
val cats = "org.typelevel" %% "cats-core" % "1.6.0"
val leveldb = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
val leveld80 = "org.iq80.leveldb" % "leveldb" % "0.7"
val leveldbjniAll = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

libraryDependencies ++= Seq(
  akka,
  akkaTest,
  akkaPersistence,
  leveld80,
  leveldbjniAll,
  scalaTest,
  scalaCheck,
  cats
)
