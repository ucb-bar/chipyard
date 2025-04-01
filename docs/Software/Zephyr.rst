.. _zephyr-overview:

Zephyr RTOS
=========================

Overview
--------

What is an RTOS?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A **Real-Time Operating System (RTOS)** is a lightweight OS designed to provide deterministic scheduling, minimal latency, and efficient resource management. Unlike general-purpose operating systems like Linux, an RTOS prioritizes **real-time constraints**, ensuring critical tasks meet strict timing deadlines. RTOSs are commonly used in embedded systems, microcontrollers, and simulation environments where fast boot times and efficient execution are essential.


Zephyr Use Cases
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Zephyr provides a lightweight build flow and execution environment that supports running complex end-to-end workloads without the overhead of Linux.

Key benefits:
* **Multicore & Threading Support**: Zephyr provides a task scheduler with multithreading and multicore support. Heterogeneous SoCs can be used via task pinning.
* **POSIX-like API**: Zephyr provides POSIX APIs for threading and synchronization, useful for migrating Linux-based workloads.
* **Fast Boot Times**: Zephyr has a significantly **faster startup time** in **RTL simulations** (~20K cycles for RocketConfig).
* **Small Footprint**: Zephyr ELF files are 20-100KB (excluding application code), suitable for bringup and prototyping targets.
* **Simplified Virtual Memory**: While Zephyr does not support **full virtual memory** like Linux, it does provide basic memory mapping and demand paging.


.. _zephyr-installation:

Installation and Basic Usage
----------------------------
Before beginning, ensure that your Chipyard RISCV toolchain is installed, and `env.sh` is sourced. Next, initialize the Zephyr submodule.

.. code-block:: shell

    git submodule update --init software/zephyrproject/zephyr/


Next, run the following commands to initiaize the Zephyr workspace. `west`` is Zephyr's CMake-based build tool and dependency manager, designed to streamline project setup, compilation, and firmware deployment. It automates building Zephyr applications into ELF binaries and manages multiple repositories and submodules. In Chipyard, `west`` is used to build ELF files for simulation on Spike, software RTL simulation, or FireSim

.. code-block:: shell

    cd software/zephyrproject/zephyr
    west init -l .
    west config manifest.file west-riscv.yml
    west update

Next, set the followwing environment variables:

.. code-block:: shell

    export ZEPHYR_BASE=$(pwd)
    export ZEPHYR_TOOLCHAIN_VARIANT=cross-compile
    export CROSS_COMPILE=$RISCV/bin/riscv64-unknown-elf-

After initializing the workspace, you can build the Zephyr kernel and sample applications. For example, to build the `hello_world` sample application, run the following commands within the Zephyr directory:

.. code-block:: shell

    west build -p -b spike_riscv64 ./samples/chipyard/hello_world

This will generate a build directory with the compiled ELF file, with example output below:

.. code-block:: shell

    [0/128] Preparing syscall dependency handling
    [128/128] Linking C executable zephyr/zephyr.elf

    Memory region         Used Size  Region Size  %age Used
                RAM:       36868 B       256 MB      0.01%
            IDT_LIST:           0 B         2 KB      0.00%


You can run the ELF file on Spike using the following command:

.. code-block:: shell

    spike ./build/zephyr/zephyr.elf

This should print the following output:

.. code-block:: shell

    *** Booting Zephyr OS build 6c1e6f64895b ***
    Hello World! spike_riscv64/spike_virt_riscv64
  
To simulate the Zephyr application in RTL simulation, follow the instrutions in the `Simulation Guide <../Simulation/index.html>`_. Use the path to the Zephyr ELF file as the `BINARY` argument to the RTL simulator.


Zephyr Core Concepts
--------------------

Below are useful concepts and terms to understand when working with Zephyr.

KConfig: Configuring Zephyr
~~~~~~~~~~~~~~~~~~~~~~~~~~~
Zephyr uses **KConfig**, a configuration system that allows developers to **enable or disable features**, **select drivers**, and **tune system parameters**. KConfig files are used to specify options that influence the build process.

* Located in `Kconfig` files within the Zephyr source tree.
* Used to enable hardware drivers (e.g., `CONFIG_UART_HTIF=y` for HTIF UART support).
* Managed using the `menuconfig` or `guiconfig` tools.

Example:
.. code-block:: kconfig

    config UART_HTIF
        bool "Enable HTIF UART driver"
        select SERIAL_HAS_DRIVER
        depends on RISCV

        help
            Enable the HTIF (Host-Target Interface) UART driver for RISC-V Spike simulation.

To modify configuration:
.. code-block:: shell

   west build -t menuconfig

This launches an interactive menu to configure Zephyr features.

Device Trees: Hardware Description
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Zephyr uses **Device Tree Source (DTS) files** to describe **hardware components**, **memory layouts**, and **peripherals** in a structured manner. 

Key components:
- **Board-level DTS files** (e.g., `spike_riscv64.dts`) define **enabled devices**.
- **SoC-level DTS files** (e.g., `virt-riscv.dtsi`) provide **shared hardware descriptions**.
- **Bindings** map devices to their respective drivers.


Device Drivers: Enabling Hardware Support
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Device drivers in Zephyr provide **abstraction layers** that interface with hardware components. Each driver is responsible for **initialization**, **communication**, and **handling interrupts** if applicable.

Drivers are located in:
.. code-block:: shell

   zephyr/drivers/<subsystem>/   # e.g., serial/

To register a driver:
1. Implement driver functions (e.g., `poll_in`, `poll_out`).
2. Define the `DEVICE_DT_DEFINE()` macro to initialize the driver.
3. Add the driver to `CMakeLists.txt` to be compiled when enabled in KConfig.


Driver Bindings: Connecting DTS to Drivers
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Zephyr uses **YAML bindings** to map **Device Tree nodes** to their respective **drivers**. These bindings define **required properties** such as memory addresses, compatible strings, and configurations.

Adding a binding ensures that Zephyr correctly **associates hardware definitions with driver implementations**.

Zephyr Subsystems
~~~~~~~~~~~~~~~~~
Zephyr includes **several subsystems** for handling standard OS functionality, such as logging, input/output, and multi-threading.

Console: Standard Output Interface
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The **console subsystem** provides a standard output interface for logging and debugging.

To enable a UART device as the console:

.. code-block:: dts

   chosen {
       zephyr,console = &htif;
   };



Zephyr will automatically redirect `printf`-like output to the chosen console device.


Adding a New Zephyr Driver: HTIF UART
-------------------------------------

This tutorial guides you through the process of adding a **Host-Target Interface (HTIF) UART driver** to Zephyr. This driver enables serial output in **Spike/FESVR simulations** and can be used for debugging or system interaction. This driver has already been integrated; this guide provides an example of how to add a new driver to Zephyr.

Prerequisites
~~~~~~~~~~~~~
Before proceeding, ensure you have:

- A working Zephyr workspace set up in **Chipyard**.
- `west` installed and initialized.
- Familiarity with **Device Tree (DTS)**, **CMake**, and Zephyr driver configuration.

Define the HTIF UART in the Device Tree
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To integrate HTIF as a serial device, update the **Spike board’s Device Tree Source (DTS)**.

Edit `boards/spike/riscv64/spike_riscv64.dts` to enable HTIF:

.. code-block:: dts

   / {
       chosen {
           zephyr,console = &htif;
           zephyr,shell-uart = &htif;
           zephyr,sram = &ram0;
       };
   };

   // Disable the default ns16550 UART
   &uart0 {
       status = "disabled";
   };

   &htif {
       status = "okay";
   };


In addition to enabling the HTIF device, this snippet sets the **HTIF UART as the console and shell UART**. The `zephyr,console` and `zephyr,shell-uart` properties specify the device node for the console and shell UART, respectively.

For the full file, refer to [`spike_riscv64.dts`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/boards/spike/riscv64/spike_riscv64.dts).

The **HTIF device itself is fully defined** in `dts/riscv/spike/virt-riscv.dtsi`, which provides a generic definition for the **RISC-V "virt" machine** used in Spike. This file includes:

.. code-block:: dts

   htif: uart {
       compatible = "ucb,htif";
       label = "HTIF_UART";
   };
  

This defines the HTIF device as a **UART-compatible peripheral**, setting its `compatible` property to `"ucb,htif"`, which corresponds to the driver binding we will add later. The `label` property provides a **human-readable name** that can be referenced elsewhere in Zephyr's configuration.

For the full file, see [`virt-riscv.dtsi`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/dts/riscv/spike/virt-riscv.dtsi).

Define Device Tree Binding
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Add a binding file to `dts/bindings/serial/ucb,htif-uart.yaml`:

.. code-block:: yaml

   # SPDX-License-Identifier: Apache-2.0
   description: HTIF UART for Spike/FESVR
   compatible: "ucb,htif"
   include: base.yaml
   properties:
     label:
       type: string
       required: true
       description: Human-readable string describing the device

This file defines the **HTIF UART device** as a **serial device** with a `label` property. The `compatible` property matches the device tree entry in `virt-riscv.dtsi`.

For the complete file, see [`ucb,htif-uart.yaml`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/dts/bindings/serial/ucb,htif-uart.yaml).

Define HTIF Registers and Mutex in a Header
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Create `include/zephyr/drivers/htif.h` to define HTIF constants and expose global variables:

.. code-block:: c

   #ifndef ZEPHYR_DRIVERS_HTIF_H
   #define ZEPHYR_DRIVERS_HTIF_H

   #include <stdint.h>
   #include <zephyr/sys/mutex.h>

   extern volatile uint64_t tohost;
   extern volatile uint64_t fromhost;
   extern struct k_mutex htif_lock;

   #endif // ZEPHYR_DRIVERS_HTIF_H

For the complete header, see [`htif.h`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/include/zephyr/drivers/htif.h).



Implement the HTIF UART Driver
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Create `drivers/serial/uart_htif.c`, implementing `poll_in` and `poll_out` based on OpenSBI logic.

Key functions:
- **`uart_htif_poll_out()`**: Transmits a character via HTIF.
- **`uart_htif_poll_in()`**: Reads a character via HTIF.

.. code-block:: c

   static void uart_htif_poll_out(const struct device *dev, unsigned char out_char) {
       k_mutex_lock(&htif_lock, K_FOREVER);
       htif_wait_for_ready();
       tohost = TOHOST_CMD(HTIF_DEV_CONSOLE, HTIF_CONSOLE_CMD_PUTC, out_char);
       k_mutex_unlock(&htif_lock);
   }

   static int uart_htif_poll_in(const struct device *dev, unsigned char *p_char) {
       k_mutex_lock(&htif_lock, K_FOREVER);
       htif_wait_for_ready();
       tohost = TOHOST_CMD(HTIF_DEV_CONSOLE, HTIF_CONSOLE_CMD_GETC, 0);
       while (fromhost == 0);
       *p_char = (char)(FROMHOST_DATA(fromhost) & 0xFF);
       fromhost = 0;
       k_mutex_unlock(&htif_lock);
       return 0;
   }

Additionally, define the UART driver API and bind it to the HTIF device:

.. code-block:: c

   static const struct uart_driver_api uart_htif_driver_api = {
       .poll_in  = uart_htif_poll_in,
       .poll_out = uart_htif_poll_out,
   };

   DEVICE_DT_DEFINE(DT_NODELABEL(htif), uart_htif_init, NULL, NULL, NULL,
                    PRE_KERNEL_1, CONFIG_KERNEL_INIT_PRIORITY_DEVICE,
                    &uart_htif_driver_api);

For the full implementation, see [`uart_htif.c`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/drivers/serial/uart_htif.c).


Update the Linker Script
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Ensure that `tohost` and `fromhost` are placed in a dedicated `.htif` section by modifying `include/zephyr/arch/riscv/common/linker.ld`:

.. code-block:: diff

   .htif ALIGN(0x100) : {
       KEEP(*(.htif))
   }

For the full linker script, see [`linker.ld`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/include/zephyr/arch/riscv/common/linker.ld).

Modify the CMake Build System
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Zephyr's build system needs to recognize the new driver. Update `drivers/serial/CMakeLists.txt` to include `uart_htif.c` when the `CONFIG_UART_HTIF` option is enabled:

.. code-block:: diff

   zephyr_library_sources_ifdef(CONFIG_UART_HTIF uart_htif.c)

For the full file, see [`CMakeLists.txt`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/drivers/serial/CMakeLists.txt).

Add Kconfig Configuration for HTIF
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Define a new Kconfig entry for enabling HTIF. Modify `drivers/serial/Kconfig`:

.. code-block:: diff

   rsource "Kconfig.htif"

Then, create a new `Kconfig.htif` file to define HTIF-specific options:

.. code-block:: kconfig

   menuconfig UART_HTIF
       bool "Enable HTIF UART driver"
       select SERIAL_HAS_DRIVER
       depends on RISCV
       help
           Enable the HTIF (Host-Target Interface) UART driver for RISC-V Spike simulation.

For the complete configuration, see [`Kconfig.htif`](https://github.com/ucb-bar/zephyr/blob/chipyard-port/drivers/serial/Kconfig.htif).

You will now be able to enable the HTIF UART driver when building Zephyr applications. 









