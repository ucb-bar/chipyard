import Tests._

// This gives us a nicer handle to the root project instead of using the
// implicit one
lazy val chipyardRoot = Project("chipyardRoot", file("."))

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "1.6",
  scalaVersion := "2.12.10",
  assembly / test := {},
  assembly / assemblyMergeStrategy := { _ match {
    case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
    case _ => MergeStrategy.first}},
  scalacOptions ++= Seq("-deprecation","-unchecked","-Xsource:2.11"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
  unmanagedBase := (chipyardRoot / unmanagedBase).value,
  allDependencies := {
    // drop specific maven dependencies in subprojects in favor of Chipyard's version
    val dropDeps = Seq(("edu.berkeley.cs", "rocketchip"))
    allDependencies.value.filterNot { dep =>
      dropDeps.contains((dep.organization, dep.name))
    }
  },
  exportJars := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal))

val rocketChipDir = file("generators/rocket-chip")

lazy val firesimAsLibrary = sys.env.get("FIRESIM_STANDALONE") == None
lazy val firesimDir = if (firesimAsLibrary) {
  file("sims/firesim/sim/")
} else {
  file("../../sim")
}

/**
  * It has been a struggle for us to override settings in subprojects.
  * An example would be adding a dependency to rocketchip on midas's targetutils library,
  * or replacing dsptools's maven dependency on chisel with the local chisel project.
  *
  * This function works around this by specifying the project's root at src/ and overriding
  * scalaSource and resourceDirectory.
  */
def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      Compile / scalaSource := baseDirectory.value / "main" / "scala",
      Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
    )
}

// Fork each scala test for now, to work around persistent mutable state
// in Rocket-Chip based generators
def isolateAllTests(tests: Seq[TestDefinition]) = tests map { test =>
  val options = ForkOptions()
  new Group(test.name, Seq(test), SubProcess(options))
} toSeq

val chiselVersion = "3.5.1"

lazy val chiselSettings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chiselVersion,
  "org.apache.commons" % "commons-lang3" % "3.12.0",
  "org.apache.commons" % "commons-text" % "1.9"),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full))

val firrtlVersion = "1.5.1"

lazy val firrtlSettings = Seq(libraryDependencies ++= Seq("edu.berkeley.cs" %% "firrtl" % firrtlVersion))

val chiselTestVersion = "2.5.1"

lazy val chiselTestSettings = Seq(libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel-iotesters" % chiselTestVersion))

// Subproject definitions begin

// -- Rocket Chip --

// Rocket-chip dependencies (subsumes making RC a RootProject)
lazy val hardfloat  = (project in rocketChipDir / "hardfloat")
  .settings(chiselSettings)
  .dependsOn(midasTargetUtils)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketConfig = (project in rocketChipDir / "api-config-chipsalliance/build-rules/sbt")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .dependsOn(hardfloat, rocketMacros, rocketConfig)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.apache.commons" % "commons-lang3" % "3.12.0",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )
  .settings( // Settings for scalafix
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused-import"
  )
lazy val rocketLibDeps = (rocketchip / Keys.libraryDependencies)


// -- Chipyard-managed External Projects --

// Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS
lazy val midasTargetUtils = ProjectRef(firesimDir, "targetutils")

lazy val testchipip = (project in file("generators/testchipip"))
  .dependsOn(rocketchip, sifive_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val chipyard = (project in file("generators/chipyard"))
  .dependsOn(testchipip, rocketchip, boom, hwacha, sifive_blocks, sifive_cache, iocell,
    sha3, // On separate line to allow for cleaner tutorial-setup patches
    dsptools, `rocket-dsp-utils`,
    gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val fft_generator = (project in file("generators/fft-generator"))
  .dependsOn(rocketchip, `rocket-dsp-utils`)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tracegen = (project in file("generators/tracegen"))
  .dependsOn(testchipip, rocketchip, sifive_cache, boom)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val icenet = (project in file("generators/icenet"))
  .dependsOn(testchipip, rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val hwacha = (project in file("generators/hwacha"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val boom = (project in file("generators/boom"))
  .dependsOn(testchipip, rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val cva6 = (project in file("generators/cva6"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val ibex = (project in file("generators/ibex"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sodor = (project in file("generators/riscv-sodor"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sha3 = (project in file("generators/sha3"))
  .dependsOn(rocketchip, midasTargetUtils)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(chiselTestSettings)
  .settings(commonSettings)

lazy val gemmini = (project in file("generators/gemmini"))
  .dependsOn(testchipip, rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(chiselTestSettings)
  .settings(commonSettings)

lazy val nvdla = (project in file("generators/nvdla"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val iocell = Project(id = "iocell", base = file("./tools/barstools/") / "src")
  .settings(
    Compile / scalaSource := baseDirectory.value / "main" / "scala" / "barstools" / "iocell",
    Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
  )
  .settings(chiselSettings)
  .settings(commonSettings)

lazy val tapeout = (project in file("./tools/barstools/"))
  .settings(chiselSettings)
  .settings(chiselTestSettings)
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .settings(commonSettings)

lazy val dsptools = freshProject("dsptools", file("./tools/dsptools"))
  .settings(
    chiselSettings,
    chiselTestSettings,
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.+" % "test",
      "org.typelevel" %% "spire" % "0.16.2",
      "org.scalanlp" %% "breeze" % "1.1",
      "junit" % "junit" % "4.13" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
  ))

lazy val `api-config-chipsalliance` = freshProject("api-config-chipsalliance", file("./tools/api-config-chipsalliance"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.+" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
    ))

lazy val `rocket-dsp-utils` = freshProject("rocket-dsp-utils", file("./tools/rocket-dsp-utils"))
  .dependsOn(rocketchip, `api-config-chipsalliance`, dsptools)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sifive_blocks = (project in file("generators/sifive-blocks"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sifive_cache = (project in file("generators/sifive-cache"))
  .settings(
    commonSettings,
    Compile / scalaSource := baseDirectory.value / "design/craft")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)

// Library components of FireSim
lazy val midas      = ProjectRef(firesimDir, "midas")
lazy val firesimLib = ProjectRef(firesimDir, "firesimLib")

lazy val firechip = (project in file("generators/firechip"))
  .dependsOn(chipyard, midasTargetUtils, midas, firesimLib % "test->test;compile->compile")
  .settings(
    chiselSettings,
    commonSettings,
    Test / testGrouping := isolateAllTests( (Test / definedTests).value ),
    Test / testOptions += Tests.Argument("-oF")
  )
lazy val fpga_shells = (project in file("./fpga/fpga-shells"))
  .dependsOn(rocketchip, sifive_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val fpga_platforms = (project in file("./fpga"))
  .dependsOn(chipyard, fpga_shells)
  .settings(commonSettings)
