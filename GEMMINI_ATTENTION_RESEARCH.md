# Gemmini Attention Accelerator Research Plan

## Scope and feasibility

Gemmini is an appropriate research platform to modify into a new accelerator generator, including replacing, adding, or removing memory-management hardware blocks. It is not a plug-in framework where arbitrary blocks can be selected solely through configuration parameters.

Gemmini's architecture separates the main areas of study:

- `LoadController`, `StoreController`, and `ExecuteController` run concurrently.
- `Scratchpad.scala` implements private banked scratchpad and accumulator memories.
- `DMA.scala` moves tensors between system memory and the private memories.
- The ROB handles dependencies across controllers.
- `Mesh.scala` performs systolic-array matrix multiplication.
- `Normalizer.scala` supports normalization-related operations.

The repository includes external/shared scratchpad support in `Controller.scala`, so the design is not conceptually limited to its default private-SRAM arrangement. A new memory block or policy must still be integrated into scheduling, hazard handling, data paths, and, where necessary, the custom instruction and software layers. This is a hardware-generator project, not merely a `GemminiArrayConfig` parameter change.

## Gemmini and LLM attention

LLMs are neural networks and therefore broadly fall under the DNN category. Gemmini is relevant because attention contains major matrix multiplications:

- Query-key multiplication: `Q × Kᵀ`
- Attention-weight/value multiplication

However, Gemmini was originally shaped primarily around DNN/GEMM and convolution workloads, not modern autoregressive LLM inference. It includes transformer-related support: its arithmetic abstraction has optional divider, square-root, and reciprocal operations, while `config_norm` supports I-BERT GELU, layer normalization, and softmax variants.

Accelerating DNNs does not automatically mean Gemmini is optimized for LLM attention. Attention combines GEMMs with softmax, normalization, and data movement. Long-context execution can be dominated by memory traffic and intermediate-state movement rather than multiply-accumulate throughput. Decoder-only inference adds causal masking and a growing key/value cache, while token-by-token decoding can present small or irregular shapes that use an array differently from large CNN/GEMM workloads.

## Project framing

Use Gemmini as a configurable, full-system baseline for attention-oriented workloads, then evaluate whether a changed memory hierarchy or dataflow improves attention performance relative to that baseline.

The project is feasible. A targeted memory-system modification for attention is aligned with Gemmini's architecture. Rebuilding Gemmini into a general LLM accelerator with a wholly new programming model, dataflow, and compute architecture is a separate, substantially larger project.

## Baseline architecture

- `generators/gemmini/src/main/scala/gemmini/Controller.scala`: RoCC integration and external/shared scratchpad support.
- `generators/gemmini/src/main/scala/gemmini/Scratchpad.scala`: banked scratchpad and accumulator storage.
- `generators/gemmini/src/main/scala/gemmini/DMA.scala`: data movement between main memory and Gemmini memory.
- `generators/gemmini/src/main/scala/gemmini/ExecuteController.scala`, `LoadController.scala`, and `StoreController.scala`: decoupled compute and memory operation control.
- `generators/gemmini/src/main/scala/gemmini/ROB.scala`: dependency tracking across controllers.
- `generators/gemmini/src/main/scala/gemmini/Mesh.scala`: systolic GEMM datapath.
- `generators/gemmini/src/main/scala/gemmini/Normalizer.scala` and `Arithmetic.scala`: transformer-related normalization and arithmetic support.

## Evaluation sequence

1. Define an attention workload and break it into query-key multiplication, softmax/normalization, and attention-value multiplication.
2. Profile the unmodified Gemmini baseline to determine whether the limiting factor is compute, scratchpad capacity or bank conflicts, DMA/system-memory bandwidth, or cross-controller synchronization.
3. Formulate one targeted memory-system hypothesis and create a Gemmini generator variant to test it.
4. Integrate that variant with affected controllers, scheduling/ROB behavior, memory datapaths, and software interfaces as required.
5. Verify functional correctness and compare the baseline and variant using identical workload shapes and configurations.
6. Report end-to-end latency and throughput plus memory-related metrics and matrix-multiply utilization. Isolated GEMM performance is not sufficient as the sole success criterion.

## Scope boundary

A focused memory-hierarchy or data-movement change for attention is tractable in Gemmini. A general LLM architecture with a new programming model, compute dataflow, and non-Gemmini interface is substantially larger in scope.

## Functional-simulation diagnostic record

### Issue

`resnet50-baremetal` produced correct predictions in Spike, while
`resnet50-pk` completed with deterministic incorrect predictions. Small PK
Gemmini tests also passed.

### Evidence and conclusion

- The failing command was:

  ```shell
  spike --extension=gemmini pk resnet50-pk
  ```

  It completed with predictions `5, 5, 0, 0` and failed the workload check.

- The identical `resnet50-pk` binary was then run with PK demand paging
  disabled:

  ```shell
  spike --extension=gemmini pk -p resnet50-pk
  ```

  It produced the expected predictions `75, 900, 641, 897` and reported
  `PASS`.

- The behavior is not unique to ResNet50. `mobilenet-pk` failed with demand
  paging enabled, then produced predictions `75, 900, 125, 897` and `PASS`
  with `pk -p`.

- `-p` is implemented by the PK runtime in
  `toolchains/riscv-tools/riscv-pk/pk/pk.c`; it sets `demand_paging = 0`.

This isolates the failure to the interaction between PK demand paging and the
large workload's memory accesses in Spike's Gemmini functional extension. It
does not indicate a Gemmini RTL TLB, DMA, or `GemminiRocketConfig` error:
Spike's `--extension=gemmini` path uses the functional model in
`generators/gemmini/software/libgemmini/gemmini.cc`, rather than elaborated
Chipyard RTL.

### Applied documentation change

The documented large-workload Spike command in `generators/gemmini/README.md`
now passes `pk -p`. This is a functional-simulation correctness workaround,
not a performance result and not a change to Gemmini hardware.
