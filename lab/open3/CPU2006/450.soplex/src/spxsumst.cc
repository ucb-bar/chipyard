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
#pragma ident "@(#) $Id: spxsumst.cpp,v 1.6 2002/03/03 13:50:35 bzfkocht Exp $"
#endif

#include <iostream>

#include "spxdefines.h"
#include "spxsumst.h"
#include "vector.h"

namespace soplex
{

void SPxSumST::setupWeights(SoPlex& base)
{
   int count;
   int i;
   Real x;
   DVector work, delta, rowLen;

   rowLen.reDim(base.nCols());
   work.reDim (base.nCols());
   delta.reDim (base.nCols());

   Real* wrk = work.get_ptr();
   const Real* lhs = base.lhs().get_const_ptr();
   const Real* rhs = base.rhs().get_const_ptr();
   const Real* up = base.upper().get_const_ptr();
   const Real* low = base.lower().get_const_ptr();

   for (i = base.nRows(); --i >= 0;)
   {
      rowLen[i] = base.rowVector(i).length2();
      if (lhs[i] > 0)
         delta.multAdd(lhs[i] / rowLen[i], base.rowVector(i));
      else if (rhs[i] < 0)
         delta.multAdd(rhs[i] / rowLen[i], base.rowVector(i));
   }

   for (count = 0;; count++)
   {
      work += delta;
      for (i = base.nCols(); --i >= 0;)
      {
         if (wrk[i] > up[i])
            wrk[i] = up[i];
         if (wrk[i] < low[i])
            wrk[i] = low[i];
      }

      //      std::cerr << -(work * base.maxObj()) << std::endl;
      if (count >= 12)
         break;

      delta.clear();
      for (i = base.nRows(); --i >= 0;)
      {
         x = base.rowVector(i) * work;
         if (lhs[i] > x)
            delta.multAdd((lhs[i] - x) / rowLen[i], base.rowVector(i));
         else if (rhs[i] < x)
            delta.multAdd((rhs[i] - x) / rowLen[i], base.rowVector(i));
      }
   }

   primal(work);
   SPxVectorST::setupWeights(base);
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
