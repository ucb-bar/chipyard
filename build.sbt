import Tests._

val chisel6 = sys.env.get("USE_CHISEL6").isDefined
val chisel6Version = "6.5.0"
val chisel3Version = "3.6.1"
val chiselTestVersion = if (chisel6) "6.0.0" else "0.6.0"
val scalaVersionFromChisel = if (chisel6) "2.13.12" else "2.13.10"

// This gives us a nicer handle to the root project instead of using the
// implicit one
lazy val chipyardRoot = Project("chipyardRoot", file("."))

// keep chisel/firrtl specific class files, rename other conflicts
val chiselFirrtlMergeStrategy = CustomMergeStrategy.rename { dep =>
  import sbtassembly.Assembly.{Project, Library}
  val nm = dep match {
    case p: Project => p.name
    case l: Library => l.moduleCoord.name
  }
  if (Seq("firrtl", "chisel3", "chisel").contains(nm.split("_")(0))) { // split by _ to avoid checking on major/minor version
    dep.target
  } else {
    "renamed/" + dep.target
  }
}

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "1.6",
  scalaVersion := scalaVersionFromChisel,
  assembly / test := {},
  assembly / assemblyMergeStrategy := {
    case PathList("chisel3", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    case PathList("chisel", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    case PathList("firrtl", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    case PathList("META-INF", _*) => MergeStrategy.discard
    // should be safe in JDK11: https://stackoverflow.com/questions/54834125/sbt-assembly-deduplicate-module-info-class
    case x if x.endsWith("module-info.class") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Ytasty-reader",
    "-Ymacro-annotations"), // fix hierarchy API
  unmanagedBase := (chipyardRoot / unmanagedBase).value,
  allDependencies := {
    // drop specific maven dependencies in subprojects in favor of Chipyard's version
    val dropDeps = Seq(("edu.berkeley.cs", "rocketchip"))
    allDependencies.value.filterNot { dep =>
      dropDeps.contains((dep.organization, dep.name))
    }
  },
  libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.3.1",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,

  exportJars := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal))

val rocketChipDir = file("generators/rocket-chip")

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


lazy val chisel6Settings = Seq(
  libraryDependencies ++= Seq("org.chipsalliance" %% "chisel" % chisel6Version),
  addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chisel6Version cross CrossVersion.full)
)
lazy val chisel3Settings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chisel3Version),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chisel3Version cross CrossVersion.full)
)

lazy val chiselSettings = (if (chisel6) chisel6Settings else chisel3Settings) ++ Seq(
  libraryDependencies ++= Seq(
    "org.apache.commons" % "commons-lang3" % "3.12.0",
    "org.apache.commons" % "commons-text" % "1.9"
  )
)

lazy val scalaTestSettings =  Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.+" % "test"
  )
)


// Subproject definitions begin

// -- Rocket Chip --

lazy val hardfloat = freshProject("hardfloat", file("generators/hardfloat/hardfloat"))
  .settings(chiselSettings)
  .settings(commonSettings)
  .dependsOn(if (chisel6) midas_standalone_target_utils else midas_target_utils)
  .settings(scalaTestSettings)

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)
  .settings(scalaTestSettings)

lazy val diplomacy = freshProject("diplomacy", file("generators/diplomacy/diplomacy"))
  .dependsOn(cde)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "diplomacy")

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .dependsOn(hardfloat, rocketMacros, diplomacy, cde)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(scalaTestSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "mainargs" % "0.5.0",
      "org.json4s" %% "json4s-jackson" % "4.0.5",
      "org.scala-graph" %% "graph-core" % "1.13.5"
    )
  )
lazy val rocketLibDeps = (rocketchip / Keys.libraryDependencies)


// -- Chipyard-managed External Projects --

lazy val testchipip = (project in file("generators/testchipip"))
  .dependsOn(rocketchip, rocketchip_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

val stageDir = if (chisel6) "tools/stage/src/main/scala" else "tools/stage-chisel3/src/main/scala"
lazy val chipyard = (project in file("generators/chipyard"))
  .dependsOn(testchipip, rocketchip, boom, rocketchip_blocks, rocketchip_inclusive_cache,
    dsptools, rocket_dsp_utils,
    gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator,
    constellation, mempress, barf, shuttle, caliptra_aes, rerocc,
    compressacc, saturn, ara)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(
    libraryDependencies ++= Seq(
      "org.reflections" % "reflections" % "0.10.2"
    )
  )
  .settings(commonSettings)
  .settings(Compile / unmanagedSourceDirectories += file(stageDir))

lazy val compressacc = (project in file("generators/compress-acc"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val mempress = (project in file("generators/mempress"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val barf = (project in file("generators/bar-fetchers"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val saturn = (project in file("generators/saturn"))
  .dependsOn(rocketchip, shuttle)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val constellation = (project in file("generators/constellation"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val fft_generator = (project in file("generators/fft-generator"))
  .dependsOn(rocketchip, rocket_dsp_utils)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tracegen = (project in file("generators/tracegen"))
  .dependsOn(testchipip, rocketchip, rocketchip_inclusive_cache, boom)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val icenet = (project in file("generators/icenet"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val boom = freshProject("boom", file("generators/boom"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val shuttle = (project in file("generators/shuttle"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val cva6 = (project in file("generators/cva6"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val ara = (project in file("generators/ara"))
  .dependsOn(rocketchip, shuttle)
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

lazy val gemmini = freshProject("gemmini", file("generators/gemmini"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val nvdla = (project in file("generators/nvdla"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val caliptra_aes = (project in file("generators/caliptra-aes-acc"))
  .dependsOn(rocketchip, rocc_acc_utils, testchipip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rerocc = (project in file("generators/rerocc"))
  .dependsOn(rocketchip, constellation, boom, shuttle)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocc_acc_utils = (project in file("generators/rocc-acc-utils"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tapeout = (project in file("./tools/tapeout/"))
  .settings(chisel3Settings) // stuck on chisel3 and SFC
  .settings(commonSettings)
  .settings(scalaVersion := "2.13.10") // stuck on chisel3 2.13.10
  .settings(libraryDependencies ++= Seq("com.typesafe.play" %% "play-json" % "2.9.2"))

val fixedpointDir = if (chisel6) "./tools/fixedpoint" else "./tools/fixedpoint-chisel3"
lazy val fixedpoint = freshProject("fixedpoint", file(fixedpointDir))
  .settings(chiselSettings)
  .settings(commonSettings)

val dsptoolsDir = if (chisel6) "./tools/dsptools" else "./tools/dsptools-chisel3"
lazy val dsptools = freshProject("dsptools", file(dsptoolsDir))
  .dependsOn(fixedpoint)
  .settings(
    chiselSettings,
    commonSettings,
    scalaTestSettings,
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chiseltest" % chiselTestVersion,
      "org.typelevel" %% "spire" % "0.18.0",
      "org.scalanlp" %% "breeze" % "2.1.0",
      "junit" % "junit" % "4.13" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
  ))

lazy val cde = (project in file("tools/cde"))
  .settings(commonSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "cde/src/chipsalliance/rocketchip")

lazy val rocket_dsp_utils = freshProject("rocket-dsp-utils", file("./tools/rocket-dsp-utils"))
  .dependsOn(rocketchip, cde, dsptools)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocketchip_blocks = (project in file("generators/rocket-chip-blocks"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocketchip_inclusive_cache = (project in file("generators/rocket-chip-inclusive-cache"))
  .settings(
    commonSettings,
    Compile / scalaSource := baseDirectory.value / "design/craft")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)

lazy val fpga_shells = (project in file("./fpga/fpga-shells"))
  .dependsOn(rocketchip, rocketchip_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val chipyard_fpga = (project in file("./fpga"))
  .dependsOn(chipyard, fpga_shells)
  .settings(commonSettings)

// Components of FireSim

lazy val firesimAsLibrary = sys.env.get("FIRESIM_STANDALONE") == None
lazy val firesimDir = if(firesimAsLibrary) {
  file("sims/firesim")
} else {
  file("sims/firesim-staging/firesim-symlink")
}

// TODO: AJG: Fix
// Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS
// this should be minimally dependent on firesim
lazy val midas_target_utils = (project in firesimDir / "sim/midas/targetutils")
  .settings(commonSettings)
  .settings(chiselSettings)

// compiles with chisel6 (only the APIs)
lazy val midas_standalone_target_utils = (project in file("tools/midas-targetutils"))
  .settings(commonSettings)
  .settings(chiselSettings)
// TODO: AJG: ^ Fix

// Provides API for bridges to be created in the target.
// Includes target-side of FireSim-provided bridges and their interfaces that are shared
// between FireSim and the target. Minimal in scope (should only depend on Chisel/Firrtl2)
lazy val firesim_lib = (project in firesimDir / "sim/firesim-lib")
  .dependsOn(midas_target_utils)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(scalaTestSettings)

// Interfaces for target-specific bridges shared with FireSim.
// Minimal in scope (should only depend on Chisel/Firrtl2).
// This is copied to FireSim's midas compiler.
lazy val firechip_bridge_interfaces = (project in file("generators/firechip/bridge-interfaces"))
  .settings(
    chiselSettings,
    commonSettings,
  )

// FireSim top-level project.
// Includes, FireSim harness, target-specific bridges, etc.
// It's tests also depend on firesim_lib's test sources.
lazy val firechip = (project in file("generators/firechip/core"))
  .dependsOn(chipyard, firesim_lib % "compile->compile;test->test", firechip_bridge_interfaces)
  .settings(
    chiselSettings,
    commonSettings,
    Test / testGrouping := isolateAllTests( (Test / definedTests).value ),
    Test / testOptions += Tests.Argument("-oF")
  )
  .settings(scalaTestSettings)
