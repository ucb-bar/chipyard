#ifndef _MT_VVADD_H
#define _MT_VVADD_H

#include <stddef.h>

//--------------------------------------------------------------------------
// Input/Reference Data
#include "dataset.h"

extern void vvadd_opt(int, int, size_t, const data_t *, const data_t *, data_t *);

#define VVADD_FUNC vvadd_opt

#endif /* _MT_VVADD_H */
