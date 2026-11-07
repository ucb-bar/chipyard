# CLAUDE.md

This file holds the project's rules and method, for people and agents alike. Tasks, owners, status, results and decisions live in the GitHub issues on [`Capheuss/boom-ustt`](https://github.com/Capheuss/boom-ustt/issues), tracked in the roadmap issue #17. The design background (STT, µSTT, and where each piece goes in BOOM) is in `project-docs/PLAN.md`. Do not add other planning files.

## Project context

This repository is a UT Austin hardware security course project (ECE 382N): implementing the **µSTT** Spectre mitigation (*µSTT: Microarchitecture Design for Speculative Taint Tracking*, ICCD'25, [FPSG-UIUC/uSTT](https://github.com/FPSG-UIUC/uSTT)) in the **SonicBOOM** (BOOMv3) out-of-order RISC-V core. µSTT has only been evaluated in gem5 on ARM; building it into a real RISC-V RTL core is the contribution. The repo is a fork of Chipyard 1.15.0, with BOOM included in the tree.

A result counts only if it is reported on all three axes:

- **Security:** the Spectre PoCs (#9) leak on the baseline and do **not** leak on the protected configuration.
- **Performance:** cycles and IPC from Verilator RTL simulation, normalized to the baseline (#11).
- **Hardware:** LUTs, FFs, BRAM, DSPs, WNS and power from Vivado 2023.2 on the VCU118 part (#12, #13). A mitigation that breaks timing is reported as such, not hidden.

## Tracks and milestones

| Track | What it produces | Issues |
| ----- | ---------------- | ------ |
| A. Rename side | Taint table and YRoT propagation, then the µSTT Age Matrix | #3, #4, #5 |
| B. Issue side | Holding back tainted transmitters, then the µSTT impede micro-op | #3, #6, #7, #8 |
| C. Security, verification and performance | Spectre PoCs, regression script, performance harness | #9, #10, #11 |
| D. Hardware cost and Vivado | VCU118 reports, out-of-context synthesis, uSTT RTL reproduction, Vivado simulation | #12 to #16 |

Milestones: **M0** environments work (#1) → **M1** baselines (leak shown, interface spec, baseline hardware reports) → **M2** conservative stand-in blocks the attack → **M3** baseline STT → **M4** µSTT mechanisms → **M5** hardware evaluation. #3 (the A/B interface spec) is the one hard dependency between tracks; settle it first. B can start immediately with the conservative stand-in (#6).

Decisions so far (#2): Spectre threat model first (Futuristic is a stretch goal); BOOM **v3** only; develop on `SmallBoomV3Config` and report on `MediumBoomV3Config`; FPGA target is **VCU118** (`xcvu9p-flga2104-2L-e`, `BoomVCU118Config`), Vivado-only since there is no board.

## Ground rules

- **Baseline is frozen.** The tag `baseline-v0` marks unmodified Chipyard 1.15.0 + BOOM `ba03175`. Every number is reported relative to it, with the commit hash it was measured on.
- **Every mechanism is a switch.** New hardware sits behind a BOOM parameter (`enableSTT`, `impedeUop`, ...; default off) and gets its own Chipyard config (for example `SmallBoomV3STTConfig`). With every switch off, the generated Verilog must be identical to the baseline; check it by diffing `generated-src`.
- **Security before speed.** A protected config is timed only after it passes riscv-tests and blocks every attack in #9.
- **Mark changes to upstream code.** Comment each change inside BOOM or Chipyard files with `// [boom-ustt]`, the way uSTT marks its gem5 changes with `[Boru, STT]`. Keep new logic in new files where possible.
- **`reference/uSTT` is read-only.** Never modify the submodule; copy or port from it instead.
- **Cycles come from RTL simulation** (Verilator, or xsim for #15), never from Spike. Spike is for functional checks and co-simulation.
- **Hardware numbers come from Vivado 2023.2** on the ECE machines; the department license is capped at 2023.10. Use the same part, clock and flow for every config.
- **Results and decisions go on the issue.** Post each measurement as a comment on its issue: configuration, commit, and the numbers. Record design decisions (interface, impede-uop design, threat model) the same way.
- **One branch per issue, merged by pull request into `dev`.** `dev` is the default branch; `main` is unused. Both are protected: direct pushes are blocked and every PR needs **one approving review** from a teammate.

## Working on an issue

1. **Pick it up.** Assign yourself on the issue.
2. **Branch from fresh `dev`:** `git switch dev && git pull && git switch -c <issue>-<short-name>` (for example `6-conservative-delay`).
3. **Work and test locally.** Run the tests your change touches; the full regression (#10) is required before merging only for changes to BOOM itself.
4. **Check `git status` before committing.** `build-setup.sh` modifies `conda-reqs/` lock files and leaves submodules dirty (`generators/constellation`, `toolchains/*`). Never `git commit -a`; stage your files by name.
5. **Open a pull request into `dev`** with `Closes #<issue>` in the description, and request a review from a teammate. Include test results (and for BOOM changes, the regression summary) in the PR.
6. **Merge after one approval**, then delete the branch.
7. **Report on the issue.** Post measurements and decisions as comments before closing it.

There is no CI for this project. Chipyard's upstream GitHub workflows came with the fork and should stay disabled (Actions tab → each workflow → Disable workflow): they need Berkeley's self-hosted runners, and BOOM builds are too heavy for GitHub's runners. All testing happens locally.

## Environment

Every command that uses the toolchain (sbt/Chisel, Verilator, RISC-V gcc, Spike) runs through `./ustt`, which sets up conda + Chipyard for that one command only:

```bash
./ustt <command>            # conda + Chipyard env
./ustt --vivado <command>   # also the xilinx/2023 module (license server) + Vivado 2023.2
./ustt                      # interactive subshell with the env
```

One-time setup: `cp ustt-env.local.sh.example ustt-env.local.sh`, edit it, then `./build-setup.sh riscv-tools` (about an hour). On ECE machines the home quota is 2 GB, so Miniforge, TeX Live and every cache live on `/misc/scratch/$USER`. Do not source the env from `~/.bashrc`: the conda env shadows system gcc/python and breaks other EDA tools. Xilinx is loaded before Chipyard so Chipyard's `riscv64-unknown-elf-*` comes first on PATH, ahead of the copy shipped with Vitis.

## Common commands

```bash
# Build the Verilator simulator for a config
./ustt make -C sims/verilator CONFIG=SmallBoomV3Config -j16

# Run all ISA tests / benchmarks on it
./ustt make -C sims/verilator CONFIG=SmallBoomV3Config run-asm-tests-fast
./ustt make -C sims/verilator CONFIG=SmallBoomV3Config run-bmark-tests-fast

# Run one program (single quotes so $RISCV expands inside the env)
./ustt bash -c 'make -C sims/verilator CONFIG=SmallBoomV3Config run-binary \
       BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add'

# Full VCU118 flow (synthesis through implementation; we use the reports)
./ustt --vivado make -C fpga SUB_PROJECT=vcu118 CONFIG=BoomVCU118Config bitstream

# Build a report PDF
./ustt make -C project-docs/final-report
```

## Machines

| Machine | Who | Use |
| ------- | --- | --- |
| UT ECE Linux servers (RHEL 8, x86) | Everyone | All builds: Chipyard, Verilator, Spike, Vivado 2023.2 (license `2100@harrison.ece.utexas.edu`), TeX Live. Chipyard's setup supports Linux only. |
| GitHub | Everyone | Issues, pull requests and reviews. No CI runs. |

## Repo map (project-relevant parts)

- `generators/boom/src/main/scala/v3/`: SonicBOOM, the core we modify (v4 sits next to it; leave it alone). Key files: `common/micro-op.scala` (uop fields), `common/parameters.scala` and `common/config-mixins.scala` (switches, core sizes), `exu/rename/` (rename stage, busy table: track A), `exu/issue-units/issue-slot.scala` (`io.request`: track B), `exu/rob.scala` (visibility point), `lsu/lsu.scala` (every load-firing path), `exu/core.scala` (branch resolution, `brupdate`). BOOM is included in the tree (not a submodule); update it with `git subtree pull --prefix=generators/boom https://github.com/riscv-boom/riscv-boom.git master`.
- `generators/chipyard/src/main/scala/config/BoomConfigs.scala`: SoC configs; add the STT configs here.
- `fpga/`: Chipyard's Vivado flows. `fpga/src/main/scala/vcu118/` holds `BoomVCU118Config`; `fpga/fpga-shells/xilinx/vcu118/` holds the board files.
- `reference/uSTT/`: original µSTT (read-only submodule). `rtl/src/` has the standalone Verilog Age Matrix (`stt_age.v`, `stt_control.v`) and baseline STT (`stt_og.v`, `younger.v`); `gem5/src/cpu/o3/` has the impede-uop and taint changes (search `[Boru, STT]`).
- `project-docs/PLAN.md`: design background and the mapping of STT onto BOOM.
- `project-docs/midterm-report/`, `project-docs/final-report/`: the reports (LaTeX, ACM `sigplan`). The midterm was imported with history from `vinle0/Project-Report`; the final starts from `ece382n-sec/Project-Report-Template`.
- `ustt`, `ustt-env.sh`, `ustt-env.local.sh.example`: environment setup.

## Conventions

- Logs and run outputs go in `logs/` (git-ignored). Never commit generated Verilog, build outputs or PDFs other than the reports' `paper.pdf`.
- Pull upstream Chipyard with `git fetch upstream --tags` and merge a release tag; do it in its own PR.
