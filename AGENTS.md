# Instructions for agents and contributors

This repository is a UT Austin hardware security course project (ECE 382N): the **µSTT** Spectre mitigation (*µSTT: Microarchitecture Design for Speculative Taint Tracking*, ICCD'25, [FPSG-UIUC/uSTT](https://github.com/FPSG-UIUC/uSTT)) built into the **SonicBOOM** (BOOMv3) out-of-order RISC-V core. It is a fork of Chipyard 1.15.0 with BOOM in the tree. Upstream contribution rules, including [CONTRIBUTING.md](CONTRIBUTING.md) and the pull request template's checklist, do not apply here.

Read first, in this order:

1. This file: the rules.
2. [project-docs/PLAN.md](project-docs/PLAN.md): the design, owners, milestones and what "done" means for each issue.
3. The GitHub issue you are working on (`gh issue view <N> -R Capheuss/boom-ustt`).

Pass `-R Capheuss/boom-ustt` to every `gh` command. The repository has an `upstream` remote (Chipyard), and `gh` may pick it.

Tasks, owners, status, results and decisions live in the [issues](https://github.com/Capheuss/boom-ustt/issues), tracked in the roadmap issue #17. Do not add other planning files.

## Project in brief

A result counts only if it is reported on all three axes:

- **Security:** the Spectre PoCs (#9) leak on the baseline and do **not** leak on the protected configuration.
- **Performance:** cycles and IPC from Verilator RTL simulation, normalized to the baseline (#11).
- **Hardware:** LUTs, FFs, BRAM, DSPs, WNS and power from Vivado 2023.2 on the VCU118 part (#12, #13). A mitigation that breaks timing is reported as such, not hidden.

| Track | Owner | What it produces | Issues |
| ----- | ----- | ---------------- | ------ |
| A. Rename side | @Capheuss | Taint table and YRoT propagation, then the µSTT Age Matrix | #3, #4, #5 |
| B. Issue side | @SamirJoshiGit | Holding back tainted transmitters, then the µSTT impede micro-op | #3, #6, #7, #8 |
| C. Security, verification and performance | @vinle0 | Spectre PoCs, regression script, performance harness | #9, #10, #11 |
| D. Hardware cost and Vivado | @erickan2002-del | VCU118 reports, out-of-context synthesis, uSTT RTL reproduction, Vivado simulation | #12 to #16 |
| Everyone | all four | Environment, decisions, presentation, final report | #1, #2, #39, #40 |

Milestones: **M0** environments work → **M1** baselines → **M2** conservative stand-in blocks the attack → **M3** baseline STT → **M4** µSTT mechanisms → **M5** hardware evaluation. #3 (the A/B interface spec) is the one hard dependency between tracks; settle it first.

Deadlines: all technical work (M0 to M5) complete **Nov 30**; presentation ready **Dec 1** (#39); final report **Dec 11** (#40). The other milestones are an order, not dates (PLAN.md section 8).

## 1. Branches and pull requests

There are two long-lived branches:

- `dev` is the default branch. All development lands here.
- `main` is unused. Do not target it.

GitHub enforces part of this with branch protection on `dev` and `main`:

- No direct pushes, force pushes or deletion. Changes arrive by PR only.
- A PR that touches a shared path listed in [.github/CODEOWNERS](.github/CODEOWNERS) needs an approving review from another teammate. A new push dismisses the approval.
- Every review conversation must be resolved before merge.
- Repo admins can override the review requirement. Keep that for emergencies (for example a tooling fix that blocks everyone), and say why in the PR.

No status check is required, because the design cannot be built on GitHub. The rest (local checks, scoped PRs, issue references, verification) is up to the team. Follow all of it exactly.

Working on an issue:

- One issue, one branch. Assign yourself, then branch from an up-to-date `dev`: `git switch dev && git pull && git switch -c <issue>-<short-name>` (for example `6-conservative-delay`).
- Never commit or push to `dev` or `main` directly.
- A PR into `dev` needs a review from another teammate before merge when it touches anything that another issue also uses or changes. Request the review; do not merge without it. This always includes:
  - the A/B interface and core-wide wiring in BOOM: `common/micro-op.scala`, `common/parameters.scala`, `common/config-mixins.scala`, `common/consts.scala`, `exu/core.scala`, `exu/rob.scala`, `exu/decode.scala`, `exu/dispatch.scala`
  - the SoC and FPGA configs: `BoomConfigs.scala`, `fpga/src/main/scala/vcu118/`
  - build and environment: `build.sbt`, `common.mk`, `variables.mk`, `conda-reqs/`, `scripts/`, `.gitmodules`, `ustt*`
  - `reference/`, `.github/`, `AGENTS.md`, `CLAUDE.md`, `project-docs/PLAN.md`, `project-docs/spec-*.md`
  - shared measurement tooling once a second track uses it: the regression script (#10), the performance harness (#11), the Vivado report script (#12)

  If unsure, check which open issues link to the file. When in doubt, ask for review.
- Keep [.github/CODEOWNERS](.github/CODEOWNERS) current. It lists only shared paths; a path that is not listed needs no review. Update it in the same PR when that PR:
  - makes a file another track depends on, or starts depending on another track's file: add the path, owned by the whole team
  - finishes with a shared path no longer in use: remove its line

  Never list one person as the only owner of a path, or their own PRs to it cannot merge. CODEOWNERS is itself shared. GitHub reads it from the base branch, so a change takes effect once its PR is merged into `dev`.
- A PR that only changes its own track's files, tests or the reports may be merged by its author once its checks pass (section 2).
- Keep PRs small and scoped to their issue. Do not mix in unrelated cleanup.
- Reference the issue in the PR body: `Closes #N` only if every acceptance criterion is met (section 3), otherwise `Refs #N`. Never write close, fix or resolve (in any form) next to an issue number anywhere else in a PR body or commit message, even negated: GitHub ignores the negation and closes the issue on merge ("does not close #4" closes #4). Write "#4 stays open" instead. Before merging, check the PR's sidebar lists no issue to close unless you meant it.
- Bring `dev` into your branch before asking for review. Resolve conflicts on the branch.
- Delete your branch after the merge. The repository does not do it for you.
- Stacked PRs: a PR may target another open PR's branch when its work depends on it. Say so in its body (`Stacked on #N`). Merge the base first. Then retarget the stacked PR (`gh pr edit <N> --base dev`), bring `dev` into it, and only then delete the base branch. Never delete a branch that an open PR targets: GitHub closes that PR.
- Check `git status` before committing. `build-setup.sh` modifies `conda-reqs/` lock files and leaves submodules dirty (`generators/constellation`, `toolchains/*`). Never `git commit -a`; stage your files by name.

Reports and deliverables:

- The reports are in `project-docs/midterm-report/` and `project-docs/final-report/`. Their PDFs are built by CI and published to the [`reports` release](https://github.com/Capheuss/boom-ustt/releases/tag/reports) on merge to `dev`. Never commit a PDF.
- A number in a report or a slide must already be on its issue, with its configuration and commit (section 3).

For everyone:

- Commits and PRs carry only the human author's name. Do not add AI attribution of any kind: no `Co-authored-by`, no `Assisted-by`, no "Generated with" line. This applies to everyone on the team and to every tool.
- Agents: commit, push and open PRs only when the person you are working for asks. Do not merge a PR, assign an issue or close one unless that person asks. Write the PR description for the reviewer: what changed, why, and how it was verified.

## 2. Check before every commit

There is no lint script and no design CI. The checks are local, and which ones apply depends on what changed:

| Change | Run before committing |
| ------ | --------------------- |
| Any Chisel or Scala | The config you touched still elaborates and builds: `./ustt make -C sims/verilator CONFIG=<config>` |
| Anything inside BOOM or a Chipyard config | With every new switch off, `generated-src` is identical to `baseline-v0`'s. Then the tests your change touches |
| BOOM, before merging | The full regression (#10) on Small and Medium. Until #10 exists: `run-asm-tests-fast` and `run-bmark-tests-fast` |
| A report (`project-docs/*-report/`) | `./ustt make -C project-docs/<report>` builds with no errors and no overfull lines. Add any new `\usepackage` to `.github/tex-packages.txt` |
| Scripts | Run the script once on a real input |
| Markdown | Tables render; links resolve |

- Do not silence a check to get a commit through. If a test is wrong, fix or remove it in its own reviewed PR and say why.
- After a formatter or style-only fix, re-run tests if code changed. Comment or whitespace-only fixes do not need a re-run.
- The only workflow that runs on GitHub is the report build (`build-reports.yml`, a few minutes). Chipyard's upstream workflows came with the fork and stay disabled: they need Berkeley's self-hosted runners, and BOOM builds are too heavy for GitHub's. Do not enable them or add workflows that build or test the design without team agreement.

## 3. Verify before you check a box or close an issue

- Do not tick an acceptance criterion, close an issue or write "done" until the thing has been built, run and observed to work. Chisel that elaborates is not verified.
- Each issue has a *Done when* checklist. Those boxes are its acceptance criteria; PLAN.md section 5 is only the overview. Run that verification. If it cannot be run (tool missing, another track's piece not merged), leave the box unticked and say what is missing.
- Each issue also lists what blocks it, in its body and as a GitHub issue dependency. Do not close an issue while a blocker is open. When a dependency changes, change both places.
- Put the evidence on the issue or in the PR: the command, the relevant output or log path, the configuration (`SmallBoomV3Config`, ...) and the commit hash. For Vivado results add the part, the clock and the flow (full or out-of-context).
- Say which checks actually ran. ChiselTest module tests, riscv-tests, Spike co-simulation, the attack PoCs, Verilator cycle counts, Vivado synthesis, Vivado implementation and xsim are separate checks. One does not stand in for another.
- Report failures as failures, with the output. A partial result is recorded as partial. A test that times out is a failure, not a skip.
- New hardware comes with a test at the lowest level that can catch its faults (PLAN.md section 6). A bug fix comes with a test that fails without the fix.
- Close an issue only when every acceptance criterion is met with evidence. If scope changed, edit the criteria in the issue and say why, instead of quietly skipping one.

## 4. Design and measurement rules

- **Baseline is frozen.** The tag `baseline-v0` marks unmodified Chipyard 1.15.0 + BOOM `ba03175`. Every number is reported relative to it, with the commit hash it was measured on.
- **Every mechanism is a switch.** New hardware sits behind a BOOM parameter (`enableSTT`, `impedeUop`, ...; default off) and gets its own Chipyard config (for example `SmallBoomV3STTConfig`). With every switch off, the generated Verilog must be identical to the baseline.
- **Security before speed.** A protected config is timed only after it passes riscv-tests and blocks every attack in #9.
- **Cycles come from RTL simulation** (Verilator, or xsim for #15), never from Spike. Spike is for functional checks and co-simulation.
- **Hardware numbers come from Vivado 2023.2** on the ECE machines; the department license is capped at 2023.10. Use the same part, clock and flow for every config.
- **Mark every number as measured or estimated**, and give the source of a measurement.
- **Results and decisions go on the issue.** Post each measurement as a comment on its issue: configuration, commit, and the numbers. Record design decisions (interface, impede-uop design, threat model) the same way.
- **Mark changes to upstream code.** Comment each change inside BOOM or Chipyard files with `// [boom-ustt]`, the way uSTT marks its gem5 changes with `[Boru, STT]`. Keep changes to upstream files minimal and put new logic in new files where possible.
- **`reference/uSTT` is read-only.** Never modify the submodule; copy or port from it instead.
- **Leave BOOM v4 alone.** It sits next to v3 and is not part of this project.

## 5. Working conventions

- The ECE servers are shared. Check the load (`uptime`) before a wide `-j`, and do not leave Vivado sessions open when idle.
- Keep builds, tool projects, caches and logs out of home directories (2 GB quota). Use `/misc/scratch/<user>`; logs and run outputs go in `logs/` (git-ignored).
- Run long jobs (`build-setup.sh`, Verilator builds of large cores, regressions, Vivado) in the background with a log file.
- Never commit generated Verilog, build outputs or PDFs.
- Pull upstream Chipyard only as a deliberate step: `git fetch upstream --tags`, merge a release tag, in its own reviewed PR. Update BOOM with `git subtree pull --prefix=generators/boom https://github.com/riscv-boom/riscv-boom.git master`, also in its own PR. Either one moves the baseline, so it needs team agreement.

## 6. Code and comment standards

- Read the surrounding code first and match its patterns. BOOM's own style is in `generators/boom/CONTRIB_AND_STYLE.md`.
- ASCII only in code and comments: `-`, `->`, `x`, `...`, `uSTT` instead of unicode equivalents.
- Comments: short, only where the code is not self-explanatory, no hard wrapping to a fixed width, no restating the code, no references to the conversation or task that produced them.
- Write code first, then add the few comments it needs.
- Prefer existing infrastructure over new components: BOOM's busy table, branch masks and wakeup logic before new structures.

## Environment

Every command that uses the toolchain (sbt/Chisel, Verilator, RISC-V gcc, Spike) runs through `./ustt`, which sets up conda + Chipyard for that one command only:

```bash
./ustt <command>            # conda + Chipyard env
./ustt --vivado <command>   # also the xilinx/2023 module (license server) + Vivado 2023.2
./ustt                      # interactive subshell with the env
```

One-time setup: `cp ustt-env.local.sh.example ustt-env.local.sh`, edit it, then `./build-setup.sh riscv-tools` (about an hour). On ECE machines Miniforge, TeX Live and every cache live on `/misc/scratch/$USER`. Do not source the env from `~/.bashrc`: the conda env shadows system gcc/python and breaks other EDA tools. Xilinx is loaded before Chipyard so Chipyard's `riscv64-unknown-elf-*` comes first on PATH, ahead of the copy shipped with Vitis.

All builds run on the UT ECE Linux servers (RHEL 8, x86); Chipyard's setup supports Linux only. Vivado's license is `2100@harrison.ece.utexas.edu`.

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

## Repo map (project-relevant parts)

- `generators/boom/src/main/scala/v3/`: SonicBOOM, the core we modify. Key files: `common/micro-op.scala` (uop fields), `common/parameters.scala` and `common/config-mixins.scala` (switches, core sizes), `exu/rename/` (rename stage, busy table: track A), `exu/issue-units/issue-slot.scala` (`io.request`: track B), `exu/rob.scala` (visibility point), `lsu/lsu.scala` (every load-firing path), `exu/core.scala` (branch resolution, `brupdate`). BOOM is included in the tree, not as a submodule.
- `generators/chipyard/src/main/scala/config/BoomConfigs.scala`: SoC configs; add the STT configs here.
- `fpga/`: Chipyard's Vivado flows. `fpga/src/main/scala/vcu118/` holds `BoomVCU118Config`; `fpga/fpga-shells/xilinx/vcu118/` holds the board files.
- `reference/uSTT/`: original µSTT (read-only submodule). `rtl/src/` has the standalone Verilog Age Matrix (`stt_age.v`, `stt_control.v`) and baseline STT (`stt_og.v`, `younger.v`); `rtl/test/` has its test generators; `gem5/src/cpu/o3/` has the impede-uop and taint changes (search `[Boru, STT]`).
- `project-docs/PLAN.md`: the plan. `project-docs/spec-*.md`: design specs (the first is #3's interface spec).
- `project-docs/midterm-report/`, `project-docs/final-report/`: the reports (LaTeX, ACM `sigplan`). `.github/workflows/build-reports.yml` builds both on every PR that touches a report (PDFs attached to the run). TeX packages are listed in `.github/tex-packages.txt`.
- `ustt`, `ustt-env.sh`, `ustt-env.local.sh.example`: environment setup.
