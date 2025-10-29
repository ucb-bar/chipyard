
import mill._
import mill.scalalib._
import mill.scalalib.publish._

import scalafmt._

import $ivy.`com.goyeau::mill-scalafix::0.3.2`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.11:0.4.0`
import de.tobiasroeser.mill.vcs.version.VcsVersion

import $file.dependencies.cde.common
import $file.dependencies.chisel.build
import $file.common

object v {
  val scala = "2.13.16"
  val chiselCrossVersions = Map(
    "6.7.0" -> (ivy"org.chipsalliance::chisel:6.7.0", ivy"org.chipsalliance:::chisel-plugin:6.7.0"),
    // build from project from source
    "source" -> (ivy"org.chipsalliance::chisel:99", ivy"org.chipsalliance:::chisel-plugin:99"),
  )
  val sourcecode = ivy"com.lihaoyi::sourcecode:0.3.1"
}

object cde extends CDE

trait CDE extends millbuild.dependencies.cde.common.CDEModule with DiplomacyPublishModule with ScalaModule {
  def scalaVersion: T[String] = T(v.scala)

  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
}

// Build form source only for dev
object chisel extends Chisel

trait Chisel
  extends millbuild.dependencies.chisel.build.Chisel {
  def crossValue = v.scala
  override def millSourcePath = os.pwd / "dependencies" / "chisel"
  def scalaVersion = T(v.scala)
}


object diplomacy extends Cross[Diplomacy](v.chiselCrossVersions.keys.toSeq)

trait Diplomacy
    extends millbuild.common.DiplomacyModule
    with DiplomacyPublishModule
    with ScalafmtModule
    with ScalafixModule
    with Cross.Module[String] {

  def scalaVersion: T[String] = T(v.scala)

  def millSourcePath = os.pwd / "diplomacy"

  def chiselModule = Option.when(crossValue == "source")(chisel)
  def chiselPluginJar = T(Option.when(crossValue == "source")(chisel.pluginModule.jar()))
  def chiselIvy = Option.when(crossValue != "source")(v.chiselCrossVersions(crossValue)._1)
  def chiselPluginIvy = Option.when(crossValue != "source")(v.chiselCrossVersions(crossValue)._2)
  def cdeModule = cde
  def sourcecodeIvy = v.sourcecode
}

trait DiplomacyPublishModule extends PublishModule {
  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "org.chipsalliance",
    url = "https://www.github.com/chipsalliance/diplomacy",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("chipsalliance", "diplomacy"),
    developers = Seq(
      Developer("terpstra", "Wesley W. Terpstra", "https://github.com/terpstra"),
      Developer("hcook", "Henry Cook", "https://github.com/hcook"),
      Developer("sequencer", "Jiuyang Liu", "https://github.com/sequencer")
    )
  )
}
