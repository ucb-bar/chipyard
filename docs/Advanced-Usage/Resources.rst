Accessing Scala Resources
===============================

A simple way to copy over a source file to the build directory to be used for a simulation compile or VLSI flow is to use the ``setResource`` or ``addResource`` functions given by FIRRTL.
They can be used in the following way:

.. code-block:: scala

    class SimSerial(w: Int) extends BlackBox with HasBlackBoxResource {
      val io = IO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Bool())
        val serial = Flipped(new SerialIO(w))
        val exit = Output(Bool())
      })

      setResource("/testchipip/vsrc/SimSerial.v")
      setResource("/testchipip/csrc/SimSerial.cc")
    }

In this example, the ``SimSerial`` files will be copied from a specific folder (in this case the ``path/to/testchipip/src/main/resources/testchipip/...``) to the build folder.
The ``set/addResource`` path retrieves resources from the ``src/main/resources`` directory.
So to get an item at ``src/main/resources/fileA.v`` you can use ``setResource("/fileA.v")``.
However, one caveat of this approach is that to retrieve the file during the FIRRTL compile, you must have that project in the FIRRTL compiler's classpath.
Thus, you need to add the SBT project as a dependency to the FIRRTL compiler in the Chipyard ``build.sbt``, which in Chipyards case is the ``tapeout`` project.
For example, you added a new project called ``myAwesomeAccel`` in the Chipyard ``build.sbt``.
Then you can add it as a ``dependsOn`` dependency to the ``tapeout`` project.
For example:

.. code-block:: scala

    lazy val myAwesomeAccel = (project in file("generators/myAwesomeAccelFolder"))
      .dependsOn(rocketchip)
      .settings(commonSettings)

    lazy val tapeout = conditionalDependsOn(project in file("./tools/barstools/tapeout/"))
      .dependsOn(myAwesomeAccel)
      .settings(commonSettings)
