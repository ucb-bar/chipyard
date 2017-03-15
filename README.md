# barstools
Useful utilities for BAR projects

Passes/Transforms that could be useful if added here:
* Check that a module was de-duplicated. Useful for MIM CAD flows and currently done in python.

Be sure to publish-local the following repositories:
* ucb-bar/chisel-testers (requires ucb-bar/firrtl-interpreter)
* ucb-bar/firrtl

Example Usage:
```
sbt
> compile
> project tapeout
> run-main barstools.tapeout.transforms.GenerateTop -i <myfile>.fir -o <myfile>.v --syn-top <mysyntop> --harness-top <myharnesstop>
```
