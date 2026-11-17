# Spectre PoCs on BOOMv3

Ported from [`riscv-boom/boom-attacks`](https://github.com/riscv-boom/boom-attacks)
to run as bare-metal RISC-V binaries on Chipyard/BOOMv3 in Verilator.

- `v1` — bounds-check bypass (`condBranchMispred.c`)
- `v2` — branch-target injection (`indirBranchMispred.c`)
- `rsb` — return-stack-buffer (`returnStackBuffer.c`); upstream reports it does
  not work, but it does leak on BOOMv3 as built here

All three recover the known secret `!"#ThisIsTheBabyBoomerTest` (26 bytes) on
the unmodified baseline. The secret is placed right after the 160-byte
`array1`, so the out-of-bounds index is 160.

## Build

Everything goes through `./ustt`:

```bash
./ustt make -C tests/spectre
```

## Run

```bash
./tests/spectre/run.sh SmallBoomV3Config v1
./tests/spectre/run.sh MediumBoomV3Config v2
DRAMSIM=1 ./tests/spectre/run.sh MediumBoomV3Config rsb
```

`run.sh` builds the binary (`make -C tests/spectre <attack>.riscv`), runs the
simulator with `+signature`, decodes the 128-byte result struct, and prints one
machine-readable line:

```
SPECTRE_RESULT attack=v1 config=SmallBoomV3Config result=LEAK attempted=26 rounds=3 recovered=26 matches=26 confidence_min=2 cycles=N simcycles=N
```

| result | exit | meaning |
|--------|-----:|---------|
| `NO_LEAK` | 0 | valid full-length run, no byte recovered in at least `MIN_HITS` (2) rounds |
| `LEAK` | 1 | all 26 bytes recovered with confidence |
| `PARTIAL_LEAK` | 1 | some bytes recovered with confidence; never a mitigation pass |
| `INCONCLUSIVE` | 2 | short `ATTACK_BYTES` run with no evidence, or confidence unavailable |
| `ERROR` | 2 | build/sim failure, timeout, no completion line, malformed/short/bad-magic signature |

A byte counts as recovered only if it matches the secret and was the top timing
hit in at least `MIN_HITS` of `rounds` probe rounds. Only a full 26-byte run can
say `NO_LEAK`. `./test-runner.sh` (or `make -C tests/spectre test`) checks this
logic with a mock simulator and synthetic signatures; it does not run RTL.

## Baseline results

Verilator, SimDRAM, secret recovered 26/26 bytes (`mask = 0x03ffffff`):

| config | attack | rdcycle | sim cycles |
|--------|--------|--------:|-----------:|
| SmallBoomV3Config | v1 | 6,307,997 | 13,046,326 |
| SmallBoomV3Config | v2 | 6,381,045 | 13,191,676 |
| SmallBoomV3Config | rsb | 2,478,336 | 5,379,626 |
| MediumBoomV3Config | v1 | 5,581,612 | 11,595,706 |
| MediumBoomV3Config | v2 | 5,618,243 | 11,660,576 |
| MediumBoomV3Config | rsb | 2,422,169 | 5,262,256 |

`rdcycle` covers the whole attack including the initial L1 flush. Each byte is
probed in 3 rounds; `count0` is 2 or 3 for every byte.

Measured from a clean `git archive` export of this branch, built with the
committed `Makefile` (`riscv64-unknown-elf-gcc` 16.1.0) and run with
`./run.sh <config> <attack>` against the `baseline-v0` RTL in
`simulator-chipyard.harness-{Small,Medium}BoomV3Config` (SimDRAM).

## Result struct

Dumped through fesvr `+signature=FILE` at `spectre_result` (128 bytes):

| offset | field | meaning |
|-------:|-------|---------|
| 0 | `magic` | `0x53504543` |
| 4 | `mask` | bit `i` set if `recovered[i] == secret[i]` |
| 8 | `cycles` | `rdcycle()` delta of the whole attack |
| 16 | `recovered[26]` | best guess per byte |
| 42 | `alt[26]` | second-best guess |
| 68 | `count0[26]` | rounds that hit the best guess (max 3) |
| 94 | `count1[26]` | rounds that hit the second-best guess |
| 120 | `attempted` | secret bytes attacked (`ATTACK_BYTES`) |
| 121 | `rounds` | probe rounds per byte (`ROUNDS`) |
| 122 | `pad[6]` | unused |

The struct is flushed from L1D before exit because fesvr reads memory directly
and BOOM's D$ is write-back. Exit is a `tohost` store followed by a flush of
the `tohost` cache set each iteration; newlib's `exit()` leaves `tohost` dirty
and never becomes visible to fesvr.

## Why it works

- **Training beats the predictor.** BOOM's TAGE has a 64-bit global history and
  a loop predictor that locks onto a fixed trip count. So the gadgets train
  70+ times (longer than the history) and the trip count changes every round
  (`TRAIN_TIMES + round % 4`); the attack call is then always mispredicted.
- **Quarter probing.** A 256-line probe array exactly fills the 16 KB L1D, and
  replacement is random. Each round probes only 64 lines (one quarter of the
  byte values) at a time, re-running the gadget before each quarter, so the
  secret's line is the only access to its set and cannot be evicted.
- **Fresh arrays per byte.** `array2` is raw DRAM at `0x80600000`, three 16 KB
  arrays per secret byte. Reusing arrays would leave the previous byte's lines
  hot and produce false hits. A big probe array in `.bss` hangs the SimDRAM
  model, hence fixed DRAM addresses.
- **Flush.** L1D replacement is random (`HellaCache.scala`), so the flush
  touches every set 16 times (`FLUSH_WAYS`) instead of once per way.
- The bound check depends on a chain of four FP divides, so the branch resolves
  long after the predictor has committed to the wrong path. `fence` between the
  probe load and `rdcycle` makes each measurement repeatable (L1 hit 29-45,
  L2/DRAM 60-86, threshold 52).

Known secret: `!"#ThisIsTheBabyBoomerTest`, `SECRET_SZ 26`. `array1[0..15]` are
all zero, so the training path always touches probe line 0, which is skipped.

## Tuning

`CACHE_HIT_THRESHOLD` (52), `TRAIN_TIMES` (70), `ROUNDS` (3) and
`ATTACK_BYTES` (26; shorter runs while tuning) can be overridden at build
time:

```bash
./ustt make -C tests/spectre EXTRA_CFLAGS='-DATTACK_BYTES=1 -DROUNDS=3'
```

`DRAMSIM=1 ./run.sh ...` adds `+dramsim`; it does not change the attack, only
the memory latencies and the simulation time.

## Build provenance

`Makefile`: `riscv64-unknown-elf-gcc` from the Chipyard toolchain (16.1.0 here),
`-march=rv64imafd -mabi=lp64d -mcmodel=medany -std=gnu99 -O2
-specs=htif_nano.specs`, default htif linker script and startup. Override with
`CFLAGS`/`EXTRA_CFLAGS`/`CROSS`. Record the commit and `gcc --version` with any
measurement. The baseline table above was measured before the Makefile was
committed, at `4ff7bf04` with the same flags; re-run before citing it as final.
A run with `ATTACK_BYTES` < 26 can only be `PARTIAL_LEAK` or `INCONCLUSIVE`:
useful for xsim bring-up, never a full security pass.

## FPGA/xsim handoff (track D, #12/#15)

Not ported here; this documents what the VCU118 harness must provide.

- **Layout.** Program, stack and `spectre_result` come from the default htif
  linker script (DRAM at `0x80000000`). Reserved, uninitialized scratch:
  `FLUSH_BASE = 0x80400000`, `FLUSH_WAYS * 4096` = 64 KB (16 ways x 4 KB; set s
  at `+ s*64 + w*4096`), and `ARRAY2_BASE = 0x80600000`, `ATTACK_BYTES * ROUNDS
  * 16 KB` (26 x 3 x 16 KB = 1.19 MB by default). Nothing needs initializing;
  they must be plain cacheable DRAM that the program/stack do not overlap.
  `FLUSH_WAYS`, `L1_SETS`, `L1_BLOCK` in `spectre.h` encode the L1D geometry
  (64 sets x 4 ways x 64 B = 16 KB, random replacement).
- **Result transport.** The binary stores 1 to `tohost` (after flushing its
  cache set) and exits; fesvr then dumps `begin_signature..end_signature`
  (`spectre_result`, 128 B) via `+signature`. The VCU118 wrapper (sdboot, DDR)
  has no fesvr, so track D must load the ELF into DDR, watch `tohost`, and read
  the 128 B at `spectre_result` (symbol from the ELF) after the D$ flush the
  binary already performs.
- **Timing.** `CACHE_HIT_THRESHOLD = 52` cycles was measured under Verilator
  with SimDRAM (L1 hit 29-45, L2/DRAM 60-86). DRAMsim runs only change memory
  latency and are not validation of VCU118 DDR. Recalibrate the threshold and
  re-show baseline leakage in the #15 harness before judging any protected
  config.
- **Scope.** All results here are tile-level Verilator runs of BOOM with
  SimDRAM; the SoC peripherals, DDR controller and boot flow of the VCU118
  system are not exercised.
