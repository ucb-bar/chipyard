import sbt._
import Keys._

object Dependencies {
  val scalatestVersion = "2.2.5"
  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  val scalacheckVersion = "1.12.4"
  val scalacheck = "org.scalacheck" %% "scalacheck" % scalacheckVersion % "test"

  // Templating!
  val handlebarsVersion =  "2.1.1"
  val handlebars = "com.gilt" %% "handlebars-scala" % handlebarsVersion exclude("org.slf4j", "slf4j-simple")
  // org.slf4j.slf4j-simple's StaticLoggerBinder (from handlebars) conflicts with
  // ch.qos.logback.logback-classic's StaticLoggerBinder (from firrtl).

  val commonDependencies: Seq[ModuleID] = Seq(
    scalatest,
    scalacheck,
    handlebars
  )
  
}
