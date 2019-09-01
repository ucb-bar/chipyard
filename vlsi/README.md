This is the starting point for a vlsi flow from this repository.

This flow will not work without the necessary CAD and technology plugins for HAMMER.

If you are a UCB-affiliate, you may be able to acquire access to the tool & tech plugins.

# Initial Setup Instructions (For all technologies)
Run the `init-vlsi.sh` script to pull correct versions of hammer, hammer-TOOL\_VENDOR-plugins, and the hammer-TECH\_NAME-plugins. Note the included technology 'asap7' is already included and will not submodule a tech plugin.
```shell
scripts/init-vlsi.sh TECH_NAME
```

An example of tool environment configuration for BWRC affiliates is given in `bwrc-env.yml`. Replace as necessary for your environment.

Finally, set up all prerequisites for the build system:
```shell
make buildfile
```

# Example design
In this example, you will be running a SHA-3 accelerator through the VLSI flow. To elaborate the Sha3RocketConfig and set up all prerequisites for the build system:
```shell
export MACROCOMPILER_MODE=' --mode synflops'
export CONFIG=Sha3RocketConfig
export TOP=Sha3Accel
make buildfile
```
Note that because the ASAP7 process does not yet have a memory compiler, synflops are elaborated instead.
>>>>>>> fix incorrect block for syn/par, but still have timing violations

HAMMER's configuration is driven by a JSON/YAML format. For HAMMER, JSON and YAML files are equivalent - you can use either one since HAMMER will convert them to the same representation for itself.

We start by pulling the HAMMER environment into the shell:

```shell
export HAMMER_HOME=$PWD/hammer
source $HAMMER_HOME/sourceme.sh
```

The configuration for the example design is contained in `example.yml` and the entry script with hooks is contained in `example-vlsi`. You may go through Hammer's readme to learn about the supported configuration options and how to write hooks.

To synthesize a design:

```shell
make syn
```

The outputs are written to a log file with a timestamp and the post-synthesis results are in `build/syn-rundir`.

Raw QoR data is available at `build/syn-rundir/reports`, and work is planned to extract this information in a more programmatic manner.

To run place and route:
```shell
make par
```

If successful, the resulting chip can be opened via `./build/par-rundir/generated-scripts/open_chip`.

To run DRC and view violations:
```shell
make drc
./build/drc-rundir/generated-scripts/view-drc
```

To run LVS and view violations:
```shell
make lvs
./build/lvs-rundir/generated-scripts/view-lvs
```
