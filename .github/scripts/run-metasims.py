#!/usr/bin/env python3

from pathlib import Path
from fabric.api import prefix, run, settings, execute # type: ignore

import fabric_cfg
from ci_variables import ci_env

def run_parallel_metasim():
    """ Runs parallel baremetal metasimulations """

    # assumptions:
    #   - machine-launch-script requirements are already installed
    #   - VCS is installed

    # repo should already be checked out

    with prefix(f"cd {manager_fsim_dir}"):
        with prefix('source sourceme-manager.sh --skip-ssh-setup'):
            with prefix('cd target-design/chipyard/software/firemarshal'):
                # build hello world baremetal test
                run("./marshal -v build test/bare.yaml")
                run("./marshal -v install test/bare.yaml")

            def run_w_timeout(workload: str, timeout: str):
                """ Run workload with a specific timeout

                :arg: workload (str) - workload yaml (abs path)
                :arg: timeout (str) - timeout amount for the workload to run
                """
                log_tail_length = 300
                # unique tag based on the ci workflow and filename is needed to ensure
                # run farm is unique to each linux-poweroff test
                script_name = Path(__file__).stem
                with prefix(f"export FIRESIM_RUNFARM_PREFIX={ci_env['GITHUB_RUN_ID']}-{script_name}"):
                    rc = 0
                    with settings(warn_only=True):
                        # avoid logging excessive amounts to prevent GH-A masking secrets (which slows down log output)
                        # pty=False needed to avoid issues with screen -ls stalling in fabric
                        rc = run(f"timeout {timeout} ./deploy/workloads/run-workload.sh {workload} --withlaunch &> {workload}.log", pty=False).return_code
                        print(f"Printing last {log_tail_length} lines of log. See {workload}.log for full info.")
                        run(f"tail -n {log_tail_length} {workload}.log")

                        # This is a janky solution to the fact the manager does not
                        # return a non-zero exit code or some sort of result summary.
                        # The expectation here is that the PR author will manually
                        # check these output files for correctness until it can be
                        # done programmatically..
                        print(f"Printing last {log_tail_length} lines of all output files. See results-workload for more info.")
                        run(f"""cd deploy/results-workload/ && LAST_DIR=$(ls | tail -n1) && if [ -d "$LAST_DIR" ]; then tail -n{log_tail_length} $LAST_DIR/*/*; fi""")

                    if rc != 0:
                        # need to confirm that instance is off
                        print("Terminating runfarm.")
                        run(f"firesim terminaterunfarm -q -c {workload}")
                        raise Exception(f"Workload {workload} failed with code: {rc}")
                    else:
                        print(f"Workload {workload} successful.")

            run_w_timeout(f"{ci_env['REMOTE_WORK_DIR']}/deploy/workloads/ci/hello-world-localhost-vcs-metasim.yaml", "45m")
            run_w_timeout(f"{ci_env['REMOTE_WORK_DIR']}/deploy/workloads/ci/hello-world-localhost-verilator-metasim.yaml", "45m")

if __name__ == "__main__":
    execute(run_parallel_metasim, hosts=["localhost"])
