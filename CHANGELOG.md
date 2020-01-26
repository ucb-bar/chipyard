# Changelog

This changelog follows the format defined here: https://keepachangelog.com/en/1.0.0/

## [1.1.0] - 2020-01-25

A more detailed account of everything included is included in the dev to master PR for this release: https://github.com/ucb-bar/chipyard/pull/367

### Added
* Gemmini generator and config (PR #356 )
* Coremark + SPEC2017 benchmarks (PR #326, #338, #344)
* Add Hwacha tests to CI (PR #284)
* Add Hwacha tests to benchmark and assembly test suites (PR #284)
* Added Hwacha + Large Boom Config (PR #315)
* Add multi-core config with a small Rocket core attached on the side (PR #361 )
* Add UART and Test Harness UART Adapter to all configurations (PR #348)
* User can specify $RISCV directory in build-toolchains.sh (PR #334)
* Checksum offload in IceNet (PR #364)

### Changed
* Rocketchip bumped to commit [4f0cdea](https://github.com/chipsalliance/rocket-chip/tree/4f0cdea85c8a2b849fd582ccc8497892001d06b0), for chisel version 3.2.0 which includes Async reset support
* FireSim release 1.8.0
* FireMarshal release 1.8.0
* BOOM release 2.2.3 (PR #397)
* baremetal software toolchains, using libgloss and newlib instead of in-house syscalls. 
* Add toolchain specific `env.sh` (PR #304)
* `run-binary`-like interface now dumps `.log` (stdout) and `.out` (stderr) files (PR #308)
* Split the VLSI build dir on type of design (PR #331)
* Reduce Ctags runtime and only look at scala, C, C++, and Python files (PR #346)
* Top/Top-level-traits now behave as a configuraboe generator (PR #347)
* Test suite makefrag generator includes Hwacha test suites (PR #342)

### Fixed
* Fix VLSI makefile requirements for SRAM generation (PR #318)
* Only filter header files from common simulation files (PR #322)
* Bump MacroCompiler for bugfixes (PR #332)
* commit-on-master check has specific behavior based on source branch (PR #345)
* Makefile filtering of blackbox resource files only omits .h files (PR #322)
* Parallel make fixed (PR #386 #392)

### Deprecated
* No longer need to specify `WithXTop`, default `Top` is a generator for all `Top`s (PR #347)

### Removed
* N/A


## [1.0.0] - 2019-10-19

### Added

* This repository used to be "project-template", a template for Chisel-based projects. Through tighter integration of multiple projects from the Berkeley Architecture Research group at UC Berkeley, this repository is re-released as Chipyard - a framework for agile hardware development of RISC-V based Systems-on-Chip.
