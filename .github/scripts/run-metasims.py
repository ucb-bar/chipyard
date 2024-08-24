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

    with prefix(f"cd {remote_fsim_dir}"):
        with prefix('source sourceme-manager.sh --skip-ssh-setup'):
            with prefix(f'cd {remote_cy_dir}/software/firemarshal'):
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
                timeout_prefix = f"timeout {timeout} "

                def run_line(cmd):
                    rc = 0
                    with settings(warn_only=True):
                        # pty=False needed to avoid issues with screen -ls stalling in fabric
                        rc = run(cmd, pty=False).return_code
                    return rc

                def print_last_n_log():
                    print(f"Printing last {log_tail_length} lines of log.")
                    run(f"""cd {remote_fsim_dir}/deploy/log && LAST_LOG=$(ls | tail -n1) && if [ -f "$LAST_LOG" ]; then tail -n{log_lines} $LAST_LOG; fi""")

                def run_firesim_cmd(typ, extra_args):
                    firesim_opts = f"-c {workload} -a {remote_cy_dir}/sims/firesim-staging/sample_config_hwdb.yaml -r {remote_cy_dir}/sims/firesim-staging/sample_config_build_recipes.yaml"
                    return run_line(f"{timeout_prefix} firesim {firesim_opts} {extra_args} {typ}", pty=False)

                with prefix(f"export FIRESIM_RUNFARM_PREFIX={ci_env['GITHUB_RUN_ID']}-{script_name}"):
                    rc = run_firesim_cmd("launchrunfarm")
                    if rc != 0:
                        print_last_n_log()

                    rc = run_firesim_cmd("infrasetup")
                    if rc != 0:
                        print_last_n_log()

                    rc = run_firesim_cmd("runworkload")
                    if rc != 0:
                        print_last_n_log()

                    # This is a janky solution to the fact the manager does not
                    # return a non-zero exit code or some sort of result summary.
                    # The expectation here is that the PR author will manually
                    # check these output files for correctness until it can be
                    # done programmatically..
                    print(f"Printing last {log_tail_length} lines of all output files. See results-workload for more info.")
                    run(f"""cd {remote_fsim_dir}/deploy/results-workload/ && LAST_DIR=$(ls | tail -n1) && if [ -d "$LAST_DIR" ]; then tail -n{log_tail_length} $LAST_DIR/*/*; fi""")

                    # need to confirm that instance is off
                    print("Terminating runfarm. Assuming this will pass.")
                    run_firesim_cmd("runworkload", "-q")
                    print_last_n_log()

                    # using rc of runworkload
                    if rc != 0:
                        raise Exception(f"Workload {workload} failed with code: {rc}")
                    else:
                        print(f"Workload {workload} successful.")

            run_w_timeout(f"{remote_cy_dir}/sims/firesim-staging/ci/hello-world-localhost-vcs-metasim.yaml", "45m")
            run_w_timeout(f"{remote_cy_dir}/sims/firesim-staging/ci/hello-world-localhost-verilator-metasim.yaml", "45m")

if __name__ == "__main__":
    execute(run_parallel_metasim, hosts=["localhost"])
