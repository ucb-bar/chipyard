#!/usr/bin/python

import re, os 
import string
import sys
import array

if len(sys.argv) != 2:
  sys.exit("Must specify trace file (.gz)")


gztrace_filename = sys.argv[1]
tracefile_filename = sys.argv[1][0:len(sys.argv[1])-3]
outfile = open(tracefile_filename,"w")
temp_trace = tracefile_filename + ".temp"

zcat_cmd = "zcat";
# accomodate OSX
if os.uname()[0] == "Darwin":
	print "Detected OSX, using gzcat..."
	zcat_cmd = "gzcat"

if not os.path.exists(gztrace_filename):
  print "Could not find gzipped tracefile either"
  quit()
else:
  print "Unzipping gz trace...",
  os.system("%s %s > %s" % (zcat_cmd, gztrace_filename, temp_trace))
if not os.path.exists(tracefile_filename):
  print "FAILED"
  quit()
else:
  print "OK"

print "Parsing ",
tracefile = open(temp_trace,"r")

if tracefile_filename.startswith("k6"):
  print "k6 trace ..."
  linePattern = re.compile(r'(0x[0-9A-F]+)\s+([A-Z_]+)\s+([0-9.,]+)\s+(.*)')
  for line in tracefile:
    searchResult = linePattern.search(line)
    if searchResult:
        (address,command,time,units) = searchResult.groups()

        length = len(time)
        time = time[0:length-5]
        temp = len(time)
        if temp==0:
            time = "0"
        time = string.replace(time,",","")
        time = string.replace(time,".","")
        if command != "BOFF" and command != "P_INT_ACK":
            outfile.write("%s %s %s\n" % (address,command,time)) 
            
elif tracefile_filename.startswith("mase"):
  print "mase trace ...",
  os.system("cp %s %s"%(temp_trace, tracefile_filename));
  print "OK"
      
else:
  print "Unknown trace file!!!"
  quit()

os.system("rm %s" % temp_trace);
