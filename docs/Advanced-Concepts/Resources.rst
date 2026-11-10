Accessing Scala Resources
===============================

A simple way to copy over a source file to the build directory to be used for a simulation compile or VLSI flow is to use the ``addResource`` function given by Chisel's ``HasBlackBoxResource`` trait.
An example of its use can be seen in `generators/testchipip/src/main/scala/SimTSI.scala <https://github.com/ucb-bar/testchipip/blob/master/src/main/scala/SimTSI.scala>`_.
Here is the example inlined:

.. code-block:: scala

    class SimTSI extends BlackBox with HasBlackBoxResource {
      val io = IO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Bool())
        val tsi = Flipped(new TSIIO)
        val exit = Output(Bool())
      })

      addResource("/testchipip/vsrc/SimTSI.v")
      addResource("/testchipip/csrc/SimTSI.cc")
    }

In this example, the ``SimTSI`` files will be copied from a specific folder (in this case the ``path/to/testchipip/src/main/resources/testchipip/...``) to the build folder.
The ``addResource`` path retrieves resources from the ``src/main/resources`` directory.
So to get an item at ``src/main/resources/fileA.v`` you can use ``addResource("/fileA.v")``.
However, one caveat of this approach is that the resource is read from the classpath while the Chisel generator elaborates the design, so the project containing it must be on the generator's classpath.
Thus, you need to add the SBT project as a dependency of the Chipyard generator project (``chipyard``) in the Chipyard ``build.sbt``.
For example, you added a new project called ``myAwesomeAccel`` in the Chipyard ``build.sbt``.
Then you can add it as a ``dependsOn`` dependency to the ``chipyard`` project.
For example:

.. code-block:: scala

    lazy val myAwesomeAccel = (project in file("generators/myAwesomeAccelFolder"))
      .dependsOn(rocketchip)
      .settings(commonSettings)
