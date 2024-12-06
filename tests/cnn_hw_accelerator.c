#include "mmio.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#define DATA_ADDR 0x4000
#define DATA_COLS 0x4008
#define DATA_ROWS 0x400C
#define FILT_ADDR 0x4010
#define FILT_COLS 0x4018
#define FILT_ROWS 0x401C
#define DEST_ADDR 0x4020
#define START     0x4028
#define BUSY      0x402C

void stall(int clocks)
{
  volatile int32_t cnt = 0;
  for (int i = 0; i < clocks; ++i) {
    cnt++;
  }
}

int main(void)
{
  
  uint32_t dataRows = 32;
  uint32_t dataCols = 32;
  uint32_t filtRows = 1;
  uint32_t filtCols = 32;
  uint32_t resultRows = dataRows - filtRows + 1;
  uint32_t resultCols = dataCols - filtCols + 1;

  uint32_t filtSize = filtRows * filtCols;
  uint32_t dataSize = dataRows * dataCols;
  uint32_t resultSize = resultRows * resultCols;

  reg_write32(DATA_COLS, dataCols);
  reg_write32(DATA_ROWS, dataRows);
  reg_write32(FILT_COLS, filtCols);
  reg_write32(FILT_ROWS, filtRows);
  
  volatile float* data = malloc(dataSize * sizeof(float));
  volatile float* filt = malloc(filtSize * sizeof(float));
  volatile float* result = malloc(resultSize * sizeof(float));

  uint32_t i;
  for (i = 0; i < dataSize; ++i){
    data[i] = (float) (i + 1);
  }

  for (i = 0; i < filtSize; ++i){
    filt[i] = (float) (i + 1);
  }

  float* resultRef = malloc(resultSize * sizeof(float));

  uint32_t j;
  for (i = 0; i < dataRows; ++i) {
    resultRef[i] = 0.0f;
    for (j = 0; j < dataCols; ++j){
      resultRef[i] += data[i*dataCols+j]*filt[j];
    }
  }

  reg_write64(DATA_ADDR, (uint64_t) data);
  reg_write64(FILT_ADDR, (uint64_t) filt);
  reg_write64(DEST_ADDR, (uint64_t) result);

  reg_write32(START, 1);

  while (reg_read32(BUSY) == 1);

  // stall(1000);

  int passFail = 0;

  int resultInt;
  int resultRefInt;

  for (i = 0; i < resultSize; ++i)
  {
    memcpy(&resultInt, &result[i], sizeof(float));
    memcpy(&resultRefInt, &resultRef[i], sizeof(float));
    printf("Ref=0x%08X. Meas=0x%08X\n", resultInt, resultRefInt);
    if (resultInt != resultRefInt){
      passFail = 1;
      printf("Error detected at index %d. Ref=0x%08X. Meas=0x%08X\n", i, resultInt, resultRefInt);
    }
  }

  free(data);
  free(filt);
  free(result);
  free(resultRef);

  return passFail;
}