import sbt._
import Keys._

object Dependencies {
  val scalatestVersion = "3.0.0"
  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  val scalacheckVersion = "1.12.4"
  val scalacheck = "org.scalacheck" %% "scalacheck" % scalacheckVersion % "test"

  val commonDependencies: Seq[ModuleID] = Seq(
    scalatest,
    scalacheck
  )
  
}