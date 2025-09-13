# Setup instruction for chipyard and hammer for the CAD's servers
This is a guide for using the designs in the chipyard framework and synthesize them using a custom library.

We DO NOT CARE the PAR (Place and Route) since it is not the focus of our research. Thus, those flows may not work (we try our best, as always).

In order to setup the environment for running chipyard (simulation and synthesis) you need to obseerve the following steps:

1. Download the hammer repository 
    ```bash 
    $ git clone git@github.com:cad-polito-it/hammer.git
    $ cd hammer 
    $ git checkout working/cad_servers
    ```
2. Install conda environment 
    ```bash 
    wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh
    bash Miniconda3-latest-Linux-x86_64.sh  
    rm -f Miniconda3-latest-Linux-x86_64.sh  
    ``` 
    You may need to export the binaries path (you can add the following line in your ``.bashrc``)
    ```bash
    export PATH=${PATH}:${HOME}/miniconda3/bin/                                    
    ```
3. Download the chipyard repository  
    ```bash 
    $ git clone git@github.com:cad-polito-it/chipyard.git
    ```

4. Install the neeeded tools for chipard by running 
    ```bash 
    $ cd chipyard 
    $ ./scripts/build-setup.sh riscv-tools -s 6 -s 7 -s 8 -s 9 
    ```
    You do not need all the tools, just the basic ones and converters from chisel to verilog.

    **It may take a while** 

    **Now you can activate your conda environment by sourcing the env.sh file**

5. Install the necessary plugins for hammer 
    ```bash 
    $ ./scripts/init-vlsi.sh synopsys
    $ ./vlsi/install_plugins.sh <path to you hammer download dir>
    ```
    This set the environemnt pointing to the python sourcee code of hammer and its synopsys plugin (in case you need to modify them)

6. Download the OpenRoad repository for generating technology-related rams 
    ```bash 
    cd ~
    git clone --recursive https://github.com/The-OpenROAD-Project/OpenROAD.git 
    echo "export OPENROAD=~/OpenROAD" >> ~/chipyard/env.sh
    ```
    **Note the APPEND to the ``env.sh`` file !**

    **This is a hard hack for the moment**

    Install the prebuilt openroad package:
   ```bash
    conda create -c litex-hub --prefix ~/.conda-openroad openroad=2.0_7070_g0264023b6
    echo "export PATH=${PATH}:~/.conda-openroad/bin" >> ~/chipyard/env.sh
   ```
Please also refer to:
-   [Hammer Docs](https://hammer-vlsi.readthedocs.io/)
-   [Chipyard setup docs](https://chipyard.readthedocs.io/en/latest/Chipyard-Basics/Initial-Repo-Setup.html)
-   [Chipyard docs](https://chipyard.readthedocs.io/en/latest/index.html)

# Compile the tests
For compiling a set of hello world applications:

```bash /home/f.angione/chipyard/vlsi/generated-src/chipyard.harness.TestHarnes
$ cd tests
$ cmake .
$ make 
```


## Compiling additional tests (SBSTs)
For compiling custom SBSTs you can:
- Add an additional folder named sbst1
- Add in the ``tests/CMakeLists.txt`` the libe ``add_subdirectory(sbst1)``
- Add/Modify the ``tests/sbst1/CMakeLists.txt`` accordingly.
- Add/Modify source files.

You can see an example in the ``tests/sbst`` folder.

For example:
```bash 
$ cd tests
$ cmake .
$ make sbst1
```
You will find the executable in ``tests/sbst1/`` named ``sbst1.riscv``

# Simulating the RTL 
For running a simulation for a given configuration in variables.mk and a specified program (BINARY var points to the compiled program) from tests folder:
```bash 
$ cd sims/vcs
$ make SUB_PROJECT=chipyard_smallboom
$ make run-binary BINARY=../../tests/hello.riscv SUB_PROJECT=chipyard_smallboom
```
It generates the verilog file and run the binary 

# Synthesis 

For generating the files for the synthesis using the technology and deesign files defined in tutorial.mk file:
```bash 
$ cd vlsi
$ make buildfile tutorial=nangate45-commercial
```

For running the synthesis:
```bash 
$ cd vlsi
$ make syn tutorial=nangate45-commercial
```
For modifying the synthesis script see ```chipyard/vlsi/hammer-synopsys-plugins/hammer/synthesis/dc/__init__.py ``

# Simulating the Gate-level
TODO WIP
TODO FIND THE CORRECT SIMULATED RAMS 
