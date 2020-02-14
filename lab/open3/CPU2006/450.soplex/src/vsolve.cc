/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
/*                                                                           */
/*                  This file is part of the class library                   */
/*       SoPlex --- the Sequential object-oriented simPlex.                  */
/*                                                                           */
/*    Copyright (C) 1997-1999 Roland Wunderling                              */
/*                  1997-2002 Konrad-Zuse-Zentrum                            */
/*                            fuer Informationstechnik Berlin                */
/*                                                                           */
/*  SoPlex is distributed under the terms of the ZIB Academic Licence.       */
/*                                                                           */
/*  You should have received a copy of the ZIB Academic License              */
/*  along with SoPlex; see the file COPYING. If not email to soplex@zib.de.  */
/*                                                                           */
/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
#ifndef SPEC_CPU
#pragma ident "@(#) $Id: vsolve.cpp,v 1.17 2002/03/10 09:15:41 bzfkocht Exp $"
#endif

#include <assert.h>

#include "spxdefines.h"
#include "clufactor.h"
#include "cring.h"

namespace soplex
{

#define MARKER    1e-100

static const Real verySparseFactor4right = 0.2;
static const Real verySparseFactor4left  = 0.1;

/**@todo The same queueing code is in forest.cpp. This should be unified. 
 */
static void enQueueMax(int* heap, int* size, int elem)
{
   int i, j;

   j = (*size)++;
   while (j > 0)
   {
      i = (j - 1) / 2;
      if (elem > heap[i])
      {
         heap[j] = heap[i];
         j = i;
      }
      else
         break;
   }
   heap[j] = elem;

#ifdef  DEBUGGING
   for (i = 1; i < *size; ++i)
      for (j = 0; j < i; ++j)
         assert(heap[i] != heap[j]);
#endif  /* DEBUGGING */
}

static int deQueueMax(int* heap, int* size)
{
   int e, elem;
   int i, j, s;
   int e1, e2;

   elem = *heap;
   e = heap[s = --(*size)];
   --s;
   for (j = 0, i = 1; i < s; i = 2 * j + 1)
   {
      e1 = heap[i];
      e2 = heap[i + 1];
      if (e1 > e2)
      {
         if (e < e1)
         {
            heap[j] = e1;
            j = i;
         }
         else
         {
            heap[j] = e;
            return elem;
         }
      }
      else
      {
         if (e < e2)
         {
            heap[j] = e2;
            j = i + 1;
         }
         else
         {
            heap[j] = e;
            return elem;
         }
      }
   }

   if (i < *size && e < heap[i])
   {
      heap[j] = heap[i];
      j = i;
   }

   heap[j] = e;
   return elem;
}

static void enQueueMin(int* heap, int* size, int elem)
{
   int i, j;

   j = (*size)++;
   while (j > 0)
   {
      i = (j - 1) / 2;
      if (elem < heap[i])
      {
         heap[j] = heap[i];
         j = i;
      }
      else
         break;
   }
   heap[j] = elem;

#ifdef  DEBUGGING
   for (i = 1; i < *size; ++i)
      for (j = 0; j < i; ++j)
         assert(heap[i] != heap[j]);
#endif  /* DEBUGGING */
}

static int deQueueMin(int* heap, int* size)
{
   int e, elem;
   int i, j, s;
   int e1, e2;

   elem = *heap;
   e = heap[s = --(*size)];
   --s;
   for (j = 0, i = 1; i < s; i = 2 * j + 1)
   {
      e1 = heap[i];
      e2 = heap[i + 1];
      if (e1 < e2)
      {
         if (e > e1)
         {
            heap[j] = e1;
            j = i;
         }
         else
         {
            heap[j] = e;
            return elem;
         }
      }
      else
      {
         if (e > e2)
         {
            heap[j] = e2;
            j = i + 1;
         }
         else
         {
            heap[j] = e;
            return elem;
         }
      }
   }

   if (i < *size && e > heap[i])
   {
      heap[j] = heap[i];
      j = i;
   }

   heap[j] = e;
   return elem;
}


int CLUFactor::vSolveLright(Real* vec, int* ridx, int rn, Real eps)
{
   METHOD( "CLUFactor::vSolveLright()" );
   int i, j, k, n;
   int end;
   Real x;
   Real *lval, *val;
   int *lrow, *lidx, *idx;
   int *lbeg;

   lval = l.val;
   lidx = l.idx;
   lrow = l.row;
   lbeg = l.start;

   end = l.firstUpdate;
   for (i = 0; i < end; ++i)
   {
      x = vec[lrow[i]];
      if (isNotZero(x, eps))
      {
         k = lbeg[i];
         idx = &(lidx[k]);
         val = &(lval[k]);
         for (j = lbeg[i + 1]; j > k; --j)
         {
            ridx[rn] = n = *idx++;
            rn += (vec[n] == 0);
            vec[n] -= x * (*val++);
            vec[n] += MARKER * (vec[n] == 0);
         }
      }
   }

   if (l.updateType)                     /* Forest-Tomlin Updates */
   {
      end = l.firstUnused;
      for (; i < end; ++i)
      {
         x = 0;
         k = lbeg[i];
         idx = &(lidx[k]);
         val = &(lval[k]);
         for (j = lbeg[i + 1]; j > k; --j)
            x += vec[*idx++] * (*val++);
         ridx[rn] = j = lrow[i];
         rn += (vec[j] == 0);
         vec[j] -= x;
         vec[j] += MARKER * (vec[j] == 0);
      }
   }
   return rn;
}


void CLUFactor::vSolveLright2(
   Real* vec, int* ridx, int* rnptr, Real eps,
   Real* vec2, int* ridx2, int* rn2ptr, Real eps2)
{
   METHOD( "CLUFactor::vSolveLright2()" );
   int i, j, k, n;
   int end;
   Real x, y;
   Real x2, y2;
   Real *lval, *val;
   int *lrow, *lidx, *idx;
   int *lbeg;

   int rn = *rnptr;
   int rn2 = *rn2ptr;

   lval = l.val;
   lidx = l.idx;
   lrow = l.row;
   lbeg = l.start;

   end = l.firstUpdate;
   for (i = 0; i < end; ++i)
   {
      j = lrow[i];
      x2 = vec2[j];
      x = vec[j];
      if (isNotZero(x, eps))
      {
         k = lbeg[i];
         idx = &(lidx[k]);
         val = &(lval[k]);
         if (isNotZero(x2, eps2))
         {
            for (j = lbeg[i + 1]; j > k; --j)
            {
               ridx[rn] = ridx2[rn2] = n = *idx++;
               y = vec[n];
               y2 = vec2[n];
               rn += (y == 0);
               rn2 += (y2 == 0);
               y -= x * (*val);
               y2 -= x2 * (*val++);
               vec[n] = y + MARKER * (y == 0);
               vec2[n] = y2 + MARKER * (y2 == 0);
            }
         }
         else
         {
            for (j = lbeg[i + 1]; j > k; --j)
            {
               ridx[rn] = n = *idx++;
               y = vec[n];
               rn += (y == 0);
               y -= x * (*val++);
               vec[n] = y + MARKER * (y == 0);
            }
         }
      }
      else if (isNotZero(x2, eps2))
      {
         k = lbeg[i];
         idx = &(lidx[k]);
         val = &(lval[k]);
         for (j = lbeg[i + 1]; j > k; --j)
         {
            ridx2[rn2] = n = *idx++;
            y2 = vec2[n];
            rn2 += (y2 == 0);
            y2 -= x2 * (*val++);
            vec2[n] = y2 + MARKER * (y2 == 0);
         }
      }
   }

   if (l.updateType)                     /* Forest-Tomlin Updates */
   {
      end = l.firstUnused;
      for (; i < end; ++i)
      {
         x = x2 = 0;
         k = lbeg[i];
         idx = &(lidx[k]);
         val = &(lval[k]);
         for (j = lbeg[i + 1]; j > k; --j)
         {
            x += vec[*idx] * (*val);
            x2 += vec2[*idx++] * (*val++);
         }
         ridx[rn] = ridx2[rn2] = j = lrow[i];
         rn += (vec[j] == 0);
         rn2 += (vec2[j] == 0);
         vec[j] -= x;
         vec2[j] -= x2;
         vec[j] += MARKER * (vec[j] == 0);
         vec2[j] += MARKER * (vec2[j] == 0);
      }
   }

   *rnptr = rn;
   *rn2ptr = rn2;
}


int CLUFactor::vSolveUright(Real* vec, int* vidx,
   Real* rhs, int* ridx, int rn, Real eps)
{
   METHOD( "CLUFactor::vSolveUright()" );
   int i, j, k, r, c, n;
   int *rorig, *corig;
   int *rperm;
   int *cidx, *clen, *cbeg;
   Real *cval;
   Real x, y;

   int *idx;
   Real *val;

   rorig = row.orig;
   corig = col.orig;
   rperm = row.perm;

   cidx = u.col.idx;
   cval = u.col.val;
   clen = u.col.len;
   cbeg = u.col.start;

   n = 0;

   while (rn > 0)
   {
      /*      Find nonzero with highest permuted row index and setup i and r
       */
      i = deQueueMax(ridx, &rn);
      r = rorig[i];

      x = diag[r] * rhs[r];
      rhs[r] = 0;
      if (isNotZero(x, eps))
      {
         c = corig[i];
         vidx[n++] = c;
         vec[c] = x;
         val = &cval[cbeg[c]];
         idx = &cidx[cbeg[c]];
         j = clen[c];
         while (j-- > 0)
         {
            k = *idx++;
            y = rhs[k];
            if (y == 0)
            {
               y = -x * (*val++);
               if (isNotZero(y, eps))
               {
                  rhs[k] = y;
                  enQueueMax(ridx, &rn, rperm[k]);
               }
            }
            else
            {
               y -= x * (*val++);
               y += MARKER * (y == 0);
               rhs[k] = y;
            }
         }

         if (rn > i*verySparseFactor4right)
         {                                   /* continue with dense case */
            for (i = *ridx; i >= 0; --i)
            {
               r = rorig[i];
               x = diag[r] * rhs[r];
               rhs[r] = 0;
               if (isNotZero(x, eps))
               {
                  c = corig[i];
                  vidx[n++] = c;
                  vec[c] = x;
                  val = &cval[cbeg[c]];
                  idx = &cidx[cbeg[c]];
                  j = clen[c];
                  while (j-- > 0)
                     rhs[*idx++] -= x * (*val++);
               }
            }
            break;
         }
      }
   }

   return n;
}


void CLUFactor::vSolveUrightNoNZ(Real* vec,
   Real* rhs, int* ridx, int rn, Real eps)
{
   METHOD( "CLUFactor::vSolveUrightNoNZ()" );
   int i, j, k, r, c;
   int *rorig, *corig;
   int *rperm;
   int *cidx, *clen, *cbeg;
   Real *cval;
   Real x, y;

   int *idx;
   Real *val;

   rorig = row.orig;
   corig = col.orig;
   rperm = row.perm;

   cidx = u.col.idx;
   cval = u.col.val;
   clen = u.col.len;
   cbeg = u.col.start;

   while (rn > 0)
   {
      if (rn > *ridx * verySparseFactor4right)
      {                                       /* continue with dense case */
         for (i = *ridx; i >= 0; --i)
         {
            r = rorig[i];
            x = diag[r] * rhs[r];
            rhs[r] = 0;
            if (isNotZero(x, eps))
            {
               c = corig[i];
               vec[c] = x;
               val = &cval[cbeg[c]];
               idx = &cidx[cbeg[c]];
               j = clen[c];
               while (j-- > 0)
                  rhs[*idx++] -= x * (*val++);
            }
         }
         break;
      }

      /*      Find nonzero with highest permuted row index and setup i and r
       */
      i = deQueueMax(ridx, &rn);
      r = rorig[i];

      x = diag[r] * rhs[r];
      rhs[r] = 0;
      if (isNotZero(x, eps))
      {
         c = corig[i];
         vec[c] = x;
         val = &cval[cbeg[c]];
         idx = &cidx[cbeg[c]];
         j = clen[c];
         while (j-- > 0)
         {
            k = *idx++;
            y = rhs[k];
            if (y == 0)
            {
               y = -x * (*val++);
               if (isNotZero(y, eps))
               {
                  rhs[k] = y;
                  enQueueMax(ridx, &rn, rperm[k]);
               }
            }
            else
            {
               y -= x * (*val++);
               y += MARKER * (y == 0);
               rhs[k] = y;
            }
         }
      }
   }
}


int CLUFactor::vSolveUright2(
   Real* vec, int* vidx, Real* rhs, int* ridx, int rn, Real eps,
   Real* vec2, Real* rhs2, int* ridx2, int rn2, Real eps2)
{
   METHOD( "CLUFactor::vSolveUright2()" );
   int i, j, k, r, c, n;
   int *rorig, *corig;
   int *rperm;
   int *cidx, *clen, *cbeg;
   Real *cval;
   Real x, y;
   Real x2, y2;

   int *idx;
   Real *val;

   rorig = row.orig;
   corig = col.orig;
   rperm = row.perm;

   cidx = u.col.idx;
   cval = u.col.val;
   clen = u.col.len;
   cbeg = u.col.start;

   n = 0;

   while (rn + rn2 > 0)
   {
      /*      Find nonzero with highest permuted row index and setup i and r
       */
      if (rn <= 0)
         i = deQueueMax(ridx2, &rn2);
      else if (rn2 <= 0)
         i = deQueueMax(ridx, &rn);
      else if (*ridx2 > *ridx)
         i = deQueueMax(ridx2, &rn2);
      else if (*ridx2 < *ridx)
         i = deQueueMax(ridx, &rn);
      else
      {
         i = deQueueMax(ridx, &rn);
         i = deQueueMax(ridx2, &rn2);
      }
      r = rorig[i];

      x = diag[r] * rhs[r];
      x2 = diag[r] * rhs2[r];
      rhs[r] = 0;
      rhs2[r] = 0;
      if (isNotZero(x, eps))
      {
         c = corig[i];
         vidx[n++] = c;
         vec[c] = x;
         vec2[c] = x2;
         val = &cval[cbeg[c]];
         idx = &cidx[cbeg[c]];
         j = clen[c];
         if (isNotZero(x2, eps2))
         {
            while (j-- > 0)
            {
               k = *idx++;
               y2 = rhs2[k];
               if (y2 == 0)
               {
                  y2 = -x2 * (*val);
                  if (isNotZero(y2, eps2))
                  {
                     rhs2[k] = y2;
                     enQueueMax(ridx2, &rn2, rperm[k]);
                  }
               }
               else
               {
                  y2 -= x2 * (*val);
                  rhs2[k] = y2 + MARKER * (y2 == 0);
               }
               y = rhs[k];
               if (y == 0)
               {
                  y = -x * (*val++);
                  if (isNotZero(y, eps))
                  {
                     rhs[k] = y;
                     enQueueMax(ridx, &rn, rperm[k]);
                  }
               }
               else
               {
                  y -= x * (*val++);
                  y += MARKER * (y == 0);
                  rhs[k] = y;
               }
            }
         }
         else
         {
            while (j-- > 0)
            {
               k = *idx++;
               y = rhs[k];
               if (y == 0)
               {
                  y = -x * (*val++);
                  if (isNotZero(y, eps))
                  {
                     rhs[k] = y;
                     enQueueMax(ridx, &rn, rperm[k]);
                  }
               }
               else
               {
                  y -= x * (*val++);
                  y += MARKER * (y == 0);
                  rhs[k] = y;
               }
            }
         }
      }
      else if (isNotZero(x2, eps2))
      {
         c = corig[i];
         vec2[c] = x2;
         val = &cval[cbeg[c]];
         idx = &cidx[cbeg[c]];
         j = clen[c];
         while (j-- > 0)
         {
            k = *idx++;
            y2 = rhs2[k];
            if (y2 == 0)
            {
               y2 = -x2 * (*val++);
               if (isNotZero(y2, eps2))
               {
                  rhs2[k] = y2;
                  enQueueMax(ridx2, &rn2, rperm[k]);
               }
            }
            else
            {
               y2 -= x2 * (*val++);
               rhs2[k] = y2 + MARKER * (y2 == 0);
            }
         }
      }

      if (rn + rn2 > i*verySparseFactor4right)
      {                                       /* continue with dense case */
         if (*ridx > *ridx2)
            i = *ridx;
         else
            i = *ridx2;
         for (; i >= 0; --i)
         {
            r = rorig[i];
            x = diag[r] * rhs[r];
            x2 = diag[r] * rhs2[r];
            rhs[r] = 0;
            rhs2[r] = 0;
            if (isNotZero(x2, eps2))
            {
               c = corig[i];
               vec2[c] = x2;
               val = &cval[cbeg[c]];
               idx = &cidx[cbeg[c]];
               j = clen[c];
               if (isNotZero(x, eps))
               {
                  vidx[n++] = c;
                  vec[c] = x;
                  while (j-- > 0)
                  {
                     rhs[*idx] -= x * (*val);
                     rhs2[*idx++] -= x2 * (*val++);
                  }
               }
               else
               {
                  while (j-- > 0)
                     rhs2[*idx++] -= x2 * (*val++);
               }
            }
            else if (isNotZero(x, eps))
            {
               c = corig[i];
               vidx[n++] = c;
               vec[c] = x;
               val = &cval[cbeg[c]];
               idx = &cidx[cbeg[c]];
               j = clen[c];
               while (j-- > 0)
                  rhs[*idx++] -= x * (*val++);
            }
         }
         break;
      }
   }

   return n;
}

int CLUFactor::vSolveUpdateRight(Real* vec, int* ridx, int n, Real eps)
{
   METHOD( "CLUFactor::vSolveUpdateRight()" );
   int i, j, k;
   int end;
   Real x, y;
   Real *lval, *val;
   int *lrow, *lidx, *idx;
   int *lbeg;

   assert(!l.updateType);               /* no Forest-Tomlin Updates */

   lval = l.val;
   lidx = l.idx;
   lrow = l.row;
   lbeg = l.start;
   end = l.firstUnused;

   for (i = l.firstUpdate; i < end; ++i)
   {
      x = vec[lrow[i]];
      if (isNotZero(x, eps))
      {
         k = lbeg[i];
         idx = &(lidx[k]);
         val = &(lval[k]);
         for (j = lbeg[i + 1]; j > k; --j)
         {
            int m = ridx[n] = *idx++;
            y = vec[m];
            n += (y == 0);
            y = y - x * (*val++);
            vec[m] = y + (y == 0) * MARKER;
         }
      }
   }
   return n;
}

void CLUFactor::vSolveUpdateRightNoNZ(Real* vec, Real /*eps*/)
{
   METHOD( "CLUFactor::vSolveUpdateRightNoNZ()" );
   int i, j, k;
   int end;
   Real x;
   Real *lval, *val;
   int *lrow, *lidx, *idx;
   int *lbeg;

   assert(!l.updateType);               /* no Forest-Tomlin Updates */

   lval = l.val;
   lidx = l.idx;
   lrow = l.row;
   lbeg = l.start;

   end = l.firstUnused;
   for (i = l.firstUpdate; i < end; ++i)
   {
      if ((x = vec[lrow[i]]) != 0.0)
      {
         k = lbeg[i];
         idx = &(lidx[k]);
         val = &(lval[k]);
         for (j = lbeg[i + 1]; j > k; --j)
            vec[*idx++] -= x * (*val++);
      }
   }
}


int CLUFactor::vSolveRight4update(Real eps,
   Real* vec, int* idx,                       /* result */
   Real* rhs, int* ridx, int rn,              /* rhs    */
   Real* forest, int* forestNum, int* forestIdx)
{
   METHOD( "CLUFactor::vSolveRight4update()" );
   rn = vSolveLright(rhs, ridx, rn, eps);

   /*  turn index list into a heap
    */
   if (forest)
   {
      Real x;
      int i, j, k;
      int* rperm;
      int* it = forestIdx;

      rperm = row.perm;

      for (i = j = 0; i < rn; ++i)
      {
         k = ridx[i];
         x = rhs[k];
         if (isNotZero(x, eps))
         {
            enQueueMax(ridx, &j, rperm[*it++ = k]);
            forest[k] = x;
         }
         else
            rhs[k] = 0;
      }
      *forestNum = rn = j;
   }
   else
   {
      Real x;
      int i, j, k;
      int* rperm;

      rperm = row.perm;
    
      for (i = j = 0; i < rn; ++i)
      {
         k = ridx[i];
         x = rhs[k];
         if (isNotZero(x, eps))
            enQueueMax(ridx, &j, rperm[k]);
         else
            rhs[k] = 0;
      }
      rn = j;
   }

   rn = vSolveUright(vec, idx, rhs, ridx, rn, eps);
   if (!l.updateType)            /* no Forest-Tomlin Updates */
      rn = vSolveUpdateRight(vec, idx, rn, eps);
   return rn;
}

int CLUFactor::vSolveRight4update2(Real eps,
   Real* vec, int* idx,                  /* result1 */
   Real* rhs, int* ridx, int rn,         /* rhs1    */
   Real* vec2, Real eps2,              /* result2 */
   Real* rhs2, int* ridx2, int rn2,      /* rhs2    */
   Real* forest, int* forestNum, int* forestIdx)
{
   METHOD( "CLUFactor::vSolveRight4update2()" );
   /*
   rn  = vSolveLright(rhs,  ridx,  rn,  eps);
   rn2 = vSolveLright(rhs2, ridx2, rn2, eps2);
    */
   vSolveLright2(rhs, ridx, &rn, eps, rhs2, ridx2, &rn2, eps2);

   /*  turn index list into a heap
    */
   if (forest)
   {
      Real x;
      int i, j, k;
      int* rperm;
      int* it = forestIdx;

      rperm = row.perm;
      for (i = j = 0; i < rn; ++i)
      {
         k = ridx[i];
         x = rhs[k];
         if (isNotZero(x, eps))
         {
            enQueueMax(ridx, &j, rperm[*it++ = k]);
            forest[k] = x;
         }
         else
            rhs[k] = 0;
      }
      *forestNum = rn = j;
   }
   else
   {
      Real x;
      int i, j, k;
      int* rperm;

      rperm = row.perm;
      for (i = j = 0; i < rn; ++i)
      {
         k = ridx[i];
         x = rhs[k];
         if (isNotZero(x, eps))
            enQueueMax(ridx, &j, rperm[k]);
         else
            rhs[k] = 0;
      }
      rn = j;
   }
   if (rn2 > thedim*verySparseFactor4right)
   {
      ridx2[0] = thedim - 1;
      /* ridx2[1] = thedim - 2; */
   }
   else
   {
      Real x;
      /*      Real  maxabs; */
      int i, j, k;
      int* rperm;

      /*      maxabs = 1;    */
      rperm = row.perm;
      for (i = j = 0; i < rn2; ++i)
      {
         k = ridx2[i];
         x = rhs2[k];
         if (x < -eps2)
         {
            /*              maxabs = (maxabs < -x) ? -x : maxabs;  */
            enQueueMax(ridx2, &j, rperm[k]);
         }
         else if (x > eps2)
         {
            /*              maxabs = (maxabs < x) ? x : maxabs;    */
            enQueueMax(ridx2, &j, rperm[k]);
         }
         else
            rhs2[k] = 0;
      }
      rn2 = j;
      /*      eps2 = maxabs * eps2;  */
   }

   rn = vSolveUright(vec, idx, rhs, ridx, rn, eps);
   vSolveUrightNoNZ(vec2, rhs2, ridx2, rn2, eps2);

   /*
   rn = vSolveUright2(vec, idx, rhs, ridx, rn, eps, vec2, rhs2, ridx2, rn2, eps2);
   */

   if (!l.updateType)            /* no Forest-Tomlin Updates */
   {
      rn = vSolveUpdateRight(vec, idx, rn, eps);
      vSolveUpdateRightNoNZ(vec2, eps2);
   }

   return rn;
}

void CLUFactor::vSolveRightNoNZ(
   Real* vec2, Real eps2,              /* result2 */
   Real* rhs2, int* ridx2, int rn2)    /* rhs2    */
{
   METHOD( "CLUFactor::vSolveRightNoNZ()" );
   rn2 = vSolveLright(rhs2, ridx2, rn2, eps2);

   if (rn2 > thedim*verySparseFactor4right)
   {
      *ridx2 = thedim - 1;
   }
   else
   {
      Real x;
      /*      Real  maxabs; */
      int i, j, k;
      int* rperm;

      /*      maxabs = 1;    */
      rperm = row.perm;
      for (i = j = 0; i < rn2; ++i)
      {
         k = ridx2[i];
         x = rhs2[k];
         if (x < -eps2)
         {
            /*              maxabs = (maxabs < -x) ? -x : maxabs;  */
            enQueueMax(ridx2, &j, rperm[k]);
         }
         else if (x > eps2)
         {
            /*              maxabs = (maxabs < x) ? x : maxabs;    */
            enQueueMax(ridx2, &j, rperm[k]);
         }
         else
            rhs2[k] = 0;
      }
      rn2 = j;
      /*      eps2 = maxabs * eps2;  */
   }

   vSolveUrightNoNZ(vec2, rhs2, ridx2, rn2, eps2);

   if (!l.updateType)            /* no Forest-Tomlin Updates */
      vSolveUpdateRightNoNZ(vec2, eps2);
}


/*****************************************************************************/

int CLUFactor::solveUleft(Real eps,
                Real* vec, int* vecidx,
                Real* rhs, int* rhsidx, int rhsn)
{
   METHOD( "CLUFactor::solveUleft()" );
   Real x, y;
   int i, j, k, n, r, c;
   int *rorig, *corig, *cperm;
   int *ridx, *rlen, *rbeg, *idx;
   Real *rval, *val;

   rorig = row.orig;
   corig = col.orig;
   cperm = col.perm;

   /*  move rhsidx to a heap
    */
   for (i = 0; i < rhsn;)
      enQueueMin(rhsidx, &i, cperm[rhsidx[i]]);

   ridx = u.row.idx;
   rval = u.row.val;
   rlen = u.row.len;
   rbeg = u.row.start;

   n = 0;

   while (rhsn > 0)
   {
      i = deQueueMin(rhsidx, &rhsn);
      c = corig[i];
      x = rhs[c];
      rhs[c] = 0;
      if (isNotZero(x, eps))
      {
         r = rorig[i];
         vecidx[n++] = r;
         x *= diag[r];
         vec[r] = x;
         k = rbeg[r];
         idx = &ridx[k];
         val = &rval[k];
         for (int m = rlen[r]; m; --m)
         {
            j = *idx++;
            y = rhs[j];
            if (y == 0)
            {
               y = -x * (*val++);
               if (isNotZero(y, eps))
               {
                  rhs[j] = y;
                  enQueueMin(rhsidx, &rhsn, cperm[j]);
               }
            }
            else
            {
               y -= x * (*val++);
               rhs[j] = y + MARKER * (y == 0);
            }
         }
      }
   }

   return n;
}


void CLUFactor::solveUleftNoNZ(Real eps, Real* vec,
   Real* rhs, int* rhsidx, int rhsn)
{
   METHOD( "CLUFactor::solveUleftNoNZ()" );
   Real x, y;
   int i, j, k, r, c;
   int *rorig, *corig, *cperm;
   int *ridx, *rlen, *rbeg, *idx;
   Real *rval, *val;

   rorig = row.orig;
   corig = col.orig;
   cperm = col.perm;

   /*  move rhsidx to a heap
    */
   for (i = 0; i < rhsn;)
      enQueueMin(rhsidx, &i, cperm[rhsidx[i]]);

   ridx = u.row.idx;
   rval = u.row.val;
   rlen = u.row.len;
   rbeg = u.row.start;

   while (rhsn > 0)
   {
      i = deQueueMin(rhsidx, &rhsn);
      c = corig[i];
      x = rhs[c];
      rhs[c] = 0;

      if (isNotZero(x, eps))
      {
         r = rorig[i];
         x *= diag[r];
         vec[r] = x;
         k = rbeg[r];
         idx = &ridx[k];
         val = &rval[k];
         for (int m = rlen[r]; m; --m)
         {
            j = *idx++;
            y = rhs[j];
            if (y == 0)
            {
               y = -x * (*val++);
               if (isNotZero(y, eps))
               {
                  rhs[j] = y;
                  enQueueMin(rhsidx, &rhsn, cperm[j]);
               }
            }
            else
            {
               y -= x * (*val++);
               rhs[j] = y + MARKER * (y == 0);
            }
         }
      }
   }
}


int CLUFactor::solveLleftForest(Real eps, Real* vec, int* nonz, int n)
{
   METHOD( "CLUFactor::solveLleftForest()" );
   int i, j, k, end;
   Real x, y;
   Real *val, *lval;
   int *idx, *lidx, *lrow, *lbeg;

   lval = l.val;
   lidx = l.idx;
   lrow = l.row;
   lbeg = l.start;
   end = l.firstUpdate;

   for (i = l.firstUnused - 1; i >= end; --i)
   {
      if ((x = vec[lrow[i]]) != 0.0)
      {
         k = lbeg[i];
         val = &lval[k];
         idx = &lidx[k];
         for (j = lbeg[i + 1]; j > k; --j)
         {
            int m = *idx++;
            y = vec[m];
            if (y == 0)
            {
               y = -x * (*val++);
               if (isNotZero(y, eps))
               {
                  vec[m] = y;
                  nonz[n++] = m;
               }
            }
            else
            {
               y -= x * (*val++);
               vec[m] = y + MARKER * (y == 0);
            }
         }
      }
   }

   return n;
}


void CLUFactor::solveLleftForestNoNZ(Real* vec)
{
   METHOD( "CLUFactor::solveLleftForestNoNZ()" );
   int i, j, k, end;
   Real x;
   Real *val, *lval;
   int *idx, *lidx, *lrow, *lbeg;

   lval = l.val;
   lidx = l.idx;
   lrow = l.row;
   lbeg = l.start;
   end = l.firstUpdate;

   for (i = l.firstUnused - 1; i >= end; --i)
   {
      if ((x = vec[lrow[i]]) != 0.0)
      {
         k = lbeg[i];
         val = &lval[k];
         idx = &lidx[k];
         for (j = lbeg[i + 1]; j > k; --j)
            vec[*idx++] -= x * (*val++);
      }
   }
}


int CLUFactor::solveLleft(Real eps, Real* vec, int* nonz, int rn)
{
   METHOD( "CLUFactor::solveLleft()" );
   int i, j, k, n;
   int r;
   Real x, y;
   Real *rval, *val;
   int *ridx, *idx;
   int *rbeg;
   int *rorig, *rperm;
   int *last;

   ridx = l.ridx;
   rval = l.rval;
   rbeg = l.rbeg;
   rorig = l.rorig;
   rperm = l.rperm;
   n = 0;

   i = l.firstUpdate - 1;
#ifndef WITH_L_ROWS
#error Not yet implemented, define WITH_L_LOOPS
   Real* lval = l.val;
   int*    lidx = l.idx;
   int*    lrow = l.row;
   int*    lbeg = l.start;

   for (; i >= 0; --i)
   {
      k = lbeg[i];
      val = &lval[k];
      idx = &lidx[k];
      x = 0;
      for (j = lbeg[i + 1]; j > k; --j)
         x += vec[*idx++] * (*val++);
      vec[lrow[i]] -= x;
   }
#else
   /*  move rhsidx to a heap
   */
   for (i = 0; i < rn;)
      enQueueMax(nonz, &i, rperm[nonz[i]]);
   last = nonz + thedim;

   while (rn > 0)
   {
      i = deQueueMax(nonz, &rn);
      r = rorig[i];
      x = vec[r];
      if (isNotZero(x, eps))
      {
         *(--last) = r;
         n++;
         k = rbeg[r];
         j = rbeg[r + 1] - k;
         val = &rval[k];
         idx = &ridx[k];
         while (j-- > 0)
         {
            assert(l.rperm[*idx] < i);
            int m = *idx++;
            y = vec[m];
            if (y == 0)
            {
               y = -x * *val++;
               if (isNotZero(y, eps))
               {
                  vec[m] = y;
                  enQueueMax(nonz, &rn, rperm[m]);
               }
            }
            else
            {
               y -= x * *val++;
               vec[m] = y + MARKER * (y == 0);
            }
         }
      }
      else
         vec[r] = 0;
   }

   for (i = 0; i < n; ++i)
      *nonz++ = *last++;
#endif

   return n;
}


void CLUFactor::solveLleftNoNZ(Real* vec)
{
   METHOD( "CLUFactor::solveLleftNoNZ()" );
   int i, j, k;
   int r;
   Real x;
   Real *rval, *val;
   int *ridx, *idx;
   int *rbeg;
   int* rorig;

   ridx = l.ridx;
   rval = l.rval;
   rbeg = l.rbeg;
   rorig = l.rorig;

#ifndef WITH_L_ROWS
   Real* lval = l.val;
   int*    lidx = l.idx;
   int*    lrow = l.row;
   int*    lbeg = l.start;

   i = l.firstUpdate - 1;
   for (; i >= 0; --i)
   {
      k = lbeg[i];
      val = &lval[k];
      idx = &lidx[k];
      x = 0;
      for (j = lbeg[i + 1]; j > k; --j)
         x += vec[*idx++] * (*val++);
      vec[lrow[i]] -= x;
   }
#else
   for (i = thedim; i--;)
   {
      r = rorig[i];
      x = vec[r];
      if (x != 0.0)
      {
         k = rbeg[r];
         j = rbeg[r + 1] - k;
         val = &rval[k];
         idx = &ridx[k];
         while (j-- > 0)
         {
            assert(l.rperm[*idx] < i);
            vec[*idx++] -= x * *val++;
         }
      }
   }
#endif
}


int CLUFactor::solveUpdateLeft(Real eps, Real* vec, int* nonz, int n)
{
   METHOD( "CLUFactor::solveUpdateLeft()" );
   int i, j, k, end;
   Real x, y;
   Real *lval, *val;
   int *lrow, *lidx, *idx;
   int *lbeg;

   assert(!l.updateType);               /* no Forest-Tomlin Updates! */

   lval = l.val;
   lidx = l.idx;
   lrow = l.row;
   lbeg = l.start;

   end = l.firstUpdate;
   for (i = l.firstUnused - 1; i >= end; --i)
   {
      k = lbeg[i];
      val = &lval[k];
      idx = &lidx[k];
      x = 0;
      for (j = lbeg[i + 1]; j > k; --j)
         x += vec[*idx++] * (*val++);
      k = lrow[i];
      y = vec[k];
      if (y == 0)
      {
         y = -x;
         if (isNotZero(y, eps))
         {
            nonz[n++] = k;
            vec[k] = y;
         }
      }
      else
      {
         y -= x;
         vec[k] = y + MARKER * (y == 0);
      }
   }

   return n;
}

int CLUFactor::vSolveLeft(Real eps,
   Real* vec, int* idx,                       /* result */
   Real* rhs, int* ridx, int rn)            /* rhs    */
{
   METHOD( "CLUFactor::vSolveLeft()" );
   if (!l.updateType)            /* no Forest-Tomlin Updates */
   {
      rn = solveUpdateLeft(eps, rhs, ridx, rn);
      rn = solveUleft(eps, vec, idx, rhs, ridx, rn);
   }
   else
   {
      rn = solveUleft(eps, vec, idx, rhs, ridx, rn);
      rn = solveLleftForest(eps, vec, idx, rn);
   }
   if (rn + l.firstUpdate > verySparseFactor4left * thedim)
   {
      solveLleftNoNZ(vec);
      return 0;
   }
   else
      return solveLleft(eps, vec, idx, rn);
}

int CLUFactor::vSolveLeft2(Real eps,
   Real* vec, int* idx,                      /* result */
   Real* rhs, int* ridx, int rn,             /* rhs    */
   Real* vec2,                               /* result2 */
   Real* rhs2, int* ridx2, int rn2)        /* rhs2    */
{
   METHOD( "CLUFactor::vSolveLeft2()" );
   if (!l.updateType)            /* no Forest-Tomlin Updates */
   {
      rn = solveUpdateLeft(eps, rhs, ridx, rn);
      rn = solveUleft(eps, vec, idx, rhs, ridx, rn);
      rn2 = solveUpdateLeft(eps, rhs2, ridx2, rn2);
      solveUleftNoNZ (eps, vec2, rhs2, ridx2, rn2);
   }
   else
   {
      rn = solveUleft(eps, vec, idx, rhs, ridx, rn);
      rn = solveLleftForest(eps, vec, idx, rn);
      solveUleftNoNZ(eps, vec2, rhs2, ridx2, rn2);
      solveLleftForestNoNZ(vec2);
   }
   rn = solveLleft(eps, vec, idx, rn);
   solveLleftNoNZ (vec2);

   return rn;
}

void CLUFactor::vSolveLeftNoNZ(Real eps,
   Real* vec2,                            /* result2 */
   Real* rhs2, int* ridx2, int rn2)     /* rhs2    */
{
   METHOD( "CLUFactor::vSolveLeftNoNZ()" );
   if (!l.updateType)            /* no Forest-Tomlin Updates */
   {
      rn2 = solveUpdateLeft(eps, rhs2, ridx2, rn2);
      solveUleftNoNZ (eps, vec2, rhs2, ridx2, rn2);
   }
   else
   {
      solveUleftNoNZ(eps, vec2, rhs2, ridx2, rn2);
      solveLleftForestNoNZ(vec2);
   }
   solveLleftNoNZ (vec2);
}
} // namespace soplex

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
