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
#pragma ident "@(#) $Id: forest.cpp,v 1.18 2002/03/11 17:43:56 bzfkocht Exp $"
#endif

#include <assert.h>

#include "spxdefines.h"
#include "clufactor.h"
#include "cring.h"
#include "spxalloc.h"

namespace soplex
{

/* This number is used to decide wether a value is zero
 * or was explicitly set to zero.
 */
#define MARKER     1e-100

/** This constant is used to discriminate to small elements
 *  @todo This was 1e-12. It is not clear why it is larger then the
 *        usual 1e-16.
 */
#define TOOSMALL   Param::epsilon()  // was 1e-12

static const Real verySparseFactor = 0.001;

/*****************************************************************************/
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

#ifndef NDEBUG
   for (i = 1; i < *size; ++i)
      for (j = 0; j < i; ++j)
         assert(heap[i] != heap[j]);
#endif  /* NDEBUG */
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

/*****************************************************************************/
/*
 *      Perform garbage collection on column file
 */
void CLUFactor::forestPackColumns()
{
   METHOD( "CLUFactor::forestPackColumns()" );
   int n, i, j, colno;
   Dring *ring, *list;

   Real *cval = u.col.val;
   int *cidx = u.col.idx;
   int *clen = u.col.len;
   int *cmax = u.col.max;
   int *cbeg = u.col.start;

   n = 0;
   list = &u.col.list;
   for (ring = list->next; ring != list; ring = ring->next)
   {
      colno = ring->idx;
      if (cbeg[colno] != n)
      {
         do
         {
            colno = ring->idx;
            i = cbeg[colno];
            cbeg[colno] = n;
            cmax[colno] = clen[colno];
            j = i + clen[colno];
            for (; i < j; ++i)
            {
               cval[n] = cval[i];
               cidx[n++] = cidx[i];
            }
            ring = ring->next;
         }
         while (ring != list);
         goto terminatePackColumns;
      }
      n += clen[colno];
      cmax[colno] = clen[colno];
   }

terminatePackColumns :
   u.col.used = n;
   u.col.max[thedim] = 0;
}

/*
 *      Ensure that column memory is at least size.
 */
void CLUFactor::forestMinColMem(int size)
{
   METHOD( "CLUFactor::forestMinColMem()" );
   if (u.col.size < size)
   {
      u.col.size = size;
      spx_realloc(u.col.idx, size);
      spx_realloc(u.col.val, size);
   }
}


/*
 *      Make column col of fac large enough to hold len nonzeros.
 */
void CLUFactor::forestReMaxCol(int p_col, int len)
{
   METHOD( "CLUFactor::forestReMaxCol()" );
   assert(u.col.max[p_col] < len);

   if (u.col.elem[p_col].next == &(u.col.list)) /* last in column file */
   {
      int delta = len - u.col.max[p_col];

      if (delta > u.col.size - u.col.used)
      {
         forestPackColumns();
         delta = len - u.col.max[p_col];
         if (u.col.size < colMemMult * u.col.used + len)
            forestMinColMem(int(colMemMult * u.col.used + len));
      }
      assert(delta <= u.col.size - u.col.used
             && "ERROR: could not allocate memory for column file");

      u.col.used += delta;
      u.col.max[p_col] = len;
   }

   else                        /* column must be moved to end of column file */
   {
      int i, j, k;
      int *idx;
      Real *val;
      Dring *ring;

      if (len > u.col.size - u.col.used)
      {
         forestPackColumns();
         if (u.col.size < colMemMult * u.col.used + len)
            forestMinColMem(int(colMemMult * u.col.used + len));
      }
      assert(len <= u.col.size - u.col.used
             && "ERROR: could not allocate memory for column file");

      j = u.col.used;
      i = u.col.start[p_col];
      k = u.col.len[p_col] + i;
      u.col.start[p_col] = j;
      u.col.used += len;

      u.col.max[u.col.elem[p_col].prev->idx] += u.col.max[p_col];
      u.col.max[p_col] = len;
      removeDR(u.col.elem[p_col]);
      ring = u.col.list.prev;
      init2DR (u.col.elem[p_col], *ring);

      idx = u.col.idx;
      val = u.col.val;
      for (; i < k; ++i)
      {
         val[j] = val[i];
         idx[j++] = idx[i];
      }
   }
}

/*****************************************************************************/


void CLUFactor::forestUpdate(int p_col, Real* p_work, int num, int *nonz)
{
   METHOD( "CLUFactor::forestUpdate()" );
   int i, j, k, h, m, n;
   int ll, c, r, rowno;
   Real x;

   Real *lval;
   int *lidx;
   int *lbeg = l.start;

   Real *cval = u.col.val;
   int *cidx = u.col.idx;
   int *cmax = u.col.max;
   int *clen = u.col.len;
   int *cbeg = u.col.start;

   Real *rval = u.row.val;
   int *ridx = u.row.idx;
   int *rmax = u.row.max;
   int *rlen = u.row.len;
   int *rbeg = u.row.start;

   int *rperm = row.perm;
   int *rorig = row.orig;
   int *cperm = col.perm;
   int *corig = col.orig;

   Real l_maxabs = maxabs;
   int dim = thedim;

   /*  Remove column p_col from U
    */
   j = cbeg[p_col];
   i = clen[p_col];
   nzCnt -= i;
   for (i += j - 1; i >= j; --i)
   {
      m = cidx[i];          // remove column p_col from row m
      k = rbeg[m];
      h = --(rlen[m]) + k;  // decrease length of row m
      while (ridx[k] != p_col)
         ++k;
      assert( k <= h );     // k is the position of p_col, h is last position
      ridx[k] = ridx[h];    // store last index at the position of p_col
      rval[k] = rval[h];
   }

   /*  Insert new vector column p_col
    *  thereby determining the highest permuted row index r.
    */
   if (num)
   {
      clen[p_col] = 0;
      if (num > cmax[p_col])
         forestReMaxCol(p_col, num);
      cidx = u.col.idx;
      cval = u.col.val;
      k = cbeg[p_col];
      r = 0;
      for (j = 0; j < num; ++j)
      {
         i = *nonz++;
         x = p_work[i];
         p_work[i] = 0;

         if (fabs(x) > TOOSMALL)
         {
            if (fabs(x) > l_maxabs)
               l_maxabs = fabs(x);

            /* insert to column file */
            assert(k - cbeg[p_col] < cmax[p_col]);
            cval[k] = x;
            cidx[k++] = i;

            /* insert to row file */
            if (rmax[i] <= rlen[i])
            {
               remaxRow(i, rlen[i] + 1);
               rval = u.row.val;
               ridx = u.row.idx;
            }
            h = rbeg[i] + (rlen[i])++;
            rval[h] = x;
            ridx[h] = p_col;

            /* check permuted row index */
            if (rperm[i] > r)
               r = rperm[i];
         }
      }
      nzCnt += (clen[p_col] = k - cbeg[p_col]);
   }
   else
   {
      /*
      clen[col] = 0;
      reMaxCol(fac, col, dim);
       */
      cidx = u.col.idx;
      cval = u.col.val;
      k = cbeg[p_col];
      j = k + cmax[p_col];
      r = 0;
      for (i = 0; i < dim; ++i)
      {
         x = p_work[i];
         p_work[i] = 0;

         if (fabs(x) > TOOSMALL)
         {
            if (fabs(x) > l_maxabs)
               l_maxabs = fabs(x);

            /* insert to column file */
            if (k >= j)
            {
               clen[p_col] = k - cbeg[p_col];
               forestReMaxCol(p_col, dim - i);
               cidx = u.col.idx;
               cval = u.col.val;
               k = cbeg[p_col];
               j = k + cmax[p_col];
               k += clen[p_col];
            }
            assert(k - cbeg[p_col] < cmax[p_col]);
            cval[k] = x;
            cidx[k++] = i;

            /* insert to row file */
            if (rmax[i] <= rlen[i])
            {
               remaxRow(i, rlen[i] + 1);
               rval = u.row.val;
               ridx = u.row.idx;
            }
            h = rbeg[i] + (rlen[i])++;
            rval[h] = x;
            ridx[h] = p_col;

            /* check permuted row index */
            if (rperm[i] > r)
               r = rperm[i];
         }
      }
      nzCnt += (clen[p_col] = k - cbeg[p_col]);
      if (cbeg[p_col] + cmax[p_col] == u.col.used)
      {
         u.col.used -= cmax[p_col];
         cmax[p_col] = clen[p_col];
         u.col.used += cmax[p_col];
      }
   }


   /*  Adjust stages of column and row singletons in U.
    */
   u.lastRowSing = u.lastColSing;

   c = cperm[p_col];
   if (r > c)                         /* Forest Tomlin update */
   {
      /*      update permutations
       */
      j = rorig[c];
      for (i = c; i < r; ++i)
         rorig[i] = rorig[i + 1];
      rorig[r] = j;
      for (i = c; i <= r; ++i)
         rperm[rorig[i]] = i;

      j = corig[c];
      for (i = c; i < r; ++i)
         corig[i] = corig[i + 1];
      corig[r] = j;
      for (i = c; i <= r; ++i)
         cperm[corig[i]] = i;


      rowno = rorig[r];
      j = rbeg[rowno];
      i = rlen[rowno];
      nzCnt -= i;

      if (i < verySparseFactor * (dim - c)) // few nonzeros to be eliminated
      {
         /*  move row r from U to p_work
          */
         num = 0;
         for (i += j - 1; i >= j; --i)
         {
            k = ridx[i];
            p_work[k] = rval[i];
            enQueueMin(nonz, &num, cperm[k]);
            m = --(clen[k]) + cbeg[k];
            for (h = m; cidx[h] != rowno; --h)
              ;
            cidx[h] = cidx[m];
            cval[h] = cval[m];
         }


         /*  Eliminate row r from U to L file
          */
         ll = makeLvec(r - c, rowno);
         lval = l.val;
         lidx = l.idx;

         assert((num == 0) || (nonz != 0));

         /* for(i = c; i < r; ++i)       */
         while (num)
         {
#ifndef NDEBUG
            assert(nonz != 0);

            // The numbers seem to be often 1e-100, is this ok ?
            for (i = 0; i < num; ++i)
               assert(p_work[corig[nonz[i]]] != 0.0);
#endif // NDEBUG
            i = deQueueMin(nonz, &num);
            if (i == r)
               break;
            k = corig[i];

            assert(p_work[k] != 0.0);

            n = rorig[i];
            x = p_work[k] * diag[n];
            lidx[ll] = n;
            lval[ll] = x;
            p_work[k] = 0;
            ll++;

            if (fabs(x) > l_maxabs)
               l_maxabs = fabs(x);

            j = rbeg[n];
            m = rlen[n] + j;
            for (; j < m; ++j)
            {
               int jj = ridx[j];
               Real y = p_work[jj];
               if (y == 0)
                  enQueueMin(nonz, &num, cperm[jj]);
               y -= x * rval[j];
               p_work[jj] = y + (y == 0) * MARKER;
            }
         }
         if (lbeg[l.firstUnused - 1] == ll)
            (l.firstUnused)--;
         else
            lbeg[l.firstUnused] = ll;


         /*  Set diagonal value
          */
         if (i != r)
         {
            stat = SLinSolver::SINGULAR;
            return;
         }
         k = corig[r];
         x = p_work[k];
         diag[rowno] = 1 / x;
         p_work[k] = 0;


         /*  make row large enough to fit all nonzeros.
          */
         if (rmax[rowno] < num)
         {
            rlen[rowno] = 0;
            remaxRow(rowno, num);
            rval = u.row.val;
            ridx = u.row.idx;
         }
         nzCnt += num;

         /*  Insert work to updated row thereby clearing work;
          */
         n = rbeg[rowno];
         for (i = 0; i < num; ++i)
         {
            j = corig[nonz[i]];
            x = p_work[j];
            assert(x != 0.0);
            {
               if (fabs(x) > l_maxabs)
                  l_maxabs = fabs(x);

               ridx[n] = j;
               rval[n] = x;
               p_work[j] = 0;
               ++n;

               if (clen[j] >= cmax[j])
               {
                  forestReMaxCol(j, clen[j] + 1);
                  cidx = u.col.idx;
                  cval = u.col.val;
               }
               cval[cbeg[j] + clen[j]] = x;
               cidx[cbeg[j] + clen[j]++] = rowno;
            }
         }
         rlen[rowno] = n - rbeg[rowno];
      }
      else            /* few nonzeros to be eliminated        */
      {
         /*  move row r from U to p_work
          */
         for (i += j - 1; i >= j; --i)
         {
            k = ridx[i];
            p_work[k] = rval[i];
            m = --(clen[k]) + cbeg[k];
            for (h = m; cidx[h] != rowno; --h)
              ;
            cidx[h] = cidx[m];
            cval[h] = cval[m];
         }


         /*  Eliminate row r from U to L file
          */
         ll = makeLvec(r - c, rowno);
         lval = l.val;
         lidx = l.idx;
         for (i = c; i < r; ++i)
         {
            k = corig[i];
            if (p_work[k])
            {
               n = rorig[i];
               x = p_work[k] * diag[n];
               lidx[ll] = n;
               lval[ll] = x;
               p_work[k] = 0;
               ll++;

               if (fabs(x) > l_maxabs)
                  l_maxabs = fabs(x);

               j = rbeg[n];
               m = rlen[n] + j;
               for (; j < m; ++j)
                  p_work[ridx[j]] -= x * rval[j];
            }
         }
         if (lbeg[l.firstUnused - 1] == ll)
            (l.firstUnused)--;
         else
            lbeg[l.firstUnused] = ll;


         /*  Set diagonal value
          */
         k = corig[r];
         x = p_work[k];
         if (isZero(x))
         {
            stat = SLinSolver::SINGULAR;
            return;
         }
         diag[rowno] = 1 / x;
         p_work[k] = 0;


         /*  count remaining nonzeros in work and make row large enough
          *  to fit them all.
          */
         n = 0;
         for (i = r + 1; i < dim; ++i)
            n += (p_work[corig[i]] != 0.0);
         if (rmax[rowno] < n)
         {
            rlen[rowno] = 0;
            remaxRow(rowno, n);
            rval = u.row.val;
            ridx = u.row.idx;
         }
         nzCnt += n;

         /*  Insert p_work to updated row thereby clearing p_work;
          */
         n = rbeg[rowno];
         for (i = r + 1; i < dim; ++i)
         {
            j = corig[i];
            x = p_work[j];
            if (x != 0.0)
            {
               if (fabs(x) > l_maxabs)
                  l_maxabs = fabs(x);

               ridx[n] = j;
               rval[n] = x;
               p_work[j] = 0;
               ++n;

               if (clen[j] >= cmax[j])
               {
                  forestReMaxCol(j, clen[j] + 1);
                  cidx = u.col.idx;
                  cval = u.col.val;
               }
               cval[cbeg[j] + clen[j]] = x;
               cidx[cbeg[j] + clen[j]++] = rowno;
            }
         }
         rlen[rowno] = n - rbeg[rowno];
      }
   }

   else if (r == c)
   {
      /*  Move diagonal element to diag.  Note, that it must be the last
       *  element, since it has just been inserted above.
       */
      rowno = rorig[r];
      i = rbeg[rowno] + --(rlen[rowno]);
      diag[rowno] = 1 / rval[i];
      for (j = i = --(clen[p_col]) + cbeg[p_col]; cidx[i] != rowno; --i)
        ;
      cidx[i] = cidx[j];
      cval[i] = cval[j];
   }
   else /* r < c */
   {
      stat = SLinSolver::SINGULAR;
      return;
   }
   maxabs = l_maxabs;

   assert(isConsistent());
   stat = SLinSolver::OK;
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
