#!/bin/bash

# Define file names and corresponding n values
declare -A files_and_n_values
files_and_n_values=(
    ["output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.volta.dim256.fsdb"]=256
    ["output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.volta.dim512.fsdb"]=512
    ["output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.volta.dim1024.fsdb"]=1024
    ["output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.ampere.dim256.fsdb"]=256
    ["output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.ampere.dim512.fsdb"]=512
    ["output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.ampere.dim1024.fsdb"]=1024
    ["output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.tcore.hopper.dim256.fsdb"]=256
    ["output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.tcore.hopper.dim512.fsdb"]=512
    ["output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.tcore.hopper.dim1024.fsdb"]=1024
    ["output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.virgo.hopper.dim256.fsdb"]=256
    ["output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.virgo.hopper.dim512.fsdb"]=512
    ["output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.virgo.hopper.dim1024.fsdb"]=1024
)

for fsdb_file in "${!files_and_n_values[@]}"; do
    n=${files_and_n_values[$fsdb_file]}

    echo "parsing sharedmem reads for file $fsdb_file"

    # Run fsdbreport command
    fsdbreport "$fsdb_file" -s "/TestDriver/testHarness/chiptop0/system/cluster_prci_domain/element_reset_domain_element/shared_mem/smemReadCounter" -of d -nolog -o /tmp/smem_activity.log

    # Extract last line and parse the second number
    last_line=$(tail -n 1 /tmp/smem_activity.log)
    reads=$(echo "$last_line" | awk '{print $2}')

    # Clean up temp file
    rm -f /tmp/smem_activity.log

    echo "reads: $reads"

    # Calculate final value
    result=$(echo "scale=6; $reads / ($n * $n / 64)" | bc)

    echo "multiple of input data size: $result"
done

