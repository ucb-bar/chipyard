#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#include "dramcache.h"
#include "encoding.h"

#define CACHE_START (1L << 32)
#define SPAN_BYTES 64
#define BLOCKN (SPAN_BYTES / sizeof(uint64_t))
#define EXTENT_BYTES (1L << 20)

static inline void init_array(uint64_t *arr, int n)
{
	for (int i = 0; i < n; i+=4) {
		arr[i + 0] = i + 0;
		arr[i + 1] = i + 1;
		arr[i + 2] = i + 2;
		arr[i + 3] = i + 3;
	}
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
	uint64_t *ptr;
	long count;

	set_extent_mapping(0, 1, 3 << 8);

	ptr = (uint64_t *) (CACHE_START + 6 * SPAN_BYTES);

	printf("Initialize array\n");
	init_array(ptr, 2 * BLOCKN);

	count = cache_counter(DRAM_CACHE_MISSES);
	if (count != 2) {
		printf("Expected 2 misses before flush, got %d instead\n", count);
		abort();
	}

	printf("Flush block\n");
	flush_line((unsigned long) (ptr + BLOCKN));

	printf("Check dest array for correctness\n");
	check_array(ptr, 2 * BLOCKN);

	count = cache_counter(DRAM_CACHE_HITS);
	if (count != 1) {
		printf("Expected 1 hit after flush, got %d instead\n", count);
		abort();
	}

	count = cache_counter(DRAM_CACHE_MISSES);
	if (count != 3) {
		printf("Expected 3 misses after flush, got %d instead\n", count);
		abort();
	}

	count = cache_counter(DRAM_CACHE_WRITEBACKS);
	if (count != 1) {
		printf("Expected 1 writeback after flush, got %d instead\n", count);
		abort();
	}

	return 0;
}
