name := """play-hibernate-util"""
organization := "com.jpozarycki"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  "org.projectlombok" % "lombok" % "1.18.16" % "provided",
  "org.hibernate" % "hibernate-core" % "5.4.18.Final",
  "com.h2database" % "h2" % "1.4.197" % Test,
  "org.mockito" % "mockito-core" % "3.5.15" % Test,
  "junit" % "junit" % "4.13.1" % Test,
  guice
)
