name := """play-hibernate-util"""
organization := "com.jpozarycki"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.197" % Test,
  "org.hibernate" % "hibernate-core" % "5.4.18.Final",
  guice
)
