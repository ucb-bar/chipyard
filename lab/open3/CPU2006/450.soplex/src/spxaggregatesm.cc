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
#pragma ident "@(#) $Id: spxaggregatesm.cpp,v 1.14 2002/03/03 13:50:33 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <iostream>

#include "spxdefines.h"
#include "spxaggregatesm.h"
#include "dataarray.h"
#include "ssvector.h"
#include "sorter.h"

namespace soplex
{
/**todo Should be moved inside SPxAggregateSM. */
struct RowCnt
{
   int row;
   int size;
};

/**todo Should be moved inside SPxAggregateSM. */
struct Compare1
{
   int operator()(RowCnt i1, RowCnt i2)
   {
      return i1.size - i2.size;
   }
};

int SPxAggregateSM::eliminate(const SVector& row, Real b)
{
   Real x, y;
   int j, k;
   int best = -1;
   Real up = 0;
   Real lo = 0;

   if (row.size() == 2)
   {
      Real xabs, yabs;
      j = row.index(0);
      x = row.value(0);
      k = row.index(1);
      y = row.value(1);
      xabs = (x > 0) ? x : -x;
      yabs = (y > 0) ? y : -y;
      if (xabs > yabs)
      {
         j = row.index(1);
         x = row.value(1);
         k = row.index(0);
         y = row.value(0);
         best = 0;
      }
      else
         best = 1;

      if (y / x > 0)
      {
         if (lp->lower(k) <= -infinity)
            up = infinity;
         else
            up = b / x - y / x * lp->lower(k);
         if (lp->upper(k) >= infinity)
            lo = -infinity;
         else
            lo = b / x - y / x * lp->upper(k);
      }
      else
      {
         if (lp->upper(k) <= -infinity)
            up = infinity;
         else
            up = b / x - y / x * lp->upper(k);
         if (lp->lower(k) >= infinity)
            lo = -infinity;
         else
            lo = b / x - y / x * lp->lower(k);
      }

      if (lo > lp->lower(j))
         lp->changeLower(j, lo);
      if (up < lp->upper(j))
         lp->changeUpper(j, up);
   }

   else
   {
      int okLow, okUp;
      Real maxabs = 0;
      int locnt = 0;
      int upcnt = 0;

      for (j = row.size() - 1; j >= 0; --j)
      {
         x = row.value(j);
         k = row.index(j);
         if (x > 0)
         {
            if (x > maxabs)
               maxabs = x;
            if (lp->upper(k) >= infinity)
               upcnt++;
            else
               up += lp->upper(k) * x;
            if (lp->lower(k) <= -infinity)
               locnt++;
            else
               lo += lp->lower(k) * x;
         }
         else if (x < 0)
         {
            if (-x > maxabs)
               maxabs = -x;
            if (lp->upper(k) >= infinity)
               locnt++;
            else
               lo += lp->upper(k) * x;
            if (lp->lower(k) <= -infinity)
               upcnt++;
            else
               up += lp->lower(k) * x;
         }
      }

      int fill = lp->nCols() + 1;
      maxabs *= stability;
      for (j = row.size() - 1; j >= 0; --j)
      {
         x = row.value(j);
         if (x > maxabs || -x > maxabs)
         {
            k = row.index(j);
            okLow = (lp->lower(k) <= -infinity);
            okUp = (lp->upper(k) >= infinity);

            if (!okLow)
            {
               if (x > 0)
               {
                  if (upcnt == 0)
                     y = (b - up) / x + lp->upper(k);
                  else if (okUp && upcnt == 1)
                     y = (b - up) / x;
                  else
                     continue;
                  okLow = (y >= lp->lower(k));
               }
               else if (x < 0)
               {
                  if (locnt == 0)
                     y = (b - lo) / x + lp->upper(k);
                  else if (okUp && locnt == 1)
                     y = (b - lo) / x;
                  else
                     continue;
                  okLow = (y >= lp->lower(k));
               }
            }

            if (okLow && !okUp)
            {
               if (x > 0)
               {
                  if (locnt == 0)
                     y = (b - lo) / x + lp->lower(k);
                  else if (locnt == 1
                            && lp->lower(k) <= -infinity)
                     y = (b - lo) / x;
                  else
                     continue;
                  okUp = (y <= lp->upper(k));
               }
               else if (x < 0)
               {
                  if (upcnt == 0)
                     y = (b - up) / x + lp->lower(k);
                  else if (upcnt == 1
                            && lp->lower(k) <= -infinity)
                     y = (b - up) / x;
                  else
                     continue;
                  okUp = (y <= lp->upper(k));
               }
            }

            if (okLow && okUp)
            {
               int f = lp->colVector(k).size();
               if (f < fill)
               {
                  best = j;
                  fill = f;
               }
            }
         }
      }
      if ((fill - 1)*(row.size() - 1) > maxFill)
         best = -1;
   }
   return best;
}

int SPxAggregateSM::simplify()
{
   int best;
   int stage, last, num, j, i, k;
   Real x, b;
   DataArray < int > remCol(lp->nCols());
   DataArray < int > remRow(lp->nRows());
   LPCol newCol;
   LPCol emptyCol;
   LPRow emptyRow;
   DVector lhs(lp->lhs());
   DVector rhs(lp->rhs());
   SSVector tmp(lp->nRows());
   DSVector pcol(lp->nRows());
   DSVector prow(lp->nCols());

   DataArray < RowCnt > rowcnt(lp->nRows());
   Compare1 compare;

   stability = 0.01;
   maxFill = 10;

   stage = 0;
   last = 0;
   num = 0;
   for (i = lp->nCols() - 1; i >= 0; --i)
      remCol[i] = 0;
   for (i = lp->nRows() - 1; i >= 0; --i)
      remRow[i] = 0;

   do
   {
      ++stage;
      if (last)
      {
         VERBOSE3( std::cout << "looping ..." << std::endl; );
         maxFill = (maxFill + 20) / 2;
      }
      last = num;

      for (i = lp->nRows() - 1; i >= 0; --i)
      {
         rowcnt[i].row = i;
         rowcnt[i].size = lp->rowVector(i).size();
      }
      sorter_qsort(rowcnt.get_ptr(), rowcnt.size(), compare);

      for (int ii = 0; ii < rowcnt.size(); ++ii)
      {
         i = rowcnt[ii].row;
         if (remRow[i] >= 0 && remRow[i] < stage)
         {
            b = lhs[i];
            if (b == rhs[i])
            {
               const SVector& row = lp->rowVector(i);
               best = eliminate(row, b);
               if (best >= 0)
               {
                  Real a = row.value(best);
                  int idx = row.index(best);
                  Real obj = lp->obj(idx);

                  pcol = lp->colVector(idx);
                  pcol.remove(pcol.number(i));
                  lp->changeCol(idx, emptyCol);
                  prow = row;
                  lp->changeRow(i, emptyRow);

                  if (prow.size() > 1)
                     for (j = pcol.size() - 1; j >= 0; --j)
                        remRow[pcol.index(j)] = stage;

                  for (j = prow.size() - 1; j >= 0; --j)
                  {
                     x = prow.value(j);
                     k = prow.index(j);
                     tmp = lp->colVector(k);
                     tmp.multAdd(-(x / a), pcol);
                     newCol.setColVector(DSVector(tmp));
                     newCol.setUpper(lp->upper(k));
                     newCol.setLower(lp->lower(k));
                     newCol.setObj(lp->obj(k) - x / a * obj);
                     lp->changeCol(k, newCol);
                  }
                  rhs.multAdd(-(b / a), pcol);
                  lhs.multAdd(-(b / a), pcol);
                  delta -= b / a * obj;
                  remRow[i] = -1;
                  remCol[idx] = -1;
                  ++num;
               }
            }
         }
      }
   }
   while (last < num);
   if (num)
   {
      lp->changeRange(lhs, rhs);
      lp->removeRows (remRow.get_ptr());
      assert(lp->isConsistent());
      lp->removeCols (remCol.get_ptr());
      assert(lp->isConsistent());
      VERBOSE1({ std::cout << "SPxAggregateSM:\tremoved " << num
                              << " row(s) and column(s)" << std::endl
                              << "SPxAggregateSM:\tdelta = " << delta
                              << std::endl; });
   }

   return 0;
}

void SPxAggregateSM::unsimplify()
{
   std::cerr << "SPxAggregateSM::unsimplify() not implemented\n";
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
