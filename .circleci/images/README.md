General
-------
This DockerFile contains the necessary steps to build a Docker container that can run
projects with riscv-tools, chisel3, firrtl, and verilator. It installs the necessary
apt-get packages and sets the environment variables needed in CircleCI.

Build and Deploy the Container
------------------------------

    sudo docker build . # to test build before building it with a tag
    sudo docker build -t <PATH_NAME>:tag . # to build with tag (ex. 0.0.3)
    sudo docker login # login into the account to push to
    sudo docker push <PATH_NAME>:tag # to push to repo with tag

Path Names
----------
Older docker images (when this Dockerfile was in `riscv-boom/riscv-boom`) can be found in the <PATH_NAME> `riscvboom/riscvboom-images`.
Current up-to-date images are located in <PATH_NAME> `ucbbar/chipyard-image`
