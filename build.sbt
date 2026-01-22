import Tests._

val chisel6Version = "6.7.0"
val chisel7Version = "7.0.0-RC4"
val chiselTestVersion = "6.0.0"
val scalaVersionFromChisel = "2.13.16"

val chisel3Version = "3.6.1"

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
lazy val chisel7Settings = Seq(
  libraryDependencies ++= Seq("org.chipsalliance" %% "chisel" % chisel7Version),
  addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chisel7Version cross CrossVersion.full)
)
lazy val chisel3Settings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chisel3Version),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chisel3Version cross CrossVersion.full)
)

// Select Chisel 7 when USE_CHISEL7 is set in the environment; default to Chisel 6.
lazy val chiselSettings = (if (sys.env.contains("USE_CHISEL7")) chisel7Settings else chisel6Settings) ++ Seq(
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

lazy val hardfloat = {
  val useChisel7 = sys.env.contains("USE_CHISEL7")
  var hf = freshProject("hardfloat", file("generators/hardfloat/hardfloat"))
    .settings(chiselSettings)
    .settings(commonSettings)
    .settings(scalaTestSettings)
  if (!useChisel7) {
    hf = hf.dependsOn(midas_target_utils)
  }
  hf
}

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

lazy val testchipip = withInitCheck((project in file("generators/testchipip")), "testchipip")
  .dependsOn(rocketchip, rocketchip_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val chipyard = {
  val useChisel7 = sys.env.contains("USE_CHISEL7")
  // Base chipyard project with always-on dependencies
  // Use explicit Project(...) so the project id remains 'chipyard'
  val baseProjects: Seq[ProjectReference] =
    Seq(
      testchipip, rocketchip, boom, rocketchip_blocks, rocketchip_inclusive_cache,
      icenet, tracegen,
      constellation, barf, shuttle, rerocc,
    ).map(sbt.Project.projectToRef) ++
    (if (useChisel7) Seq() else Seq(sbt.Project.projectToRef(firrtl2_bridge))) ++
    (if (useChisel7) Seq() else Seq(sbt.Project.projectToRef(dsptools), sbt.Project.projectToRef(rocket_dsp_utils)))

  val baseDeps: Seq[sbt.ClasspathDep[sbt.ProjectReference]] =
    baseProjects.map(pr => sbt.ClasspathDependency(pr, None))

  // Optional settings to exclude specific sources under Chisel 7
  val dspExcludeSettings: Seq[Def.Setting[_]] = if (useChisel7) Seq(
    Compile / unmanagedSources := {
      val files = (Compile / unmanagedSources).value
      val root = (ThisBuild / baseDirectory).value
      val excludeList = Seq(
        // Directories or files relative to repo root
        "generators/chipyard/src/main/scala/example/dsptools",
        "generators/chipyard/src/main/scala/config/MMIOAcceleratorConfigs.scala",
        "generators/chipyard/src/main/scala/config/TutorialConfigs.scala",
        "generators/chipyard/src/main/scala/upf"
      ).map(p => (root / p).getCanonicalFile)
      val (excludeDirs, excludeFiles) = excludeList.partition(_.isDirectory)
      files.filterNot { f =>
        val cf = f.getCanonicalFile
        excludeFiles.contains(cf) || excludeDirs.exists(d => cf.toPath.startsWith(d.toPath))
      }
    }
  ) else Seq.empty

  var cy = Project(id = "chipyard", base = file("generators/chipyard"))
    .dependsOn(baseDeps: _*)
    .settings(libraryDependencies ++= rocketLibDeps.value)
    .settings(
      libraryDependencies ++= Seq(
        "org.reflections" % "reflections" % "0.10.2"
      )
    )
    .settings(commonSettings)
    .settings(Compile / unmanagedSourceDirectories += {
      if (useChisel7) file("tools/stage-chisel7/src/main/scala")
      else file("tools/stage/src/main/scala")
    })
    .settings(dspExcludeSettings: _*)

  // Optional modules discovered via initialized submodules (no env or manifest)
  val optionalModules: Seq[(String, ProjectReference)] = Seq(
    // Generators with Chipyard-facing glue compiled from their repos
    "cva6" -> cva6,
    "ibex" -> ibex,
    "vexiiriscv" -> vexiiriscv,
    "riscv-sodor" -> sodor,
    "ara" -> ara,
    "saturn" -> saturn,
    "tacit" -> tacit,
    "gemmini" -> gemmini,
    "nvdla" -> nvdla,
    "radiance" -> radiance,
    "caliptra-aes-acc" -> caliptra_aes,
    "compress-acc" -> compressacc,
    "mempress" -> mempress,
    "fft-generator" -> fft_generator
  )

  // Discover optional modules if their submodule is initialized
  val discovered = optionalModules.filter { case (dir, _) =>
    file(s"generators/$dir/.git").exists
  }

  // Wire in project dependencies only for discovered modules
  if (discovered.nonEmpty) {
    // dependsOn requires ClasspathDep[ProjectReference]; wrap explicitly
    cy = cy.dependsOn(discovered.map { case (_, pr) => sbt.ClasspathDependency(pr, None) }: _*)
  }

  // Also add their Chipyard-facing sources without symlinks
  cy = cy.settings(
    Compile / unmanagedSourceDirectories ++=
      discovered.map { case (dir, _) =>
        // Resolve from repo root so paths are correct regardless of project base
        (ThisBuild / baseDirectory).value / s"generators/$dir/chipyard"
      }.filter(_.exists)
  )

  cy
}

lazy val compressacc = withInitCheck((project in file("generators/compress-acc")), "compress-acc")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val mempress = withInitCheck((project in file("generators/mempress")), "mempress")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val barf = withInitCheck((project in file("generators/bar-fetchers")), "bar-fetchers")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val saturn = withInitCheck((project in file("generators/saturn")), "saturn")
  .dependsOn(rocketchip, shuttle)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val constellation = withInitCheck((project in file("generators/constellation")), "constellation")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val fft_generator = withInitCheck((project in file("generators/fft-generator")), "fft-generator")
  .dependsOn(rocketchip, rocket_dsp_utils, testchipip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tracegen = (project in file("generators/tracegen"))
  .dependsOn(testchipip, rocketchip, rocketchip_inclusive_cache, boom)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val icenet = withInitCheck((project in file("generators/icenet")), "icenet")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val boom = freshProject("boom", file("generators/boom"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val shuttle = withInitCheck((project in file("generators/shuttle")), "shuttle")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

// Helper: fail fast if a generator project is used without its submodule initialized.
def withInitCheck(p: Project, genDirName: String): Project = {
  val checkTask = Def.task {
    val root = (ThisBuild / baseDirectory).value
    val dir = root / s"generators/$genDirName"
    val looksInitialized = (dir / ".git").exists
    if (!dir.exists || !looksInitialized) {
      sys.error(
        s"Generator '$genDirName' is not initialized at '" + dir.getAbsolutePath +
        "'. Run scripts/build-setup.sh or init the submodule (scripts/init-submodules-no-riscv-tools-nolog.sh).")
    }
  }
  p.settings(
    // Run the check whenever this project's code is compiled/tested/run
    Compile / compile := (Compile / compile).dependsOn(checkTask).value,
    Test / compile := (Test / compile).dependsOn(checkTask).value,
    Compile / run := (Compile / run).dependsOn(checkTask).evaluated
  )
}

lazy val cva6 = withInitCheck((project in file("generators/cva6")), "cva6")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val ara = withInitCheck((project in file("generators/ara")), "ara")
  .dependsOn(rocketchip, shuttle)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val ibex = withInitCheck((project in file("generators/ibex")), "ibex")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val vexiiriscv = withInitCheck((project in file("generators/vexiiriscv")), "vexiiriscv")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sodor = withInitCheck((project in file("generators/riscv-sodor")), "riscv-sodor")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val radiance = withInitCheck((project in file("generators/radiance")), "radiance")
  .dependsOn(rocketchip, gemmini, testchipip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chiseltest" % chiselTestVersion,
      "org.scalatest" %% "scalatest" % "3.2.+" % "test",
      "junit" % "junit" % "4.13" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
  ))
  .settings(commonSettings)

lazy val gemmini = withInitCheck(freshProject("gemmini", file("generators/gemmini")), "gemmini")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val nvdla = withInitCheck((project in file("generators/nvdla")), "nvdla")
  .dependsOn(rocketchip, testchipip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tacit = withInitCheck((project in file("generators/tacit")), "tacit")
  .dependsOn(rocketchip, shuttle, testchipip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val caliptra_aes = withInitCheck((project in file("generators/caliptra-aes-acc")), "caliptra-aes-acc")
  .dependsOn(rocketchip, rocc_acc_utils, testchipip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rerocc = withInitCheck((project in file("generators/rerocc")), "rerocc")
  .dependsOn(rocketchip, constellation, boom, shuttle)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocc_acc_utils = withInitCheck((project in file("generators/rocc-acc-utils")), "rocc-acc-utils")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tapeout = (project in file("./tools/tapeout/"))
  .settings(chisel3Settings) // stuck on chisel3 and SFC
  .settings(commonSettings)
  .settings(scalaVersion := "2.13.10") // stuck on chisel3 2.13.10
  .settings(libraryDependencies ++= Seq("com.typesafe.play" %% "play-json" % "2.9.2"))

lazy val fixedpoint = freshProject("fixedpoint", file("./tools/fixedpoint"))
  .settings(chiselSettings)
  .settings(commonSettings)

lazy val dsptools = freshProject("dsptools", file("./tools/dsptools"))
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

lazy val rocketchip_blocks = withInitCheck((project in file("generators/rocket-chip-blocks")), "rocket-chip-blocks")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocketchip_inclusive_cache = withInitCheck((project in file("generators/rocket-chip-inclusive-cache")), "rocket-chip-inclusive-cache")
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

lazy val firrtl2 = freshProject("firrtl2", file("./tools/firrtl2"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(Antlr4Plugin)
  .settings(commonSettings)
  .settings(
    sourceDirectory := file("./tools/firrtl2/src"),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-language:existentials",
      "-language:implicitConversions"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.14" % "test",
      "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0" % "test",
      "com.github.scopt" %% "scopt" % "4.1.0",
      "org.json4s" %% "json4s-native" % "4.1.0-M4",
      "org.apache.commons" % "commons-text" % "1.10.0",
      "com.lihaoyi" %% "os-lib" % "0.8.1",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"),
    Antlr4 / antlr4GenVisitor := true,
    Antlr4 / antlr4GenListener := true,
    Antlr4 / antlr4PackageName := Option("firrtl2.antlr"),
    Antlr4 / antlr4Version := "4.9.3",
    Antlr4 / javaSource := (Compile / sourceManaged).value,
    buildInfoPackage := "firrtl2",
    buildInfoUsePackageAsPath := true,
    buildInfoKeys := Seq[BuildInfoKey](buildInfoPackage, version, scalaVersion, sbtVersion)
  )

lazy val firrtl2_bridge = freshProject("firrtl2_bridge", file("./tools/firrtl2/bridge"))
  .dependsOn(firrtl2)
  .settings(commonSettings)
  .settings(chiselSettings)

lazy val firesimDir = file("sims/firesim")

// Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS.
// Minimal in scope (should only depend on Chisel/Firrtl that is
// cross-compilable between FireSim Chisel 3.* and Chipyard Chisel 6+)
lazy val midas_target_utils = (project in firesimDir / "sim/midas/targetutils")
  .settings(commonSettings)
  .settings(chiselSettings)

// Provides API for bridges to be created in the target.
// Includes target-side of FireSim-provided bridges and their interfaces that are shared
// between FireSim and the target. Minimal in scope (should only depend on Chisel/Firrtl that is
// cross-compilable between FireSim Chisel 3.* and Chipyard Chisel 6+)
lazy val firesim_lib = (project in firesimDir / "sim/firesim-lib")
  .dependsOn(midas_target_utils)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(scalaTestSettings)

// Interfaces for target-specific bridges shared with FireSim.
// Minimal in scope (should only depend on Chisel/Firrtl).
// This is copied to FireSim's GoldenGate compiler.
lazy val firechip_bridgeinterfaces = (project in file("generators/firechip/bridgeinterfaces"))
  .settings(
    chiselSettings,
    commonSettings,
  )

// Target-side bridge definitions, CC files, etc used for FireSim.
// This only compiled with Chipyard.
lazy val firechip_bridgestubs = (project in file("generators/firechip/bridgestubs"))
  .dependsOn(chipyard, firesim_lib % "compile->compile;test->test", firechip_bridgeinterfaces)
  .settings(
    chiselSettings,
    commonSettings,
    Test / testGrouping := isolateAllTests( (Test / definedTests).value ),
    Test / testOptions += Tests.Argument("-oF")
  )
  .settings(scalaTestSettings)

// FireSim top-level project that includes the FireSim harness, CC files, etc needed for FireSim.
lazy val firechip = (project in file("generators/firechip/chip"))
  .dependsOn(chipyard, firesim_lib % "compile->compile;test->test", firechip_bridgestubs, firechip_bridgeinterfaces)
  .settings(
    chiselSettings,
    commonSettings,
    Test / testGrouping := isolateAllTests( (Test / definedTests).value ),
    Test / testOptions += Tests.Argument("-oF")
  )
  .settings(scalaTestSettings)
