.. _hammer_basic_flow:

Using Hammer To Place and Route a Custom Block
=================================================

.. IMPORTANT:: In order to use the Hammer VLSI flow, you need access to Hammer tools and technology plugins. You can obtain these by emailing hammer-plugins-access@lists.berkeley.edu with a request for which plugin(s) you would like access to. 

Initialize the Hammer Plug-ins
----------------------------------
In the Chipyard root, run:

.. code-block:: shell

    ./scripts/init-vlsi.sh <tech-plugin-name>
    
This will pull the Hammer & CAD tool plugin submodules, assuming the technology plugins are available on github.
Currently only the asap7 technology plugin is available on github.
If you have additional private technology plugins (this is a typical use-case for proprietry process technologies with require NDAs and secure servers), you can clone them directly
into VLSI directory with the name ``hammer-<tech-plugin-name>-plugin``.
For example, for an imaginary process technology called tsmintel3:

.. code-block:: shell

    cd vlsi
    git@my-secure-server.berkeley.edu:tsmintel3/hammer-tsmintel3-plugin.git


Next, we define the Hammer environment into the shell:

.. code-block:: shell

    cd vlsi    # (if you haven't done so yet)
    export HAMMER_HOME=$PWD/hammer
    source $HAMMER_HOME/sourceme.sh


Setting up the Hammer Configuration Files
--------------------------------------------

The first configuration files that needs to be set up is the Hammer environment configuration files ``env.yml``. In this file you need to set the paths to the EDA tools and license servers you will be using. You do not have to fill all the fields in this configuration file, you need to fill the paths only for the tools that you will be using.
If you are working within a shared server farm environment with an LSF cluster setup (for example, the Berkeley Wireless Research Center), please note the additional possible environment configuration listed in the :ref:`Advanced Environment Setup` segment of this documentation page. 

Hammer relies on YAML-based configuration files. While these configuration can be consolidated within a single files (as is the case in the ASAP7 tutorial :ref:`tutorial` and the ``nangate45``
OpenRoad example), the generally applicable way to work with an arbitrary process technology or tools plugins would be to use three configuration files, matching the three Hammer concerns - tools, tech, and design. 
The ``vlsi`` directory includes three such example configuration files matching the three concerns: ``example-tools.yml``, ``example-tech.yml``, and ``example-design.yml``.

The ``example-tools.yml`` file configures which EDA tools hammer will use. This example files uses Cadence Innovus, Genus and Calibre (which are likely the tools you will use if you're working in the Berkeley Wireless Research Center).

The ``example-design.yml`` file contrain basic build system information (how many cores/threads to use, etc.), as well as configuration that are specific to the design we are working on such as clock signal, power modes, and additional contraints that we will add later on.

Finally, the ``example-tech`` file is a template file for a process technology plugin configuration. We will copy this file, and replace its fields with the appropriate process technology details for the tech plugin that we have access to. For example, for the ``asap7`` tech plugin we will replace the <tech_name> field with "asap7", the Node size "N" with "7", and the path to the process technology files installation directory.

We recommend copying these example configuration files and customizing them with a different name, so you can have different configuration files for different process technologies and designs.


Building the Design
---------------------
After we have set the configuration files, we will now elaborate our Chipyard Chisel design into Verilog, while also performing the required transformations in order to make the Verilog VLSI-friendly.
Additionally, we will automatically generate another set of Hammer configuration files matching to this design, which will be used in order to configure the physical design tools.
We will do so by calling ``make buildfile`` with appropriate Chipyard configuration variables and Hammer configuration files.
As in the rest of the Chipyard flows, we specify our SoC configuration using the ``CONFIG`` make variable. 
However, unlike the rest of the Chipyard flows, in the case of physical design we might be interested in working in a hierarchical fashion and therefore we would like to work on a single module.
Therefore, we can also specify a ``VLSI_TOP`` make variable with the same of a specific Verilog module (which should also match the name of the equivalent Chisel module) which we would like to work on.
The makefile will automatically call tools such as Barstools and the MacroCopmiler (:ref:`barstools`) in order to make the generated Verilog more VLSI friendly. 
By default, the MacroCopmiler will attempt to map memories into the SRAM options within the Hammer technology plugin. However, if you are wokring with a new process technology are prefer to work with flipflop arrays, you can configure the MacroCompiler using the ``MACROCOMPILER_MODE`` make variable. For example, the ASAP7 process technology does not have associated SRAMs, and therefore the ASAP7 Hammer tutorial (:ref:`tutorial`) uses the ``MACROCOMPILER_MODE='--mode synflops'`` option.

We call the ``make buildfile`` command while also specifying the name of the process technology we are working with (same ``tech_name`` for the configuration files and plugin name) and the configuration files we created. Note, in the ASAP7 tutorial ((:ref:`tutorial`)) these configuration files are merged into a single file called ``example-asap7.yml``.

Hence, if we want to monolitically place and route the entire SoC, the relevant command would be
.. code-block:: shell

    make buildfile CONFIG=<chipyard_config_name> tech_name=<tech_name> INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

In a more typical scenario of working on a single module, for example the Gemmini accelerator within the GemminiRocketConfig Chipyard SoC configuration, the relevant command would be
.. code-block:: shell

    make buildfile CONFIG=GemminiRocketConfig VLSI_TOP=Gemmini tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

Running the VLSI Flow
---------------------

Running a basic VLSI flow using the Hammer default configurations is fairly simple, and consists of simpele ``Make`` command with the previously mentioned Make variables.

Synthesis
^^^^^^^^^

In order to run synthesis, we run ``make syn`` with the matching Make variables. 
Post-synthesis logs and collateral will be saved in ``build/syn-rundir``. The raw QoR data wil be found in ``build/syn-rundir/reports``.

Hence, if we want to monolitically synthesize the entire SoC, the relevant command would be
.. code-block:: shell

    make syn CONFIG=<chipyard_config_name> tech_name=<tech_name> INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

In a more typical scenario of working on a single module, for example the Gemmini accelerator within the GemminiRocketConfig Chipyard SoC configuration, the relevant command would be
.. code-block:: shell

    make syn CONFIG=GemminiRocketConfig VLSI_TOP=Gemmini tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"



Customizing Your VLSI Flow in Hammer
----------------------------------------

Advanced Environment Setup
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you have access to a shared LSF cluster and you would like Hammer to submit it's compute-intensive jobs to the LSF cluster rather than your login machine, you can add the following code segment to your ``env.yml`` file (completing the relevant values for the bsub binary path, the number of CPUs requested, and the requested LSF queue):

.. code-block:: shell

    #submit command (use LSF)
    vlsi.submit:
        command: "lsf"
        settings: [{"lsf": {
            "bsub_binary": "</path/to/bsub/binary/bsub>",
            "num_cpus": <N>,
            "queue": "<lsf_queu>",
            "extra_args": ["-R", "span[hosts=1]"]
            }
        }]
        settings_meta: "append"


example-vlsi
^^^^^^^^^^^^
This is the entry script with placeholders for hooks. In the ``ExampleDriver`` class, a list of hooks is passed in the ``get_extra_par_hooks``. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods as shown in this example. Refer to the Hammer documentation on hooks for a detailed description of how these are injected into the VLSI flow.
