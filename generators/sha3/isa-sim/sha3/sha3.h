//see LICENSE for license
#ifndef _RISCV_SHA3_ROCC_H
#define _RISCV_SHA3_ROCC_H

#include "rocc.h"
#include "mmu.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define SHA3_224_DIGEST_SIZE (224 / 8)
#define SHA3_224_BLOCK_SIZE (200 - 2 * SHA3_224_DIGEST_SIZE)

#define SHA3_256_DIGEST_SIZE (256 / 8)
#define SHA3_256_BLOCK_SIZE (200 - 2 * SHA3_256_DIGEST_SIZE)

#define SHA3_384_DIGEST_SIZE (384 / 8)
#define SHA3_384_BLOCK_SIZE (200 - 2 * SHA3_384_DIGEST_SIZE)

#define SHA3_512_DIGEST_SIZE (512 / 8)
#define SHA3_512_BLOCK_SIZE (200 - 2 * SHA3_512_DIGEST_SIZE)

#define SHA3_DEFAULT_BLOCK_SIZE    SHA3_256_BLOCK_SIZE
#define SHA3_DEFAULT_DIGEST_SIZE   SHA3_256_DIGEST_SIZE

class sha3_t : public rocc_t
{
public:
  sha3_t() {};

  const char* name() { return "sha3"; }

  void reset()
  {
    msg_addr = 0;
    hash_addr = 0;
    msg_len = 0;
  }

  reg_t custom2(rocc_insn_t insn, reg_t xs1, reg_t xs2)
  {
    switch (insn.funct)
    {
      case 0: // setup msg and hash addr
        msg_addr = xs1;
        hash_addr = xs2;
        break;
      case 1: // setup msg length and run
        msg_len = xs1;

        //read message into buffer
        unsigned char* input;
        input  = (unsigned char*)malloc(msg_len*sizeof(char));
        for(uint32_t i = 0; i < msg_len; i++)
          input[i] = p->get_mmu()->load_uint8(msg_addr + i);
          
        unsigned char output[SHA3_256_DIGEST_SIZE];
        sha3ONE(input, msg_len, output);

        //write output
        for(uint32_t i = 0; i < SHA3_256_DIGEST_SIZE; i++)
          p->get_mmu()->store_uint8(hash_addr + i, output[i]);
        
        //clean up
        free(input);

        break;

      default:
        illegal_instruction();
    }

    return -1; // in all cases, the accelerator returns nothing
  }

private:
  reg_t msg_addr;
  reg_t hash_addr;
  reg_t msg_len;


typedef struct {
  uint64_t st[25];
  unsigned int md_len;
  unsigned int rsiz;
  unsigned int rsizw;
  
  unsigned int partial;
  uint8_t buf[SHA3_DEFAULT_BLOCK_SIZE];  
} sha3_state;

#define KECCAK_ROUNDS 24

#define ROTL64(x, y) (((x) << (y)) | ((x) >> (64 - (y))))

static constexpr uint64_t keccakf_rndc[24] = 
  {
    0x0000000000000001, 0x0000000000008082, 0x800000000000808a,
    0x8000000080008000, 0x000000000000808b, 0x0000000080000001,
    0x8000000080008081, 0x8000000000008009, 0x000000000000008a,
    0x0000000000000088, 0x0000000080008009, 0x000000008000000a,
    0x000000008000808b, 0x800000000000008b, 0x8000000000008089,
    0x8000000000008003, 0x8000000000008002, 0x8000000000000080, 
    0x000000000000800a, 0x800000008000000a, 0x8000000080008081,
    0x8000000000008080, 0x0000000080000001, 0x8000000080008008
  };

static constexpr int keccakf_rotc[24] = 
  {
    1,  3,  6,  10, 15, 21, 28, 36, 45, 55, 2,  14, 
    27, 41, 56, 8,  25, 43, 62, 18, 39, 61, 20, 44
  };

static constexpr int keccakf_piln[24] = 
  {
    10, 7,  11, 17, 18, 3, 5,  16, 8,  21, 24, 4, 
    15, 23, 19, 13, 12, 2, 20, 14, 22, 9,  6,  1 
  };

void printState(uint64_t st[25]);

// update the state with given number of rounds
void keccakf(uint64_t st[25], int rounds);

int sha3ONE(unsigned char *message, unsigned int len, unsigned char *digest);

void sha3_init(sha3_state *sctx);
void sha3_update(sha3_state *sctx, const uint8_t *data, unsigned int len);
void sha3_final(sha3_state *sctx, uint8_t *out);

};
REGISTER_EXTENSION(sha3, []() { return new sha3_t; })
#endif
