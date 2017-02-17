// See LICENSE for license details.

import Dependencies._

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-deprecation", "-feature"),
  libraryDependencies ++= commonDependencies
)

val defaultVersions = Map(
  "chisel3" -> "3.1-SNAPSHOT",
  "chisel-iotesters" -> "1.2-SNAPSHOT"
)

lazy val tapeout = (project in file("tapeout"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq("chisel3","chisel-iotesters").map {
      dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
    }
  )