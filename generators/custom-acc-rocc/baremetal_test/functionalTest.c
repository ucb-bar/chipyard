#include "../../../tests/rocc.h"
#include <inttypes.h>
#include <stdio.h>

uint8_t elem_from_vec(uint64_t vec, uint8_t idx) {
  uint64_t mask = 0xff;
  uint64_t temp;

  mask = mask << (idx * 8);
  temp = vec & mask;

  return (uint8_t) (temp >> (idx * 8)); 
}

uint64_t vec_from_arr(uint8_t* arr, uint8_t num_entries) {
  uint64_t result = 0;
  uint64_t curr_elem;

  for (uint8_t i = 0; i < num_entries; i++) {
    curr_elem = (uint64_t) arr[i];
    result += curr_elem << (i * 8); 
  }

  return result;
}

/* Functional Model */
uint64_t accel_model(uint64_t vec_1, uint64_t vec_2, uint8_t num_entries) {
  uint8_t output_buf[num_entries];
  
  for (uint8_t i = 0; i < num_entries; i++) {
      output_buf[i] = elem_from_vec(vec_1, i) + elem_from_vec(vec_2, i);
  }

  return vec_from_arr(output_buf, num_entries);
}

int main(void) {

  uint64_t test_a_vec_1 = 0x0706050403020100; 
  uint64_t test_a_vec_2 = 0x0807060504030201;

  uint64_t output_vec_acc;
  uint64_t output_vec_model;

	/* Fence to ensure inputs are ready to be shared between core and RoCC accelerator, no pending accesses */
  asm volatile("fence");

  /* Issue a RoCC instruction with opcode custom0, rd, rs1, and rs2 */
  ROCC_INSTRUCTION_DSS(0, output_vec_acc, test_a_vec_1, test_a_vec_2, 0);

  /* Fence waits for de-assertion of busy signal, to ensure the RoCC accelerator has completed its task */
  asm volatile("fence");

  output_vec_model = accel_model(test_a_vec_1, test_a_vec_2, 8);

  printf("Functional Model Output = %#018" PRIx64 " | Accelerator Output = %#018" PRIx64 "\n", output_vec_model, output_vec_acc);

  return !(output_vec_model == output_vec_acc);

}
