########################################################################################################################
# file:  CMakeLists.txt
#
# usage: 
#   Edit "VARIABLES"-section to suit project requirements.
#   Build instructions:
#     cmake -S ./ -B ./build/ -D CMAKE_BUILD_TYPE=Debug
#     cmake --build ./build/ --target all
#   Cleaning:
#     cmake --build ./build/ --target clean
########################################################################################################################
cmake_minimum_required(VERSION 3.10)

project(chipyard-tests LANGUAGES C CXX)


#################################
# RISCV Toolchain
#################################

set(CMAKE_SYSTEM_NAME         "Generic" CACHE STRING "")
set(CMAKE_SYSTEM_PROCESSOR    "riscv"   CACHE STRING "")

set(TOOLCHAIN_PREFIX    "riscv64-unknown-elf-")

set(CMAKE_AR            "${TOOLCHAIN_PREFIX}ar")
set(CMAKE_ASM_COMPILER  "${TOOLCHAIN_PREFIX}gcc")
set(CMAKE_C_COMPILER    "${TOOLCHAIN_PREFIX}gcc")
set(CMAKE_CXX_COMPILER  "${TOOLCHAIN_PREFIX}g++")
set(CMAKE_LINKER        "${TOOLCHAIN_PREFIX}ld")
set(CMAKE_OBJCOPY       "${TOOLCHAIN_PREFIX}objcopy")
set(CMAKE_OBJDUMP       "${TOOLCHAIN_PREFIX}objdump")
set(CMAKE_SIZE          "${TOOLCHAIN_PREFIX}size")

set(CMAKE_EXECUTABLE_SUFFIX            ".riscv")


#################################
# Flags
#################################

# CPU architecture
set(ARCH                "rv64imafd_zicsr_zifencei")
set(ABI                 "lp64d")
set(CMODEL              "medany")
set(ARCH_FLAGS          -march=${ARCH} -mabi=${ABI} -mcmodel=${CMODEL})

# spec
set(SPECS               "htif_nano.specs")
set(SPEC_FLAGS          -specs=${SPECS})

# linker script
set(LINKER_SCRIPT       "htif.ld")

add_compile_options(-std=gnu99)
add_compile_options(-O2 -Wall -Wextra)
add_compile_options(-fno-common -fno-builtin-printf)
add_compile_options(-ffunction-sections -fdata-sections)
add_compile_options(${ARCH_FLAGS})
add_compile_options(${SPEC_FLAGS})

add_link_options(-static)
add_link_options(${SPEC_FLAGS})
add_link_options(-T ${LINKER_SCRIPT})


#################################
# Build
#################################

add_executable(pwm pwm.c)
add_executable(blkdev blkdev.c)
add_executable(accum accum.c)
add_executable(charcount charcount.c)
add_executable(cpp-hello cpp-hello.cpp)
add_executable(nic-loopback nic-loopback.c)
add_executable(big-blkdev big-blkdev.c)
add_executable(pingd pingd.c)
add_executable(streaming-passthrough streaming-passthrough.c)
add_executable(streaming-fir streaming-fir.c)
add_executable(nvdla nvdla.c)
add_executable(spiflashread spiflashread.c)
add_executable(spiflashwrite spiflashwrite.c)
add_executable(fft fft.c)
add_executable(gcd gcd.c)
add_executable(hello hello.c)
add_executable(mt-hello mt-hello.c)
add_executable(symmetric symmetric.c)
add_executable(ctc-test ctc-test.c)
add_executable(multi-ctc-test multi-ctc-test.c)
add_executable(tma_inject_baremetal tmatests/tma_inject_baremetal.c)

#################################
# Disassembly
#################################

# Add a target to generate all disassemblies
add_custom_target(dump ALL)

# Function to add disassembly target for an executable
function(add_dump_target target_name)
    add_custom_target(${target_name}-dump
        BYPRODUCTS ${CMAKE_BINARY_DIR}/${target_name}.dump
        COMMAND ${CMAKE_OBJDUMP} -D $<TARGET_FILE:${target_name}> > ${CMAKE_BINARY_DIR}/${target_name}.dump
        DEPENDS ${target_name}
        COMMENT "Generating disassembly for ${target_name}"
    )
    add_dependencies(${target_name}-dump ${target_name})
    add_dependencies(dump ${target_name}-dump)
endfunction()

add_dump_target(pwm)
add_dump_target(blkdev)
add_dump_target(accum)
add_dump_target(charcount)
add_dump_target(cpp-hello)
add_dump_target(nic-loopback)
add_dump_target(big-blkdev)
add_dump_target(pingd)
add_dump_target(streaming-passthrough)
add_dump_target(streaming-fir)
add_dump_target(nvdla)
add_dump_target(spiflashread)
add_dump_target(spiflashwrite)
add_dump_target(fft)
add_dump_target(gcd)
add_dump_target(hello)
add_dump_target(mt-hello)
add_dump_target(symmetric)
add_dump_target(ctc-test)
add_dump_target(multi-ctc-test)
add_dump_target(tma_inject_baremetal)

#################################
# Microbenchmarks (ubench/)
#################################

set(UBENCH_DIR ${CMAKE_SOURCE_DIR}/ubench)

# Umbrella target: cmake --build ./build/ --target ubench
add_custom_target(ubench)

# Helper: add a microbenchmark from ubench/<name>/bench.c
function(add_ubench name)
    set(target "ubench-${name}")
    add_executable(${target} ${UBENCH_DIR}/${name}/bench.c)
    target_include_directories(${target} PRIVATE ${UBENCH_DIR} ${UBENCH_DIR}/${name})
    target_compile_definitions(${target} PRIVATE TARGET_VERILATOR=1)
    target_link_libraries(${target} m)
    set_target_properties(${target} PROPERTIES OUTPUT_NAME ${name} EXCLUDE_FROM_ALL TRUE)
    add_dump_target(${target})
    add_dependencies(ubench ${target})
endfunction()

# All microbenchmarks.  Six of them (CCe, CCh, CCh_st, CCl, CF1, CRm)
# ship with a pre-generated randArr.h that is committed to the repo.
# Do NOT regenerate these files — the random data must stay identical
# to preserve reproducibility of all prior profiling results.
# (rand_c_arr.py + rand_arr_args.txt are kept for reference only.)
add_ubench(CCa)
add_ubench(CCe)
add_ubench(CCh)
add_ubench(CCh_st)
add_ubench(CCl)
add_ubench(CCm)
add_ubench(CF1)
add_ubench(CRd)
add_ubench(CRf)
add_ubench(CRm)
add_ubench(CS1)
add_ubench(CS3)
add_ubench(DP1d)
add_ubench(DP1f)
add_ubench(DPcvt)
add_ubench(DPT)
add_ubench(DPTd)
add_ubench(ED1)
add_ubench(EF)
add_ubench(EI)
add_ubench(EM1)
add_ubench(EM5)
add_ubench(MC)
add_ubench(MCS)
add_ubench(MD)
add_ubench(M_Dyn)
add_ubench(MI)
add_ubench(MIM)
add_ubench(MIM2)
add_ubench(MIP)
add_ubench(ML2)
add_ubench(ML2_BW_ld)
add_ubench(ML2_BW_ldst)
add_ubench(ML2_BW_st)
add_ubench(ML2_st)
add_ubench(MM)
add_ubench(MM_st)
add_ubench(STc)
add_ubench(STL2)
add_ubench(STL2b)


# Add custom command to generate spiflash.img from spiflash.py
add_custom_command(
    OUTPUT ${CMAKE_BINARY_DIR}/spiflash.img
    COMMAND python3 ${CMAKE_SOURCE_DIR}/spiflash.py --outfile ${CMAKE_SOURCE_DIR}/spiflash.img
    DEPENDS ${CMAKE_SOURCE_DIR}/spiflash.py
    COMMENT "Generating spiflash.img"
)

# Add a target for spiflash.img
add_custom_target(spiflash_img ALL
    DEPENDS ${CMAKE_BINARY_DIR}/spiflash.img
)
