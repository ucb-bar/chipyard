#ifndef _MT_MATMUL_H
#define _MT_MATMUL_H

#include <stddef.h>

//--------------------------------------------------------------------------
// Input/Reference Data
#include "dataset.h"

extern void matmul_naive(unsigned int, unsigned int, size_t, const data_t *, const data_t *, data_t *);

#define MATMUL_FUNC matmul_naive

#endif /* _MT_VVADD_H */
