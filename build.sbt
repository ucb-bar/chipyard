// See LICENSE for license details.

import Dependencies._

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.8",
  libraryDependencies ++= commonDependencies
)

lazy val executionoptions = (project in file("executionoptions"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= executionoptionsDependencies
  )
