Running A Simulation
========================================================

ReBAR provides support and intergration for multiple simulation flows, for various user levels and requirments.
In the majority of cases during a digital design development process, simple software RTL. When more advanced full-system evaluation is required, with long running workloads, FPGA-accelerated simulation will then become a preferable solution.


Software RTL Simulation
------------------------
The ReBAR framework provides wrappers for two common software RTL simulators: the open-source Verilator simulator. and the proprietry VCS simulator.The following instructions assume at least one of these simulators is installed.

Verilator
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Verilator is an open-source RTL simulator. We run Verilator simulations from within the `sims/verisim` directory.
In order to construct the simulator with our custom design, we run the following command within the `sims/verisim` directory:

..
  make TOP=<my_top_level_name> CONFIG=<my_config_name> SBT_PROJECT=<my_sbt_package_name> MODEL=<my_test_environment>

Where `<my_top_level_name>` is the class name of the top level design, `<my
The `make` command my have additional parameters (such as `CONFIG_PACKAGE` or `MODEL_PACKAGE`) depending on the complexity of the design and integration with multiple sub-project repositories in the sbt-based build system.

Common configurations are package using a `SUB_PROJECT` make variable. There, in order to simulate a simple Rocket-based example system we can use:

..
  make SUB_PROJECT=example

Alternatively, if we would like to simulate a simple BOOM-based example system we can use:

..
  make SUB_PROJECT=exampleboom


Once the simulator has been constructed, we would like to run RISC-V programs on it. In the `sims/verisim` directory, we will find an executable file called `TODO`. We run this executable with out target RISC-V program as a command line argument. For example:

..
  TODO

Alternatively, we can run a pre-packaged suite of RISC-V assembly tests, by adding the make target run-asm-tests. For example

..
  make run-asm-tests TOP=<my_top_level_name> CONFIG=<my_config_name> SBT_PROJECT=<my_sbt_package_name> MODEL=<my_test_environment>

or 

..
  make run-asm-tests SUB_PROJECT=example



VCS
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VCS is a proprietry RTL simulator. This guide assumes that the VCS installation is found on our PATH.  We run VCS simulations from within the `sims/vsim` directory.
In order to construct the simulator with our custom design, we run the following command within the `sims/vsim` directory:

..
  make TOP=<my_top_level_name> CONFIG=<my_config_name> SBT_PROJECT=<my_sbt_package_name> MODEL=<my_test_environment>

Where `<my_top_level_name>` is the class name of the top level design, `<my
The `make` command my have additional parameters (such as `CONFIG_PACKAGE` or `MODEL_PACKAGE`) depending on the complexity of the design and integration with multiple sub-project repositories in the sbt-based build system.

Common configurations are package using a `SUB_PROJECT` make variable. There, in order to simulate a simple Rocket-based example system we can use:

..
  make SUB_PROJECT=example

Alternatively, if we would like to simulate a simple BOOM-based example system we can use:

..
  make SUB_PROJECT=exampleboom


Once the simulator has been constructed, we would like to run RISC-V programs on it. In the `sims/vsim` directory, we will find an executable file called `TODO`. We run this executable with out target RISC-V program as a command line argument. For example:

..
  TODO

Alternatively, we can run a pre-packaged suite of RISC-V assembly tests, by adding the make target run-asm-tests. For example

..
  make run-asm-tests TOP=<my_top_level_name> CONFIG=<my_config_name> SBT_PROJECT=<my_sbt_package_name> MODEL=<my_test_environment>

or 

..
  make run-asm-tests SUB_PROJECT=example



FPGA Accelerated Simulation
---------------------------
FireSim enables simulations at 1000x-100000x the speed of standard software simulation. This is enabled using FPGA-acceleration on F1 instances of the AWS (Amazon Web Services) public cloud. There FireSim simulation require to be set-up on the AWS public cloud rather than on our local development machine. 

To run an FPGA-accelerated simulation using FireSim, a we need to clone the ReBAR repository (or our fork of the ReBAR repository) to an AWS EC2, and follow the setup instructions specificied in the FireSim Initial Setup documentation page.

After setting up the FireSim environment, we now need to generate a FireSim simulation around our selected digital design. We will work from within the `sims/firesim` directory.

TODO: Continue from here
 
