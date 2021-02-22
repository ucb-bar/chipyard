// See LICENSE for license details.

val defaultVersions = Map(
  "chisel3" -> "3.4.+",
  "chisel-iotesters" -> "1.5.+"
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
    "org.scalatest" %% "scalatest" % "3.2.2" % "test",
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal
  )
)

disablePlugins(sbtassembly.AssemblyPlugin)

lazy val mdf = (project in file("mdf/scalalib"))
lazy val macros = (project in file("macros"))
  .dependsOn(mdf)
  .settings(commonSettings)
  .settings(
    mainClass := Some("barstools.macros.MacroCompiler")
  )
  .enablePlugins(sbtassembly.AssemblyPlugin)

lazy val tapeout = (project in file("tapeout"))
  .settings(commonSettings)
  .settings(scalacOptions in Test ++= Seq("-language:reflectiveCalls"))

lazy val root = (project in file(".")).aggregate(macros, tapeout)
