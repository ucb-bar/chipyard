import sbt._
import Keys._

object Dependencies {
  val scalatestVersion = "2.2.5"
  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  val scalacheckVersion = "1.12.4"
  val scalacheck = "org.scalacheck" %% "scalacheck" % scalacheckVersion % "test"

  // Templating!
  val handlebarsVersion =  "2.1.1"
  val handlebars = "com.gilt" %% "handlebars-scala" % handlebarsVersion

  val commonDependencies: Seq[ModuleID] = Seq(
    scalatest,
    scalacheck,
    handlebars
  )
  
}