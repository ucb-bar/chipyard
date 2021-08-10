// See LICENSE for license details.

val defaultVersions = Map(
  "chisel3" -> "3.5-SNAPSHOT",
  "chisel-iotesters" -> "2.5-SNAPSHOT"
)

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "0.4-SNAPSHOT",
  scalaVersion := "2.12.10",
  scalacOptions := Seq("-deprecation", "-feature", "-language:reflectiveCalls", "-Xsource:2.11"),
  libraryDependencies ++= Seq("chisel3","chisel-iotesters").map {
    dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
  },
  libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-json" % "2.9.2",
    "org.scalatest" %% "scalatest" % "3.2.9" % "test",
    "org.apache.logging.log4j" % "log4j-api" % "2.11.2",
    "org.apache.logging.log4j" % "log4j-core" % "2.11.2"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal
  )
)

//disablePlugins(sbtassembly.AssemblyPlugin)
//
//enablePlugins(sbtassembly.AssemblyPlugin)

lazy val tapeout = (project in file("tapeout"))
  .settings(commonSettings)
  .settings(scalacOptions in Test ++= Seq("-language:reflectiveCalls"))
  .settings(fork := true)
  .settings(
    mainClass := Some("barstools.macros.MacroCompiler")
  )
  .enablePlugins(sbtassembly.AssemblyPlugin)

lazy val root = (project in file(".")).aggregate(tapeout)
