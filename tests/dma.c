#include "mmio.h"
#include <stdlib.h>
#include <stdio.h>

#define SOURCE 0x4000
#define DEST   0x4008
#define SIZE   0x4010
#define START  0x4014

int main(void)
{
  
  int isError = 0;

  /* All Writes in one word */
  isError = isError | runUnitTest(1, 8, 0);
  isError = isError | runUnitTest(15, 3, 0);
  isError = isError | runUnitTest(31, 5, 3);
  isError = isError | runUnitTest(112, 6, 1);

  /* Writes Contained in two words */
  isError = isError | runUnitTest(7, 16, 0);
  isError = isError | runUnitTest(57, 9, 0);
  isError = isError | runUnitTest(89, 11, 5);
  isError = isError | runUnitTest(207, 12, 2);
  
  /* Writes Contained in three words */
  isError = isError | runUnitTest(71, 24, 0);
  isError = isError | runUnitTest(19, 21, 0);
  isError = isError | runUnitTest(177, 17, 7);
  isError = isError | runUnitTest(231, 12, 5);

  /* Writes Contained in eight words */
  isError = isError | runUnitTest(43, 64, 0);
  isError = isError | runUnitTest(101, 61, 0);
  isError = isError | runUnitTest(127, 60, 4);
  isError = isError | runUnitTest(191, 57, 5);

  if (isError) {
    printf("Overall Fail\n");
  } else {
    printf("Overall Pass\n");
  }  
}

void stall(int clocks)
{
  volatile int32_t cnt = 0;
  for (int i = 0; i < clocks; ++i) {
    cnt++;
  }
}

int runUnitTest(int startVal, int numVals, int offset)
{

  printf("Unit Test Configuration:\n");
  printf("\tStart Value = %d\n", startVal);
  printf("\tNum Values = %d\n", numVals);
  printf("\tOffset = %d\n", offset);

  volatile uint8_t* x = malloc((numVals + 7) * sizeof(uint8_t));
  volatile uint8_t* y = malloc((numVals + 7) * sizeof(uint8_t));

  for (int i = 0; i < numVals; ++i)
  {
    x[i + offset] = (uint8_t) (startVal + i);
    y[i + offset] = 0;
  }

  reg_write64(SOURCE, ((uint64_t) x) + ((uint64_t) offset));
  reg_write64(DEST, ((uint64_t) y) + ((uint64_t) offset));
  reg_write32(SIZE, numVals * sizeof(uint8_t));
  reg_write32(START, 1);

  stall(100);

  int isError = 0;
  uint8_t result, ref;
  for (int i = 0; i < numVals; ++i)
  {
    result = y[i + offset];
    ref = (uint8_t) (startVal + i);
    if (result != ref)
    {
        isError = 1;
        printf("\tHardware result %d does not match reference value %d\n", result, ref);
    }
  }

  if (isError)
  {
    printf("\nFail\n\n");
  } else {
    printf("\nPass\n\n");
  }
  
  free(x);
  free(y);

  return isError;
}