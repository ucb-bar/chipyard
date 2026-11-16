# RTL checks

Scripts for the checks in AGENTS.md section 2 that run on the generated design. Run them from anywhere in the repository; they find the root themselves. Logs go to `logs/` (git-ignored).

| Script | Check | Pass criterion |
| --- | --- | --- |
| `switches-off.sh CONFIG...` | With every switch off, generated Verilog matches `baseline-v0` | Every `.sv`/`.v` file matches, ignoring source locators |
| `riscv-tests.sh CONFIG...` | Build, then `run-asm-tests-fast` and `run-bmark-tests-fast` | Build succeeds, make exits 0 (a timeout is a failure), and every test run finished |
| `verilog-diff.sh REF NEW` | Compare two `generated-src` config directories | Used by `switches-off.sh` |

## Switches-off check

Byte-for-byte identity is impossible once a BOOM file is edited, because the generated Verilog embeds Scala line numbers in `// @[file.scala:line]` comments and in assertion messages. `verilog-diff.sh` removes those and compares everything else.

Record the reference once, on a checkout whose generators match `baseline-v0` (`git diff --stat baseline-v0 -- generators/` is empty):

```bash
tests/rtl/switches-off.sh --save-ref SmallBoomV3Config MediumBoomV3Config
```

Then, on any branch:

```bash
tests/rtl/switches-off.sh SmallBoomV3Config MediumBoomV3Config
```

## riscv-tests

```bash
tests/rtl/riscv-tests.sh MediumBoomV3STTConfig SmallBoomV3STTConfig
tail -f logs/riscv-tests/progress.log   # one line per step
```

A Small build takes a few minutes and Medium is longer. The two suites together take roughly 20-30 minutes per config at `-j32`; check `uptime` first on shared machines.

These scripts cover part of the regression in #10. They do not run the attacks, co-simulation or module tests.
