Managing Published Scala Dependencies
=====================================

In preparation for Chisel 3.5, in Chipyard 1.5 Chisel, FIRRTL, the FIRRTL
interpreter, and Treadle, were transitioned from being built-from-source to
managed as published dependencies. Their submodules have been removed.
Switching between published versions can be achieved by changing the versions
specified in Chipyard's ``build.sbt``.

Lists of available artifacts can be using search.maven.org or mvnrepository.org:

- `Chisel3 <https://mvnrepository.com/artifact/edu.berkeley.cs/chisel3>`_
- `FIRRTL <https://mvnrepository.com/artifact/edu.berkeley.cs/firrtl>`_
- `FIRRTL Interpreter <https://mvnrepository.com/artifact/edu.berkeley.cs/firrtl-interpreter>`_
- `Treadle <https://mvnrepository.com/artifact/edu.berkeley.cs/treadle>`_


Publishing Local Changes
-------------------------

Under the new system, the simplest means to make custom source modifications to the packages
above is to run ``sbt +publishLocal`` from within a locally modified clone of each
of their respective repositories. This will post your custom variant
to your local ivy2 repository, which can generally be found at ``~/.ivy2``.  See the `SBT
documentation <https://www.scala-sbt.org/1.x/docs/Publishing.html#Publishing+locally>`_
for more detail.

In practice, this will require the following steps:

#. Check out and modify the desired projects.
#. Take note of, or modify, the versions of each projects (in
   their ``build.sbt``). If you're cloning from ``master`` generally
   these will have default versions of ``1.X-SNAPSHOT``, where ``X`` is
   not-yet-released next major version. You can modify the version string, to say ``1.X-<MYSUFFIX>``, to uniquely identify your
   change.
#. Call ``sbt +publishLocal`` in each subproject. You may need to rebuild other
   published dependencies. SBT will be clear about what it is publishing and
   where it is putting it. The ``+`` is generally necessary and ensures that
   all cross versions of the package are published.
#. Update the Chisel or FIRRTL version in Chipyard's ``build.sbt`` to match the
   versions of your locally published packages.
#. Use Chipyard as you would normally. Now when you call out to make in
   Chipyard you should see SBT resolving dependencies to the locally
   published instances in your local ivy2 repository.
#. When you're finished, consider removing your locally published packages (by
   removing the appropriate directory in your ivy2 repository) to prevent
   accidentally reusing them in the future.

A final word of caution: packages you publish to your local ivy repository will
be visible to other projects you may be building on your system. For example,
if you locally publish Chisel 3.5.0, other projects that depend on Chisel 3.5.0
will preferentially use the locally published variant over the version
available on Maven (the "real" 3.5.0).  Take care to note versions you are
publishing and remove locally published versions once you are done with them.
