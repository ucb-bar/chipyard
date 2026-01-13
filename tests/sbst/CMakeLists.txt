#################################
# Build SBST CMake File
#################################

cmake_minimum_required(VERSION 3.10)

# Adding c files
add_executable(sbst sbst.c)


add_dump_target(sbst)