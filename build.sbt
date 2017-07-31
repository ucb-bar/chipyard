// See LICENSE for license details.

import Dependencies._

val defaultVersions = Map(
  "chisel3" -> "3.1-SNAPSHOT",
  "chisel-iotesters" -> "1.2-SNAPSHOT"
)

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-deprecation", "-feature", "-language:reflectiveCalls"),
  libraryDependencies ++= commonDependencies,
  libraryDependencies ++= Seq("chisel3","chisel-iotesters").map {
    dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
  },
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
  )
)

lazy val mdf = (project in file("mdf/scalalib"))
lazy val macros = (project in file("macros"))
  .dependsOn(mdf)
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "firrtl-interpreter" % "0.1-SNAPSHOT" % Test
    )
  ))

lazy val tapeout = (project in file("tapeout"))
  .settings(commonSettings)
  .settings(scalacOptions in Test ++= Seq("-language:reflectiveCalls"))

lazy val root = (project in file(".")).aggregate(macros, tapeout)
