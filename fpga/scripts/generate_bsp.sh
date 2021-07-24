#!/bin/bash

#A Shell Script to generate bsp files for an elaborated FPGA design in
#Chipyard

#Had to do some shenanigans to add "flash@0" device in DTS under SPI Flash node. This is checked by overlay generators in
#the Freedom E SDK


#Name of the directory where generated board files are placed
#in the default project, this is the following:
LONG_NAME=${1}

if [[ -z "${FPGA_PATH}" ]]; then
	echo "FPGA_PATH Environment variable not set. Please set this to be the path to the FPGA directory inside chipyard";
	exit 1;
fi

if [[ -z "${FREEDOM_SDK}" ]]; then
	echo "FREEDOM_SDK Environment variable not set. Please set this to be the path to the Freedom E SDK directory"
	exit 2;
fi

#Generated DTS Path
GENERATED_DTS=${FPGA_PATH}/generated-src/${LONG_NAME}/${LONG_NAME}.dts
MODIFIED_DTS=${FPGA_PATH}/generated-src/${LONG_NAME}/${LONG_NAME}_modified.dts
EXIT_DIR=$(pwd)
cd "$(dirname "$0")"

if [ ! -f "${GENERATED_DTS}" ]; then
	echo "Generated DTS file does not exist. Ensure the \"LONG_NAME\" variable is set correctly in this script";
	exit 1;
else
	cp ${GENERATED_DTS} ${MODIFIED_DTS}
fi



LINE_NUM=$(awk '/@10014000/ {print FNR}' ${MODIFIED_DTS})
[ -z "$LINE_NUM" ] && echo "SPI Flash@10014000 device not found, exiting" && exit 1
if grep -qF "flash" ${MODIFIED_DTS};then
	echo "DTS File already has device flash"
else
	echo "Editing DTS File"
	
	#edit dts file to include 'flash' device under spi@10014000 node	
	sed -i "${LINE_NUM},/\};/{s/\};/\tflash@0 \{\n\t\t\t\tcompatible = \"jedec,spi-nor\"; \n\t\t\t\treg = <0x20000000 0x7a12000>; \n\t\t\t\}; \n\t\t\};/}" ${MODIFIED_DTS}
	#edit dts file to set clock frequency to 65MHz instead of 100MHz
	sed -i "0,/clock-frequency = <100/s//clock-frequency = <32/" ${MODIFIED_DTS}
fi


if [ ! -d "${FREEDOM_SDK}/bsp/chipyard_arty" ]; then
	mkdir ${FREEDOM_SDK}/bsp/chipyard_arty;
fi

#Use Freedom E SDK 'update-targets.sh' script to generate board files
cd ${FREEDOM_SDK}/bsp
./update-targets.sh --target-name chipyard_arty --target-type arty --sdk-path=${FREEDOM_SDK} --target-dts=${MODIFIED_DTS} || exit 1;

echo "File Generation Complete. Find the created files in ${FREEDOM_SDK}/bsp/chipyard_arty"

cd ${EXIT_DIR}
