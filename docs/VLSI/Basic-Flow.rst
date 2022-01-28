.. _hammer_basic_flow:

Using Hammer To Place and Route a Custom Block
=================================================

.. IMPORTANT:: In order to use the Hammer VLSI flow, you need access to Hammer tools and technology plugins. You can obtain these by emailing hammer-plugins-access@lists.berkeley.edu with a request for which plugin(s) you would like access to. Make sure your email includes your github ID and proof (through affiliation or otherwise) that you have licensed access to relevant tools.

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
    git clone git@my-secure-server.berkeley.edu:tsmintel3/hammer-tsmintel3-plugin.git


Next, we define the Hammer environment into the shell:

.. code-block:: shell

    cd vlsi    # (if you haven't done so yet)
    export HAMMER_HOME=$PWD/hammer
    source $HAMMER_HOME/sourceme.sh


.. Note:: Some VLSI EDA tools are supported only on RHEL-based operating systems. We recommend using Chipyard on RHEL7 and above. However, many VLSI server still have old operating systems such as RHEL6, which have software packages older than the basic chipyard requirements. In order to build Chipyard on RHEL6, you will likely need to use tool packages such as devtoolset (for example, devtoolset-8) and/or build from source gcc, git, gmake, make, dtc, cc, bison, libexpat and liby.

Setting up the Hammer Configuration Files
--------------------------------------------

The first configuration file that needs to be set up is the Hammer environment configuration file ``env.yml``. In this file you need to set the paths to the EDA tools and license servers you will be using. You do not have to fill all the fields in this configuration file, you only need to fill in the paths for the tools that you will be using.
If you are working within a shared server farm environment with an LSF cluster setup (for example, the Berkeley Wireless Research Center), please note the additional possible environment configuration listed in the :ref:`VLSI/Basic-Flow:Advanced Environment Setup` segment of this documentation page.

Hammer relies on YAML-based configuration files. While these configuration can be consolidated within a single files (as is the case in the ASAP7 tutorial :ref:`tutorial` and the ``nangate45``
OpenRoad example), the generally suggested way to work with an arbitrary process technology or tools plugins would be to use three configuration files, matching the three Hammer concerns - tools, tech, and design.
The ``vlsi`` directory includes three such example configuration files matching the three concerns: ``example-tools.yml``, ``example-tech.yml``, and ``example-design.yml``.

The ``example-tools.yml`` file configures which EDA tools hammer will use. This example file uses Cadence Innovus, Genus and Voltus, Synopsys VCS, and Mentor Calibre (which are likely the tools you will use if you're working in the Berkeley Wireless Research Center). Note that tool versions are highly sensitive to the process-technology in-use. Hence, tool versions that work with one process technology may not work with another.

The ``example-design.yml`` file contains basic build system information (how many cores/threads to use, etc.), as well as configurations that are specific to the design we are working on such as clock signal name and frequency, power modes, floorplan, and additional constraints that we will add later on.

Finally, the ``example-tech.yml`` file is a template file for a process technology plugin configuration. We will copy this file, and replace its fields with the appropriate process technology details for the tech plugin that we have access to. For example, for the ``asap7`` tech plugin, we will replace the <tech_name> field with "asap7" and the path to the process technology files installation directory. The technology plugin (which for ASAP7 is within Hammer) will define the technology node and other parameters.

We recommend copying these example configuration files and customizing them with a different name, so you can have different configuration files for different process technologies and designs (e.g. create tech-tsmintel3.yml from example-tech.yml)


Building the Design
---------------------
After we have set the configuration files, we will now elaborate our Chipyard Chisel design into Verilog, while also performing the required transformations in order to make the Verilog VLSI-friendly.
Additionally, we will automatically generate another set of Hammer configuration files matching to this design, which will be used in order to configure the physical design tools.
We will do so by calling ``make buildfile`` with appropriate Chipyard configuration variables and Hammer configuration files.
As in the rest of the Chipyard flows, we specify our SoC configuration using the ``CONFIG`` make variable.
However, unlike the rest of the Chipyard flows, in the case of physical design we might be interested in working in a hierarchical fashion and therefore we would like to work on a single module.
Therefore, we can also specify a ``VLSI_TOP`` make variable with the same of a specific Verilog module (which should also match the name of the equivalent Chisel module) which we would like to work on.
The makefile will automatically call tools such as Barstools and the MacroCompiler (:ref:`Tools/Barstools:barstools`) in order to make the generated Verilog more VLSI friendly.
By default, the MacroCompiler will attempt to map memories into the SRAM options within the Hammer technology plugin. However, if you are working with a new process technology and prefer to work with flip-flop arrays, you can configure the MacroCompiler using the ``MACROCOMPILER_MODE`` make variable. For example, if your technology plugin does not have an SRAM compiler ready, you can use the ``MACROCOMPILER_MODE='--mode synflops'`` option (Note that synthesizing a design with only flipflops is very slow and will often may not meet constraints).

We call the ``make buildfile`` command while also specifying the name of the process technology we are working with (same ``tech_name`` for the configuration files and plugin name) and the configuration files we created. Note, in the ASAP7 tutorial ((:ref:`tutorial`)) these configuration files are merged into a single file called ``example-asap7.yml``.

Hence, if we want to monolithically place and route the entire SoC, the relevant command would be

.. code-block:: shell

    make buildfile CONFIG=<chipyard_config_name> tech_name=<tech_name> INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

In a more typical scenario of working on a single module, for example the Gemmini accelerator within the GemminiRocketConfig Chipyard SoC configuration, the relevant command would be:

.. code-block:: shell

    make buildfile CONFIG=GemminiRocketConfig VLSI_TOP=Gemmini tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

Running the VLSI Flow
---------------------

Running a basic VLSI flow using the Hammer default configurations is fairly simple, and consists of simple ``make`` command with the previously mentioned Make variables.

Synthesis
^^^^^^^^^

In order to run synthesis, we run ``make syn`` with the matching Make variables.
Post-synthesis logs and collateral will be saved in ``build/<config-name>/syn-rundir``. The raw QoR data (area, timing, gate counts, etc.) will be found in ``build/<config-name>/syn-rundir/reports``.

Hence, if we want to monolithically synthesize the entire SoC, the relevant command would be:

.. code-block:: shell

    make syn CONFIG=<chipyard_config_name> tech_name=<tech_name> INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

In a more typical scenario of working on a single module, for example the Gemmini accelerator within the GemminiRocketConfig Chipyard SoC configuration, the relevant command would be:

.. code-block:: shell

    make syn CONFIG=GemminiRocketConfig VLSI_TOP=Gemmini tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"


It is worth checking the final-qor.rpt report to make sure that the synthesized design meets timing before moving to the place-and-route step.

Place-and-Route
^^^^^^^^^^^^^^^
In order to run place-and-route, we run ``make par`` with the matching Make variables.
Post-PnR logs and collateral will be saved in ``build/<config-name>/par-rundir``. Specifically, the resulting GDSII file will be in that directory with the suffix ``*.gds``. and timing reports can be found in ``build/<config-name>/par-rundir/timingReports``.
Place-and-route is requires more design details in contrast to synthesis. For example, place-and-route requires some basic floorplanning constraints. The default ``example-design.yml`` configuration file template allows the tool (specifically, the Cadence Innovus tool) to use it's automatic floorplanning capability within the top level of the design (``ChipTop``). However, if we choose to place-and-route a specific block which is not the SoC top level, we need to change the top-level path name to match the ``VLSI_TOP`` make parameter we are using.

Hence, if we want to monolitically place-and-route the entire SoC with the default tech plug-in parameters for power-straps and corners, the relevant command would be:

.. code-block:: shell

    make par CONFIG=<chipyard_config_name> tech_name=<tech_name> INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

In a more typical scenario of working on a single module, for example the Gemmini accelerator within the GemminiRocketConfig Chipyard SoC configuration,

.. code-block:: shell

  vlsi.inputs.placement_constraints:
    - path: "Gemmini"
      type: toplevel
      x: 0
      y: 0
      width: 300
      height: 300
      margins:
        left: 0
        right: 0
        top: 0
        bottom: 0

The relevant ``make`` command would then be:

.. code-block:: shell

    make par CONFIG=GemminiRocketConfig VLSI_TOP=Gemmini tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

Note that the width and height specification can vary widely between different modulesi and level of the module hierarchy. Make sure to set sane width and height values.
Place-and-route generally requires more fine-grained input specifications regarding power nets, clock nets, pin assignments and floorplanning. While the template configuration files provide defaults for automatic tool defaults, these will usually result in very bad QoR, and therefore it is recommended to specify better-informed floorplans, pin assignments and power nets. For more information about cutomizing theses parameters, please refer to the :ref:`VLSI/Basic-Flow:Customizing Your VLSI Flow in Hammer` sections or to the Hammer documentation.
Additionally, some Hammer process technology plugins do not provide default values for required settings such as tool paths and pin assignments (for example, ASAP7). In those cases, these constraints will need to be specified manually in the top-level configuration yml files, as is the case in the ``example-asap7.yml`` configuration file.

Place-and-route tools are very sensitive to process technologes (significantly more sensitive than synthesis tools), and different process technologies may work only on specific tool versions. It is recommended to check what is the appropriate tool version for the specific process technology you are working with.


.. Note:: If you edit the yml configuration files in between synthesis and place-and-route, the ``make par`` command will automatically re-run synthesis. If you would like to avoid that and are confident that your configuration file changes do not affect synthesis results, you may use the ``make redo-par`` command instead with the variable ``HAMMER_EXTRA_ARGS='-p <your-changed.yml>'``.



Power Estimation
^^^^^^^^^^^^^^^^^^^^
Power estimation in Hammer can be performed in one of two stages: post-synthesis (post-syn) or post-place-and-route (post-par). The most accurate power estimation is post-par, and it includes finer grained details of the places instances and wire lengths.
Post-par power estimation can be based on static average signal toggles rates (also known as "static power estimation"), or based on simulation-extracted signal toggle data (also known as "dynamic power estimation").

.. Warning:: In order to run post-par power estimation, make sure that a power estimation tool (such as Cadence Voltus) has been defined in your ``example-tools.yml`` file. Make sure that the power estimation tool (for example, Cadence Voltus) version matches the physical design tool (for example, Cadence Innovus) version, otherwise you will encounter a database mismatch error.

Simulation-exacted power estimation often requires a dedicated testharness for the block under evalution (DUT). While the Hammer flow supports such configurations (further details can be found in the Hammer documentation), Chipyard's integrated flows support an automated full digital SoC simulation-extracted post-par power estimation through the integration of software RTL simulation flows with the Hammer VLSI flow. As such, full digital SoC simulation-extracted power estimation can be performed by specifying a simple binary executable with the associated ``make`` command.

.. code-block:: shell

    make power-par BINARY=/path/to/baremetal/binary/rv64ui-p-addi.riscv CONFIG=<chipyard_config_name> tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"


The simulation-extracted power estimation flow implicitly uses Hammer's gate-level simulation flow (in order to generate the ``saif`` activity data file). This gate-level simulation flow can also be run independantly from the power estimation flow using the ``make sim-par`` command.


.. Note:: The gate-level simulation flow (and there the simulation-extracted power-estimation) is currently integrated only with the Synopsys VCS simulation (Verilator does not support gate-level simulation. Support for Cadence Incisive is work-in-progress)


Signoff
^^^^^^^^^

During chip tapeout, you will need to perform sign-off check to make sure the generated GDSII can be fabricated as intended. This is done using dedicated signoff tools that perform design rule checking (DRC) and layout versus schematic (LVS) verification.
In most cases, placed-and-routed designs will not pass DRC and LVS on first attempts due to nuanced design rules and subtle/silent failures of the place-and-route tools. Passing DRC and LVS will often requires adding manual placement constraints to "force" the EDA tools into certain patterns.
If you have placed-and-routed a design with the goal of getting area and power estimates, DRC and LVS are not strictly neccessary and the results will likely be quite similar. If you are intending to tapeout and fabricate a chip, DRC and LVS are mandatory and will likely requires multiple-iterations of refining manual placement constraints.
Having a large number of DRC/LVS violations can have a significant impact on the runtime of the place-and-route procedure (since the tools will try to fix each of them several times). A large number of DRC/LVS violations may also be an indication that the design is not necessarily realistic for this particular process technology, which may have power/area implications.

Since signoff checks are required only for a complete chip tapeout, they are currently not fully automated in Hammer, and often require some additional manual inclusion of custom Makefiles associated with specific process technologies. However, the general steps from running signoff within Hammer (under the assumption of a fully automated tech plug-in) are Make commands similar to the previous steps.

In order to run DRC, the relevant ``make`` command is ``make drc``. As in the previous stages, the make command should be accompanied by the relevant configuration Make variables:

.. code-block:: shell

    make drc CONFIG=GemminiRocketConfig VLSI_TOP=Gemmini tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"


DRC does not emit easily audited reports, as the rule names violated can be quite esoteric. It is often more productive to rather use the scripts generated by Hammer to open the DRC error database within the appropriate tool. These generated scripts can be called from ``./build/<config-name>/drc-rundir/generated-scripts/view_drc``.


In order to run LVS, the relevant ``make`` command is ``make lvs``. As in the previous stages, the make command should be accompanied by the relevant configuration Make variables:

.. code-block:: shell

    make lvs CONFIG=GemminiRocketConfig VLSI_TOP=Gemmini tech_name=tsmintel3 INPUT_CONFS="example-design.yml example-tools.yml example-tech.yml"

LVS does not emit easily audited reports, as the violations are often cryptic when seen textually. As a result it is often more productive to visually see the LVS issues using the generated scripts that enable opening the LVS error database within the appropriate tool. These generated scripts can be called from ``./build/<config-name>/lvs-rundir/generated-scripts/view_lvs``.


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



Composing a Hierarchical Design
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For large designs, a monolithic VLSI flow may take the EDA tools a very long time to process and optimize, to the extent that it may not be feasable sometimes.
Hammer supports a hierarchical physical design flow, which decomposes the design into several specified sub-components and runs the flow on each sub-components separetly. Hammer is then able to assemble these blocks together into a top-level design. This hierarchical approach speeds up the VLSI flow for large designs, especially designs in which there may me multiple instantiations of the same sub-components(since the sub-component can simply be replicated in the layout).
While hierarchical physical design can be performed in multiple ways (top-down, bottom-up, abutment etc.), Hammer currently supports only the bottom-up approach.
The bottom-up approach traverses a tree representing the hierarchy starting from the leaves and towards the direction of the root (the "top level"), and runs the physical design flow on each node of the hierarchy tree using the previously layed-out children nodes.
As nodes get closer to the root (or "top level") of the hierarchy, largers sections of the design get layed-out.

The Hammer hierarchical flow relies on a manually-specified descrition of the desired heirarchy tree. The specification of the heirarchy tree is defined based on the instance names in the generated Verilog, which sometime make this specification challenging due to inconsisent instance names. Additionally, the specification of the heirarchy tree is intertwined with the manual specification of a floorplan for the design.

For example, if we choose to specifiy the previously mentioned ``GemminiRocketConfig`` configuration in a hierarchical fashion in which the Gemmini accelerator and the last-level cache are run separetly from the top-level SoC, we would replace the floorplan example in ``example-design.yml`` from the :ref:`VLSI/Basic-Flow:Place-and-Route` section with the following specification:

.. code-block:: shell

    vlsi.inputs.hiearchical.top_module: "ChipTop"
    vlsi.inputs.hierarchical.mode: manual"
    vlsi.inputs.manual_modules:
      - ChipTop:
        - RocketTile
        - InclusiveCache
      - RocketTile:
        - Gemmini
    vlsi.manual_placement_constraints:
      - ChipTop
        - path: "ChipTop"
          type: toplevel
          x: 0
          y: 0
          width: 500
          height: 500
          margins:
            left: 0
            right: 0
            top: 0
            bottom: 0
      - RocketTile
        - path: "chiptop.system.tile_prci_domain.tile"
          type: hierarchical
          master: ChipTop
          x: 0
          y: 0
          width: 250
          height: 250
          margins:
            left: 0
            right: 0
            top: 0
            bottom: 0
      - Gemmini
        - path: "chiptop.system.tile_prci_domain.tile.gemmini"
          type: hierarchical
          master: RocketTile
          x: 0
          y: 0
          width: 200
          height: 200
          margins:
            left: 0
            right: 0
            top: 0
            bottom: 0
      - InclusiveCache
        - path: "chiptop.system.subsystem_l2_wrapper.l2"
          type: hierarchical
          master: ChipTop
          x: 0
          y: 0
          width: 100
          height: 100
          margins:
            left: 0
            right: 0
            top: 0
            bottom: 0


In this specification, ``vlsi.inputs.hierarchical.mode`` indicates the manual specification of the heirarchy tree (which is the only mode currently supported by Hammer), ``vlsi.inputs.hiearchical.top_module`` sets the root of the hierarchical tree, ``vlsi.inputs.hierarchical.manual_modules`` enumerates the tree of hierarchical modules, and ``vlsi.inputs.hierarchical.manual_placement_constraints`` enumerates the floorplan for each module.


.. Specifying a Custom Floorplan
.. ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^


Customizing Generated Tcl Scripts
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The ``example-vlsi`` python script is the Hammer entry script with placeholders for hooks. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods as shown in the ``example-vlsi`` entry script example. In this particular example, a list of hooks is passed in the ``get_extra_par_hooks`` function in the ``ExampleDriver`` class. Refer to the `Hammer documentation on hooks <https://hammer-vlsi.readthedocs.io/en/latest/Hammer-Use/Hooks.html>`__ for a detailed description of how these are injected into the VLSI flow.
