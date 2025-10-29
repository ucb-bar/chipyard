#define BIMODAL_TABLE_SIZE 256
#define MAX_COUNTER 3

#include <stdint.h>
#include <stdio.h>

int bimodal_table[BIMODAL_TABLE_SIZE];

extern "C" void initialize_branch_predictor()
{
  for (int i = 0; i < BIMODAL_TABLE_SIZE; i++)
    bimodal_table[i] = 0;
}

extern "C" void predict_branch(unsigned long long ip, unsigned long long hist, unsigned char *pred)
{
    uint32_t hash = ip % BIMODAL_TABLE_SIZE;
    *pred = (bimodal_table[hash] >= ((MAX_COUNTER + 1)/2)) ? 1 : 0;


}

extern "C" void update_branch(unsigned long long ip, unsigned long long hist, unsigned char taken)
{
  uint32_t hash = ip % BIMODAL_TABLE_SIZE;

  if (taken && (bimodal_table[hash] < MAX_COUNTER))
    bimodal_table[hash]++;
  else if ((taken == 0) && (bimodal_table[hash] > 0))
    bimodal_table[hash]--;
}
