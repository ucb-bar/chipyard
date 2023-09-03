.. _custom_chisel:

Integrating Custom Chisel Projects into the Generator Build System
==================================================================

.. warning::
   This section assumes integration of custom Chisel through git submodules.
   While it is possible to directly commit custom Chisel into the Chipyard framework,
   we heavily recommend managing custom code through git submodules. Using submodules decouples
   development of custom features from development on the Chipyard framework.


While developing, you want to include Chisel code in a submodule so that it can be shared by different projects.
To add a submodule to the Chipyard framework, make sure that your project is organized as follows.

.. code-block:: none

    yourproject/
        build.sbt
        src/main/scala/
            YourFile.scala

Put this in a git repository and make it accessible.
Then add it as a submodule to under the following directory hierarchy: ``generators/yourproject``.

The ``build.sbt`` is a minimal file which describes metadata for a Chisel project.
For a simple project, the ``build.sbt`` can even be empty, but below we provide an example
build.sbt.

.. code-block:: scala

    organization := "edu.berkeley.cs"

    version := "1.0"

    name := "yourproject"

    scalaVersion := "2.12.4"



.. code-block:: shell

    cd generators/
    git submodule add https://git-repository.com/yourproject.git

Then add ``yourproject`` to the Chipyard top-level build.sbt file.

.. code-block:: scala

    lazy val yourproject = (project in file("generators/yourproject")).settings(commonSettings).dependsOn(rocketchip)

You can then import the classes defined in the submodule in a new project if
you add it as a dependency. For instance, if you want to use this code in
the ``chipyard`` project, add your project to the list of sub-projects in the
`.dependsOn()` for `lazy val chipyard`. The original code may change over time, but it
should look something like this:

.. code-block:: scala

    lazy val chipyard = (project in file("generators/chipyard"))
        .dependsOn(testchipip, rocketchip, boom, hwacha, sifive_blocks, sifive_cache, iocell,
            sha3, dsptools, `rocket-dsp-utils`,
            gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator,
            yourproject, // <- added to the middle of the list for simplicity
            constellation, mempress)
        .settings(libraryDependencies ++= rocketLibDeps.value)
        .settings(
            libraryDependencies ++= Seq(
            "org.reflections" % "reflections" % "0.10.2"
            )
        )
        .settings(commonSettings)
