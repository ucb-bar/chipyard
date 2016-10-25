import sbt._
import Keys._

object Dependencies {
  val scalatestVersion = "3.0.0"
  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  val scoptVersion = "3.4.0"
  val scopt =  "com.github.scopt" %% "scopt" % scoptVersion

  val commonDependencies: Seq[ModuleID] = Seq(
		scalatest
  )
  val executionoptionsDependencies: Seq[ModuleID] = Seq(
    scopt
  )
}
