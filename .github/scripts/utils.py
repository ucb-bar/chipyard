from fabric.api import run, settings # type: ignore
from enum import Enum
import argparse
import os
from ci_variables import remote_fsim_dir

def search_match_in_last_workloads_output_file(file_name: str = "uartlog", match_key: str = "*** PASSED ***") -> int:
    # if grep doesn't find any results, this command will fail
    out = run(f"""cd deploy/results-workload/ && LAST_DIR=$(ls | tail -n1) && if [ -d "$LAST_DIR" ]; then grep -an "{match_key}" $LAST_DIR/*/{file_name}; fi""")
    out_split = [e for e in out.split('\n') if match_key in e]
    out_count = len(out_split)
    print(f"Found {out_count} '{match_key}' strings in {file_name}")
    return out_count

class FpgaPlatform(Enum):
    vitis = 'vitis'
    xilinx_alveo_u250 = 'xilinx_alveo_u250'

    def __str__(self):
        return self.value

def create_args():
    parser = argparse.ArgumentParser(description='')
    parser.add_argument('--platform', type=FpgaPlatform, choices=list(FpgaPlatform), required=True)
    args = parser.parse_args()
    return args

def setup_shell_env_vars():
    # if the following env. vars exist, then propagate to fabric subprocess
    shell_env_vars = {
        "TEST_DISABLE_VERILATOR",
        "TEST_DISABLE_VIVADO",
        "TEST_DISABLE_BENCHMARKS",
    }
    export_shell_env_vars = set()
    for v in shell_env_vars:
        if v in os.environ:
            export_shell_env_vars.add(f"{v}={os.environ[v]}")

    return ("export " + " ".join(export_shell_env_vars)) if export_shell_env_vars else "true"

def run_warn_only(*args, **kwargs):
    rc = 0
    with settings(warn_only=True):
        # pty=False needed to avoid issues with screen -ls stalling in fabric
        rc = run(*args, **kwargs).return_code
    return rc

def print_last_firesim_log(log_lines = 300):
    print(f"Printing last {log_lines} lines of most recent log.")
    run(f"""cd {remote_fsim_dir}/deploy/logs && LAST_LOG=$(ls | tail -n1) && if [ -f "$LAST_LOG" ]; then tail -n{log_lines} $LAST_LOG; fi""")

def print_last_firesim_workload(log_lines = 300):
    workload_path = f"{remote_fsim_dir}/deploy/results-workload"
    print(f"Printing last {log_lines} lines of all output files. See {workload_path} for more info.")
    run(f"""cd {workload_path} && LAST_DIR=$(ls | tail -n1) && if [ -d "$LAST_DIR" ]; then tail -n{log_lines} $LAST_DIR/*/*; fi""")
