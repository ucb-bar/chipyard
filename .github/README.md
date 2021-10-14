Chipyard Continuous Integration (CI)
===========

Website: https://gihub.com/gh/ucb-bar/chipyard/actions

GitHub Actions Brief Explanation
---------------------------

CI is executed by Github Actions (GA). GA is controlled by `.yml` files in the `.github/workflows/` directory.
In our case, we have just one workflow named `chipyard-run-tests.yml`.
It defines a number of `jobs` within it that do particular tasks.
All jobs in the workflow must pass for the CI run to be successful.
In general, a job is run in parallel with others unless it depends on some other job.
The dependency of one job on the completion of another is specified via the `needs` field.

For example:
```yaml
  prepare-chipyard-cores:
    name: prepare-chipyard-cores
    needs: [make-keys, setup-complete]
```
This specifies that the `prepare-chipyard-cores` job needs the both the `make-keys` and the `setup-complete` steps to
be completed before it can run.

Chipyard runs its CI using a docker image created from `dockerfiles/Dockerfile`.
See its [README](../dockerfiles/README.md) for more details.

Finally, within each job's `steps:` section, the steps are run sequentially and state persists throughout a job.
So when you run something like `checkout` the next step has the checked out code.

[Composite Actions](https://docs.github.com/en/actions/creating-actions) (CA) allow for limited subroutine like code re-use within GA.
We use both community created and our own Composite Actions in our CI process. CA capabilities are changing rapidly.
Nesting of composite actions was only recently unveiled. There is a lot of room for more code reuse, in particular
we specify things over and over like docker image tag and checkout commands.

One use of CA: our process relies on caching to avoid running time-consuming and intensive tasks more often than necessary.

The following is an example of using the cache@v2 composite action. A step `uses: actions/cache@v2` which take as parameters the
path that contains the data to be cached and a key. Paths can have multiple targets.
The following step can look at the result of the cache operation, if there was cache miss, then we run the command that
will generate the data to be cached. The caching of the generated data is implicit.
>Note: GA cache documentation suggests using the yml level `if: steps.cache-primes.outputs.cache-hit != 'true'` to
> determine whether to run the data generation command.
> At the time of this writing the if construct has a bug and will not run correctly within a composite action. The use
> of a bash based if is a [hack found on stackoverflow](https://stackoverflow.com/questions/65473359/github-action-unable-to-add-if-condition-in-steps)
```yaml
    - uses: actions/cache@v2
      id: rtl-build-id
      with:
        path: |
          sims/verilator
          sims/firesim/sim
          generators/gemmini/software/gemmini-rocc-tests
        key: ${{ inputs.group-key }}-${{ github.ref }}-${{ github.sha }}
    - name: run rtl build script if not cached
      run: |
        if [[ "${{ steps.rtl-build-id.outputs.cache-hit }}" != 'true' ]]; then
          echo "Cache miss on ${{ inputs.group-key }}-${{ github.ref }}-${{ github.sha }}"
          ./.github/scripts/${{ inputs.build-script }} ${{ inputs.group-key }} ${{ inputs.build-type }}
        else
          echo "cache hit do not prepare rtl"
        fi
      shell: bash
```

Our own composite actions are defined in the `.github/actions/<ActionName>/action.yml`

.github/scripts directory
-------------------

This directory contains most the collateral for the Chipyard CI to work.
The following is included in `.github/scripts/: directory

    `build-toolchains.sh`        # build either riscv-tools or esp-tools
    `create-hash.sh`             # create hashes of riscv-tools/esp-tools to use as hash keys
    `remote-do-rtl-build.sh`            # use verilator to build a sim executable (remotely)
    `defaults.sh`                # default variables used
    `check-commit.sh`            # check that submodule commits are valid
    `build-extra-tests.sh`       # build default chipyard tests located in tests/
    `clean-old-files.sh`         # clean up build server files
    `do-fpga-rtl-build.sh`       # similar to `do-rtl-build` but using fpga/
    `remote-install-verilator.sh`       # install verilator on build server
    `remote-run-firesim-scala-tests.sh` # run firesim scala tests
    `run-tests.sh                # run tests for a specific set of designs

How things are set up for Chipyard
---------------------------------

The steps for CI to run are as follows.
1. Build the toolchains in parallel (note: `esp-tools` is currently not used in the run).
The docker image sets up the `PATH` and `RISCV` variable so that `riscv-tools` is the default (currently the `env.sh` script that is created at tool build is unused).
2. Create the simulator binary.
This requires the `riscv-tools` for `fesvr` and `verilator` to be able to build the binary.
This stores all collateral for the tests (srcs, generated-srcs, sim binary, etc) to run "out of the gate" in the next job (make needs everything or else it will run again).
3. Finally, run the desired tests.

Other CI Setup
--------------

To get the CI to work correctly you need to create the following GH Repository Secrets

| Secret | Value |
| -------| ------------- |
| BUILDSERVER | the hostname of the remote build server (likely be a millennium machine) |
| BUILDUSER | the login to use on the build server |
| BUILDDIR | the directory to use on the build server |
| SERVERKEY | a private key to access the build server |

The main workflow also constructs and places in the environment a SERVER and a work directyory on that server env using the above secrets.
The SERVER is constructed like this:
```bash
SERVER = ${{ secrets.BUILDUSER }}@${{ secrets.BUILDSERVER }}
```

Additionally, you need to add under the "PERMISSIONS" "SSH Permissions" section a private key that is on the build server that you are using.
After adding a private key, it will show a fingerprint that should be added under the jobs that need to be run.

Note: On the remote server you need to have the `*.pub` key file added to the `authorized_keys` file.

Notes on CIRCLE CI
------------------
This code is heavily based on the origin [CircleCI]() work. There a quite a few differences
- CCI supports workflow level variables, in GA we must define things like `BUILDSERVER: ${{ secrets.BUILDSERVER }}` in every job
- CCI allows a much larger cache. The entire CY directory with toolchains and RTL could be cached, with GA there is a 5Gb total cache limit
- GA support more parallel jobs 20 vs 4
- GA seems to allow much longer run times
