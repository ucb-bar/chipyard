# boom-ustt project plan

Goal: build STT, then the two µSTT mechanisms, into SonicBOOM (BOOMv3). Show
that it blocks Spectre attacks, and measure what it costs in performance and
in hardware (area/timing on Vivado, then on an FPGA).

## 1. Background in one page

**STT (Speculative Taint Tracking).** An *access* instruction (a load) that runs
speculatively produces a secret, so its destination register is *tainted*. Taint
spreads to any instruction that reads a tainted register. A *transmitter* is an
instruction whose timing can leak an operand: a load or store address, or a
branch or `jalr` outcome. A tainted transmitter cannot execute until its taint
is cleared. Taint is cleared once the root load reaches the *visibility point*
(VP), the point where it can no longer be squashed.

- **Spectre threat model:** the VP is the oldest unresolved branch.
- **Futuristic threat model:** the VP is the ROB head.

Each register's taint is stored as its **YRoT** ("youngest root of taint"): the
ROB index of the youngest access load it depends on. An instruction's YRoT is the
youngest YRoT among its sources, and it is safe once its YRoT is older than the VP.

**µSTT** (`reference/uSTT`, ICCD'25) identifies two hardware problems with STT and
fixes each one:

| Problem | µSTT fix | Reference code |
| --- | --- | --- |
| Computing YRoT at rename means comparing ages across the whole decode group, which is slow | **Age Matrix**: a shallow circuit that propagates taint | `reference/uSTT/rtl/src/stt_age.v`, `stt_control.v` (standalone Verilog, timing/area study only; 8-wide, 380 phys regs, 512 ROB). The baseline STT circuit is `stt_og.v` + `younger.v`. |
| Every issue-queue entry needs logic to hold back a tainted transmitter, which costs area | **Impede micro-op**: decode puts a special uop in front of each transmitter. The impede uop waits in the issue queue until the VP passes its YRoT. The transmitter depends on it, so the ordinary wakeup logic delays the transmitter. | gem5 only: `reference/uSTT/gem5/src/cpu/o3/{rename,iew,inst_queue,rob}.cc`, `src/arch/arm/isa/insts/*stt.isa`. Look for `[Boru, STT]` comments. |

The uSTT code has only ever run in gem5 on ARM. Building it into a real RISC-V RTL
core is what makes this project new.

## 2. Where it goes in BOOM v3

All paths below are relative to `generators/boom/src/main/scala/v3/`. Use v3
(SonicBOOM): Chipyard's BOOM FPGA configs are built on v3.

| STT piece | BOOM v3 location | Notes |
| --- | --- | --- |
| YRoT field on each uop | `common/micro-op.scala` | Add `yrot` (ROB index + valid bit). It sits next to the existing `rob_idx`, `br_mask`, `prs1/2`, `pdst`. |
| Per-physical-register taint table | `exu/rename/rename-busytable.scala` (pattern to copy), `exu/rename/rename-stage.scala` | Read for the sources and write for `pdst` at rename. A load's `pdst` gets the load's own `rob_idx`. |
| YRoT computation within a rename group | `exu/rename/rename-stage.scala` | `decodeWidth` is 1 to 5 (Small to Mega). The bypass between instructions in the same group is where the Age Matrix replaces the age comparisons. |
| VP and the "is it safe?" check | `exu/rob.scala`, branch masks | Spectre model: a uop with `br_mask === 0` is not under any unresolved branch. We also need the ROB index of the oldest unresolved branch, plus an age comparison that handles wraparound. |
| Holding back transmitters | `exu/issue-units/issue-slot.scala` (`io.request`, line ~241) | Baseline STT: AND `request` with `yrot_safe` for transmitters. µSTT: replace this with impede uops. |
| Load/store paths outside the issue queue | `lsu/lsu.scala` | BOOM can fire loads from the LDQ without a fresh issue: retries, speculative wakeup, TLB-miss replay. Every one of these paths must respect taint. The prefetcher is also a possible leak. |
| Branches | `exu/execution-units/functional-unit.scala` (`ALUUnit`, jump unit), `exu/core.scala` (`brupdate`) | A tainted branch must not resolve or redirect. The simplest correct approach is to delay its issue. |
| Configs | `common/config-mixins.scala`, `generators/chipyard/src/main/scala/config/BoomConfigs.scala` | Add `enableSTT`, `impedeUop` and `threatModel` parameters, plus configs such as `SmallBoomV3STTConfig`. |

## 3. Workstreams (one owner each, all can start now)

The four tracks mirror uSTT's structure. A and B are the two design halves
(rename side and issue side), each building baseline STT first and the µSTT
mechanism second. C and D are the two evaluation halves (security/performance
and hardware cost).

### A. Taint tracking and the Age Matrix (rename side)

1. Read `rename-stage.scala`, `rename-busytable.scala` and `rob.scala`. Write a
   short interface spec covering the new uop fields and the `isSafe(yrot)` /
   VP signal, and agree on it with B in the first week. *This is the one hard
   dependency between tracks.*
2. Build baseline STT taint propagation: the taint table, YRoT at rename with the
   same-group bypass, loads as taint roots, and the Spectre-model VP.
3. Port the Age Matrix (`stt_age.v` / `stt_control.v`) to Chisel at BOOM's widths,
   behind a config flag. Test it against the baseline YRoT logic; both must
   produce identical YRoTs.

### B. Holding back transmitters and the impede micro-op (issue side)

1. Start right away, without waiting on A, with a **conservative stand-in**: hold
   every load, store and branch while it is under any unresolved branch
   (`br_mask =/= 0`). This is stronger than STT, so C can already confirm that a
   blocked attack stays blocked.
2. Swap in A's `yrot_safe` to get real baseline STT, and audit every load-firing
   path in `lsu.scala`.
3. Design how impede uops fit BOOM. BOOM doesn't crack instructions into multiple
   uops the way gem5-ARM does, so choose between an extra uop at decode/dispatch
   and an extra issue-queue entry reserved at dispatch. Write it up, then build it.

### C. Attacks, functional checks and performance

1. Port `riscv-boom/boom-attacks` (Spectre v1, v2 and RSB) to a Chipyard bare-metal
   test. Add it as a submodule under `reference/` or `tests/`. Show that it
   **leaks on baseline BOOM** in Verilator.
2. Write a regression script: `riscv-tests` (`make run-asm-tests run-bmark-tests`),
   the attack tests, and optionally Spike co-simulation (`MediumBoomV3CosimConfig`).
   Each STT config must pass everything and must not leak.
3. Build a performance harness that reports cycles for baseline, conservative,
   STT and µSTT: CoreMark, Dhrystone, and `bmarks` from riscv-tests, plus anything
   heavier that runs in reasonable simulation time.

### D. Hardware cost and FPGA

**Target: VCU118** (`xcvu9p-flga2104-2L-e`), with `BoomVCU118Config` (MegaBoom).
We have no board, so everything below runs in Vivado 2023.2 on the ECE machines.
Part, board files, `ddr4:2.2` IP and license have all been checked there.

1. Run the baseline flow through implementation:
   `./ustt --vivado make -C fpga SUB_PROJECT=vcu118 CONFIG=BoomVCU118Config bitstream`.
   The bitstream itself is a by-product. What we want are the reports: hierarchical
   utilization (`report_utilization -hierarchical`, so STT logic can be attributed
   to `BoomTile` and its submodules), timing summary/WNS, and vectorless power.
   Keep the hierarchy intact so the core can be separated from the shell and DDR.
   Write a script that collects these into a table.
2. Out-of-context synthesis of just the BOOM tile, for Small/Medium/Mega. This is
   faster than the full flow, so use it to iterate.
3. Synthesize uSTT's standalone RTL (`stt_og` vs `stt_age`) at BOOM-sized
   parameters to reproduce the paper's claims. These are the numbers to compare
   the Chisel versions from A and B against.
4. **Vivado simulation ("emulation")**. Investigate, then pick one:
   (a) Run Chipyard's simulation harness in xsim. It uses DPI-C models for memory
   and the host link; xsim supports DPI-C, but the C++ models need to build under
   `xsc`. (b) Run post-synthesis/post-implementation functional sim of the BOOM
   tile with a small testbench, to check that synthesized STT logic behaves like
   the Chisel. Expect xsim of a whole BOOM SoC to be slow (on the order of kHz),
   so use it for short targeted programs such as the Spectre gadget, not for
   benchmarks. Verilator stays the main tool for functional and performance runs.

## 4. Milestones

| # | Milestone | Owners | Done when |
| --- | --- | --- | --- |
| M0 | Everyone's environment works | all | Each person builds `SmallBoomV3Config` in Verilator and runs one riscv-test via `./ustt` |
| M1 | Baselines established | C, A+B, D | The PoC recovers a secret in simulation, the interface spec is merged, and baseline VCU118 reports exist |
| M2 | The conservative stand-in blocks the attack | B, C | Attack fails, riscv-tests pass, slowdown measured |
| M3 | Baseline STT | A, B, C | Attack fails, tests pass, faster than M2 |
| M4 | µSTT: Age Matrix + impede uop | A, B | Same checks as M3, plus area/timing deltas from D |
| M5 | Hardware evaluation | D | VCU118 utilization/timing/power for each config, and Vivado simulation of the protected design |

## 5. Decisions for the team

- **Threat model:** start with the Spectre model, which is simpler and uses BOOM's
  existing branch masks. Add the Futuristic model (VP = ROB head) later if time
  allows.
- **Main config:** use `SmallBoomV3Config` or `MediumBoomV3Config` for development,
  since Verilator is slow on big cores. uSTT's own RTL assumes an 8-wide core,
  which is bigger than MegaBoom.
- **FPGA target (decided):** VCU118, Vivado-only (reports + xsim), since we have no
  board. Vivado must be 2023.2 on the ECE machines, because the license is capped
  at 2023.10.
- **Workflow:** one branch per task (`a/taint-table`, `c/spectre-v1`, ...), with PRs
  into `dev`.

## 6. Getting started (everyone, for M0)

```bash
git clone https://github.com/Capheuss/boom-ustt.git && cd boom-ustt && git switch dev
cp ustt-env.local.sh.example ustt-env.local.sh   # edit for your machine
./build-setup.sh riscv-tools                     # roughly an hour, one time
./ustt make -C sims/verilator CONFIG=SmallBoomV3Config -j16
./ustt make -C sims/verilator CONFIG=SmallBoomV3Config run-asm-tests-fast   # all ISA tests
# or a single program:
./ustt bash -c 'make -C sims/verilator CONFIG=SmallBoomV3Config run-binary \
       BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add'
```
