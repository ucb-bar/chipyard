# Kodiak OPU array activity counters

This is a separate instrumented build of the one-core Kodiak OPU design. The
certified `alveo_u250_firesim_kodiak_opu_1core` bitstream and its hardware
database entry remain untouched. This patch is tied to Saturn `ea373800169ce7bbb9ca27ececf74101a7caf9d5`;
the OPU source file at that pin has SHA-256
`7d782ff91e99653ff4c774edf2862bb02b65aa75f8c259454462404da4130a95` and is byte-identical
to the later `e16a301` file. FireSim targetutils API signatures were checked
against the pinned FireSim commit `b084672c2f8cf32e55d78f73a001074c23f8a2b8`.
This commit stages source and build inputs; no metasim or FPGA result exists yet.

## Source and build

Start from a **managed** Chipyard task at the Kodiak package's documented
Chipyard/Saturn/FireSim pins. Apply that package's
`firesim/01-saturn-OPUConfigs.patch` and
`firesim/02-chipyard-firechip-TargetConfigs.patch`, then apply
`01-saturn-autocounter.patch` at the Saturn root. Merge the distinct entry in
`02-firesim-counter-recipe.yaml` into FireSim's `config_build_recipes.yaml`.
The new `PLATFORM_CONFIG` enables AutoCounter in Golden Gate; the existing
base-platform recipe does not. Set `MERLIN_EICG_PASSTHROUGH_V` in the remote
elaboration environment as the Kodiak package's FireSim guide requires.

The RTL patch adds two target-clock counters to each `OuterProductUnit`:

| Counter | Value accumulated each core cycle |
| --- | --- |
| `opu_active_row_cycles` | `PopCount(io.op.macc)` |
| `opu_macc_gate_low_cycles` | one if MAC control is asserted while `clock_enable` is low |

Each enabled row drives every cell across its eight clusters. At dLen 256,
each row has 128 int8 MAC cells, and there are eight rows. The counters use
`io.op.macc` because it directly enables the cells; `clock_enable` is checked
separately, since this FireSim target replaces the physical cluster clock gate
with a passthrough. The pinned AutoCounter bridge **accumulates counters on
every target cycle, but emits a sample only while the global trigger is on**.
The global trigger controls observation, not the accumulation window. Do not
label a trigger-gated CSV interval as a compute ROI without checking plateaus.

## Acceptance sequence

1. Add distinct software start/end instruction markers **outside** the timed
   compute region that emits `METRIC cycles`. Configure TracerV's instruction
   trigger (`selector: 3`) to start before a pre-compute idle plateau and end
   after a post-compute idle plateau. The target must stay alive, with no OPU
   operation, for at least **two AutoCounter read intervals on each plateau**.
   An end marker immediately after compute loses the final sample because the
   bridge requires both `doSample` and `trigger` to emit one. Confirm exactly
   one start/end marker pair in the exact image and inspect the metasim
   generated bridge to confirm TracerV is the trigger source. Golden Gate
   defaults its trigger to enabled if no source is wired, so CSV rows alone
   do not prove that the requested markers controlled sampling.
2. In metasim, enable `autocounter.read_rate` (for example 100000 base target
   cycles) and TracerV tracing. Run a known 64³ OPU GEMM and its plain-RVV
   control. In `AUTOCOUNTERFILE*.csv`, select the unique one-core OPU instance
   columns by path. Require 64-bit `Accumulate` metadata, monotonic cumulative
   row and gate-low values without wrap, at least two flat row-count samples
   before compute, and at least two flat samples after compute while the
   trigger remains active. Subtract a pre-compute plateau value from a
   post-compute plateau value. Use CSV `local_cycle` to order samples: the CSV
   base-clock column advances per *emitted sample*, so trigger gaps make it
   unsuitable as an absolute cycle coordinate. Require positive OPU row delta,
   zero RVV-control row delta, and zero gate-low delta. Reject missing samples
   or non-flat plateaus; do not estimate the last value from `finish()` because
   it only drains samples already enqueued.
3. Synthesize this recipe under a **new** hardware database key, verify timing
   and resource closure, then run the exact Spike-gated Spectformer and
   TinyLlama images on that new bitstream. Compare full hashes and target
   cycles with the uninstrumented bitstream before using the new counters.

For one OPU instance over one inference, let `R` be the difference between
the post-compute and pre-compute **cumulative row counter plateaus**, `C` the
exact target `METRIC cycles` around compute, and `M` the exact valid routed
MACs from the image's SHA-checked routing sidecar. The exact ELF must invoke
the OPU only inside that compute window; verify this from the program path and
the stable plateaus. Then:

- Issued physical MAC slots: `128 × R`.
- MAC issue duty: `R / (8 × C)`.
- Useful slot efficiency: `M / (128 × R)`.
- Useful throughput versus physical peak: `M / (1024 × C)`.

The row counter measures **issued MAC controls**, not transistor switching or
power. Padded work consumes issued slots but is excluded from `M`. Reject the
receipt if `C` or `R` is zero, the gate-low delta is nonzero, useful slot
efficiency exceeds one, or the exact image/bitstream identities differ. The
receipt must pin the CSV, ELF, bitstream, and routing-sidecar SHA-256 digests.
For a multi-core design, sum issued slots across OPU instances and use the
matching per-core compute windows; do not silently sum core-cycle denominators.
