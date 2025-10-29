// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.TestModule.ScalaTest
import $ivy.`com.lihaoyi::mill-contrib-bloop:`

import scalalib._
// support BSP
import mill.bsp._

object boom extends ScalaModule { m =>
  override def millSourcePath = os.pwd
  override def scalaVersion = "2.13.14"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-P:chiselplugin:genBundleElements"
  )
  val chiselVersion = "6.5.0"
  val rocketVersion = "1.6-snapshot"
  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:$chiselVersion",
    ivy"org.chipsalliance::cde:$rocketVersion",
    ivy"org.chipsalliance::macros:$rocketVersion",
    ivy"org.chipsalliance::diplomacy-$chiselVersion:$rocketVersion",
    ivy"org.chipsalliance::hardfloat-$chiselVersion:$rocketVersion",
    ivy"org.chipsalliance::rocketchip-$chiselVersion:$rocketVersion",
	ivy"ch.epfl.scala::bloop-config:2.0.3"
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:$chiselVersion",
  )
}
