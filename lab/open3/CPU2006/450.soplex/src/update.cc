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
#pragma ident "@(#) $Id: update.cpp,v 1.10 2002/03/03 13:50:36 bzfkocht Exp $"
#endif

#include <assert.h>

#include "spxdefines.h"
#include "clufactor.h"
#include "cring.h"

namespace soplex
{
void CLUFactor::update(int p_col, Real* p_work, const int* p_idx, int num)
{
   METHOD( "CLUFactor::update()" );
   int ll, i, j;
   int* lidx;
   Real* lval;
   Real x, rezi;

   assert(p_work[p_col] != 0);
   rezi = 1 / p_work[p_col];
   p_work[p_col] = 0;

   ll = makeLvec(num, p_col);
   //   ll = fac->makeLvec(num, col);
   lval = l.val;
   lidx = l.idx;

   for (i = num - 1; (j = p_idx[i]) != p_col; --i)
   {
      lidx[ll] = j;
      lval[ll] = rezi * p_work[j];
      p_work[j] = 0;
      ++ll;
   }

   lidx[ll] = p_col;
   lval[ll] = 1 - rezi;
   ++ll;

   for (--i; i >= 0; --i)
   {
      j = p_idx[i];
      lidx[ll] = j;
      lval[ll] = x = rezi * p_work[j];
      p_work[j] = 0;
      ++ll;

      if (fabs(x) > maxabs)
         maxabs = fabs(x);
   }
   stat = SLinSolver::OK;
}

void CLUFactor::updateNoClear(
   int p_col, 
   const Real* p_work, 
   const int* p_idx,
   int num)
{
   METHOD( "CLUFactor::updateNoClear()" );
   int ll, i, j;
   int* lidx;
   Real* lval;
   Real x, rezi;

   assert(p_work[p_col] != 0);
   rezi = 1 / p_work[p_col];
   ll = makeLvec(num, p_col);
   //ll = fac->makeLvec(num, col);
   lval = l.val;
   lidx = l.idx;

   for (i = num - 1; (j = p_idx[i]) != p_col; --i)
   {
      lidx[ll] = j;
      lval[ll] = rezi * p_work[j];
      ++ll;
   }

   lidx[ll] = p_col;
   lval[ll] = 1 - rezi;
   ++ll;

   for (--i; i >= 0; --i)
   {
      j = p_idx[i];
      lidx[ll] = j;
      lval[ll] = x = rezi * p_work[j];
      ++ll;

      if (fabs(x) > maxabs)
         maxabs = fabs(x);
   }
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
