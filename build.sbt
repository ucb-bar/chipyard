// See LICENSE for license details.

val defaultVersions = Map(
  "chisel3" -> "3.4-SNAPSHOT",
  "chisel-iotesters" -> "1.5-SNAPSHOT"
)

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.12.8",
  scalacOptions := Seq("-deprecation", "-feature", "-language:reflectiveCalls", "-Xsource:2.11"),
  libraryDependencies ++= Seq("chisel3","chisel-iotesters").map {
    dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
  },
  libraryDependencies in Test ++= Seq(
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "org.scalacheck" %% "scalacheck" % "1.12.4" % "test"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
  )
)

disablePlugins(sbtassembly.AssemblyPlugin)

lazy val mdf = (project in file("mdf/scalalib"))
lazy val macros = (project in file("macros"))
  .dependsOn(mdf)
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "firrtl-interpreter" % "1.2-SNAPSHOT" % Test
    ),
    mainClass := Some("barstools.macros.MacroCompiler")
  ))
  .enablePlugins(sbtassembly.AssemblyPlugin)

lazy val tapeout = (project in file("tapeout"))
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "io.github.daviddenton" %% "handlebars-scala-fork" % "2.3.0"
    )
  ))
  .settings(scalacOptions in Test ++= Seq("-language:reflectiveCalls"))

lazy val root = (project in file(".")).aggregate(macros, tapeout)
