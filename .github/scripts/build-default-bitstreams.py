#!/usr/bin/env python3

from pathlib import Path
from fabric.api import prefix, run, settings, execute # type: ignore

import fabric_cfg
from ci_variables import ci_env, remote_fsim_dir, remote_cy_dir
from github_common import upload_binary_file
from utils import print_last_firesim_log

from typing import List, Tuple

GH_REPO = 'firesim-public-bitstreams'
GH_ORG = 'firesim'
URL_PREFIX = f"https://raw.githubusercontent.com/{GH_ORG}/{GH_REPO}"

shared_build_dir = "/scratch/buildbot/FIRESIM_BUILD_DIR"

from_chipyard_firesim_build_recipes = "sims/firesim-staging/sample_config_build_recipes.yaml"
from_chipyard_firesim_hwdb = ci_env['CHIPYARD_HWDB_PATH']

sample_hwdb_filename = f"{remote_fsim_dir}/deploy/sample-backup-configs/sample_config_hwdb.yaml"

def run_local_buildbitstreams():
    """Runs local buildbitstreams"""

    # assumptions:
    #   - machine-launch-script requirements are already installed
    #   - XILINX_VITIS, XILINX_XRT, XILINX_VIVADO are setup (in env / LD_LIBRARY_PATH / path / etc)

    # repo should already be checked out

    with prefix(f"cd {remote_fsim_dir}"):

        with prefix('source sourceme-manager.sh --skip-ssh-setup'):

            # return a copy of config_build.yaml w/ hwdb entry(s) uncommented + new build dir
            def modify_config_build(hwdb_entries_to_gen: List[str]) -> str:
                build_yaml = f"{remote_fsim_dir}/deploy/config_build.yaml"
                copy_build_yaml = f"{remote_fsim_dir}/deploy/config_build_{hash(tuple(hwdb_entries_to_gen))}.yaml"

                # comment out old lines
                build_yaml_lines = open(build_yaml).read().split("\n")
                with open(copy_build_yaml, "w") as byf:
                    for line in build_yaml_lines:
                        if "- midas" in line:
                            # comment out midasexample lines
                            byf.write("# " + line + '\n')
                        elif 'default_build_dir:' in line:
                            byf.write(line.replace('null', shared_build_dir) + '\n')
                        else:
                            byf.write(line + '\n')

                # add new builds to run
                build_yaml_lines = open(copy_build_yaml).read().split("\n")
                with open(copy_build_yaml, "w") as byf:
                    for line in build_yaml_lines:
                        if "builds_to_run:" in line and not "#" in line:
                            byf.write(line + '\n')
                            start_space_idx = line.index('b')
                            for hwdb_to_gen in hwdb_entries_to_gen:
                                    byf.write((' ' * (start_space_idx + 4)) + f"- {hwdb_to_gen}" + '\n')
                        else:
                            byf.write(line + '\n')

                return copy_build_yaml

            def add_host_list(build_yaml: str, hostlist: List[Tuple[str, bool, str]]) -> str:
                copy_build_yaml = f"{remote_fsim_dir}/deploy/config_build_{hash(tuple(hostlist))}.yaml"
                build_yaml_lines = open(build_yaml).read().split("\n")
                with open(copy_build_yaml, "w") as byf:
                    for line in build_yaml_lines:
                        if "build_farm_hosts:" in line and not "#" in line:
                            byf.write(line + '\n')
                            start_space_idx = line.index('b')
                            for host, use_unique, unique_build_dir in hostlist:
                                if use_unique:
                                    byf.write((' ' * (start_space_idx + 4)) + f"- {host}:" + '\n')
                                    byf.write((' ' * (start_space_idx + 8)) + f"override_build_dir: {unique_build_dir}" + '\n')
                                else:
                                    byf.write((' ' * (start_space_idx + 4)) + f"- {host}" + '\n')
                        elif '- localhost' in line and not '#' in line:
                            byf.write("# " + line + '\n')
                        else:
                            byf.write(line + '\n')
                return copy_build_yaml

            def build_upload(build_yaml: str, hwdb_entries: List[str], platforms: List[str]) -> List[str]:

                print(f"Printing {build_yaml}...")
                run(f"cat {build_yaml}")

                rc = 0
                with settings(warn_only=True):
                    # pty=False needed to avoid issues with screen -ls stalling in fabric
                    build_result = run(f"timeout 10h firesim buildbitstream -b {build_yaml} -r {remote_cy_dir}/{from_chipyard_firesim_build_recipes} --forceterminate", pty=False)
                    rc = build_result.return_code

                if rc != 0:
                    print(f"Buildbitstream failed.")
                    print_last_firesim_log(200)
                    raise Exception(f"Failed with code: {rc}")

                hwdb_entry_dir = f"{remote_fsim_dir}/deploy/built-hwdb-entries"
                links = []

                for hwdb_entry_name, platform in zip(hwdb_entries, platforms):
                    hwdb_entry = f"{hwdb_entry_dir}/{hwdb_entry_name}"

                    print(f"Printing {hwdb_entry}...")
                    run(f"cat {hwdb_entry}")

                    with open(hwdb_entry, 'r') as hwdbef:
                        lines = hwdbef.readlines()
                        for line in lines:
                            if "bitstream_tar:" in line:
                                file_path = Path(line.strip().split(' ')[1].replace('file://', '')) # 2nd element (i.e. the path) (no URI)
                                file_name = f"{platform}/{hwdb_entry_name}.tar.gz"
                                run(f"shasum -a 256 {file_path}")
                                sha = upload_binary_file(file_path, file_name)
                                link = f"{URL_PREFIX}/{sha}/{file_name}"
                                print(f"Uploaded bitstream_tar for {hwdb_entry_name} to {link}")
                                links.append(link)
                                break

                return links


            def replace_in_hwdb(hwdb_entry_name: str, link: str) -> None:
                # replace the sample hwdb's bit line only
                sample_hwdb_lines = open(sample_hwdb_filename).read().split('\n')

                with open(sample_hwdb_filename, "w") as sample_hwdb_file:
                    match_bit = False
                    for line in sample_hwdb_lines:
                        if hwdb_entry_name in line.strip().split(' ')[0].replace(':', ''):
                            # hwdb entry matches key name
                            match_bit = True
                            sample_hwdb_file.write(line + '\n')
                        elif match_bit == True:
                            if ("bitstream_tar:" in line.strip().split(' ')[0]):
                                # only replace this bit
                                match_bit = False

                                new_bit_line = f"    bitstream_tar: {link}"
                                print(f"Replacing {line.strip()} with {new_bit_line}")

                                # print out the bit line
                                sample_hwdb_file.write(new_bit_line + '\n')
                            else:
                                raise Exception("::ERROR:: Something went wrong")
                        else:
                            # if no match print other lines
                            sample_hwdb_file.write(line + '\n')

                    if match_bit == True:
                        raise Exception(f"::ERROR:: Unable to replace URL for {hwdb_entry_name} in {sample_hwdb_filename}")

                # strip newlines from end of file
                with open(sample_hwdb_filename, "r+") as sample_hwdb_file:
                    content = sample_hwdb_file.read()
                    content = content.rstrip('\n')
                    sample_hwdb_file.seek(0)

                    sample_hwdb_file.write(content)
                    sample_hwdb_file.truncate()

            # priority == roughly the more powerful and available
            # ipaddr, buildtool:version, use unique build dir, unique build dir path, priority (0 is highest)(unused by code but used to track which machine has most resources)
            hosts = [
                (    "localhost",  "vivado:2022.1", False, "", 0),
                ("buildbot1@as4",  "vivado:2022.1",  True, "/scratch/buildbot1/FIRESIM_BUILD_DIR", 0),
                ("buildbot2@as4",  "vivado:2022.1",  True, "/scratch/buildbot2/FIRESIM_BUILD_DIR", 0),
                (          "a17",   "vitis:2022.1", False, "", 0),
                ("buildbot1@a17",   "vitis:2022.1",  True, "/scratch/buildbot1/FIRESIM_BUILD_DIR", 0),
                ("buildbot2@a17",   "vitis:2021.1",  True, "/scratch/buildbot2/FIRESIM_BUILD_DIR", 0),
                ("buildbot3@a17",   "vitis:2021.1",  True, "/scratch/buildbot3/FIRESIM_BUILD_DIR", 0),
                ("buildbot4@a17",   "vitis:2021.1",  True, "/scratch/buildbot4/FIRESIM_BUILD_DIR", 0),
                (     "firesim1",   "vitis:2021.1", False, "", 1),
                (        "jktgz",  "vivado:2023.1", False, "", 2),
                (       "jktqos",  "vivado:2023.1", False, "", 2),
            ]

            def do_builds(batch_hwdbs):
                assert len(hosts) >= len(batch_hwdbs), f"Need at least {len(batch_hwdbs)} hosts to run builds"

                # map hwdb tuple to hosts
                hwdb_2_host = {}
                for hwdb, platform, buildtool_version in batch_hwdbs:
                    for host_name, host_buildtool_version, host_use_unique, host_unique_build_dir, host_prio in hosts:
                        if host_buildtool_version == buildtool_version:
                            if not host_name in [h[0] for h in hwdb_2_host.values()]:
                                hwdb_2_host[hwdb] = (host_name, host_use_unique, host_unique_build_dir)
                                break

                assert len(hwdb_2_host) == len(batch_hwdbs), "Unable to map hosts to hwdb build"

                hwdbs_ordered = [hwdb[0] for hwdb in batch_hwdbs]
                platforms_ordered = [hwdb[1] for hwdb in batch_hwdbs]
                hosts_ordered = hwdb_2_host.values()

                print("Mappings")
                print(f"HWDBS: {hwdbs_ordered}")
                print(f"Platforms: {platforms_ordered}")
                print(f"Hosts: {hosts_ordered}")

                copy_build_yaml = modify_config_build(hwdbs_ordered)
                copy_build_yaml_2 = add_host_list(copy_build_yaml, hosts_ordered)
                links = build_upload(copy_build_yaml_2, hwdbs_ordered, platforms_ordered)
                for hwdb, link in zip(hwdbs_ordered, links):
                    replace_in_hwdb(hwdb, link)

                # wipe old data
                for host_name, host_use_unique, host_unique_build_dir in hosts_ordered:
                    if host_use_unique:
                        run(f"ssh {host_name} rm -rf {host_unique_build_dir}")
                    else:
                        run(f"ssh {host_name} rm -rf {shared_build_dir}")

            # note: next two statements can be duplicated to run different builds in phases
            # i.e. run 4 agfis in 1st phase, then 6 in next

            # order of following list roughly corresponds to build host to use.
            # i.e. if 1st hwdb in list wants a host with V0 of tools, it will get the 1st host with V0 of tools
            # in the hosts list

            # hwdb_entry_name, platform_name, buildtool:version
            batch_hwdbs_in = [
                ## hwdb's to verify FPGA builds
                #("vitis_firesim_rocket_singlecore_no_nic", "vitis", "vitis:2022.1"),
                ("nitefury_firesim_rocket_singlecore_no_nic", "rhsresearch_nitefury_ii", "vitis:2022.1"),
                #("alveo_u200_firesim_rocket_singlecore_no_nic", "xilinx_alveo_u200", "vitis:2021.1"),
                #("alveo_u250_firesim_rocket_singlecore_no_nic", "xilinx_alveo_u250", "vitis:2021.1"),
                #("alveo_u280_firesim_rocket_singlecore_no_nic", "xilinx_alveo_u280", "vitis:2021.1"),
                ## TODO: disable due to not having a license
                ##("xilinx_vcu118_firesim_rocket_singlecore_4GB_no_nic", "xilinx_vcu118", "vivado:2023.1"),

                ## extra hwdb's to run CI with
                #("alveo_u250_firesim_rocket_quadcore_no_nic", "xilinx_alveo_u250", "vivado:2022.1"),
                #("alveo_u250_firesim_boom_singlecore_no_nic", "xilinx_alveo_u250", "vivado:2022.1"),
                #("alveo_u250_firesim_rocket_singlecore_nic", "xilinx_alveo_u250", "vivado:2022.1"),

                ## TODO: disable gemmini builds until target runs under chisel6
                ### extra hwdb's
                ##("alveo_u250_firesim_gemmini_rocket_singlecore_no_nic", "xilinx_alveo_u250", "vitis:2021.1"),
            ]

            do_builds(batch_hwdbs_in)

            print(f"Printing {sample_hwdb_filename}...")
            run(f"cat {sample_hwdb_filename}")

            # copy back to workspace area so you can PR it
            run(f"cp -f {sample_hwdb_filename} {ci_env['GITHUB_WORKSPACE']}/{from_chipyard_firesim_hwdb}")

if __name__ == "__main__":
    execute(run_local_buildbitstreams, hosts=["localhost"])
