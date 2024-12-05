#include "mmio.h"
#include <stdlib.h>
#include <stdio.h>

#define DATA_ADDR 0x4000
#define DATA_COLS 0x4008
#define DATA_ROWS 0x400C
#define FILT_ADDR 0x4010
#define FILT_COLS 0x4018
#define FILT_ROWS 0x401C
#define DEST_ADDR 0x4020
#define START     0x4028

void stall(int clocks)
{
  volatile int32_t cnt = 0;
  for (int i = 0; i < clocks; ++i) {
    cnt++;
  }
}

int main(void)
{
  
  // uint32_t dataRows = 64;
  // uint32_t dataCols = 64;
  // uint32_t filtRows = 1;
  // uint32_t filtCols = 64;
  // uint32_t resultRows = dataRows - filtRows + 1;
  // uint32_t resultCols = dataCols - filtCols + 1;

  // uint32_t filtSize = filtRows * filtCols;
  // uint32_t dataSize = dataRows * dataCols;
  // uint32_t resultSize = resultRows * resultCols;

  // reg_write32(DATA_COLS, dataCols);
  // reg_write32(DATA_ROWS, dataRows);
  // reg_write32(FILT_COLS, filtCols);
  // reg_write32(FILT_ROWS, filtRows);

  
  // volatile float* data = malloc(dataSize * sizeof(float));
  // volatile float* filt = malloc(filtSize * sizeof(float));
  // volatile float* result = malloc(resultSize * sizeof(float));

  // uint32_t i;
  // for (i = 0; i < dataSize; ++i){
  //   data[i] = (float) (i + 1);
  // }

  // for (i = 0; i < filtSize; ++i){
  //   filt[i] = (float) (i + 1);
  // }

  // reg_write64(DATA_ADDR, (uint64_t) data);
  // reg_write64(FILT_ADDR, (uint64_t) filt);

  // reg_write32(START, 1);

  // stall(1000);

  // for (i = 0; i < resultSize; ++i)
  // {
  //   printf("result[%d][%d] = %.4f\n", i / resultCols, i % resultCols, result[i]);
  // }

  // free(data);
  // free(filt);
  reg_write32(DATA_COLS, 1);
  uint32_t data = reg_read32(DATA_COLS);

  if (data){
    return 0;
  } else {
    return 1;
  }
}