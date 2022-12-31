import mill._
import mill.scalalib._
import mill.define.{TaskModule, Command}
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.Utest
import coursier.maven.MavenRepository
import $file.common

object v {
  val scala = "2.12.10"
  val utest = ivy"com.lihaoyi::utest:latest.integration"
  val mainargs = ivy"com.lihaoyi::mainargs:0.3.0"
  // for arithmetic
  val upickle = ivy"com.lihaoyi::upickle:latest.integration"
  val osLib = ivy"com.lihaoyi::os-lib:latest.integration"
  val bc = ivy"org.bouncycastle:bcprov-jdk15to18:latest.integration"
  val spire = ivy"org.typelevel::spire:latest.integration"
  val evilplot = ivy"io.github.cibotech::evilplot:latest.integration"
  val chisel3 = ivy"edu.berkeley.cs::chisel3:3.5.5"
  val chisel3plugin = ivy"edu.berkeley.cs:::chisel3-plugin:3.5.5"
  val rocketchip = ivy"edu.berkeley.cs::rocketchip:1.5-SNAPSHOT"
}

object chipyard extends common.ChipyardModule with ScalafmtModule {
  m =>
  def millSourcePath = os.pwd

  def scalaVersion = v.scala

  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-P:chiselplugin:genBundleElements"
  )

  override def scalacPluginIvyDeps = T {
    Seq(
      v.chisel3plugin
    )
  }
  override def ivyDeps = T {
    Seq(
      v.chisel3,
      v.rocketchip
    )
  }
}

object tests extends Module {
  object elaborate extends ScalaModule with ScalafmtModule {
//    override def scalacPluginClasspath = T {
//      Agg(chisel3.plugin.jar())
//    }
//
//    override def scalacOptions = T {
//      super.scalacOptions() ++ Some(chisel3.plugin.jar()).map(path => s"-Xplugin:${path.path}") ++ Seq("-Ymacro-annotations")
//    }

    override def scalaVersion = v.scala

    override def moduleDeps = Seq(chipyard)

    override def ivyDeps = T {
      Seq(
        v.chisel3
      )
    }

    def elaborate = T {
      // class path for `moduleDeps` is only a directory, not a jar, which breaks the cache.
      // so we need to manually add the class files of `moduleDeps` here.
      upstreamCompileOutput()
      mill.modules.Jvm.runLocal(
        finalMainClass(),
        runClasspath().map(_.path),
        Seq(
          "--dir", T.dest.toString,
        ),
      )
      PathRef(T.dest)
    }

    def chiselAnno = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("anno.json") => p }.map(PathRef(_)).get
    }

    def chirrtl = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("fir") => p }.map(PathRef(_)).get
    }

    def topName = T {
      chirrtl().path.last.split('.').head
    }

  }

  object mfccompile extends Module {

    def compile = T {
      os.proc("firtool",
        elaborate.chirrtl().path,
        s"--annotation-file=${elaborate.chiselAnno().path}",
        "-disable-infer-rw",
        "-dedup",
        "-O=debug",
        "--split-verilog",
        "--preserve-values=named",
        "--output-annotation-file=mfc.anno.json",
        s"-o=${T.dest}"
      ).call(T.dest)
      PathRef(T.dest)
    }

    def rtls = T {
      os.read(compile().path / "filelist.f").split("\n").map(str =>
        try {
          os.Path(str)
        } catch {
          case e: IllegalArgumentException if e.getMessage.contains("is not an absolute path") =>
            compile().path / str.stripPrefix("./")
        }
      ).filter(p => p.ext == "v" || p.ext == "sv").map(PathRef(_)).toSeq
    }

    def annotations = T {
      os.walk(compile().path).filter(p => p.last.endsWith("mfc.anno.json")).map(PathRef(_))
    }
  }

}
