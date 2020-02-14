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
#pragma ident "@(#) $Id: spxdefaultrt.cpp,v 1.10 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "spxdefaultrt.h"

namespace soplex
{
/*
   Here comes the ratio test for selecting a variable to leave the basis. it is
   assumed, that #Vec.delta()# and #fVec.idx()# have been setup
   correctly!
 
   The leaving variable is selected such that the update of #fVec()# (using
   #fVec.value() * fVec.delta()#) keeps the basis feasible within
   #solver()->delta()#. Hence, #fVec.value()# must be chosen such that one
   updated value of #thefvec# just reaches its bound and no other one exceeds
   them by more than #solver()->delta()#. Further, #fVec.value()# must have the
   same sign as argument #val#.
 
   The return value of #selectLeave()# is the number of a variable in the
   basis selected to leave the basis. -1 indicates that no variable could be
   selected. Otherwise, parameter #val# contains the chosen #fVec.value()#.
 */
int SPxDefaultRT::selectLeaveX(Real& val, int start, int incr)
{
   const Real* vec = solver()->fVec().get_const_ptr();
   const Real* upd = solver()->fVec().delta().values();
   const IdxSet& idx = solver()->fVec().idx();
   const Real* ub = solver()->ubBound().get_const_ptr();
   const Real* lb = solver()->lbBound().get_const_ptr();

   Real delta = solver()->delta();
   Real epsilon = solver()->epsilon();
   int leave = -1;

   Real x;
   int i, j;

   if (val > 0)
   {
      for (j = idx.size() - 1 - start; j >= 0; j -= incr)
      {
         i = idx.index(j);
         x = upd[i];
         if (x > epsilon)
         {
            x = (ub[i] - vec[i] + delta) / x;
            if (x < val && ub[i] < infinity)  // added "&& ub[i] < infinity"
            {
               leave = i;
               val = x;
            }
         }
         else if (x < -epsilon)
         {
            x = (lb[i] - vec[i] - delta) / x;
            if (x < val && lb[i] > -infinity)    // added "&& lb[i] > -infinity"
            {
               leave = i;
               val = x;
            }
         }
      }
      if (leave >= 0)
      {
         x = upd[leave];
         val = ((x > epsilon)
            ? (ub[leave] - vec[leave])
            : (lb[leave] - vec[leave])) 
            / x;
      }
      assert(val > -epsilon);
   }
   else
   {
      for (j = idx.size() - 1 - start; j >= 0; j -= incr)
      {
         i = idx.index(j);
         x = upd[i];
         if (x < -epsilon)
         {
            x = (ub[i] - vec[i] + delta) / x;
            if (x > val && ub[i] < infinity)    // added "&& ub[i] < infinity"
            {
               leave = i;
               val = x;
            }
         }
         else if (x > epsilon)
         {
            x = (lb[i] - vec[i] - delta) / x;
            if (x > val && lb[i] > -infinity)    // added "&& lb[i] > -infinity"
            {
               leave = i;
               val = x;
            }
         }
      }
      if (leave >= 0)
      {
         x = upd[leave];
         if (x < epsilon)
            val = (ub[leave] - vec[leave]) / x;
         else
            val = (lb[leave] - vec[leave]) / x;
      }
      assert(val < epsilon);
   }

   return leave;
}

SPxId SPxDefaultRT::selectEnter(Real& val)
{
   solver()->coPvec().delta().setup();
   solver()->pVec().delta().setup();
   return selectEnterX(val, 0, 1, 0, 1);
}

/*
    Here comes the ratio test. It is assumed, that #theCoPvec.delta()# and
    #theCoPvec.idx()# have been setup correctly!
 */
SPxId SPxDefaultRT::selectEnterX(
   Real& max, int start1, int incr1, int start2, int incr2)
{
   const Real* pvec = solver()->pVec().get_const_ptr();
   const Real* pupd = solver()->pVec().delta().values();
   const IdxSet& pidx = solver()->pVec().idx();
   const Real* lpb = solver()->lpBound().get_const_ptr();
   const Real* upb = solver()->upBound().get_const_ptr();

   const Real* cvec = solver()->coPvec().get_const_ptr();
   const Real* cupd = solver()->coPvec().delta().values();
   const IdxSet& cidx = solver()->coPvec().idx();
   const Real* lcb = solver()->lcBound().get_const_ptr();
   const Real* ucb = solver()->ucBound().get_const_ptr();

   Real epsilon = solver()->epsilon();
   Real delta = solver()->delta();
   Real val = max;
   int pnum = -1;
   int cnum = -1;

   int i, j;
   Real x;
   SPxId enterId;

   if (val > 0)
   {
      for (j = pidx.size() - 1 - start1; j >= 0; j -= incr1)
      {
         i = pidx.index(j);
         x = pupd[i];
         if (x > epsilon)
         {
            x = (upb[i] - pvec[i] + delta) / x;
            if (x < val && upb[i] < infinity)  // added "&& upb[i] < infinity"
            {
               enterId = solver()->id(i);
               val = x;
               pnum = j;
            }
         }
         else if (x < -epsilon)
         {
            x = (lpb[i] - pvec[i] - delta) / x;
            if (x < val && lpb[i] > -infinity)  // added "&& lpb[i] > -infinity"
            {
               enterId = solver()->id(i);
               val = x;
               pnum = j;
            }
         }
      }
      for (j = cidx.size() - 1 - start2; j >= 0; j -= incr2)
      {
         i = cidx.index(j);
         x = cupd[i];
         if (x > epsilon)
         {
            x = (ucb[i] - cvec[i] + delta) / x;
            if (x < val && ucb[i] < infinity)  // added "&& ucb[i] < infinity"
            {
               enterId = solver()->coId(i);
               val = x;
               cnum = j;
            }
         }
         else if (x < -epsilon)
         {
            x = (lcb[i] - cvec[i] - delta) / x;
            if (x < val && lcb[i] > -infinity)  // added "&& lcb[i] > -infinity"
            {
               enterId = solver()->coId(i);
               val = x;
               cnum = j;
            }
         }
      }
      if (cnum >= 0)
      {
         i = cidx.index(cnum);
         x = cupd[i];
         if (x > epsilon)
            val = (ucb[i] - cvec[i]) / x;
         else
            val = (lcb[i] - cvec[i]) / x;
      }
      else if (pnum >= 0)
      {
         i = pidx.index(pnum);
         x = pupd[i];
         if (x > epsilon)
            val = (upb[i] - pvec[i]) / x;
         else
            val = (lpb[i] - pvec[i]) / x;
      }
   }
   else
   {
      for (j = pidx.size() - 1 - start1; j >= 0; j -= incr1)
      {
         i = pidx.index(j);
         x = pupd[i];
         if (x > epsilon)
         {
            x = (lpb[i] - pvec[i] - delta) / x;
            if (x > val && lpb[i] > -infinity)  // added "&& lpb[i] > -infinity"
            {
               enterId = solver()->id(i);
               val = x;
               pnum = j;
            }
         }
         else if (x < -epsilon)
         {
            x = (upb[i] - pvec[i] + delta) / x;
            if (x > val && upb[i] < infinity)  // added "&& upb[i] < infinity"
            {
               enterId = solver()->id(i);
               val = x;
               pnum = j;
            }
         }
      }
      for (j = cidx.size() - 1 - start2; j >= 0; j -= incr2)
      {
         i = cidx.index(j);
         x = cupd[i];
         if (x > epsilon)
         {
            x = (lcb[i] - cvec[i] - delta) / x;
            if (x > val && lcb[i] > -infinity)  // added "&& lcb[i] > -infinity"
            {
               enterId = solver()->coId(i);
               val = x;
               cnum = j;
            }
         }
         else if (x < -epsilon)
         {
            x = (ucb[i] - cvec[i] + delta) / x;
            if (x > val && ucb[i] < infinity)  // added "&& ucb[i] < infinity"
            {
               enterId = solver()->coId(i);
               val = x;
               cnum = j;
            }
         }
      }
      if (cnum >= 0)
      {
         i = cidx.index(cnum);
         x = cupd[i];
         if (x < epsilon)
            val = (ucb[i] - cvec[i]) / x;
         else
            val = (lcb[i] - cvec[i]) / x;
      }
      else if (pnum >= 0)
      {
         i = pidx.index(pnum);
         x = pupd[i];
         if (x < epsilon)
            val = (upb[i] - pvec[i]) / x;
         else
            val = (lpb[i] - pvec[i]) / x;
      }
   }

   if (enterId.isValid() && solver()->isBasic(enterId))
   {
      DEBUG({ std::cerr << "isValid() && isBasic(): max=" << max
                        << std::endl; });
      if (cnum >= 0)
         solver()->coPvec().delta().clearNum(cnum);
      else
         solver()->pVec().delta().clearNum(pnum);
      return SPxDefaultRT::selectEnter(max);
   }

   DEBUG({
      if( !enterId.isValid() )
         std::cerr << "!isValid(): max=" << max << ", x=" << x << std::endl;
   });

   max = val;
   return enterId;
}

int SPxDefaultRT::selectLeave(Real& val)
{
   solver()->fVec().delta().setup();
   return selectLeaveX(val, 0, 1);
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
