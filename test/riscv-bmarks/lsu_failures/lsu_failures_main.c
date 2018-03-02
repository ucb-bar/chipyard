// See LICENSE for license details.

//**************************************************************************
// Vector-vector add benchmark
//--------------------------------------------------------------------------
//
// This benchmark uses adds to vectors and writes the results to a
// third vector. The input data (and reference data) should be
// generated using the vvadd_gendata.pl perl script and dumped
// to a file named dataset1.h.
 
#include "util.h"

//--------------------------------------------------------------------------
// lsu_failures function

void lsu_failures( /* args */ )
{
  // code
}

//--------------------------------------------------------------------------
// Main

int main( int argc, char* argv[] )
{
  // Do the vvadd
  setStats(1);
  lsu_failures();
  setStats(0);

  // Check the results
  return 0;
}
