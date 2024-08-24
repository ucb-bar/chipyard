#!/usr/bin/env python3

from fabric.api import prefix, run, settings, execute # type: ignore

import fabric_cfg
from ci_variables import ci_env
from utils import search_match_in_last_workloads_output_file, create_args, FpgaPlatform

args = create_args()
if args.platform != FpgaPlatform.xilinx_alveo_u250:
    raise Exception(f"Unable to run this script with {args.platform}")

def run_linux_poweroff():
    """ Runs Linux Poweroff Tests - All Single-node Tests (Single-core Rocket/BOOM, Multi-core Rocket)"""

    # assumptions:
    #   - machine-launch-script requirements are already installed
    #   - repo is already setup fully

    # repo should already be checked out

    with prefix(f"cd {remote_fsim_dir}"):
        with prefix('source sourceme-manager.sh --skip-ssh-setup'):
            with prefix(f'cd {remote_cy_dir}/software/firemarshal'):
                # build outputs.yaml (use this workload since firemarshal can guestmount)
                run("./marshal -v build test/outputs.yaml")
                run("./marshal -v install test/outputs.yaml")

            def run_w_timeout(workload_path, config_runtime, workload, timeout, num_passes):
                print(f"Starting workload run {workload}.")
                log_tail_length = 300

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
                    firesim_opts = f"-c {workload_path}/{config_runtime} -a {remote_cy_dir}/sims/firesim-staging/sample_config_hwdb.yaml -r {remote_cy_dir}/sims/firesim-staging/sample_config_build_recipes.yaml"
                    return run_line(f"{timeout_prefix} firesim {firesim_opts} {extra_args} {typ}", pty=False)

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
                run_firesim_cmd("terminaterunfarm", "-q")
                print_last_n_log()

                # using rc of runworkload
                if rc != 0:
                    raise Exception(f"Workload {workload} failed with code: {rc}")
                else:
                    print(f"Workload run {workload} successful. Checking workload files...")

                    def check(match_key, file_name = 'uartlog'):
                        out_count = search_match_in_last_workloads_output_file(file_name, match_key)
                        assert out_count == num_passes, f"Workload {file_name} files are malformed: '{match_key}' found {out_count} times (!= {num_passes}). Something went wrong."

                    # first driver completed successfully
                    check('*** PASSED ***')

                    # verify login was reached (i.e. linux booted)
                    check('running /etc/init.d/S99run')

                    # verify reaching poweroff
                    check('Power down')

                    print(f"Workload run {workload} successful.")

            run_w_timeout(f"{remote_cy_dir}/deploy/workloads/ci/{args.platform}", "config_runtime_rocket_singlecore.yaml", "linux-poweroff-singlenode-rocketsinglecore", "30m", 1)
            run_w_timeout(f"{remote_cy_dir}/deploy/workloads/ci/{args.platform}", "config_runtime_rocket_quadcore.yaml", "linux-poweroff-singlenode-rocketquadcore", "30m", 1)
            run_w_timeout(f"{remote_cy_dir}/deploy/workloads/ci/{args.platform}", "config_runtime_boom_singlecore.yaml", "linux-poweroff-singlenode-boomsinglecore", "30m", 1)

if __name__ == "__main__":
    execute(run_linux_poweroff, hosts=["localhost"])
