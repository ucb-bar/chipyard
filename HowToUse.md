How to use this branch?
=======================

This branch just for zcu102 fpga.

Follow the steps below to use this branch:

```bash
# 1. Install the docker engine on your ubuntu host.
# 2. Docker pull the docker image, which has all the necessary tools and dependencies installed.
docker pull jerryy959/chipyard-zcu:v1.13.0
# 3. Run the docker container with one shared directory for building the vivado project on your host.
docker run -itd -v /workspace:/jerry jeffery959/chipyard-zcu:v1.13.0 /bin/bash
# 4. Create the working directory.
mkdir -p /workspace
# 5. Attach to the running container.
docker attach <container_id>
# 6. Pull the newest code from the chipyard repo in the container.
cd /workspace/chipyard && git checkout . && git remote set-url origin git@github.com:Jerryy959/chipyard.git && git pull origin zcu102
# 7. Build the chipyard project in the container.
cd /workspace/chipyard/fpga && source ../env.sh && make clean && make SUB_PROJECT=zcu102 verilog
# 8. Copy the whole project to the shared directory which is </jerry>.
cp -rf /workspace/chipyard /jerry
# 9. Open a new terminal on your host and rebuild the project, just for test.
cd /workapce/chipyard/fpga && source ../env.sh && make clean && make SUB_PROJECT=zcu102 vivado
# 10. Source the vivado settings64.sh.
source /tools/Xilinx/Vivado/2022.2/settings64.sh
# 11. Rebuild the project to generate the bitstream.
cd /workapce/chipyard/fpga && source ../env.sh && make clean && make SUB_PROJECT=zcu102 bitstream
# 12. Then flash the bitstream to the zcu102 board.
```