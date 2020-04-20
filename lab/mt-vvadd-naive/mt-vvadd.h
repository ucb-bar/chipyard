#ifndef _MT_VVADD_H
#define _MT_VVADD_H

#include <stddef.h>

//--------------------------------------------------------------------------
// Input/Reference Data
#include "dataset.h"

extern void vvadd_naive(int, int, size_t, const data_t *, const data_t *, data_t *);

#define VVADD_FUNC vvadd_naive

#endif /* _MT_VVADD_H */
