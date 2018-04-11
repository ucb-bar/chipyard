// See LICENSE for license details.

//**************************************************************************
// Multi-threaded Matrix Multiply benchmark
//--------------------------------------------------------------------------
// TA     : Christopher Celio
// Student: 
//
//
// This benchmark multiplies two 2-D arrays together and writes the results to
// a third vector. The input data (and reference data) should be generated
// using the matmul_gendata.pl perl script and dumped to a file named
// dataset.h. 

//--------------------------------------------------------------------------
// Includes 

#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stddef.h>


//--------------------------------------------------------------------------
// Input/Reference Data

#include "dataset.h"
 

//--------------------------------------------------------------------------
// Basic Utilities and Multi-thread Support

#include "util.h"

   
//--------------------------------------------------------------------------
// matmul function
 
extern void matmul(const size_t coreid, const size_t ncores, const size_t lda,  const data_t A[], const data_t B[], data_t C[] );
extern void matmul_opt(const size_t coreid, const size_t ncores, const size_t lda,  const data_t A[], const data_t B[], data_t C[] );

//--------------------------------------------------------------------------
// Main
//
// all threads start executing thread_entry(). Use their "coreid" to
// differentiate between threads (each thread is running on a separate core).
  
void thread_entry(int cid, int nc)
{
   static data_t results_data[ARRAY_SIZE];

   barrier(nc);
   stats(matmul(cid, nc, DIM_SIZE, input1_data, input2_data, results_data); barrier(nc), DIM_SIZE * DIM_SIZE * DIM_SIZE);
 
   if (cid == 0) {
     int res = verify(ARRAY_SIZE, results_data, verify_data);
     if (res) printf("Naive matmul: FAIL\n");
     else printf("Naive matmul: SUCCESS\n");
     // re-zero the array
     for (int i = 0; i < ARRAY_SIZE; i++)
       results_data[i] = 0;
   }

   barrier(nc);
   stats(matmul_opt(cid, nc, DIM_SIZE, input1_data, input2_data, results_data); barrier(nc), DIM_SIZE * DIM_SIZE * DIM_SIZE);

   if (cid == 0) {
     int res = verify(ARRAY_SIZE, results_data, verify_data);
     if (res) {
       printf("Optimized matmul: FAIL\n");
       printf("Correct matrix:\n");
       printMatrix(verify_data, DIM_SIZE, DIM_SIZE);
       printf("Actual matrix:\n");
       printMatrix(results_data, DIM_SIZE, DIM_SIZE);
     } else printf("Optimized matmul: SUCCESS\n");
   }

   barrier(nc);
   exit(0);
}
