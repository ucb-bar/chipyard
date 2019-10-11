![CHIPYARD](https://github.com/ucb-bar/chipyard/raw/alon-docs-dev/docs/_static/images/chipyard-logo-full.png)

# Chipyard Framework [![CircleCI](https://circleci.com/gh/ucb-bar/chipyard/tree/master.svg?style=svg)](https://circleci.com/gh/ucb-bar/chipyard/tree/master)

## Using Chipyard

To get started using Chipyard, see the documentation on the Chipyard documentation site: https://chipyard.readthedocs.io/

## What is Chipyard

Chipyard is an open source framework for agile development of Chisel-based Systems-on-Chip.
It will allow you to leverage the Chisel HDL, Rocket Chip SoC generator, and other [Berkeley][berkeley] projects to produce a [RISC-V][riscv] SoC with everything from MMIO-mapped peripherals to custom accelerators.
Chipyard contains processor cores ([Rocket][rocket-chip], [BOOM][boom]), accelerators ([Hwacha][hwacha]), memory systems, and additional peripherals and tooling to help create a full featured SoC.
Chipyard supports multiple conccurent flows of agile hardware development, including software RTL simulaiton, FPGA-accelerated simulation ([FireSim][firesim]), automated VLSI flows ([Hammer][hammer]), and software workload generation for bare-metal and Linux-based systems ([FireMarshal][firemarshal]).
Chipyard is actively developed in the [Berkeley Architecture Research Group][ucb-bar] in the [Electrical Engineering and Computer Sciences Department][eecs] at the [University of California, Berkeley][berkeley].

## Resources

* Chipyard Documentation: https://chipyard.readthedocs.io/
* Chipyard Basics slides: 

## Need help?

* Join the Chipyard Mailing list: https://groups.google.com/forum/#!forum/chipyard
* Post an issue on this repo


[hwacha]:http://hwacha.org
[hammer]:https://github.com/ucb-bar/hammer
[firesim]:https://fires.im
[ucb-bar]: http://bar.eecs.berkeley.edu
[eecs]: https://eecs.berkeley.edu
[berkeley]: https://berkeley.edu
[riscv]: https://riscv.org/
[rocket-chip]: https://github.com/freechipsproject/rocket-chip
[boom]: https://github.com/ucb-bar/riscv-boom
[firemarshal]: https://github.com/firesim/FireMarshal/
