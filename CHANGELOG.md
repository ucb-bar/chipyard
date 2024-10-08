# Changelog

This changelog follows the format defined here: https://keepachangelog.com/en/1.0.0/

## [1.13.0]

Chipyard 1.13.0 contains numerous new RTL changes and decouples Firesim from Chipyard.

RTL changes:
 - Integration of Saturn and Ara vector units
 - Support for RISC-V `B` extension instructions in Rocket, Shuttle, and BOOMv4
 - Added VexiiRiscv SpinalHDL RV64GCB core
 - Added example VivadoHLS accelerator flow

Firesim's compiler is now decoupled from Chipyard's making Firesim more modular and reusable.

## Added

- Add dependabot to auto-update submodules (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1940)
- Update dependabot to also update GH-A workflows (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1950)
- Nexys video fpga bringup config (by @SeahK in https://github.com/ucb-bar/chipyard/pull/1942)
- Integrate vector unit (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1928)
- Update Initial-Repo-Setup.rst (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1977)
- Add FireSim paper tests (migrating them from FireSim) (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1980)
- Integrate Ara vector unit (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1973)
- Bump rocket-chip/saturn/shuttle (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1991)
- Update rerocc to support rerocc MMIO ctrl (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1981)
- Enable B extension in Rocket/Shuttle/BoomV4 (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/2001)
- Add CI help text to PR template | Reduce more CI runs (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/2022)
- Move FireChip target-specific CI from FireSim to Chipyard (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/2014)
- ADD: Add CMake build option for tests and example C++ program (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/2036)
- Add vexiiriscv CPU support (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/2057)
- Add example accelerator using HLS (by @schwarz-em in https://github.com/ucb-bar/chipyard/pull/2056)

## Changed

- Remove need for users to install conda-lock themselves (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1941)
- Cleaner CI conda env setup | Selective weekly dependabot updates (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1957)
- Add hook into Chipyard build system for FireSim (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1935)
- FireSim x Chipyard Decoupled (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1984)
- Support FIRRTL2 bridge + Chisel 6 annos for FireSim (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/2013)

## Fixed

- Fix VLSI CI - Fix conda environment creation (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1963)
- Create fresh conda environment for conda-lock instead of cloning base (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1970)
- Bump constellation to fix support for AXI-converted TL (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1976)
- Update generate-conda-lockfiles.sh (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1978)
- Bump Checkpointing Script + Docs (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1911)
- Vector bugfixes (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/2003)
- ADD: add dump flow and documentation on tests building (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/2051)

## [1.12.3] - 2024-8-21

Small QoL updates and fixes.

## Added

- Bump rocket-chip/saturn/shuttle (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1991)
- Update rerocc to support rerocc MMIO ctrl (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1981)
- Enable B extension in Rocket/Shuttle/BoomV4 (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/2001)

## Fixed

- Vector bugfixes (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/2003)

## [1.12.0] - 2024-8-1

Major update switching to Chisel 6 for RTL/FPGA/VLSI flows. Compress-acc and ReRoCC added as new RTL blocks. Software updated to GCC-13 with Linux Kernel 6.4.

## Added

- Support BINARIES_DIR make flag (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1773)
- Add large boom cospike config (by @joonho3020 in https://github.com/ucb-bar/chipyard/pull/1786)
- Enable debug SBA on all default designs (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1763)
- Allow passing BINARY_ARGS to target binaries (pk) (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1792)
- Add example rocket-chip-blocks timer integration (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1793)
- Add support for building CIRCT from source (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1806)
- Bump constellation - adds support for inlining noc routers (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1809)
- Add GPIOPunchthrough harnessbinder (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1823)
- EXT_FILELISTS and EXT_INCDIR APIs for including external verilog projects (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1832)
- Add EmptyChipTop example (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1845)
- add sv39 fragments (by @joonho3020 in https://github.com/ucb-bar/chipyard/pull/1874)
- Support Chisel6 for RTL-sim/VLSI/FPGA flows (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1854)
- Add GCD IOBinders examples (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1884)
- Integrate ReRoCC (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1783)
- Generate a log file of the CIRCT output (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1899)
- Add compress-acc (by @joonho3020 in https://github.com/ucb-bar/chipyard/pull/1906)
- Add DMI Bridge to FireSim (by @soohyuk-cho in https://github.com/ucb-bar/chipyard/pull/1852)
- Add makefile flag to disable binary disassembly during sims (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1921)

## Changed

- Improve GCD example (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1757)
- Update MMIO peripheral docs (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1774)
- Bump to latest rocket-chip (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1770)
- CHANGE: Organize abstract config into sections and change comment format to docstrings (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1722)
- Bump to Verilator v5.022 (by @vighneshiyer in https://github.com/ucb-bar/chipyard/pull/1800)
- Bump testchipip for improved TLSerdesser (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1768)
- REFACTOR: minor fix on code styling (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1815)
- REFACTOR: rearrange gitsubmodule list to match directory order (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1813)
- Add PD-optimized variant of boom (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1816)
- Bump rocket-chip to standalone diplomacy (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1818)
- Bump to gcc 13 (by @vighneshiyer in https://github.com/ucb-bar/chipyard/pull/1802)
- Use SV48 when possible (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1847)
- Flatten barstools into Chipyard (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1855)
- Automatically set `DTSTimebase` to PBUS frequency (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1856)
	- Bump firesim to version with detached build.sbt (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1864)
- add upper bound for python version to avoid incompatible packages (by @bwhitchurch in https://github.com/ucb-bar/chipyard/pull/1898)
- Bump FireMarshal (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1903)
- Update checkpointing script (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1909)
- Update rocket-chip with modern diplomacy/prci packaging (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1895)
- Non-recursive clone of compress-acc (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1910)
- Bump rc/components to improve module naming (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1894)
- Bump rocc-acc-utils for updated logger (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1917)
- Bump rocket-chip/submods for reorganized rocket configs (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1914)
- Update testchipip for bootrom that clears MIP (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1923)
- Bump to chisel 6.5.0 (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1927)

## Fixed

- Fix unnecessary runs/rebuilds of RTL simulators (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1758)
- Fix submodule recusive cloning (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1765)
- Fix invalid escape sequences (by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1798)
- docs: Update FireSim docs (by @geekLucian in https://github.com/ucb-bar/chipyard/pull/1795)
- Fix Gemmini Spike test in CI + Fix FireMarshal launch jobs erroring (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1804)
- FIX: Change default UART FIFO size and combine UART Config fragments (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1683)
- Fix InitZero example + add to CI (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1826)
- Fix driver dts issues (align w/ RTL) (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1837)
- Bump FireMarshal (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1840)
- Bump rc w/ tile interrupt fix (by @joonho3020 in https://github.com/ucb-bar/chipyard/pull/1841)
- Update build-toolchain-extra.sh (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1848)
- Update MultiHarnessBinders to connect differently typed ports + Ignore TLMonitors on DigitalTop scope (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1868)
- Fix classpath_cache bug (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1880)
- Fix clock name and macro paths for Sky130 VLSI flow (by @nayiri-k in https://github.com/ucb-bar/chipyard/pull/1882)
- Name all the generated ClockDomains (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1891)
- Bump FireMarshal + PK (Pk bumped to fix toolchain bump issues w/ linux) (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1905)

## Removed

- Remove useless conda env sourcing (by @joonho3020 in https://github.com/ucb-bar/chipyard/pull/1759)
- Remove legacy SFC flags (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1859)
- Remove tapeout.GenerateModelStageMain (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1860)

## Uncategorized

- Fix typo in build-setup.sh (by @buggy213 in https://github.com/ucb-bar/chipyard/pull/1760)
- vlsi/Makefile: truncate file SRAM_GENERATOR_CONF (by @oharboe in https://github.com/ucb-bar/chipyard/pull/1778)
- Switch update-circt action to v1 (by @seldridge in https://github.com/ucb-bar/chipyard/pull/1788)
- Ensure all GPIO signals are tied off (by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1732)
- Enabling JTAG Debuging in VCU118 FPGA changelog:added (by @ksungkeun84 in https://github.com/ucb-bar/chipyard/pull/1796)
- Bump boom to fix Chisel 6 compatibility (by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1820)
- fix: Link conda lib during CIRCT build (by @geekLucian in https://github.com/ucb-bar/chipyard/pull/1824)
- Fix missing step in documentation. (by @gonsolo in https://github.com/ucb-bar/chipyard/pull/1830)
- Spelling fix. (by @gonsolo in https://github.com/ucb-bar/chipyard/pull/1833)
- [docs] Fix sim output executable name usage in docs (NFC) (by @eymay in https://github.com/ucb-bar/chipyard/pull/1858)
- Spelling fixes. (by @gonsolo in https://github.com/ucb-bar/chipyard/pull/1862)
- Improve insert includes (by @bwhitchurch in https://github.com/ucb-bar/chipyard/pull/1902)
- Bump firesim (by @joonho3020 in https://github.com/ucb-bar/chipyard/pull/1913)
- Add configuration for MegaBoom that uses SimBlockDevice (by @eszpotanski in https://github.com/ucb-bar/chipyard/pull/1924)

**Full Changelog:** https://github.com/ucb-bar/chipyard/compare/1.11.0...1.12.0

## [1.11.0] - 2024-1-22

Adds Ubuntu support for firemarshal, multi-chiptop configuration for chiplets, caliptra accelerator, spike-cosim.

## Added
- Provide example of tethered-config simulation with MultiHarnessBinders (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1484)
- Add release note automation (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1548)
- Improve peripheral config binders (Reapply #1511) (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1527)
- UPF Generation (by @sriramsridhar23 in https://github.com/ucb-bar/chipyard/pull/1347)
- Improve: SiFive peripheral device doumentation (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1578)
- Bump to latest rocket-chip | integrated fixed-point | pull in stage/phase (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1577)
- Enable precommit | Format files (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1585)
- KLayout section of Sky130+OpenROAD tutorial (by @nayiri-k in https://github.com/ucb-bar/chipyard/pull/1514)
- Added useful "Build script exited at step X" errors for each step in build-setup.sh (by @JL102 in https://github.com/ucb-bar/chipyard/pull/1614)
- Adds support for Digilent Nexys Video FPGA board. (by @milovanovic in https://github.com/ucb-bar/chipyard/pull/1616)
- Add NarrowRocketCache config fragment (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1603)
- Add Caliptra AES256 ECB engine as RoCC accelerator (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1642)
- Support using HarnessBinders without IOBinders (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1636)
- Add Bringup on Arty100T config, using PMOD-gpio for ser-tl (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1657)
- Install uart_tsi utility as part of build-toolchain-extra (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1670)
- Add spike devices (by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1692)
- Bump testchipip to support multi-serial-tl (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1630)
- ADD: bump fpga-shell (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1703)
- Add UART device/JTAG to Arty100T default design (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1712)
- Add generators/ submodule status check to all Make targets (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1718)
- Add clearer --skip flags to build-setup (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1724)
- Add Baremetal IDE support (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1534)
- ADD: add information on Baremetal-IDE in docs (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1730)
- Support clustered-cores (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1629)
- Add debug clock tap port to all default designs (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1697)
- Add support for symmetric serial-tilelink (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1716)
- Add Chip ID Pin and Port (by @schwarz-em in https://github.com/ucb-bar/chipyard/pull/1721)
- FastSimRocketConfig with no TLMonitors (by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1752)
- Use lean conda for faster initial setup (by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1750)
- Add BroadcastCoherence rocket config (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1755)

## Changed
- Bump FireMarshal (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1556)
- Bump spike | support M-mode only cosim (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1530)
- Bump to chisel3.6 (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1531)
- Replace Dromajo FireSim bridge with Cospike (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1588)
- Add PortAPI between IO and Harness blocks (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1610)
- REFACTOR: change bootaddr register address and PC reset vector address (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1605)
- REFACTOR: rename arty35t explicitly (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1682)
- Add HasHarnessInstantiators into MultiHarnessBinder (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1674)
- Install CIRCT out of Conda (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1675)
- [cd] Bump CIRCT from firtool-1.58.0 to firtool-1.60.0 (copy #1688) (by @mergify[bot] in https://github.com/ucb-bar/chipyard/pull/1689)
- Bump constellation with improved TL-noc (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1633)
- Update testchipip imports with new testchipip organization (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1710)
- CHANGE: Set scratchpad as default config (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1719)
- Rename blocks/cache submodules to match new chipsalliance ownership (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1726)
- Rename timeout_cycles to TIMEOUT_CYCLES to match convention (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1706)
- [cd] Bump CIRCT from firtool-1.60.0 to firtool-1.61.0 (copy #1691) (by @mergify[bot] in https://github.com/ucb-bar/chipyard/pull/1699)
- Bump tcip/barf/marshal/fsim (by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1748)
- Remove skip-validate option (by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1749)
- Add .gitignore for Embench Build Folder (by @vighneshiyer in https://github.com/ucb-bar/chipyard/pull/1754)

## Fixed
- Bump lockfiles | Force user-installed conda during conda-lock (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1555)
- Fix FIFO-fixing for PRCI bus (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1563)
- Fix VCU118 freq adjustment configs (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1597)
- Fix Arty100T verilog build (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1608)
- Make scripts/insert-includes.py use Python from conda env (by @hansungk in https://github.com/ucb-bar/chipyard/pull/1615)
- Fix icenet-loopback clock and reset domain (by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1612)
- Bump FireMarshal for linux rng init fix (by @raghav-g13 in https://github.com/ucb-bar/chipyard/pull/1620)
- Bump barstools (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1625)
- [vlsi][ci skip] Fix tutorial.mk so that INPUT_CONFS override only affects tutorials (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1631)
- Fix regex for similar module names that are uniquified (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1640)
- Rebuild `jar`s when `sv`/`v` files are changed + Add documentation on blackboxes (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1639)
- Use TARGET instead of PREFIX in /tests Makefile(s) (by @raghav-g13 in https://github.com/ucb-bar/chipyard/pull/1647)
- Fix IO direction for host Serial-TL port (by @ethanwu10 in https://github.com/ucb-bar/chipyard/pull/1654)
- More robust env.sh | Fix conda activate if in subshell (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1653)
- Always generate MFC Blackbox filelist (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1668)
- Remove references to ENABLE_YOSYS (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1695)
- FIX: fix vcu118 sd card frequency (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1685)
- FIX: fix conda-lock version, >1.4.0 does not work! (by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1714)
- docs: correct to TOP_MACROCOMPILER_MODE (by @oharboe in https://github.com/ucb-bar/chipyard/pull/1744)
- Use non-lock-file conda requirements in RTD (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1742)
- Fix GCD MMIO example (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1753)

## Removed
- Remove pre-commit from env.sh (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1587)
- Remove Dromajo (by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1598)
- Remove MultiClockHarnessAXIMem (by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1601)

## Uncategorized
- Documentation: Change "don't use Windows" to "if using Windows, use WSL" (by @JL102 in https://github.com/ucb-bar/chipyard/pull/1572)
- docs: Add info on chipyard's tests directory and how to do multithreading in RTL simulations (by @JL102 in https://github.com/ucb-bar/chipyard/pull/1646)
- Reduce CDE lookups for IOBinders (by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1720)
- Update deprecated APIs to prepare for Chisel 5 (by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1723)
- Allow sed override in uniquify-module-names.py (by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1729)
- Update deprecated/unused Chisel APIs (by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1743)

**Full Changelog:** https://github.com/ucb-bar/chipyard/compare/1.10.0...main

## [1.10.0] - 2023-6-16

Adds superscalar in-order core, prefetchers, architectural checkpointing, examples for custom-chiptop/tapeout-chip/flat-chiptop. FireSim bumped with new local FPGA support: Xilinx VCU118 (w/XDMA), Xilinx Alveo U250/U280 (w/XDMA, in addition to previous Vitis support), RHSResearch NiteFury II (w/XDMA). FireSim now also supports Xcelium for metasims.

### Added
* QoL improvement to IOBinders + custom ChipTop example by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1399
* New Scala-based Config Finder by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1424
* ADD: improve Makefile in tests/, add explicit arch flags by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1439
* Add mt-helloworld example by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1428
* Add tutorial software by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1447
* Support not instantiating the TileClockGater/ResetSetter PRCI control by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1459
* ELF-based-loadmem | architectural restartable checkpoints by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1438
* Add embench build support by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1479
* Support multi-run of binaries by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1480
* Integrate barf (prefetchers) by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1505
* Add higher level explanations of RoCC + more resources by @nikhiljha in https://github.com/ucb-bar/chipyard/pull/1486
* Support banked/partitioned scratchpads by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1431
* Add dual-issue in-order "shuttle" core by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1495
* Improve peripheral fragments to include more peripheral devices and support instantiating multiple instances of same device by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1511

### Changed
* Bump to latest rocket-chip/chisel3.5.6 by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1411
* Resolve merge conflicts in chisel3.5.6 bump by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1430
* PLL integration example + FlatChipTop/TestHarness by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1427
* bump testchipip by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1434
* Fix ChipLikeQuadRocketConfig crossing by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1436
* Bump TestChipIp to improve default serial_tl behavior by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1435
* Bump testchipip to standardize TL serdesser bundle params by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1446
* Bump to Hammer 1.1.1 by @harrisonliew in https://github.com/ucb-bar/chipyard/pull/1451
* Always initialize fpga-shells with init-submodules.sh by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1456
* Support uni-directional TLSerdesser by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1476
* Move xcelium.mk out of top-level by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1482
* Set default config back to 1-channel by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1483
* Unify supernode/harness-clocking across chipyard/firesim/fpga by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1474
* Use fat jar's to remove SBT invocations by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1375
* Bump to latest rocket-chip by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1475
* Improvements to chipyard clocking by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1489
* Downgrade cryptography | Pin linux sysroot by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1494
* bump mempress by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1498
* bump sha3 by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1499
* Bump FireMarshal by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1502
* Split NVDLA config out of ManyMMIOAccels config to reduce CI load by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1503
* Ignore barstools compilation if not needed by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1504
* Disable NVDLA simulations in CI by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1507
* Update NoC example config to match new PRCI organization by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1509
* Bump gemmini by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1519

### Fixed
* Various improvements and fixes by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1420
* Ensure conda cleanup regex properly filters out non-numeric chars by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1425
* Clear screen on prompt by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1449
* misc: many fixes to cospike by @tianrui-wei in https://github.com/ucb-bar/chipyard/pull/1450
* Uniquify module names that are common to Top & Model by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1442
* Use pk/encoding.h for hello/mt-hello by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1454
* Fix no-uart configs by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1457
* Fix support for no-bootROM systems by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1458
* Check that HarnessClockInstantiator doesn't receive requests for similarly-named-clocks with different frequencies by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1460
* uniquify module names by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1452
* Flip serial_tl_clock to be generated off-chip by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1445
* Move TestHarness to chipyard.harness, make chipyard/harness directory by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1463
* Separate out conda-lock generation into new script by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1466
* Bump DRAMSim2 to avoid verbose log files by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1468
* Bump Verilator and use `TestDriver.v` as top by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1398
* Add 1GB / 4GB DRAM firechip configs for FireSim VCU118 by @sagark in https://github.com/ucb-bar/chipyard/pull/1471
* Rename SerialAdapter+SimSerial to TSIToTileLink/SimTSI/TSIHarness by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1465
* Make BootAddrReg optional by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1464
* Fix vcd/fst/fsdb waveform generation by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1473
* Switch RTL sims to absolute clock-generators by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1472
* Generate objdump | check BINARY | cospike fixes by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1467
* Small QOL fixes for Xcelium by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1485
* (VCU118 DDR HarnessBinder)Fix data field width mismatch between DDR AXI and TileLink MemoryBus  by @jerryhethatday in https://github.com/ucb-bar/chipyard/pull/1487
* Force conda-lock to v1 by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1492
* Loosen/tighten conda requirements | Fix conda-lock req by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1497
* Misc Makefile Fixes by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1496
* Bump constellation to fix interconnect FIFO-fixers by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1510
* [ci skip] Fix broken docs link by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1515
* Revert changes to peripheral fragments by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1518

### New Contributors
* @tianrui-wei made their first contribution in https://github.com/ucb-bar/chipyard/pull/1450
* @jerryhethatday made their first contribution in https://github.com/ucb-bar/chipyard/pull/1487
* @nikhiljha made their first contribution in https://github.com/ucb-bar/chipyard/pull/1486

## [1.9.1] - 2023-04-21

Various fixes for Linux boot, More Chip/bringup examples, Chisel 3.5.6 bump

### Added
* QoL improvement to IOBinders + custom ChipTop example by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1399
* PLL integration example + FlatChipTop/TestHarness by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1427
* Bump TestChipIp to improve default serial_tl behavior by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1435
* Bump testchipip to standardize TL serdesser bundle params by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1446
* HarnessBinder asserts to catch bad clock generation by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1460

### Changed
* New Scala-based Config Finder by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1424
* Bump to latest rocket-chip/chisel3.5.6 by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1411
* Resolve merge conflicts in chisel3.5.6 bump by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1430
* bump testchipip by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1434
* ADD: improve Makefile in tests/, add explicit arch flags by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1439
* Various submodule bumps by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1448
* Support not instantiating tile reset/clock contorl features by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1459

### Fixed
* Various improvements and fixes by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1420
* Ensure conda cleanup regex properly filters out non-numeric chars by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1425
* Fix ChipLikeQuadRocketConfig crossing by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1436
* Uniquify module names that are common to Top & Model by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1442
* Support for no-bootROM systems by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1458
* Support for no-UART systems by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1457

## [1.9.0] - 2023-03-23

Faster FIRRTL build support work CIRCT. New software support for RISC-V GCC12 and Linux 6.2. Various bumps and fixes of all submodules.

### Added
* Add example ring-only NoC Config by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1325
* Bump Gemmini by @hngenc, @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1276 https://github.com/ucb-bar/chipyard/pull/1326
* Bump FireMarshal, Bump to newer RV toolchain (deprecate use of esp-tools for Gemmini) by @abejgonzalez, @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1284 https://github.com/ucb-bar/chipyard/pull/1304 https://github.com/ucb-bar/chipyard/pull/1306 https://github.com/ucb-bar/chipyard/pull/1327 https://github.com/ucb-bar/chipyard/pull/1334  https://github.com/ucb-bar/chipyard/pull/1335  https://github.com/ucb-bar/chipyard/pull/1344 https://github.com/ucb-bar/chipyard/pull/1394  https://github.com/ucb-bar/chipyard/pull/1403 https://github.com/ucb-bar/chipyard/pull/1415
* Add support for VC707 FPGA board by @Lorilandly in https://github.com/ucb-bar/chipyard/pull/1278
* Fail simulations on TSI errors by @tymcauley in https://github.com/ucb-bar/chipyard/pull/1288
* Add pre-commit support by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1294 https://github.com/ucb-bar/chipyard/pull/1310
* Bump mempress by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1305
* CIRCT Integration by @abejgonzalez, @joey0320 in https://github.com/ucb-bar/chipyard/pull/1239 https://github.com/ucb-bar/chipyard/pull/1312 https://github.com/ucb-bar/chipyard/pull/1372 https://github.com/ucb-bar/chipyard/pull/1396
* Bump to scala 2.13.10/chisel 3.5.5/latest rocketchip by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1303
* Spike-as-a-Tile and use for co-simulation by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1307 https://github.com/ucb-bar/chipyard/pull/1323 https://github.com/ucb-bar/chipyard/pull/1360
* Add clone-tile configs by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1322
* New Hammer by @harrisonliew in https://github.com/ucb-bar/chipyard/pull/1324 https://github.com/ucb-bar/chipyard/pull/1368  https://github.com/ucb-bar/chipyard/pull/1374  https://github.com/ucb-bar/chipyard/pull/1369 https://github.com/ucb-bar/chipyard/pull/1410
* Config finder `make` target by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1328 https://github.com/ucb-bar/chipyard/pull/1381
* Arty100T board + TSI-over-UART by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1345
* Add graphml visualization section to docs by @schwarz-em in https://github.com/ucb-bar/chipyard/pull/1387
* Add a frag./config for MMIO only FireSim bridges by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1393
* Add log of chisel elaboration to generated src by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1400
* Xcelium support by @sagark in https://github.com/ucb-bar/chipyard/pull/1386
* Bump Sodor @a0u in https://github.com/ucb-bar/chipyard/pull/1338
* Bump Constellation by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1339

### Changed
* remove RocketTilesKey by @SingularityKChen in https://github.com/ucb-bar/chipyard/pull/1264
* Move setup script to scripts/, use a symlink at top-level by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1271
* Decoupled sbus width from boom|hwacha|gemmini memory interface widths by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1273
* Remove conda from build-toolchains-extra.sh by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1266
* Rework build-setup | Add single-node CI by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1282
* Switch simulators to C++17. by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1285
* Init FPGA submodules in build-setup.sh by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1292
* Stripped down rocket configs for FireSim testing by @t14916 in https://github.com/ucb-bar/chipyard/pull/1302
* Add more minimal firesim configs for testing by @t14916 in https://github.com/ucb-bar/chipyard/pull/1313
* Add workshop info to README.md by @sagark in https://github.com/ucb-bar/chipyard/pull/1314
* Removed FireSim tests and harnesses by @nandor in https://github.com/ucb-bar/chipyard/pull/1317
* Move boom's tracegen interface to boom submodule  by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1331
* Split up RocketConfigs.scala by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1340
* Sky130/Openroad Tutorial Fixes by @nayiri-k in https://github.com/ucb-bar/chipyard/pull/1392
* Testing VLSI commands for chipyard tutorial by @nayiri-k in https://github.com/ucb-bar/chipyard/pull/1395
* Reduce test cases for noc-config in CI by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1359
* Remove TLHelper, directly use tilelink node constructors by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1358
* Remove chisel-testers submodule by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1378
* Cache `.ivy2` and `.sbt` within Chipyard root directory by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1362

### Fixed
* Remove extra parenthesis by @odxa20 in https://github.com/ucb-bar/chipyard/pull/1261
* Fixed typo in Initial-Repo-Setup.rst by @PisonJay in https://github.com/ucb-bar/chipyard/pull/1269
* fix: S-interpolator for assert, assume and printf by @SingularityKChen in https://github.com/ucb-bar/chipyard/pull/1242
* Revert "fix: S-interpolator for assert, assume and printf" by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1272
* changelog: fixed TinyRocketArtyConfig FPGA reset signal polarity (Please Backport) by @T-K-233 in https://github.com/ucb-bar/chipyard/pull/1257
* Fix CY logo in README by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1295
* More files to gitignore by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1297
* Bump rocket-dsp-utils for ShiftRegisterMem fix. by @milovanovic in https://github.com/ucb-bar/chipyard/pull/1298
* Set VLOGMODEL=MODEL by default in variables.mk by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1337
* Fix compile breaking due to merge conflict by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1321
* Makefile bug fixes by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1336
* Fix Verilog Prerequisites + Ignore `mv` stdout by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1406
* Fix Chisel hierarchy API - Fixes #1356 by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1361
* Remove gen-collateral when rebuilding by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1342
* Fix VLSI input files list emission to avoid bash "too many arguments" error by @sagark in https://github.com/ucb-bar/chipyard/pull/1348
* Small build system improvements by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1349
* Fix socket name length issues on CI by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1353
* Fix TestDriver.v missing from gen-collateral after recompiling by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1354
* Consolidate CI testing configs to improve CI runtime by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1352
* Remove Duplicate Compiler Flags by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1351
* fpga makefile clean fix by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1357
* Fix newline in message in build-setup.sh by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1365
* Update assert message if configs can't be split by `:` by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1373
* Remove Duplicate Compiler Flags by @joey0320 in https://github.com/ucb-bar/chipyard/pull/1367
* Move more tmp/ folders to a unique location by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1382
* Remove stale conda env's after 2 days by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1389
* Match CY/FireSim deps | Unpin deps | Update lockfiles by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1391
* Only support HTML docs by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1401
* Only HTML docs v2 by @abejgonzalez in https://github.com/ucb-bar/chipyard/pull/1402
* Fix ANSI color output by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1407
* Fix chisel elab errors not causing flow to stop by @jerryz123 in https://github.com/ucb-bar/chipyard/pull/1409
* lean gemmini tutorial by @sagark in https://github.com/ucb-bar/chipyard/pull/1413

## [1.8.1] - 2022-10-18

Various fixes and improvements, bump FireSim to 1.15.1.

### Added
* Bump chipyard to integrate mempress #1253

### Changed
* Default VCS simulators should generate FSDB #1252
* Bump to FireSim 1.15.1 for various improvements #1258

### Fixed
* Revert to using sbt-launch.jar to run SBT #1251
* Fix open files issue automatically on systems with a sufficiently large hard limit, to work around buildroot bug. #1258
* Fixed PATH issues in generated env.sh #1258
* Fixed scripts/repo-clean.sh to restore clean repo state again #1258

## [1.8.0] - 2022-09-30

Adds support for NoC-based interconnects with Constellation (https://constellation.readthedocs.io/en/latest/). Switch to Conda for dependency/environment management. Bump Rocket Chip and other hardware libraries. Bump to FireSim 1.15.0.

### Added
* RTL: Support for packet-switched NoC-based TileLink main bus interconnects with Constellation
* Conda: Support setting up a Chipyard development environment through Conda
* Hammer: Fully open-source Sky130 flow tutorials in open-source and commercial tools
* Hammer: IR key history
* Hammer: Joules power analysis support
* Hammer: Tempus STA support

### Changed
* RTL: Default memory-mapped addresses for fft/dsp/example MMIO accelerators changed to be non-overlapping
* Repo: Update SiFive submodules to new chipsalliance upstreams
* Rocketchip: Bumped to 1.6
* Chisel: Bumped to 3.5.2
* Firesim: Bumped to 1.15.0
* Firemarshal: Bumped to latest

### Fixed
* RTL: Fix clock frequency rounding
* Hammer: VCS FGP is now opt-in
* Dromajo: Fix to variable definition
* Testchipip: Fix write-strobe handling for 64B configurations
* Build: Ignore dotfiles in lookup_srcs

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
