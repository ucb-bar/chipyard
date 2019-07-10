#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#include "dramcache.h"
#include "encoding.h"

#define CACHE_START (1L << 32)
#define SPAN_BYTES 64
#define BLOCKN (SPAN_BYTES / sizeof(uint64_t))
#define EXTENT_BYTES (1L << 20)
#define TESTN 1000
#define WAYSIZE (1L << 14)
#define NWAYS 7

//#define SOFT_PREFETCH
#define AUTO_PREFETCH

static inline void init_array(uint64_t *arr, int n)
{
	for (int i = 0; i < n; i+=4) {
		arr[i + 0] = i + 0;
		arr[i + 1] = i + 1;
		arr[i + 2] = i + 2;
		arr[i + 3] = i + 3;
	}
}

static inline void copy_array(uint64_t *dst, uint64_t *src, int n)
{
#ifdef SOFT_PREFETCH
	int lastn = (n % BLOCKN == 0) ? (n - BLOCKN) : ((n/BLOCKN) * BLOCKN);

	prefetch_write(dst, SPAN_BYTES);
	prefetch_read(src,  SPAN_BYTES);

	for (int i = 0; i < lastn; i += BLOCKN) {
		uint64_t remaining = n - i - BLOCKN;
		uint64_t pfbytes = (remaining < BLOCKN) ? (remaining * sizeof(uint64_t)) : SPAN_BYTES;
		prefetch_write(&dst[i + BLOCKN], pfbytes);
		prefetch_read(&src[i + BLOCKN],  pfbytes);

		for (int j = 0; j < BLOCKN; j+=4) {
			dst[i + j + 0] = src[i + j + 0];
			dst[i + j + 1] = src[i + j + 1];
			dst[i + j + 2] = src[i + j + 2];
			dst[i + j + 3] = src[i + j + 3];
		}
	}

	for (int i = lastn; i < n; i+=4) {
		dst[i + 0] = src[i + 0];
		dst[i + 1] = src[i + 1];
		dst[i + 2] = src[i + 2];
		dst[i + 3] = src[i + 3];
	}
#else
	for (int i = 0; i < n; i+=4) {
		dst[i + 0] = src[i + 0];
		dst[i + 1] = src[i + 1];
		dst[i + 2] = src[i + 2];
		dst[i + 3] = src[i + 3];
	}
#endif
}

static inline void check_array(uint64_t *arr, int n)
{
	for (int i = 0; i < n; i++) {
		if (arr[i] != i) {
			printf("Error arr[%d] = %ld\n", i, arr[i]);
			abort();
		}
	}
}

int main(void)
{
	long nextents = ((NWAYS + 1) * WAYSIZE) / EXTENT_BYTES;
	long cycles;
	uint64_t *dst, *src;

	if (nextents == 0)
		nextents = 1;

	for (int i = 0; i < nextents; i++)
		set_extent_mapping(i, i + 1, 3 << 8);

	dst = (uint64_t *) CACHE_START;
	src = dst + TESTN;

	printf("Initialize source array\n");
	init_array(src, TESTN);

	for (int i = 0; i < NWAYS; i++) {
		uint64_t *start = (uint64_t *) (CACHE_START + WAYSIZE * (i + 1));
		printf("Dirty way %d\n", i);
		init_array(start, TESTN);
	}

#ifdef AUTO_PREFETCH
	enable_auto_prefetch();
#endif

	printf("Copy source array to dest\n");
	asm volatile ("fence");
	cycles = -rdcycle();
	copy_array(dst, src, TESTN);
	cycles += rdcycle();

	printf("%d cycles\n", cycles);

#ifdef AUTO_PREFETCH
	disable_auto_prefetch();
#endif

	printf("Check dest array for correctness\n");
	check_array(dst, TESTN);

	return 0;
}
