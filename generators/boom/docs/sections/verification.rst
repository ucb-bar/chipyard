Verification
============

This chapter covers the current recommended techniques for verifying
BOOM. Although not provided as part of the BOOM or Rocket Chip
repositories, it is also recommended that BOOM be tested on "hello-world
+ riscv-pk" and the RISC-V port of Linux to properly stress the
processor.

RISC-V Tests
------------

A basic set of functional tests and micro-benchmarks can be found at
(https://github.com/riscv/riscv-tests). These are invoked by the ``make
run`` targets in the ``verilator`` and ``vcs`` directories located in the
Chipyard template repository.

RISC-V Torture Tester
---------------------

Berkeley’s riscv-torture tool is used to stress the BOOM pipeline, find
bugs, and provide small code snippets that can be used to debug the
processor. Torture can be found at (https://github.com/ucb-bar/riscv-torture).

Continuous Integration (CI)
---------------------------

The CircleCI Continuous Integration (CI) tool is used to check pull requests and
the master branch of BOOM. All files associated with it can be found in
two directories. Firstly, the configuration file used to run CI is located at
``.circleci/config.yml``. This specifies the current tests and builds that
are run using which BOOM configurations. Additionally, the DockerFile used to
build the CI docker images resides in ``.circleci/images``. Finally, all
scripts that are used during the CI run are located at ``.circleci/``. Note that even
though BOOM template is cloned during the CI process, the BOOM repository specifies
which version of Rocket Chip to use (which in turn determines the proper version of
riscv-tools).
