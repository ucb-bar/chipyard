set_config -global_max_jobs 16 
set_config -fsim_std_args "-fsim=limit+hyperactive+0"

## DYNAMIC RUNTIME - Do not modify this! The __init__.py checks for the args string to parse the required arguments
create_testcases -name {"test1"} \
    -exec ./simv \
    -args ""

# Start fault simulation
fsim -verbose 

# Write results report
report -campaign  chiptop0 -report /home/n.digruttolagiardino/benchmarks/chipyard/vlsi/build-nangate45-commercial-rocket/chipyard.harness.TestHarness.RocketConfig-ChipTop/fsim-syn-rundir/RocketConfig/saf/hello/fsim_out.rpt -overwrite
report -campaign  chiptop0 -report /home/n.digruttolagiardino/benchmarks/chipyard/vlsi/build-nangate45-commercial-rocket/chipyard.harness.TestHarness.RocketConfig-ChipTop/fsim-syn-rundir/RocketConfig/saf/hello/fsim_out_hier.rpt -overwrite -hierarchical 100
