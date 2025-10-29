#!/usr/bin/python 
"""

This script generates a series of commands to do parameter sweeps. One way to
use this script is to generate a big matrix of configurations and then run them
on different binaries to diff the output.  This can be used as a poor man's
regression test when doing code cleanups (i.e. where functionality is not
supposed to change as a result of a commit.

Or, if you just set a single binary and comment out the diff stuff at the
bottom, it is just a convenient way to do parameter sweeps. 

Since this uses the command line overrides (-o flag), it needs a fairly recent
commit of DRAMSim2
( see: https://github.com/dramninjasUMD/DRAMSim2/commit/e46f525bd274a0b3312002dce3efe83c769ea2ce )

Just redirect the output of this command to a file and then run it in bash. 

"""

import itertools 

parameters = {'QUEUING_STRUCTURE': ['per_rank', 'per_rank_per_bank'],
					'ROW_BUFFER_POLICY': ['open_page', 'close_page'],
					'SCHEDULING_POLICY': ['rank_then_bank_round_robin','bank_then_rank_round_robin']
					}

devices = ['DDR3_micron_64M_8B_x4_sg15.ini', 'DDR2_micron_32M_4B_x4_sg3E.ini'];

traces = ['k6_bsc_vector1.trc', 'k6_video_tracking_128kL2_trace.trc',  'k6_aoe_02_short.trc']
binaries = ['DRAMSim.master', 'DRAMSim.cleanup']

dramsim_flags = '-c 2000000 -n -S 8192 -q '

# get the parameter permutations

master_list = []
for k,v in parameters.iteritems():
#	print v
	master_list.append(v)
	
paramOverrideList=[]
for i in itertools.product(*master_list):
	tmp=[]
	for j,param in enumerate(i):
		tmp.append("%s=%s"%(parameters.keys()[j],param))
	paramOverrideList.append(",".join(tmp))
#print paramOverrideList

print "#!/bin/bash"
print "rm DRAMSim.*.vis"
i=0
for trace in traces: 
	for device in devices:
		for paramOverrides in paramOverrideList:
			for executable in binaries:
				output_file = "%s_%d"%(executable, i)
				print "./%s -s system.ini -d ini/%s -t traces/%s -o %s %s -v %s &"%(executable, device, trace, paramOverrides, dramsim_flags, output_file)
			i+=1

print "echo -n waiting"
print "wait"
print "echo OK"
print "echo Starting diff phase"
for x in range(i):
	diff_args="%s_%d.vis %s_%d.vis"%(binaries[0],x,binaries[1],x)
	print "echo %s_%d.vis and %s_%d.vis:"%(binaries[0],x,binaries[1],x)
	print "is_different=`diff -q %s`"%(diff_args)
	print "if [ -n \"$is_different\" ] ; then"	
	print "diff -u %s"%(diff_args); 
	print "fi"
