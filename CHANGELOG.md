# Changelog

This changelog follows the format defined here: https://keepachangelog.com/en/1.0.0/

## [1.7.1] - 2022-07-06

FireSim bump for new builddriver command and various fixes. See FireSim 1.14.1 CHANGELOG.md.

### Changed
* Bump FireSim to 1.14.1

## [1.7.0] - 2022-06-18

FireSim bump for local (on-premises) FPGA and distributed metasimulation support. Hammer now supports the OpenROAD open-source EDA tools for a fully open-source RTL-to-GDS VLSI flow.

### Added
* Add a FireSim config with no mem port (#1172)
* Hammer OpenROAD plugins: Yosys (syn), OpenROAD (par), Magic (drc), Netgen (lvs) (#1183)

### Changed
* Bump FireSim to 1.14.0
* Give the PRCI widgets valnames to clean up module naming (#1152)

### Fixed
* Add missing Apache commons dependencies (fixes #1144) (#1147)
* Disable Boost for spike (#1168)
* VCS enhancements (#1150)
    * Support multi-thread VCS simv option like FGP, Xprof etc.
    * Idle tsi in the target thread
* Don't shallow clone submodules (revert #1064) (#1143)
* Remove extra spaces in FPGA makefile (#1135)

## [1.6.3] - 2022-04-06

FireSim bump for various fixes. Revert shallow cloning. Various CI fixes.

### Fixed
* Bump to FireSim 1.13.4 (changelog: https://github.com/firesim/firesim/blob/1.13.4/CHANGELOG.md#1134---2022-04-06)
* Revert shallow cloning.
* Various CI fixes.

## [1.6.2] - 2022-03-01

Minor fixes to FireSim.

### Fixed
* Bump to FireSim 1.13.3 (#1134)

## [1.6.1] - 2022-03-01

Minor fixes to FireSim.

### Fixed
* Bump to FireSim 1.13.2 (#1133)

## [1.6.0] - 2022-02-15

A more detailed account of everything included is included in the dev to master PR for this release: https://github.com/ucb-bar/chipyard/pull/913

### Added
* Diplomatic IOBinder-like approach to setting up PRCI across different deployment targets (#900)
* Default set of MMIO-controlled reset-setters and clock-gaters (#900)
* Added simulation makefile options `torture` and `torture-overnight` for running Torture (#992)
* FSDB waveform support (#1072, #1102)
* Use GitHub Actions for CI (#1004, #999, #1090, #1092)
* Add MAKE variable in `build-toolchains.sh` (#1021)
* Cleanup GH issue and PR templates (#1029, #1032)
* Add support for Ibex core (#979)
* Add system bus width fragment (#1071)
* Add support for FSDB waveform files (#1072, #1102)
* Document simulator timeout settings (#1094)
* Add FFT Generator (#1067)
* Add waveforms for post-PNR and power (#1108)
* Have PRCI control registers use clock of corresponding bus (#1109)
* Add check to verify that user is running on tagged release (#1114)
* Hammer tutorial in Sky130 (#1115)

### Changed
* Bump CVA6 (#909 )
* Bump Hammer tutorial for ASAP7 r1p7 (#934)
* Use Published Chisel, FIRRTL, Treadle, FIRRTLInterpreter packages instead of building from source. #1054
* Change serialTL width to 32. Speeds up simulations (#1040)
* Update how sbt flag is overridden (by using `SBT_BIN` variable) (#1041)
* Use published dependencies for Chisel, FIRRTL, Treadle, and FIRRTLInterpreter (#1054)
* Split `ConfigFragments.scala` into multiple files (with more organization) (#1061)
* Avoid initializing nvdla software by default (#1063)
* Update ASAP to 1.7 in Hammer (#934)
* Shorten Gemmini docs and point to repo (#1078)
* Bump Gemmini to 0.6.2 (#1083)
* Use python2 for tracegen script (#1107)
* Bump to Chisel/FIRRTL 3.5.1 (#1060, #1113)
* Bump to FireMarshal 1.12.1 (#1116)
* Bump to FireSim 1.13.0 (#1118 )

### Fixed
* Fix UART portmap for Arty (#968)
* Support changing make variable `MODEL` from the cmdline (#1030)
* Force FIRRTL to 1.4.1 (#1052)
* Fix MMIO IOBinder (#1045)
* Mask `fd` warning when running make (#1057)
* Fix Sodor 5-stage hazard check (#1086)
* Fix Sodor val io issue (#1089)
* Fix BOOM reference in Readme (#1104)
* Fix waveforms for post-P&R power analysis (#1108)

### Removed
* Remove duplicate `WithUARTIOCells` fragment (#1047)
* Remove MaxPermSize in java variables (#1082)
* Remove support for CircleCI (#1105)

## [1.5.0] - 2021-06-13

A more detailed account of everything included is included in the dev to master PR for this release: https://github.com/ucb-bar/chipyard/pull/773

### Added
* FireMarshal support for FPGA prototypes (#849)
* Hammer update to include power estimation flows, rail analysis, hierarchical sim support, and improved ASAP7 plugin with dummy SRAMs (#886)
* Docker image
* Support specifying architecture when building tools. (#802)
* Add Config fragments: WithMultiRoCCFromBuildRoCC, PMP (#809, #821)
* Add support for simulating an AXI memory interface over the default TL serial link (#812)
* Add option to add async queues between chip-serialIO and harness serdes (#828)
* Spike support for multiple extensions, and add sha3 spike model to esp-tools (#837, #897)
* Default generator support for I2C and PWM (#885)

### Changed
* Gemmini bump to version 0.5
* FireSim bump to version 1.12
* FireMarshal bump to version 1.12
* Changes default FireSim frequency from 3.2 GHz (dual clock domains) to 1 GHz (single clock domain)
* Bump pygments from 2.2.0 to 2.7.4 in docs
* Hammer tutorial example is now a TinyRocketConfig (#886)
* Sha3 Spike model moved from sha3 repo to esp-isa-sim

### Fixed
* Avoid permissions conflict on shared protocjar.webcache (#774)
* Passing MBus clock frequency to SimDRAM (#790)
* Fix parsing of --ignore-qemu option (#791)
* FPGA Prototype - Support Adding Pullup R's to Bringup GPIOs (#806)
* Use "tile" instead of "core" to assign frequencies in WithTileFrequency config. fragment (#807)
* Fix IOCell generation for clock and reset to use IOCellKey (#824)
* Fix TileResetCtrl to be ahead of reset synchronizers (#826)
* Fix memory alignment in character count RoCC test (#853)
* Synchronize JTAG reset to JTAG.TCK. (#859)
* Updates to system requirements scripts (#874)
* Rocket-dsp-utils integration and cleanup for dsptools (#888)

### Removed
* Dummy DCO collateral from Hammer tutorial example (#886)


## [1.4.0] - 2021-01-19

A more detailed account of everything included is included in the dev to master PR for this release: https://github.com/ucb-bar/chipyard/pull/599

### Added
* OpenSBI Support (#633)
* Support for Diplomacy-based clocking (#614, #682)
* Support for Diplomacy-based IOBinders (#699)
* Sodor core integration (#648)
* Simple Divider-Only PLL for Multiclock RTL Simulation (#676)
* Enable parallel Hammer simulations (#600)
* OpenRoad nangate45 Hammer backend (#608)
* Add support for "LBWIF" backing memory through serialized TileLink (#673)
* Add variable to control FIRRTL logging verbosity (#627)
* Add RANDOM_SEED variable to set random init for VCS and Verilator simulations (#629)
* Fast LoadMem support (#635)
* Multithreaded Verilator (#654)
* Support for custom Verilator optimization flags (#728)
* Add config-fragment to use broadcast manager instead of L2 for coherence (#721)
* Added optional ignore QEMU flag to `build-toolchains.sh` (#709)
* Split `JAVA_ARGS` into `JAVA_OPTS` and `SBT_OPTS` (#719)
* Experimental support for SBT thin client. Enable with `export ENABLE_SBT_THIN_CLIENT=1` (https://github.com/sbt/sbt/pull/5620) (#719)
* Helper `make` targets to launch SBT console (`sbt`) and shutdown/start thin server (<start/shutdown>-sbt-server) (#719)
* Allow users to override `CC` and `CXX` for `build-toolchains.sh` (#739)
* Support VCU118/Arty local FPGA prototypes through `fpga-shells` (#747)
* A 16-core LargeBOOM configuration has been added to FireChip to highlight the resource-optimizing platform configurations added to FireSim in firesim/firesim#636 (#756)

### Changed
* Bump Chisel to 3.4.1.x (#742, #719, #751)
* Bump RocketChip to a7b016e (#742, #719)
* Bump FireSim to 1.11
* Bump Gemmini to v0.5
* Bump to SBT 1.4.4 (#719)
* Split IOBinders into IOBinders and HarnessBinders | punch out clocks to harness for simwidgets and bridges (#670, #674)
* Have FireSim build recipes use Chipyard configs rather than FireChip configs (#695)
* FireMarshal boot default to OpenSBI rather than BBL (#633)
* Override default baud rate for FireChip (#625)
* DTM only supports HTIF in DMI mode (#672)
* Unify HTIF implementation between Chipyard and Firesim (#683)
* Renamed Ariane to CVA6 (#710)
* `build.sbt` refactoring/fixes for RC/Chisel/Firrtl bump (#719)
* Use `; x; y; z;` syntax to run multiple SBT commands (#719)
* CI Improvements: Cleanup `check-commit` printout. Don't transfer `.git` folders. (#750)

### Fixed
* Multi-SHA3 configs (#597)
* Allow dramsim_ini folder to be set at the command line (#598)
* Emit HTIF Node in device tree (#607)
* Fixes for AXI4 MMIO and FBus ports (#618)
* Only punch realistic subset of DebugIO through chiptop | default to JTAG+Serial (#664)
* IceNet bug fixes (#720)
* smartelf2hex.sh bug fixes (#677, #693)
* env.sh zsh compatibility (#705)
* build-toolchains.sh bug fixes (#745 #739)
* Bump Dromajo to work with older version of glibc (#709)

### Removed
* Support for synchronous ChipTop reset (#703)
* Split `JAVA_ARGS` into `JAVA_OPTS` and `SBT_OPTS` (#719)


## [1.3.0] - 2020-05-31

A more detailed account of everything included is included in the dev to master PR for this release: https://github.com/ucb-bar/chipyard/pull/500

### Added
* A new Top-level module, ChipTop, has been created. ChipTop instantiates a "system" module specified by BuildSystem. (#480)
* A new BuildSystem key has been added, which by default builds DigitalTop (#480)
* The IOBinders API has changed. IOBinders are now called inside of ChipTop and return a tuple3 of (IO ports, IO cells, harness functions). The harness functions are now called inside the TestHarness (this is analogous to the previous IOBinder functions). (#480)
* IO cell models have been included in ChipTop. These can be replaced with real IO cells for tapeout, or used as-is for simulation. (#480)
* CI now checks documentation changes (#485)
* Support FireSim multi-clock (#468)
* Allows make variables to be injected into build system (#499)
* Various documentation/comment updates (#511,#517,#518,#537,#533,#542,#570,#569)
* DSPTools documentation and example (#457, #568)
* Support for no UART configs (#536)
* Assemble firrtl-test.jar (#551)
* Add SPI flash configurations (#546)
* Add Dromajo + FireSim Dromajo simulation support (#523, #553, #560)
* NVDLA integration (#505, #559, #580)
* Add support for Hammer Sim (#512,#581,#580,#582)

### Changed
* Bump FireSim to version 1.10 (#574,#586)
* Bump BOOM to version 3.0 (#523, #574,#580)
* Bump Gemmini to version 0.3 (#575, #579)
* Bump SPEC17 workload (#504, #574)
* Bump Hwacha for fixes (#580)
* Bump SHA3 for Linux 5.7rc3 support (#580)
* Bump Rocket Chip to commit 1872f5d (including stage/phase compilation) (#503,#544)
* Bump FireMarshal to version 1.9.0 (#574)
* Chisel 3.3 and FIRRTL 1.3 (#503,#544)
* BuildTop now builds a ChipTop dut module in the TestHarness by default (#480)
* The default for the TOP make variable is now ChipTop (was Top) (#480)
* Top has been renamed to DigitalTop (#480)
* Bump libgloss (#508, #516, #580)
* The default version of Verilator has changed to v4.034 (#547). Since this release adds enhanced support for Verilog timescales, the build detects if Verilator v4.034 or newer is visible in the build environment and sets default timescale flags appropriately.
* Use Scalatests for FireSim CI (#528)
* Cleanup Ariane pre-processing (#505)
* Modify Issue Template to be more explicit (#557)
* FireChip uses Chipyard generator (#554)
* Have all non-synthesizeable constructs in test harness (#572)

### Fixed
* Aligned esp-tools spike with Gemmini (#509)
* Fix debug rule in Verilator (#513)
* Clean up SBT HTTP warnings (#526,#549)
* Artefacts dropped in FireSim (#534)
* Working IceNet + TestChipIP Unit Tests (#525)
* Don't initialize non-existent Midas submodule (#552)
* Verilator now supports +permissive similar to VCS (#565)
* Fix direction of IOCell OE (#586)

### Deprecated
* N/A

### Removed
* Removed MIDAS examples CI (until a better solution that is faster is found) (#589)


## [1.2.0] - 2020-03-14

A more detailed account of everything included is included in the dev to master PR for this release: https://github.com/ucb-bar/chipyard/pull/418

### Added
* Ring Topology System Bus NoC (#461)
* Integration of the Ariane core into Chipyard (#448)
* FireMarshal now generates an extra copy of linux kernel with dwarf debugging info for use in FirePerf (#427)
* Add option to use blackboxed SimDRAM instead of SimAXIMem (#449)
* Log `init-submodules` script (#433)
* Moved the Docker image used for CI into Chipyard (prev. in BOOM) (#463)

### Changed
* Bump FireSim to 1.9.0 - Includes FirePerf TracerV Flame Graph features
* IOBinders and BuildTop unification between FireChip and Chipyard (#390)
* Bump BOOM to version 2.2.4 (#463)
* Bump Gemmini to version 0.2 (#469)
* Update to CircleCI 2.1 config. syntax and cleanup CI file (#421)
* FireMarshal moved from FireSim to Chipyard (#415)
* Rename config. mixins to config fragments (#451)

### Fixed
* `git status` should be clean. (Although you will need to manually cleanup the libgloss and qemu directories after first setup). (#411, #414)
* Fix Hetero. BOOM + Rocket + Hwacha config (#413)
* Fix VCS stdout (#417)
* Add a git version check to the init scripts and make them work outside of the repo root (#459)
* Fix generation of env.sh for zsh (#435)
* GCD example bug (#465)

### Deprecated
* N/A

### Removed
* N/A



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
* Top/Top-level-traits now behave as a configurable generator (PR #347)
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
