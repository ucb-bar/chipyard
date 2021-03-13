# Guide


```
$ export RISCV_LINUX=~/riscv-linux
$ export BUILDS=$(RISCV_LINUX)/builds
$ export OPENSBI=$(RISCV_LINUX)/opensbi
$ export LINUX=$(RISCV_LINUX)/linux
$ export CHIPYARD=~/chipyard
$ export gen_src=$(CHIPYARD)/fpga/generated-src
$ export RocketVC709Config=chipyard.fpga.vc709.VC709FPGATestHarness.RocketVC709Config
$ export BoomVC709Config=chipyard.fpga.vc709.VC709FPGATestHarness.BoomVC709Config
```
Build Linux kernel
```
$ make ARCH=riscv CROSS_COMPILE=riscv64-unknown-linux-gnu- menuconfig
$ make j8 ARCH=riscv CROSS_COMPILE=riscv64-unknown-linux-gnu-
```
Convert *.dts files into *.dtb format.
```
$ cd $RISCV_LINUX/$BUILDS
$ cp -p $gen_src/$RocketVC709Config/$(RocketVC709Config).dts $BUILDS
$ dtc -I dts -O dtb -o $(RocketVC709Config).dtb $(RocketVC709Config).dts
$ cp -p $gen_src/$BoomVC709Config/$(BoomVC709Config).dts $BUILDS
$ dtc -I dts -O dtb -o $(BoomVC709Config).dtb $(BoomVC709Config).dts
```
Build Open SBI for RocketVC709Config.
```
make PLATFORM=generic CROSS_COMPILE=riscv64-unknown-linux-gnu- PLATFORM_RISCV_XLEN=64 FW_PAYLOAD=y FW_PAYLOAD_PATH=~/riscv-linux/linux/arch/riscv/boot/Image FW_FDT_PATH=$BUILDS/$(RocketVC709Config).dtb clean
make PLATFORM=generic CROSS_COMPILE=riscv64-unknown-linux-gnu- PLATFORM_RISCV_XLEN=64 FW_PAYLOAD=y FW_PAYLOAD_PATH=~/riscv-linux/linux/arch/riscv/boot/Image FW_FDT_PATH=$BUILDS/$(RocketVC709Config).dtb install
```
Build Open SBI for BoomVC709Config.
```
make PLATFORM=generic CROSS_COMPILE=riscv64-unknown-linux-gnu- PLATFORM_RISCV_XLEN=64 FW_PAYLOAD=y FW_PAYLOAD_PATH=~/riscv-linux/linux/arch/riscv/boot/Image FW_FDT_PATH=$BUILDS/$(BoomVC709Config).dtb clean
make PLATFORM=generic CROSS_COMPILE=riscv64-unknown-linux-gnu- PLATFORM_RISCV_XLEN=64 FW_PAYLOAD=y FW_PAYLOAD_PATH=~/riscv-linux/linux/arch/riscv/boot/Image FW_FDT_PATH=$BUILDS/$(BoomVC709Config).dtb install
```
Find the `ttyUSB*` device. The outputs looks like this: `[79643.136986] usb 1-9: cp210x converter now attached to ttyUSB2`.
```
$ sudo dmesg | grep tty
```
Download `fw_payload.bin` to the board, then start up the kernel.
```
$ sudo ./serial /dev/ttyUSB2 0x80000000 ../opensbi/build/platform/generic/firmware/fw_payload.bin
$ sudo ./serial /dev/ttyUSB2
```