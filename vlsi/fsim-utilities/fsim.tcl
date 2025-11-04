set_config -global_max_jobs 16 
set_config -fsim_std_args "-fsim=limit+hyperactive+0"

## DYNAMIC RUNTIME - Do not modify this! The __init__.py checks for the args string to parse the required arguments
create_testcases -name {"test1"} \
    -exec ./simv \
    -args ""

# Start fault simulation
fsim -verbose 

# Write results report
report -campaign  chiptop0 -report fsim_out.rpt -overwrite -showfaultid
report -campaign  chiptop0 -report fsim_out_hier.rpt -overwrite -hierarchical 0
