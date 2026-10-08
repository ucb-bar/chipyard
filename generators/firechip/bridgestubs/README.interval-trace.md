# Interval trace bridge

The interval trace bridge observes one tile's retirement trace and PMU event
increments in FireSim. Its FPGA-side widget splits retirement into basic blocks
(BBs), closes intervals of a fixed number of retired instructions, and streams BB
and PMU records to the host. The host driver aggregates them into interval JSONL:
for each interval, the instructions, cycles and executions of every block, and the
PMU counter deltas.

## Attachment

[`TargetConfigs.scala`](../chip/src/main/scala/TargetConfigs.scala) provides:

- `firechip.chip.FireSimLargeBoomV3IntervalTraceConfig`
- `firechip.chip.FireSimMegaBoomV3IntervalTraceConfig`

[`WithBoomIntervalTrace`](../chip/src/main/scala/BridgeBinders.scala) adds interval
tracing to a BOOM v3 config. It composes:

- `boom.v3.common.WithPMUEvents`: each tile registers its PMU events.
- `chipyard.config.WithTraceIO`: each tile's retirement trace.
- `chipyard.iobinders.WithPMUTraceIO(retirementLatency = 1)`: exports each tile's
  PMU event increments alongside its trace port.
- `WithIntervalTraceBridge`: one interval trace bridge per traced tile.

The configs above use `WithDefaultMMIOOnlyFireSimBridges`, which has no TracerV
bridge: TracerV attaches to the same trace port. Harness binders use first-match
selection, so put `WithBoomIntervalTrace` before any other `TracePort` binder.

Any tile whose module implements the PMU library's `CanHavePMUEvents` and that has
a trace port can use `WithPMUTraceIO` and `WithIntervalTraceBridge` directly.
`retirementLatency` is the number of cycles by which the tile's trace retirement
lags its PMU events, either 0 or 1. BOOM v3 reports retirement one cycle after its
events. Composing BOOM's `WithTilePMU` adds a memory-mapped tile PMU; its controls
do not affect tracing.

The bridge carries each tile's event list (ID, name, unit and description). Its
increment ports are unsized: FIRRTL width inference sizes them from the tile's
increments, and Golden Gate builds each channel from those widths.

The widget requires an RV64 trace (64-bit trap cause). Cloned BOOM tiles and BOOM
v4 are not supported.

## Accounting

[`IntervalTraceDatapath`](../goldengateimplementations/src/main/scala/interval/IntervalTraceDatapath.scala)
processes one aligned target cycle at a time:

- A slot's `valid` is architectural retirement. Lane order and holes are kept.
  The original instruction bits identify RV64 conditional branches (taken or not),
  JAL/JALR, their compressed forms, and trap returns; each ends a BB.
- A PC that does not follow the previous instruction, or a privilege change, ends
  the open BB early as a partial block. A trap flag in a cycle ends the open BB
  after that cycle's retirements, and the next BB starts partial. PCs are
  sign-extended to 64 bits; counts come from valid slots, never PC subtraction.
- Time and the modulo-2^64 PMU counters advance only on accepted, non-reset target
  cycles within a capture epoch. Cycles without retirement still count. A PMU
  snapshot includes the whole cycle's increments.
- A BB's elapsed cycles run from the preceding BB boundary through its own. When
  several BBs end in one cycle, the first receives the elapsed time and the
  others zero.
- An interval closes at the first completed BB at or past the nominal length.
  The widget subtracts the nominal length once per closed interval, so a long
  block's overshoot carries into the next interval and the grid does not drift.
  Partial blocks never close an interval; their instructions still count toward it.

The widget delays PMU increments and reset by `retirementLatency` accepted target
cycles, so each retirement pairs with the increments of its own cycle. The first
target cycle only seeds that delay. At reset assertion, the last pre-reset
retirement pairs with its pre-reset increments; the following cycle carries reset
and is excluded.

The trace has no address-space identity, so identical PCs in different address
spaces are the same block.

## Capture control

The driver reads these options:

| Option | Meaning |
| --- | --- |
| `+interval-trace-file=PREFIX` | Output prefix, before `-<bridge index>`. Default `intervaltrace`. |
| `+interval-trace-interval=N` | Nominal interval length in retired instructions. Default 1,000,000. |
| `+interval-trace-select=0\|1\|2\|3` | Trigger mode: always on, cycle window, PC endpoints, or instruction endpoints. Default 0. |
| `+interval-trace-start=V`, `+interval-trace-end=V` | Trigger endpoints, interpreted by mode (below). |
| `+interval-trace-trigger` | Shorthand for mode 3 with the FireMarshal markers. |
| `+interval-trace-csv` | Also write the per-record CSVs. |
| `+interval-trace-raw` | Also write the received stream bytes to `PREFIX-<index>.bin`. |
| `+interval-trace-disable` | Consume the trace without recording a capture. |

| Mode | Endpoints |
| --- | --- |
| `0` | None; capture is always on. |
| `1` | Unsigned decimal, inclusive window of target cycles. Defaults 0 through 2^64-1. |
| `2` | Hexadecimal start and end PCs, both required, compared after sign extension. |
| `3` | Hexadecimal `MMMMMMMMIIIIIIII`: a 32-bit mask over a 32-bit instruction value. |

Hexadecimal values may have a `0x` prefix. An instruction matches when
`((instruction ^ value) & mask) == 0`. Mode 3 defaults to the FireMarshal markers:
start `ffffffff00008013` (`addi x0,x1,0`) and end `ffffffff00010013`
(`addi x0,x2,0`). Emit them with `.4byte` in inline assembly so they are not
compressed.

Cycle numbers are zero-based and count aligned target cycles from the start of
simulation, including reset cycles; target reset does not restart them. The window closes after its
inclusive end cycle. A window that overlaps a target reset produces one capture
epoch on each side of it.

PC and instruction endpoints match valid retirements only, and the whole endpoint
cycles are included, so retirement counts and PMU increments cover the same
cycles. If several endpoints match in one cycle, the last match in retirement
order decides whether capture continues; when start and end match the same
instruction, end wins. Each later start match begins a new epoch.

A capture starts at the first non-reset target cycle that satisfies the trigger,
with an epoch start and a zero baseline snapshot; the first BB is partial. The
nominal length is fixed for the epoch. A target reset closes the epoch, and in
modes 2 and 3 a new start match is then needed. At the end of simulation the
driver stops the capture without further target cycles: it emits a final PMU
snapshot, any open partial BB and the epoch end.

## Outputs

By default the driver writes, for bridge index `N`:

- `PREFIX-N.json`: the bridge's identity and event list, with its catalog ID.
- `PREFIX-N-blocks.csv`: block definitions.
- `PREFIX-N-intervals.jsonl`: interval JSONL. While the capture runs, it is named
  `PREFIX-N-intervals.jsonl.partial`; it receives its final name after the stream
  validates and the outputs close.

### Interval JSONL

One JSON object per line, one per interval, in capture order:

| Field | Meaning |
|---|---|
| `interval_format_version` | `2`. |
| `epoch` | Capture epoch ID, starting at 1. Target reset and each new trigger window start a new epoch. |
| `snapshot_id` | ID of the PMU snapshot that closes this interval. |
| `nominal_interval` | Nominal interval length in retired instructions, fixed for the epoch. |
| `start_retired`, `end_retired` | Logical retirement counts at the interval's opening and closing boundaries, from the epoch baseline. |
| `start_cycle`, `end_cycle` | Target cycle counts at the opening and closing snapshots. |
| `start_physical_retired`, `end_physical_retired` | Retirement counts of the complete target cycles at the opening and closing snapshots. |
| `instructions` | `end_retired - start_retired`; the sum of the block entries' instructions. |
| `cycles` | `end_cycle - start_cycle`. |
| `attributed_cycles` | Sum of the block entries' cycles. Equal to `cycles` except in a final interval. |
| `unattributed_cycles` | `cycles - attributed_cycles`: cycles after the last block of a final interval. |
| `physical_retirements` | `end_physical_retired - start_physical_retired`: retirements over the same whole cycles as `counter_deltas`. |
| `partial_blocks` | Number of block instances in the interval that start or end mid-block. |
| `flags` | Flags of the closing PMU snapshot: `0` nominal; bit 4 final snapshot; bit 5 target-reset closure. |
| `bb_flags` | Bitwise OR of the stream flags of every block instance in the interval. |
| `ipc` | `instructions / cycles`; `null` when `cycles` is 0. |
| `final` | `true` for the last interval of an epoch, closed by the final snapshot. It can be shorter than `nominal_interval`. |
| `counter_deltas` | For each PMU event, in event-ID order, the counter difference between the opening and closing snapshots, modulo 2^64. |
| `blocks` | One `[block_id, instructions, cycles, executions]` entry per distinct block in the interval. |

`PREFIX-N-blocks.csv` defines the block IDs:

| Column | Meaning |
|---|---|
| `id` | Block ID, from 1, in order of first appearance. Stable across the intervals and epochs of one capture. |
| `start_pc`, `end_pc` | Hexadecimal PCs of the block's first and last retired instructions. |
| `privilege` | Privilege/debug value from the trace's three-bit field (`0` user, `1` supervisor, `3` machine). |
| `partial_start`, `partial_end` | `1` if the block's instances start (end) mid-block: at capture start or stop, a trap, a PC discontinuity, or a privilege change. |

All five columns together identify a block. Each definition is written before
the first interval that refers to it.

### Per-record CSVs

With `+interval-trace-csv`:

- `PREFIX-N-bb.csv`: each BB record's PCs, instruction count, elapsed cycles,
  interval and partial flags, privilege and complete flag word.
- `PREFIX-N-pmu.csv`: baseline, nominal and final snapshots, with their logical
  retirement, cycle and physical retirement endpoints and one column per event.
  Counters are cumulative 64-bit values; take modular differences between
  snapshots within an epoch.
- `PREFIX-N-epochs.csv`: epoch start and end records, with the nominal interval,
  catalog ID, totals and reset flag.

Every row carries its `epoch` and a one-based `record` number shared by the three
files; sorting by `record` restores stream order.

### Validation

The driver decodes and validates the stream as it arrives: format version,
lengths, padding, catalog identity, epoch and snapshot ordering, and that every
interval-closing BB matches one PMU snapshot. A validation or file error fails the
simulation. A successful capture prints one summary line with its epochs,
intervals, BBs, resets, instructions, cycles and stream bytes.

## Stream format

[`IntervalTraceFormat`](../bridgeinterfaces/src/main/scala/IntervalTraceFormat.scala)
defines the records, which are also the contents of the `.bin` dump. Words are
little-endian unsigned 64-bit values, in 512-bit beats; padding is zero. The header
word holds bits 7:0 type, 15:8 version (1), 31:16 flags and 63:32 the payload word
count.

| Type | Payload words, in order | Size |
| --- | --- | --- |
| 1: BB | start PC, end PC, instruction count, elapsed cycles | One beat |
| 2: PMU | snapshot ID, logical boundary retirement count, cycle, whole-cycle retirement count, counters in event-ID order | `ceil((5 + events) / 8)` beats |
| 3: Epoch start | epoch ID, nominal interval, event count, catalog ID | One beat |
| 4: Epoch end | epoch ID, total retired, elapsed cycles, final snapshot ID | One beat |

| Flag bits | Meaning |
| --- | --- |
| 0 | BB closes a nominal interval |
| 1, 2 | BB partial start / partial end |
| 3 | PMU capture baseline |
| 4 | PMU final snapshot |
| 5 | Target-reset closure |
| 6 | Trap ended a partial BB |
| 7 | Unexpected PC discontinuity ended a partial BB |
| 8 | Privilege change ended a partial BB |
| 15:13 | BB privilege/debug value |

Epoch IDs start at 1 and the baseline snapshot ID is 0. Each nominal snapshot and
the final snapshot take the next ID. Within a target cycle, PMU records precede BB
records; an epoch start precedes its baseline, and an epoch end follows the final
snapshot and any partial BB.
