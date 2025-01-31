#!/bin/bash

# Define file names and corresponding n values
fsdbs=(
    "output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.volta.dim256.fsdb"
    "output/chipyard.harness.TestHarness.VirgoFP16Config/kernel.radiance.gemm.tcore.ampere.dim256.fsdb"
    "output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.tcore.hopper.dim256.fsdb"
    "output/chipyard.harness.TestHarness.VirgoHopperConfig/kernel.radiance.gemm.virgo.hopper.dim256.fsdb"
)

for fsdb_file in "${fsdbs[@]}"; do

    echo "parsing sharedmem reads for file $fsdb_file"

    # Run fsdbreport command
    fsdbreport "$fsdb_file" -s "/TestDriver/testHarness/chiptop0/system/cluster_prci_domain/element_reset_domain_element/shared_mem/smemReadCounter" -of d -nolog -o /tmp/smem_activity.log 2>&1 > /dev/null

    # Extract last line and parse the second number
    last_line=$(tail -n 1 /tmp/smem_activity.log)
    reads=$(echo "$last_line" | awk '{print $2}')

    # Clean up temp file
    rm -f /tmp/smem_activity.log

    echo "number of 4-byte reads: $reads"

    # Calculate final value
    result=$(echo "scale=3; $reads * 4 / 1048576" | bc)

    echo -e "total read data in MiB: $result\n"
done

