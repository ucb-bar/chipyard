#################################
# Build SBST CMake File
#################################

cmake_minimum_required(VERSION 3.10)

# Additional linker script
set(ALINKER_SCRIPT       "sbst.ld")
add_link_options(-T ${ALINKER_SCRIPT})

# Adding c files
add_executable(sbst sbst.c)


add_dump_target(sbst)