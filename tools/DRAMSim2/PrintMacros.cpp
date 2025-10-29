#include "PrintMacros.h"

/*
 * Enable or disable PRINT() statements.
 *
 * Set by flag in TraceBasedSim.cpp when compiling standalone DRAMSim tool.
 *
 * The DRAMSim libraries do not include the TraceBasedSim object and thus
 * library users can optionally override the weak definition below.
 */
#ifndef _WIN32
int __attribute__((weak)) SHOW_SIM_OUTPUT = false;
#endif
