# Final project plan: µSTT in SonicBOOM

ECE 382N Hardware Security, Fall 2026. Deadlines: technical work complete **Nov 30**, presentation ready **Dec 1**, final report due **Dec 11**. Status: draft for team review, written 2026-11-14 from the midterm report.

This plan covers what we build, in what order, who owns it, and how we check it. Rules for working in the repository are in [AGENTS.md](../AGENTS.md). Tasks, status and results live in the GitHub issues, tracked in #17. Paths are relative to the repository root. Every number is tagged:

- **[M]** measured by us (source given).
- **[E]** estimate or inference. Treat it as a hypothesis until a task replaces it with a measurement.

Sources: `project-docs/midterm-report/` (all sections), issues #1 to #17, #39 and #40, the BOOM v3 source in `generators/boom/src/main/scala/v3/`, and `reference/uSTT`.

---

## 1. Summary

1. **Goal.** Build STT, then the two µSTT mechanisms (Age Matrix, impede micro-op), into the RTL of SonicBOOM (BOOMv3). µSTT has only run in gem5 on ARM and as a standalone rename circuit, and neither evaluation ran an attack.
2. **Four configurations**, each behind switches that default to off: Baseline, Conservative, STT, µSTT (section 4).
3. **Three axes for every configuration:** security (Spectre PoCs), performance (cycles and IPC in Verilator), hardware cost (Vivado 2023.2 on the VCU118 part). A result counts only when all three are reported.
4. **Four tracks, one owner each:** A rename side (Christopher), B issue side (Samir), C security, verification and performance (Vincent), D hardware cost (Kuei Chen).
5. **Order:** baselines, then the conservative stand-in, then baseline STT, then µSTT. Security is checked before anything is timed.
6. **Cut line.** If integration runs late, a correct baseline STT with a full evaluation comes before the µSTT mechanisms. The report then says which mechanisms are incomplete.

What shapes the schedule:

- **A and B meet at one interface** (#3). It is the only hard dependency between tracks, and nothing in M3 can start without it. It is due first.
- **Sixteen days remain for all technical work, and they include Thanksgiving** (Nov 26). Everything through M5 is due Nov 30, the day before the presentation has to be ready.
- **Two of four environments are not confirmed** (#1). Tracks B and D cannot start their own issues until theirs are.

---

## 2. Facts established

### 2.1 Environment and baseline

| Item | Value |
| --- | --- |
| Repository | Fork of Chipyard 1.15.0, BOOM `ba03175` in the tree. Tag `baseline-v0` is the frozen baseline |
| Toolchain | `./ustt` wrapper: conda + Chipyard, Verilator, Spike; `./ustt --vivado` adds Vivado 2023.2 |
| `SmallBoomV3Config` Verilator build | 5 min wall time with `-j32` on koopa [M, #1 and `logs/m0-smallboom.log`] |
| `rv64ui-p-add` on it | Passed after 101,106 simulation cycles [M, #1] |
| Vivado | v2023.2, license from `2100@harrison.ece.utexas.edu`; synthesis for `xcvu9p` is licensed [M, #1]. The department license is capped at 2023.10 |
| M0 status | Done: Capheuss, vinle0. Not confirmed: erickan2002-del, SamirJoshiGit [#1, 2026-11-14] |
| CI | Only the report build runs on GitHub. All design builds and tests are local |

### 2.2 Core sizes (from `common/config-mixins.scala`)

| Config | Decode width | ROB | Int phys regs | Issue entries (mem/int/fp) | LDQ/STQ | Max branches |
| --- | --- | --- | --- | --- | --- | --- |
| Small | 1 | 32 | 52 | 8 / 8 / 8 | 8 / 8 | 8 |
| Medium | 2 | 64 | 80 | 12 / 20 / 16 | 16 / 16 | 12 |
| Large | 3 | 96 | 100 | 16 / 32 / 24 | 24 / 24 | 16 |
| Mega | 4 | 128 | 128 | 24 / 40 / 32 | 32 / 32 | 20 |

Consequences:

- **Small has a decode width of 1, so there is no same-cycle bypass to test on it.** The taint bypass and the Age Matrix first matter on Medium. Small is for bring-up and fast regression; Medium is where A's logic is exercised.
- uSTT's standalone RTL assumes an 8-wide core with 380 physical registers and a 512-entry ROB, well above Mega. #14 has to re-parameterize it before its numbers can be compared with ours.
- `BoomVCU118Config` is `MegaBoomV3Config` at 50 MHz (`fpga/src/main/scala/vcu118/Configs.scala`).

### 2.3 What BOOM already has

| Existing piece | Where | Use for us |
| --- | --- | --- |
| `br_mask` on every uop | `common/micro-op.scala` | Non-zero means the uop is under an unresolved branch. The conservative stand-in needs nothing else |
| `unsafe` (loads, stores, branches, `jalr`) | `common/micro-op.scala:152` | The same set of instructions as STT's transmitters. A starting point for `is_transmitter` [E: confirm in #3] |
| Point of no return (`rob_pnr_idx`) | `exu/rob.scala` | The ROB already tracks the oldest instruction that can still cause a squash. A possible basis for the VP [E: confirm in #3; it also counts exceptions, which the Spectre model does not need] |
| Issue request | `exu/issue-units/issue-slot.scala:241` (`io.request`) | The one place where issue can be held back |
| Busy table | `exu/rename/rename-busytable.scala` | The pattern to copy for the taint table |
| Spike co-simulation | `MediumBoomV3CosimConfig` | Lockstep check of every committed instruction |

---

## 3. Background

**STT (Speculative Taint Tracking).** An *access* instruction (a load) that runs speculatively produces a secret, so its destination register is *tainted*. Taint spreads to any instruction that reads a tainted register. A *transmitter* is an instruction whose timing can leak an operand: a load or store through its address, a branch or `jalr` through its outcome. A tainted transmitter cannot execute until its taint is cleared. Taint is cleared once the root load reaches the *visibility point* (VP), where it can no longer be squashed.

- **Spectre model (ours):** the VP is the oldest unresolved branch.
- **Futuristic model (stretch goal):** the VP is the ROB head.

Each register's taint is stored as its **YRoT** (youngest root of taint): the ROB index of the youngest access load it depends on. An instruction's YRoT is the youngest among its sources, and it is safe once its YRoT is older than the VP.

**µSTT** (`reference/uSTT`, ICCD'25) fixes two hardware costs of STT:

| Cost | µSTT fix | Reference code |
| --- | --- | --- |
| Computing YRoT at rename compares ages across the whole decode group, which is slow | **Age Matrix**: a shallow circuit that propagates taint | `reference/uSTT/rtl/src/stt_age.v`, `stt_control.v`. Baseline circuit: `stt_og.v`, `younger.v`. Test generators in `rtl/test/` |
| Every issue-queue entry needs logic to hold back a tainted transmitter, which costs area | **Impede micro-op**: a special uop in front of each transmitter waits until the VP passes its YRoT. The transmitter depends on it, so ordinary wakeup delays the transmitter | gem5 only: `reference/uSTT/gem5/src/cpu/o3/{rename,iew,inst_queue,rob}.cc`, `src/arch/arm/isa/insts/*stt.isa`. Search `[Boru, STT]` |

Threat model, in short (midterm report section 3): attacker and victim share an address space, bare-metal; the attacker trains predictors, the victim holds a Spectre gadget, and the secret is recovered by timing a probe array in the L1 data cache with `rdcycle`. Out of scope: data read non-speculatively, speculative store bypass, exception-based attacks, physical attacks.

---

## 4. What we build

### 4.1 Configurations

| Configuration | Taint (rename) | Delay (issue and LSU) | Switches | Issues |
| --- | --- | --- | --- | --- |
| Baseline | none | none | all off; Verilog identical to `baseline-v0` | |
| Conservative | none | every transmitter waits while `br_mask =/= 0` | conservative flag | #6 |
| STT | taint table + same-cycle bypass | `isSafe(yrot)` at issue, and on every LSU path | `enableSTT` | #4, #7 |
| µSTT | Age Matrix | impede micro-op | `enableSTT`, Age Matrix flag, `impedeUop` | #5, #8 |

Each exists as a Chipyard config at two sizes: Small for development, Medium for reported results. Switch and config names are fixed in #3.

### 4.2 Where it goes in BOOM v3

Paths are relative to `generators/boom/src/main/scala/v3/`.

| STT piece | Location | Notes |
| --- | --- | --- |
| YRoT and transmitter flag on each uop | `common/micro-op.scala` | `yrot` (ROB index + valid bit), next to `rob_idx`, `br_mask`, `prs1/2`, `pdst` |
| Per-physical-register taint table | `exu/rename/rename-stage.scala`, modeled on `rename-busytable.scala` | Read for the sources, write for `pdst`. A load's `pdst` gets the load's own `rob_idx`. Restored on mispredict and exception, as the rename table is |
| YRoT within a rename group | `exu/rename/rename-stage.scala` | The bypass between instructions in the same group is where the Age Matrix replaces the age comparisons |
| VP and `isSafe(yrot)` | `exu/rob.scala`, branch masks | ROB index of the oldest unresolved branch, plus an age comparison that handles wraparound |
| Holding back transmitters | `exu/issue-units/issue-slot.scala` | STT: AND `request` with `yrot_safe` for transmitters. µSTT: impede uops replace this |
| Load and store paths outside the issue queue | `lsu/lsu.scala` | Retries, speculative wakeup, TLB-miss replay, store-to-load forwarding, the prefetcher. Every one must respect taint |
| Branches | `exu/execution-units/functional-unit.scala`, `exu/core.scala` (`brupdate`) | A tainted branch must not resolve or redirect. Delaying its issue is the simplest correct approach |
| Switches and configs | `common/parameters.scala`, `common/config-mixins.scala`, `generators/chipyard/src/main/scala/config/BoomConfigs.scala` | `enableSTT`, `impedeUop`, `threatModel`, all default off |

### 4.3 Design questions still to answer

| Question | Decided in | Options |
| --- | --- | --- |
| How does a cleared taint wake a waiting transmitter? | #3 | Broadcast when the VP moves, or re-check `isSafe` every cycle |
| Where does the VP come from? | #3 | New logic over branch masks, or BOOM's existing point of no return (section 2.3) |
| How does an impede uop fit a core that does not crack instructions? | #8 | An extra uop at dispatch, or an issue-queue entry reserved at dispatch |
| How do LSU paths that skip the issue queue respect taint under µSTT? | #7, #8 | A transmitter that is replayed or retried in the LSU skips its impede uop, so the LSU needs its own check |

---

## 5. Tracks, owners and tasks

Owners are from the midterm report (section 6), which calls them provisional. Integration, review, the presentation and the reports are shared by everyone.

| Track | Owner | GitHub | Issues |
| --- | --- | --- | --- |
| A. Rename side: taint tracking, Age Matrix | Christopher Rodolph | @Capheuss | #3 (with B), #4, #5 |
| B. Issue side: transmitter delay, impede uop | Samir Joshi | @SamirJoshiGit | #3 (with A), #6, #7, #8 |
| C. Security, verification, performance | Vincent Le | @vinle0 | #9, #10, #11 |
| D. Hardware cost, VCU118, Vivado | Kuei Chen Kan | @erickan2002-del | #12, #13, #14, #15, #16 |
| Everyone | | | #1 (environment), #2 (decisions), #39 (presentation), #40 (final report) |
| Tracker | Christopher Rodolph | @Capheuss | #17 |

Each issue carries a *Done when* checklist and its blockers; the issue is the authoritative copy, and this table is the overview. A box is ticked only with evidence on the issue (AGENTS.md section 3). Blockers are also recorded as GitHub issue dependencies.

| # | Task | Done when | Blocked by |
| --- | --- | --- | --- |
| 1 | Environment (everyone) | Each person builds `SmallBoomV3Config`, passes `run-asm-tests-fast`, sees Vivado v2023.2, and comments with machine and commit | |
| 2 | Decisions | No open objection; the choices in section 9 are recorded on the issue | |
| 3 | A1 Interface spec | `project-docs/spec-interface.md` merged with B's review: uop fields, VP signal, `isSafe` with wraparound, how taint clears, switch and config names | |
| 4 | A2 Taint propagation | ChiselTest cases pass (load as root, same-cycle bypass, restore after mispredict and exception); riscv-tests pass on Small and Medium with `enableSTT`; switches off gives identical Verilog | #3 |
| 5 | A3 Age Matrix | Produces the same YRoTs as #4's bypass on random rename groups (uSTT's generator); riscv-tests pass; behind its own flag | #4 |
| 6 | B1 Conservative stand-in | riscv-tests and benchmarks pass with no timeout on Small and Medium; every attack in #9 fails; switches off gives identical Verilog | #9 for the attack check |
| 7 | B2 STT delay and LSU audit | Written list of every load-firing path in `lsu.scala` and how each respects taint; the assertion of section 6 is in and never fires; attacks fail; riscv-tests pass | #3, #4, #6 |
| 8 | B3 Impede micro-op | Design note merged first; then the same checks as #7 with the per-entry delay logic removed | #7 |
| 9 | C1 Spectre PoCs | v1, v2 and RSB each recover a known secret on baseline Small and Medium, with a PASS/LEAK line a script can read; cycles per attack recorded | |
| 10 | C2 Regression script | One command runs riscv-tests, the attacks, co-simulation and the switches-off Verilog diff for a config, treats a timeout as a failure, and prints a summary | #9 |
| 11 | C3 Performance harness | Cycles and IPC for CoreMark, Dhrystone and the riscv-tests benchmarks, normalized to baseline, written to a CSV with config and commit | |
| 12 | D1 VCU118 baseline | Full flow completes on `BoomVCU118Config`; hierarchical utilization, WNS/TNS and vectorless power collected by a script into a CSV; run time and peak memory recorded | |
| 13 | D2 Out-of-context synthesis | Tile-only synthesis for Small, Medium and Mega, reported by the same script | #12 for the script |
| 14 | D3 uSTT RTL reproduction | `stt_og` and `stt_age` synthesized at uSTT's parameters and at BOOM's; uSTT's testbenches pass in xsim; area and delay compared with the paper | |
| 15 | D4 Vivado simulation | Approach chosen and written up; the Spectre gadget runs in xsim on the synthesized design, on baseline and one protected config | #9, #12 |
| 16 | D5 Cost comparison | One table: LUT, FF, BRAM, DSP, WNS and power for all four configs, same part, clock and flow, with deltas attributed to rename, issue, ROB and LSU | #5, #6, #7, #8, #12, #13 |
| 39 | Presentation (everyone) | Slides ready and rehearsed by Dec 1; every number already on its issue; measured and planned work kept apart | #10, #11, #16 |
| 40 | Final report (everyone) | Midterm sections carried into `project-docs/final-report/`, evaluation added, builds in CI, submitted by Dec 11 | #39, #10, #11, #15, #16 |

---

## 6. Verification

STT only delays instructions. It never changes what they compute. So a protected core must produce exactly the baseline's architectural results and differ only in timing.

| Level | What runs | Pass criterion | Issue |
| --- | --- | --- | --- |
| Module | ChiselTest on the taint table: load takes its own ROB index, same-cycle bypass, restore after mispredict and exception | Zero failures | #4 |
| Equivalence | Age Matrix against the baseline bypass on random rename groups, using uSTT's generator | Identical YRoTs | #5 |
| Switches off | Diff of `generated-src` against `baseline-v0` | Identical Verilog | #10 |
| Functional | riscv-tests ISA and benchmarks in Verilator | All pass; a timeout is a failure (it catches a transmitter waiting on a taint that never clears) | #10 |
| Co-simulation | Spike lockstep (`MediumBoomV3CosimConfig` and its STT variants) | No divergence | #10 |
| Assertion | At every point a transmitter can fire, including LSU retry, replay and forwarding: stop simulation if its YRoT is not yet safe | Never fires on any test | #7 |
| Security | Spectre v1, v2, RSB | Leak on baseline; nothing beyond chance on every protected config | #9 |
| Synthesized design | uSTT's testbenches, then the Spectre gadget, in xsim | Matches the Verilator result | #14, #15 |

The conservative stand-in is strictly stronger than STT. An attack that succeeds against it points to a path that bypasses the issue queue, not to a flaw in taint tracking.

These are tests, not proofs. They show the known variants are blocked and the assertion never fires on our workloads. Formal analysis is out of scope.

Reporting rule: module tests, riscv-tests, co-simulation, attack runs, Verilator cycle counts, synthesis, implementation and xsim are separate checks. Every result says which ones ran.

---

## 7. Evaluation

| Axis | Measurement | Tool | Reported as |
| --- | --- | --- | --- |
| Security | Secret recovered or not, per attack | Verilator | Leak / no leak, per config |
| Performance | Cycles and IPC: CoreMark, Dhrystone, riscv-tests benchmarks | Verilator (never Spike) | Normalized to baseline, on Medium |
| Hardware | LUT, FF, BRAM, DSP, WNS, vectorless power, hierarchical | Vivado 2023.2, `xcvu9p-flga2104-2L-e` | Absolute and delta against baseline, same clock and flow |

Every number carries its config and commit and is posted on its issue. A mitigation that breaks timing is reported as such.

What we expect to see (midterm report, conclusion), all [E] until measured:

- Each PoC recovers the secret on baseline and fails on every protected config.
- Performance cost of STT and µSTT falls between baseline and the conservative stand-in, with µSTT slightly above STT, as in gem5.
- The Age Matrix shortens the rename path and the impede uop reduces issue-queue area, in a full core and not only in the standalone circuit.

---

## 8. Schedule and risks

### 8.1 Deadlines and order

Three dates are deadlines. Everything else is an order, not a date.

| Date | Deadline | Tracked by |
| --- | --- | --- |
| Mon Nov 30 | **All technical work complete**: M0 to M5, every configuration built, verified and measured on all three axes | Milestone M5; issues #1 to #16 |
| Tue Dec 1 | **Presentation ready**: slides and rehearsal. The sessions are Dec 2 and Dec 7; our slot is not confirmed, so we prepare for the first | #39 |
| Fri Dec 11 | **Final report due** | #40 |

Milestones M0 to M4 have no date. They are reached in this order, and each one gates the next:

| Milestone | Reached when | Issues |
| --- | --- | --- |
| M0: environments work | All four confirmed on #1 | #1 |
| M1: baselines | PoCs leak on baseline; interface spec merged; baseline VCU118 reports and uSTT RTL numbers exist; decisions closed | #2, #3, #9, #12, #13, #14 |
| M2: conservative stand-in | Attacks fail, riscv-tests pass, slowdown measured; regression script and performance harness in use | #6, #10, #11 |
| M3: baseline STT | Taint propagation and `isSafe` delay merged, LSU audit written; STT passes the whole ladder of section 6 and is measured on all three axes | #4, #7 |
| M4: µSTT mechanisms | Age Matrix and impede uop checked as in M3 | #5, #8 |
| M5: hardware evaluation (**Nov 30**) | Hardware comparison across all configs; Vivado simulation | #15, #16 |

Sixteen days cover six milestones, so the tracks have to run in parallel from today:

| Track | First | Then | Last, by Nov 30 |
| --- | --- | --- | --- |
| A | Interface spec (#3) | Taint table, bypass, VP (#4) | Age Matrix (#5) |
| B | Environment; spec review; conservative stand-in (#6) | `isSafe` delay and LSU audit (#7) | Impede uop: design note, then build (#8) |
| C | PoCs leak on baseline (#9) | Regression script (#10), harness (#11), conservative results | STT and µSTT results |
| D | Environment; baseline flow (#12), uSTT RTL (#14) | Out-of-context synthesis (#13); each protected config as it lands | All configs (#16), xsim (#15) |

Dec 2 to Dec 10 is for the report (#40) and presentation feedback, not for new design work.

### 8.2 Risks

| # | Risk | Why it matters | Mitigation and early test |
| --- | --- | --- | --- |
| 1 | The interface spec (#3) slips | It blocks #4 and #7, and through them #5, #8 and #16 | First task on tracks A and B, before any STT code. Keep it short; B reviews within a day |
| 2 | An LSU path fires a tainted load | The defense is silently incomplete, and the PoCs may not exercise that path | The assertion at every firing point (section 6), and the conservative stand-in as a second reference |
| 3 | Deadlock: a transmitter waits on a taint that never clears, or the oldest branch cannot resolve | Tests hang | Timeout counts as failure. In #6, check the oldest branch can always issue |
| 4 | The PoCs do not leak on our baseline | No security result at all | #9 is the first task on track C. `boom-attacks` was written for BOOM; check its config assumptions against Small and Medium |
| 5 | The impede uop does not fit BOOM cleanly | BOOM does not crack instructions into uops as gem5-ARM does | Design note before code (#8). Cut line: report STT plus the Age Matrix, and the impede uop as designed but not built |
| 6 | The full Mega flow on the VCU118 is slow | Each config costs hours [E]; four configs plus reruns may not fit | Measure run time once in #12. Iterate with tile-only synthesis (#13); run the full flow once per config |
| 7 | Hardware and performance are reported on different core sizes | Performance on Medium, FPGA flow on Mega: the costs do not describe the same core | Decide in #2 (section 10, question 2) before #16 |
| 8 | Two environments are not set up | B and D cannot start | #1 first. Known `build-setup.sh` workaround is in #1's comment |
| 9 | Sixteen days for M0 to M5, with Thanksgiving inside | #16 sits at the end of the longest chain (#3, #4, #7, #8), so any slip lands on the Nov 30 deadline | Tracks run in parallel; D measures each config as it lands instead of waiting for all four. The cut line (section 1) decides what is dropped: µSTT mechanisms before baseline STT |
| 10 | Verilator time on larger cores | Regression and benchmarks on Medium for four configs | Small for the edit loop, Medium for results. Record the run time of the regression in #10 |

---

## 9. Decisions

| Decision | Choice | Main tradeoff |
| --- | --- | --- |
| Threat model | Spectre (VP is the oldest unresolved branch). Futuristic is a stretch goal | Does not cover speculative store bypass or exceptions |
| Core | BOOM v3 only | v4 is left alone |
| Development and results configs | Small to develop, Medium to report | Small has no same-cycle rename group (section 2.2) |
| FPGA target | VCU118 (`xcvu9p-flga2104-2L-e`), Vivado 2023.2, reports and xsim only | No board, so no on-hardware run |
| Order | Conservative stand-in before STT | A week spent on a mechanism that is not the final design; it buys early security testing and an upper bound on cost |
| Switches | Every mechanism behind a parameter, default off, identical Verilog when off | A little more configuration code |
| Cycles | RTL simulation only | Slower than Spike |
| Cut line | Correct baseline STT and its evaluation before µSTT | µSTT may be reported as partial |
| Workflow | One branch per issue (`<issue>-<short-name>`), PRs into `dev` | |

---

## 10. Open questions

None blocks the work that starts now. Each has an issue that settles it.

1. **Presentation slot:** Dec 2 or Dec 7. Until it is confirmed, we are ready on Dec 1 (#39).
2. **Which core size does the hardware comparison use?** `BoomVCU118Config` is Mega, results are on Medium. Options: add a Medium VCU118 config for the full flow, or report tile-only synthesis on Medium and the full flow on Mega for baseline only. Decide on #2 before #16.
3. **Owners are provisional** in the midterm report. Change the assignee on the issue and the table in section 5 if the team moves a task.
4. **How much of uSTT's published evaluation can we compare against?** Its gem5 numbers are ARM and 8-wide. #14 says which claims we can test at BOOM's sizes.
5. **Benchmarks heavier than CoreMark:** only if they finish in reasonable Verilator time. #11 records what was tried.

---

## 11. Getting started (M0)

```bash
git clone https://github.com/Capheuss/boom-ustt.git && cd boom-ustt && git switch dev
cp ustt-env.local.sh.example ustt-env.local.sh   # edit for your machine
./build-setup.sh riscv-tools                     # roughly an hour, one time
./ustt make -C sims/verilator CONFIG=SmallBoomV3Config -j16
./ustt make -C sims/verilator CONFIG=SmallBoomV3Config run-asm-tests-fast   # all ISA tests
./ustt --vivado vivado -version                  # should print Vivado v2023.2
```

Then comment on #1 with your machine and the commit.
