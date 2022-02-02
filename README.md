Barstools
==================

---

![Test](https://github.com/freechipsproject/ucb-bar/barstools/Test/badge.svg)

**Barstools** is a collection of useful utilities for BAR projects

Passes/Transforms that could be useful if added here:
* Check that a module was de-duplicated. Useful for MIM CAD flows and currently done in python.

Be sure to publish-local the following repositories:
* ucb-bar/chisel-testers (requires ucb-bar/firrtl-interpreter)
* ucb-bar/firrtl

Example Usage:
```
sbt
> compile
> runMain barstools.tapeout.transforms.GenerateTop -i <myfile>.fir -o <myfile>.v --syn-top <mysyntop> --harness-top <myharnesstop>
```
Building the macro compiler JAR:
```
$ sbt
[...]
[info] Set current project to tapeout (in build file:/mnt/data/dev/barstools_pcad/)
> assembly
[...]
[info] SHA-1: 77d4c759c825fd0ea93dfec26dbbb649f6cd5c89
[info] Packaging [...]/macros/target/scala-2.11/macros-assembly-0.1-SNAPSHOT.jar ...
[info] Done packaging.
[success] Total time: 28 s, completed Mar 21, 2018 2:28:34 PM
```
