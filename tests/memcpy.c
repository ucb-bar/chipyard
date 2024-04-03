#include "mmio.h"
#include <stdio.h>
#include <stdlib.h>

#define MEMCPY_STS 0x5000
#define SRC_PTR 0x5008
#define DEST_PTR 0x5010
#define SIZE 0x5018

#define x 8
#define y 8

int main(void)
{
  volatile int A[x][y];
  volatile int B[x][y];
  int size = x*y/2;

  for (int i=0; i<x; i=i+1) {
    for (int j=0; j<y; j=j+1) {
      A[i][j] = i+j+1;
    }
  }

  // wait for peripheral to be ready
  while ((reg_read8(MEMCPY_STS) & 0x2) == 0) ;

  reg_write64(SRC_PTR, A);
  reg_write64(DEST_PTR, B);
  reg_write64(SIZE, size);

  // wait for peripheral to complete
  while ((reg_read8(MEMCPY_STS) & 0x1) == 0) ;

  for (int i=0; i<x; i=i+1) {
    for (int j=0; j<y; j=j+1) {
      if (A[i][j] != B[i][j]) {
        printf("Incorrect Result, %d\n", B[i][j]);
        exit(1);
      }
    }
  }

  printf("PASS\n");
  exit(0);
}

