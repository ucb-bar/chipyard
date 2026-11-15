# A/B interface: YRoT and the visibility point

Proposed contract for [issue #3](https://github.com/Capheuss/boom-ustt/issues/3), owned by Christopher (A), for review by Samir (B). This is a design specification, not an implementation or a security result. Track B approval and merge into `dev` are still required. Related implementation: [#4](https://github.com/Capheuss/boom-ustt/issues/4), [#5](https://github.com/Capheuss/boom-ustt/issues/5), [#6](https://github.com/Capheuss/boom-ustt/issues/6), [#7](https://github.com/Capheuss/boom-ustt/issues/7), [#8](https://github.com/Capheuss/boom-ustt/issues/8).

Source inspection: repository commit `45ab0b5d2c71790490d597b64510e8cdd315f644`; reference uSTT commit `663329abc336c556695c13fb9209cd222cea0b88`. This contract specializes the [project plan](PLAN.md) to BOOM v3. Names below are proposed additions unless explicitly described as existing.

## 1. Scope and decisions

The supported threat model is Spectre v1, v2 and RSB as exercised by #9: speculative loads introduce taint; dependent memory addresses and branch decisions must not transmit it before the root is safe. Exception-based attacks, speculative store bypass, and timing channels outside the plan's transmitter set are outside this claim.

| Question | Contract |
| --- | --- |
| What does a uop carry? | One aggregate source YRoT and a transmitter flag. A load's destination gets a separate new root in the taint table. |
| What is the VP? | The oldest live unresolved conditional branch or `jalr` in the ROB. No such branch is represented explicitly by `valid = false`. |
| Can we use `rob_pnr_idx`? | No. Its unsafe set includes loads, stores and exceptions, making it a different, more restrictive boundary than the selected Spectre VP. |
| How does a waiting transmitter resume? | Re-check safety each cycle; no taint-clear wakeup port in baseline STT. Operand readiness and taint safety are independent. |
| How do tags survive ROB wraparound? | They do not survive indefinitely: every persistent copy clears safe taint before the root can retire and its slot be reused. |
| Where is taint calculated? | At the rename2/dispatch boundary, using the final physical operands and ROB indices, with writes only on accepted dispatch lanes. |

The VP is an **exclusive** boundary: a root must be strictly older. The reference gem5 implementation instead stores the sequence number of the last unsquashable instruction and compares inclusively. Do not copy its `yrot <= vp` test onto this interface. See reference [rename.cc](../reference/uSTT/gem5/src/cpu/o3/rename.cc) and [rob.cc](../reference/uSTT/gem5/src/cpu/o3/rob.cc).

## 2. Signals and ownership

All indices use BOOM's existing `robAddrSz`, including row and bank bits. Never hard-code a width or treat the index as only a ROB row. Small uses 5 bits and Medium uses 6 bits; these are configuration facts, not measurements.

| Name | Chisel shape when present | Producer | Consumer / meaning |
| --- | --- | --- | --- |
| `MicroOp.yrot` | `Valid(UInt(robAddrSz.W))` | A at dispatch | B issue/LSU; youngest root among the instruction's register inputs |
| `MicroOp.is_transmitter` | `Bool()` | A's shared decode wiring | B issue/LSU; instruction belongs to the transmitter set |
| `Rob.io.stt_vp` | `Output(Valid(UInt(robAddrSz.W)))` | A in ROB | Core fans out to rename taint logic, issue and LSU; oldest unresolved branch |
| `rob_head_idx` | Existing `UInt(robAddrSz.W)` | ROB | Same-cycle reference for every circular age comparison; add routing where absent |
| `stt_block` | `Bool()` | A's core wiring | B issue/LSU; inhibit protected transmitter execution during recovery |

Use elaboration-time optional fields/ports: `yrot`, `stt_vp` and the new head routing exist only with `enableSTT`; `is_transmitter` and `stt_block` exist with `enableSTT || conservativeSTT`. For example the Scala `yrot` member is `Some(Valid(UInt(robAddrSz.W)))` when enabled and `None` otherwise; enabled code accesses `yrot.get`. New logic and assertions must also be inside Scala configuration guards. Default-off must preserve the baseline generated RTL, including bundle layouts.

`yrot.valid = false` means untainted; index zero is a valid root. Drive invalid payloads to zero on creation/clear for deterministic traces. Decode/null uops start untainted. A produces the final tag before the accepted uop forks to the ROB, dispatcher and LDQ/STQ; every fork receives the same value. Existing whole-uop copies preserve the fields, with the lifetime rules in section 5.

A provides `STTIsSafe(yrot, stt_vp, rob_head_idx)` in a new shared `v3/common/stt.scala`, plus the shared transmitter classification. B owns routing inside issue units and the LSU and all execution gates. The new helper path must be added to CODEOWNERS in the PR that introduces it; this spec is already covered by `/project-docs/spec-*.md`.

## 3. Taint generation and dispatch

Classify the original decoded instruction as:

```text
is_transmitter = uses_ldq || (uses_stq && !is_fence) || is_br || is_jalr
```

This matches the current `MicroOp.unsafe` expression, but STT must have its own named contract so future PNR changes cannot silently change security policy. It covers integer/FP loads and stores, LR/SC/AMOs, conditional branches and indirect jumps/returns. Direct `jal`, fences and ordinary ALU/FP operations are not transmitters. Preserve the flag through store splitting; do not recompute it from a rewritten issue opcode.

For baseline STT, aggregate **all actual register inputs**, including store data. This deliberately delays some stores more than an address-only policy. Ignore immediates, absent operands and integer x0. FP f0 is an ordinary register; include `prs3` only when `frs3_en` is set. Select the integer or FP taint table using the source register type. Cross-domain moves/conversions must propagate taint. Protected configs initially require `enableSFBOpt = false`, since predicate-register taint is not defined here.

For each accepted lane in program order:

1. Read and normalize existing source tags using section 5. Bypass the destination tags of earlier accepted lanes, choosing the latest matching writer in the same register class.
2. Select the youngest valid source using circular ROB age. Invalid tags contribute nothing. Put that tag in `uop.yrot`.
3. For a destination-writing access (`uses_ldq || is_amo`), write `{valid = true, bits = this_uop.rob_idx}` to its physical destination's taint table. This conservatively includes SC results. For other destination-writing instructions, write the aggregate source tag. Do not allocate a destination tag for integer x0 or an absent destination.

An untainted-address load may execute under an unresolved branch: its **input** YRoT is invalid, while its **result** is tainted by the load's own index. Giving that load its own root as the input YRoT would incorrectly block it. Ordinary dependent ALU operations may execute and propagate taint; they do not wait for the VP.

BOOM currently assigns `dis_uops(w).rob_idx` in [core.scala](../generators/boom/src/main/scala/v3/exu/core.scala), after rename's output. A must supply these candidate indices to the taint calculation; reading the earlier rename uop's `rob_idx` is insufficient. Resolve integer/FP operand merging before computing the final aggregate. Do not feed the taint result back into `dis_fire` or create a combinational loop through `dis_uops`.

Taint-table writes and bypass validity use `dis_fire`, not decode validity. A stalled suffix of a Medium dispatch group must re-read/recompute on its later acceptance, seeing the earlier prefix's committed taint-table writes. A held lane creates no new root. Do not normalize newly created same-cycle roots against the old ROB snapshot: an older branch in that same dispatch group is not in that snapshot yet. Keep new roots and their same-group descendants valid until at least the following cycle.

## 4. VP generation, age and cycle timing

Maintain separate unresolved-branch state in the ROB. A straightforward implementation is one pending bit per valid ROB entry, initialized from `allocate_brtag` on `enq_valids`. With SFB disabled, this is the conditional-branch/`jalr` set. Retain the entry's branch tag for resolution matching. Reuse BOOM's age-priority selection and branch recovery machinery; do not use ordinary writeback, LSU unsafe-clear, or `rob_unsafe` as the STT resolution condition.

The current-cycle output selects the oldest entry whose ROB valid and pending bits are set, ordered relative to current `rob_head_idx`. It is combinational from registered state, without bypassing same-cycle branch resolution. When there is no pending entry, set `stt_vp.valid = false` and its payload to zero; neither the ROB tail nor index zero is a sentinel.

At the edge, `brupdate.b1.resolve_mask` clears matching pending bits on existing entries. Branch kills and ROB invalidation remove affected entries. Newly accepted entries initialize their own pending bit after old-entry updates, so reuse of a resolved branch tag cannot erase a new branch. Reset/full flush clear pending state. An unresolved live branch may not retire. Branches still in decode/rename cannot be older than already dispatched roots; same-group creation is handled by section 3.

Define the pure helper using existing [IsOlder](../generators/boom/src/main/scala/v3/util/util.scala):

```text
older(a, b, head) = (a < b) XOR (a < head) XOR (b < head)
isSafe(y, vp, head) = !y.valid || !vp.valid || older(y.bits, vp.bits, head)
```

Equality with a valid VP is unsafe. A valid root is a load/atomic, not a branch, so equality is not a normal live state; keeping the comparison strict avoids giving malformed tags permission. This comparator requires tags from the same live ROB window, enforced by section 5. A plain unsigned comparison or signed subtraction is not sufficient. For a non-power-of-two bank count, compare actual row/bank encodings with `IsOlder`, not modulo `numRobEntries` arithmetic; unused bank encodings are never allocated.

Illustrative Small ROB cases (not measurements):

| Head | VP valid/index | YRoT valid/index | `isSafe` |
| --- | --- | --- | --- |
| 28 | true / 2 | true / 30 | true: root precedes the wrapped branch |
| 28 | true / 2 | true / 4 | false: root follows the branch |
| 28 | true / 2 | true / 2 | false: strict boundary |
| 28 | true / 2 | false / 0 | true: untainted |
| 28 | false / 0 | true / 4 | true: no unresolved branch |

If the oldest branch resolves correctly during cycle C, consumers still see that branch during C and see the advanced VP in C+1. The oldest unresolved branch can itself issue: its register roots precede it and are therefore safe. Do not insert a branch's own tag into its `br_mask`; BOOM's existing allocation excludes it.

`stt_block` is asserted for either phase of a mispredict (`b1.mispredict_mask.orR` or `b2.mispredict`), a backend flush, or rollback/recovery until the ROB, rename maps and taint state are consistent. Core owns extending it if recovery lasts longer than those pulses. It blocks transmitters even with invalid YRoT. Kill wins over release; a resolving mispredict must never release a wrong-path transmitter merely because its branch bit cleared. Existing branch/exception kill checks remain mandatory. Correct branch resolution does not assert `stt_block`.

Keep `stt_block` separate from `isSafe`: metadata cleanup uses the pre-edge VP/head even during recovery, and must not miss a root retiring while execution is inhibited. The resolving branch already executed before its resolution asserts `stt_block`; branch-resolution assertions check its taint/kill provenance, not that this recovery signal is low.

## 5. Clearing, retention and recovery

Every cycle, every retained tag must either be killed or have its valid bit cleared when `isSafe` becomes true. A waiting transmitter becomes eligible combinationally in that cycle; clearing the stored bit at the edge makes that release permanent. No physical-register wakeup is generated, and no readiness bit is set by taint clearing.

This applies to taint tables, resident IQ/LDQ/STQ entries, ROB copies, pipeline registers, and any replay or snapshot storage that can later supply a tag. Clear on both hold and transfer paths; normalize incoming tags as well as resident tags, except for the dispatch-cycle new roots described above. An already safe tag must not become unsafe again when a newer branch is allocated. Raw tags must not reappear from a long-latency execution response after their roots retire; either normalize those retained copies each cycle or retain the canonical normalized metadata at the receiver and never overwrite it from that response.

The lifetime invariant is: **every valid tag visible to a consumer names a live root in the current ROB window** (plus newly accepted dispatch roots at that edge). A root that retires is older than every unresolved branch, so all copies clear using the pre-retirement snapshot at that same edge. Check this invariant in simulation. Merely normalizing a tag when a physical register is next read is incorrect: the register can outlive many ROB wraps. Adding one generation bit is not a substitute for a lifetime rule.

On mispredict, kill younger uops/roots with existing branch recovery and restore the rename maps/free lists as BOOM already does. Preserve and continue clearing surviving physical-register taints; clearing the entire table on every mispredict would lose taint from older surviving roots. Physical-register indexing permits keeping tags for old mappings without a full taint-table snapshot. Reclaimed destinations must be overwritten on allocation before any use. If implementation uses snapshots, snapshots must also obey clearing and cannot restore expired tags.

On a full exception/flush recovery, discard all squashed work and restore committed mappings. Committed values are untainted in this model. Clear the remaining table state before reopening dispatch/execution, while `stt_block` covers the recovery interval. Reset starts with empty tables, invalid VP and no valid uops. Partial recovery must preserve taints of surviving speculative instructions rather than apply the full-flush rule.

## 6. Track B execution contract

Compute an additional permission, then AND it into **every final request path**:

```text
STT permission = !is_transmitter || (!stt_block && isSafe(yrot, stt_vp, head))
Conservative permission = !is_transmitter || (!stt_block && !updated_br_mask.orR)
```

Retain ordinary operand readiness, branch kill, functional-unit availability and speculative-load poison/replay behavior. In [issue-slot.scala](../generators/boom/src/main/scala/v3/exu/issue-units/issue-slot.scala), later `s_valid_1`/`s_valid_2` assignments overwrite the initial `io.request`; gating only its first assignment is ineffective. `request_hp` must derive from the gated request. Preserve and normalize metadata on `io.uop`, `io.out_uop`, incoming replacements and queue compaction.

For split stores/AMOs, both halves retain the original aggregate tag and transmitter flag, including a pure STD half. FP store-data paths need the same permission. This is conservative but keeps a single unambiguous input tag. For #8, an impede uop waits on that same tag and wakes its transmitter through the dependency mechanism to be specified there. Removing baseline per-entry issue comparisons must not remove LSU protection, tag lifetime handling or recovery checks.

Section 5's eager clearing has hardware cost. The #8 design must explain how it removes per-entry age comparators, including those used only for tag cleanup; a shared release mechanism or canonical metadata lookup may replace those comparisons while preserving the lifetime invariant. Moving the same comparator from request logic to cleanup logic does not establish the intended area reduction.

An issue permission is not blanket permission for future LSU retries. B must gate or prove safe every path in [lsu.scala](../generators/boom/src/main/scala/v3/lsu/lsu.scala), with assertions at actual side effects. The required audit starts here:

| Path / existing signal | Required treatment |
| --- | --- |
| `will_fire_load_incoming`, `will_fire_stad_incoming`, `will_fire_sta_incoming`, `will_fire_std_incoming` | Preserve permission through register read/address generation; assert safe before TLB/cache activity and store capture. |
| `will_fire_load_retry`, `will_fire_sta_retry` | Re-check the retained LDQ/STQ tag before TLB replay. |
| `will_fire_load_wakeup` | Re-check before a cache nack/retry request. Filter unsafe candidates before selection so they cannot starve older safe entries. |
| `mem_forward_valid`, forwarded writeback | Preserve the load's input tag and check forwarding eligibility; a forwarded load still creates its own destination root. |
| Speculative load wakeup | It may mark operands ready, but must never clear taint or bypass transmitter permission. |
| `will_fire_store_commit`, FP store data | Check STQ/FP metadata too. The store-commit path also handles AMOs before ordinary store commit, so its name is not a safety proof. |
| Prefetch training/requests | #7 audits [prefetcher.scala](../generators/boom/src/main/scala/v3/lsu/prefetcher.scala); Small/Medium protected configs keep prefetching disabled initially. Its `NullMicroOp` is not proof of safe provenance. |
| `will_fire_hella_incoming`, `will_fire_hella_wakeup`, SFENCE and cache releases | Document provenance and serialization/exemption. External accelerator traffic is unsupported in the initial protected configs; do not classify coherence housekeeping as a tainted instruction. |
| Branch/`jalr` execution and `brupdate` | Gate issue; assert safe operands at execution/resolution, including a mispredicting branch. |

An LSU request already accepted upstream cannot simply be dropped by adding a late gate. BOOM asserts that incoming execution requests are serviced. Either safety is preserved from issue and asserted at that boundary, or B must add a real hold/retry handshake. All surviving requests retain their instruction identity and normalized input taint until completion. Issue #7 records the complete audit and tests; this table is a contract, not evidence that those paths have been secured.

## 7. Parameters and configurations

Add these elaboration-time `BoomCoreParams` members and corresponding `HasBoomCoreParameters` accessors:

| Name | Type / default | Meaning |
| --- | --- | --- |
| `conservativeSTT` | `Boolean = false` | Branch-mask transmitter delay, without taint tables |
| `enableSTT` | `Boolean = false` | Taint tracking, VP and baseline STT safety checks |
| `enableAgeMatrix` | `Boolean = false` | Replace baseline same-group taint propagation with #5's Age Matrix |
| `impedeUop` | `Boolean = false` | Replace baseline issue delay with #8's impede mechanism; retain LSU checks |
| `threatModel` | `String = "spectre"` | Only accepted value initially; no hardware enabled by this selector alone |

Require `!conservativeSTT || !enableSTT`, `!enableAgeMatrix || enableSTT`, and `!impedeUop || enableSTT`. Reject unsupported `threatModel` values at elaboration; do not silently treat `"futuristic"` as Spectre. The plan's ROB-head stretch model needs its own reviewed lifetime/progress contract. Initially reject protected configurations with SFB, prefetching or RoCC enabled until their extensions are reviewed. The named Small/Medium bases already leave SFB and prefetching off.

Reserve mixins `WithConservativeSTT`, `WithSTT`, `WithSTTAgeMatrix`, and `WithSTTImpedeUop` in `boom.v3.common`. `WithSTT` enables baseline STT; each latter mixin enables STT and its named mechanism without resetting the other mechanism's setting. They modify BOOM v3 tiles only. Chipyard configuration mixins precede the corresponding existing size config:

| Variant | Small config | Medium config | Enabled Boolean switches |
| --- | --- | --- | --- |
| Baseline (existing) | `SmallBoomV3Config` | `MediumBoomV3Config` | none |
| Conservative | `SmallBoomV3ConservativeConfig` | `MediumBoomV3ConservativeConfig` | `conservativeSTT` |
| STT | `SmallBoomV3STTConfig` | `MediumBoomV3STTConfig` | `enableSTT` |
| STT + Age Matrix | `SmallBoomV3STTAgeMatrixConfig` | `MediumBoomV3STTAgeMatrixConfig` | `enableSTT`, `enableAgeMatrix` |
| STT + impede (ablation) | `SmallBoomV3STTImpedeConfig` | `MediumBoomV3STTImpedeConfig` | `enableSTT`, `impedeUop` |
| uSTT | `SmallBoomV3USTTConfig` | `MediumBoomV3USTTConfig` | `enableSTT`, `enableAgeMatrix`, `impedeUop` |

Reserve `MediumBoomV3ConservativeCosimConfig`, `MediumBoomV3STTCosimConfig`, `MediumBoomV3STTAgeMatrixCosimConfig`, `MediumBoomV3STTImpedeCosimConfig` and `MediumBoomV3USTTCosimConfig`, composed over existing `MediumBoomV3CosimConfig`. Introduce each config only with its working implementation; until then its name here is a reservation, not a runnable target. FPGA size/flow choices remain with #2/#12/#13.

## 8. Handoff and required verification

A owns the shared uop fields/classification, parameter definitions, helper, ROB VP/recovery output, taint tables, bypass and core fanout in #4. B owns conservative configs/mixin and delay in #6, STT issue/LSU consumers in #7, and impede configs/mixin in #8. A owns STT configs/mixin in #4 and Age Matrix configs/mixin in #5; B composes USTT when both mechanisms exist. Shared edits require teammate review. If B starts #6 first, it may introduce the agreed classifier, conservative parameter and recovery gate; coordinate those shared hunks in #3 rather than invent parallel names.

B can implement #6 immediately and prepare #7 against this contract. A consumer testbench may drive VP/head/tags directly, but tying YRoT permanently invalid is not an integrated STT implementation or a security result. The following are required directed cases for #4/#7, with Age Matrix equivalence in #5:

| Case | Expected observation |
| --- | --- |
| Root versus input | Clean-address speculative load issues; its destination becomes a root and a dependent probe load waits. |
| Source propagation | Youngest of multiple roots wins; x0/absent inputs contribute nothing; FP f0, third operands and cross-domain moves propagate. |
| Medium same-group and partial dispatch | Load-to-ALU/transmitter bypass works, including a stalled suffix and an older same-group branch. No writes on stalled lanes. |
| Branch progress | Oldest unresolved branch issues with older roots; multiple branches resolving out of order advance the VP only past resolved entries. |
| Circular age | Wrapped roots/VP, equality, empty/full ROB and bank boundaries match program order. |
| Tag lifetime | Keep a physical-register value and a queued/replay copy across more than two ROB wraps. Cleared taint never reappears or aliases a new root. |
| Transfer/retire collision | Clear on compaction, dispatch, stalled registers, snapshot restore and long-latency return; cover the edge where a root retires. |
| Recovery | Correct resolution releases next cycle; b1/b2 mispredict and exceptions never release killed transmitters; surviving older taints persist. Include branch-tag reuse. |
| LSU and stores | Exercise each audited path, split and FP stores, LR/SC/AMOs, forwarding and poisoned wakeup without dropping an accepted request. |
| Feature combinations | Illegal combinations fail elaboration; switches off produce identical `generated-src` to `baseline-v0`. |

Integration evidence must separately report Chisel/module checks, Small/Medium RTL ISA and benchmarks, #9 attack results, and co-simulation where run. A timeout is a failure. Run the repository's required elaboration/build and switches-off diff for the implementing changes. No RTL build or attack outcome is claimed by this documentation-only change.

For #3, the review must explicitly accept the exclusive VP, dispatch-time root creation, all-source aggregation, eager lifetime clearing, recovery timing and config names. Record the decision and verification evidence on #3 when publishing this spec; approval and merge remain its remaining acceptance criteria. The impede allocation/dependency design is intentionally owned by #8 and does not block this interface.
