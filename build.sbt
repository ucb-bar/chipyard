import Tests._

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "1.0",
  scalaVersion := "2.12.4",
  traceLevel := 15,
  test in assembly := {},
  assemblyMergeStrategy in assembly := { _ match {
    case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
    case _ => MergeStrategy.first}},
  scalacOptions ++= Seq("-deprecation","-unchecked","-Xsource:2.11"),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.6.1",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  libraryDependencies += "edu.berkeley.cs" %% "firrtl-interpreter" % "1.2-SNAPSHOT",
  libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0",
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal))

val rocketChipDir = file("generators/rocket-chip")

lazy val firesimAsLibrary = sys.env.get("FIRESIM_STANDALONE") == None
lazy val firesimDir = if (firesimAsLibrary) {
  file("sims/firesim/sim/")
} else {
  file("../../")
}

// Checks for -DROCKET_USE_MAVEN.
// If it's there, use a maven dependency.
// Else, depend on subprojects in git submodules.
def conditionalDependsOn(prj: Project): Project = {
  if (sys.props.contains("ROCKET_USE_MAVEN")) {
    prj.settings(Seq(
      libraryDependencies += "edu.berkeley.cs" %% "testchipip" % "1.0-020719-SNAPSHOT",
    ))
  } else {
    prj.dependsOn(testchipip)
  }
}

// Fork each scala test for now, to work around persistent mutable state
// in Rocket-Chip based generators
def isolateAllTests(tests: Seq[TestDefinition]) = tests map { test =>
      val options = ForkOptions()
      new Group(test.name, Seq(test), SubProcess(options))
  } toSeq

// Subproject definitions begin

// NB: FIRRTL dependency is unmanaged (and dropped in sim/lib)
lazy val chisel  = (project in rocketChipDir / "chisel3")

 // Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS
lazy val midasTargetUtils = ProjectRef(firesimDir, "targetutils")

 // Rocket-chip dependencies (subsumes making RC a RootProject)
lazy val hardfloat  = (project in rocketChipDir / "hardfloat")
  .settings(commonSettings).dependsOn(midasTargetUtils)

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)

// HACK: I'm strugging to override settings in rocket-chip's build.sbt (i want
// the subproject to register a new library dependendency on midas's targetutils library)
// So instead, avoid the existing build.sbt altogether and specify the project's root at src/
lazy val rocketchip = (project in rocketChipDir / "src")
  .settings(
    commonSettings,
    scalaSource in Compile := baseDirectory.value / "main" / "scala",
    resourceDirectory in Compile := baseDirectory.value / "main" / "resources")
  .dependsOn(chisel, hardfloat, rocketMacros)

lazy val testchipip = (project in file("generators/testchipip"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val example = conditionalDependsOn(project in file("generators/example"))
  .dependsOn(boom, hwacha, sifive_blocks, sifive_cache, systolic_array)
  .settings(commonSettings)

lazy val utilities = conditionalDependsOn(project in file("generators/utilities"))
  .settings(commonSettings)

lazy val icenet = (project in file("generators/icenet"))
  .dependsOn(rocketchip, testchipip)
  .settings(commonSettings)

lazy val hwacha = (project in file("generators/hwacha"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val boom = (project in file("generators/boom"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val systolic_array = (project in file("generators/systolic-array"))
   .settings(commonSettings)
   .dependsOn(icenet)

lazy val tapeout = conditionalDependsOn(project in file("./tools/barstools/tapeout/"))
  .settings(commonSettings)

lazy val mdf = (project in file("./tools/barstools/mdf/scalalib/"))
  .settings(commonSettings)

lazy val barstoolsMacros = (project in file("./tools/barstools/macros/"))
  .dependsOn(mdf, rocketchip)
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .settings(commonSettings)

lazy val sifive_blocks = (project in file("generators/sifive-blocks"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val sifive_cache = (project in file("generators/sifive-cache")).settings(
    commonSettings,
    scalaSource in Compile := baseDirectory.value / "craft"
  ).dependsOn(rocketchip)

// Library components of FireSim
lazy val midas      = ProjectRef(firesimDir, "midas")
lazy val firesimLib = ProjectRef(firesimDir, "firesimLib")

lazy val firechip = (project in file("generators/firechip"))
  .dependsOn(boom, icenet, testchipip, sifive_blocks, sifive_cache, systolic_array, midasTargetUtils, midas, firesimLib % "test->test;compile->compile")
  .settings(
    commonSettings,
    testGrouping in Test := isolateAllTests( (definedTests in Test).value )
  )

