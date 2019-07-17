Chipyard CI
===========

Website: https://circleci.com/gh/ucb-bar/chipyard

CircleCI Brief Explanation
---------------------------

CircleCI is controlled by the `config.yml` script.
It consists of a *workflow* which has a series of *jobs* within it that do particular tasks.
All jobs in the workflow must pass for the CI run to be successful.

At the bottom of the `config.yml` there is a `workflows:` section that specifies the order in which the jobs of the workflow should run.
For example:

    - prepare-rocketchip:
        requires:
            - install-riscv-toolchain
            - install-verilator

This specifies that the `prepare-rocketchip` job needs the `install-riscv-toolchain` and `install-verilator` steps to run before it can run.

All jobs in the CI workflow are specified at the top of `config.yml`
They specify a docker image to use (in this case a riscv-boom image since that is already available and works nicely) and an environment.
Finally, in the `steps:` section, the steps are run sequentially and state persists throughout a job.
So when you run something like `checkout` the next step has the checked out code.
Caching in the job is done by giving a file to cache on.
`restore_cache:` loads the cache into the environment if the key matches while `save_cache:` writes to the cache with the key IF IT IS NOT PRESENT.
Note, if the cache is already present for that key, the write to it is ignored.
Here the key is built from a string where the `checksum` portion converts the file given into a hash.

.circleci directory
-------------------

This directory contains all the collateral for the Chipyard CI to work.
The following is included:

    `build-toolchains.sh` # build either riscv-tools or esp-tools
    `build-verilator.sh`  # build verilator (remotely)
    `create-hash.sh`      # create hashes of riscv-tools/esp-tools so circleci caching can work
    `do-rtl-build.sh`     # use verilator to build a sim executable (remotely)
    `config.yml`          # main circleci config script to enumerate jobs/workflows
    `defaults.sh`         # default variables used

How things are setup for Chipyard
---------------------------------

The steps for CI to run are as follows.
1st, build the toolchains in parallel (note: `esp-tools` is currently not used in the run).
The docker image sets up the `PATH` and `RISCV` variable so that `riscv-tools` is the default (currently the `env.sh` script that is created at tool build is unused).
2nd, install verilator using the `*.mk` to cache unique versions of verilator (mainly for if verilator is bumped).
3rd, create the simulator binary.
This requires the `riscv-tools` for `fesvr` and `verilator` to be able to build the binary.
This stores all collateral for the tests (srcs, generated-srcs, sim binary, etc) to run "out of the gate" in the next job (make needs everything or else it will run again).
4th, finally run the tests that were wanted.

Other CI Setup
--------------

To get the CI to work correctly you need to setup CircleCI environment variables to point to the remote directory to build files and the server user/ip.
In the project settings, you can find this under "Build Settings" "Environment Variables".
You need to add two variables like the following:

CI\_DIR = /path/to/where/you/want/to/store/remote/files
SERVER = username@myserver.coolmachine.berkeley.edu

Additionally, you need to add under the "PERMISSIONS" "SSH Permissions" section a private key that is on the build server that you are using.
After adding a private key, it will show a fingerprint that should be added under the jobs that need to be run.

Note: On the remote server you need to have the `*.pub` key file added to the `authorized_keys` file.
