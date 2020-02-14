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
#pragma ident "@(#) $Id: spxrem1sm.cpp,v 1.12 2002/03/03 13:50:34 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <iostream>

#include "spxdefines.h"
#include "spxrem1sm.h"
#include "dataarray.h"

namespace soplex
{
int SPxRem1SM::simplify()
{
   int cont, num, j, i;
   Real up, lo, x;
   DataArray < int > rem;

   do
   {
      cont = 0;

      rem.reSize(lp->nRows());
      num = 0;
      for (i = lp->nRows() - 1; i >= 0; --i)
      {
         rem[i] = 0;
         const SVector& row = lp->rowVector(i);
         if (row.size() == 0)
         {
            if (lp->rhs(i) < 0 || lp->lhs(i) > 0)
               return -1;
            rem[i] = -1;
            num++;
         }
         else if (lp->rhs(i) >= infinity && lp->lhs(i) <= -infinity)
         {
            rem[i] = -1;
            num++;
         }
         else if (row.size() == 1)
         {
            x = row.value(0);
            j = row.index(0);

            if (x > 0)
            {
               up = lp->rhs(i) / x;
               lo = lp->lhs(i) / x;
            }
            else if (x < 0)
            {
               lo = lp->rhs(i) / x;
               up = lp->lhs(i) / x;
            }
            else if (lp->rhs(i) < 0 || lp->lhs(i) > 0)
               return -1;
            else
            {
               lo = lp->lower(j);
               up = lp->upper(j);
            }
            rem[i] = -1;
            num++;
            if (up < lp->upper(j))
               lp->changeUpper(j, up);
            if (lo > lp->lower(j))
               lp->changeLower(j, lo);
         }
      }
      if (num)
      {
         cont += num;
         lp->removeRows(rem.get_ptr());
         VERBOSE1({ std::cout << "SPxRem1SM:\tremoved " << num
                                 << " row(s)" << std::endl; });
         assert(lp->isConsistent());
      }

      num = 0;
      rem.reSize(lp->nCols());
      for (i = lp->nCols() - 1; i >= 0; --i)
      {
         const SVector& col = lp->colVector(i);
         rem[i] = 0;
         if (col.size() == 0)
         {
            if (lp->maxObj(i) > 0)
            {
               if (lp->upper(i) >= infinity)
                  return 1;
               delta += lp->upper(i) * lp->obj(i);
            }
            else if (lp->maxObj(i) < 0)
            {
               if (lp->lower(i) <= -infinity)
                  return 1;
               delta += lp->lower(i) * lp->obj(i);
            }
            rem[i] = -1;
            num++;
         }
         else if ((x = lp->upper(i)) == lp->lower(i))
         {
            rem[i] = -1;
            num++;
            if (x != 0)
            {
               for (j = col.size() - 1; j >= 0; --j)
               {
                  int k = col.index(j);
                  if (lp->rhs(k) < infinity)
                     lp->changeRhs(k, lp->rhs(k) - x*col.value(j));
                  if (lp->lhs(k) > -infinity)
                     lp->changeLhs(k, lp->lhs(k) - x*col.value(j));
               }
               delta += x * lp->obj(i);
            }
         }
         else if (col.size() == 1 && lp->maxObj(i) == 0)
         {
            x = col.value(0);
            j = col.index(0);
            if (x > 0)
            {
               if (lp->lower(i) > -infinity)
                  up = lp->rhs(j) - lp->lower(i) * x;
               else
                  up = infinity;
               if (lp->upper(i) < infinity)
                  lo = lp->lhs(j) - lp->upper(i) * x;
               else
                  lo = -infinity;
            }
            else if (x < 0)
            {
               if (lp->lower(i) > -infinity)
                  lo = lp->lhs(j) - lp->lower(i) * x;
               else
                  lo = -infinity;
               if (lp->upper(i) < infinity)
                  up = lp->rhs(j) - lp->upper(i) * x;
               else
                  up = infinity;
            }
            else
            {
               up = lp->rhs(j);
               lo = lp->lhs(j);
            }
            lp->changeRange(j, lo, up);
            rem[i] = -1;
            num++;
         }
      }
      if (num)
      {
         cont += num;
         lp->removeCols(rem.get_ptr());
         VERBOSE1({ std::cout << "SPxRem1SM:\tremoved " << num
                                 << " column(s)" << std::endl; });
         assert(lp->isConsistent());
      }
   }
   while(cont);

   return 0;
}

void SPxRem1SM::unsimplify()
{
   std::cerr << "SPxRem1SM::unsimplify() not implemented\n";
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
