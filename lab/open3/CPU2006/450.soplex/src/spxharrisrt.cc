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
#pragma ident "@(#) $Id: spxharrisrt.cpp,v 1.17 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "spxharrisrt.h"

namespace soplex
{
/**@todo suspicious: *max is not set, but it is used 
         (with the default setting *max=1) in selectLeave and selectEnter
         The question might be if max shouldn't be updated with themax?
*/
int SPxHarrisRT::maxDelta(
   Real* /*max*/,             /* max abs value in upd */
   Real* val,             /* initial and chosen value */
   int num,             /* # of indices in idx */
   const int* idx,             /* nonzero indices in upd */
   const Real* upd,             /* update vector for vec */
   const Real* vec,             /* current vector */
   const Real* low,             /* lower bounds for vec */
   const Real* up,              /* upper bounds for vec */
   Real delta,           /* allowed bound violation */
   Real epsilon)         /* what is 0? */
{
   Real x;
   Real theval;
   /**@todo patch suggests using *max instead of themax */
   Real themax;
   int sel;
   int i;

   assert(*val >= 0);

   theval = *val;
   themax = 0;
   sel = -1;

   while (num--)
   {
      i = idx[num];
      x = upd[i];
      if (x > epsilon)
      {
         themax = (x > themax) ? x : themax;
         x = (up[i] - vec[i] + delta) / x;
         if (x < theval && up[i] < infinity)
            theval = x;
      }
      else if (x < -epsilon)
      {
         themax = (-x > themax) ? -x : themax;
         x = (low[i] - vec[i] - delta) / x;
         if (x < theval && low[i] > -infinity)
            theval = x;
      }
   }
   *val = theval;
   return sel;
}

/**@todo suspicious: *max is not set, but it is used 
    (with the default setting *max=1)
    in selectLeave and selectEnter
*/
int SPxHarrisRT::minDelta(
   Real* /*max*/,             /* max abs value in upd */
   Real* val,             /* initial and chosen value */
   int num,             /* # of indices in idx */
   const int* idx,             /* nonzero indices in upd */
   const Real* upd,             /* update vector for vec */
   const Real* vec,             /* current vector */
   const Real* low,             /* lower bounds for vec */
   const Real* up,              /* upper bounds for vec */
   Real delta,           /* allowed bound violation */
   Real epsilon)         /* what is 0? */
{
   Real x;
   Real theval;
   /**@todo patch suggests using *max instead of themax */
   Real themax;
   int sel;
   int i;

   assert(*val < 0);

   theval = *val;
   themax = 0;
   sel = -1;

   while (num--)
   {
      i = idx[num];
      x = upd[i];
      if (x > epsilon)
      {
         themax = (x > themax) ? x : themax;
         x = (low[i] - vec[i] - delta) / x;
         if (x > theval && low[i] > -infinity)
            theval = x;
      }
      else if (x < -epsilon)
      {
         themax = (-x > themax) ? -x : themax;
         x = (up[i] - vec[i] + delta) / x;
         if (x > theval && up[i] < infinity)
            theval = x;
      }
   }
   *val = theval;
   return sel;
}

/*
    Here comes our implementation of the Haris procedure improved by shifting
    bounds. The basic idea is to used the tollerated infeasibility within
    #delta# for searching numerically stable pivots.
 
    The algorithms operates in two phases. In a first phase, the maximum #val#
    is determined, when in feasibility within #delta# is allowed. In the second
    phase, between all variables with values #< val# the one is selected which
    gives the best step forward in the simplex iteration. However, this may not
    allways yield an improvement. In that case, we shift the variable toward
    infeasibility and retry. This avoids cycling in the shifted LP.
 */
int SPxHarrisRT::selectLeave(Real& val)
{
   int i, j;
   Real stab, x, y;
   Real max;
   Real sel;
   Real lastshift;
   Real useeps;
   int leave = -1;
   Real maxabs = 1;

   Real epsilon = solver()->epsilon();
   Real delta = solver()->delta();

   /**@todo numCycle and maxCycle are integers. So degeneps will be 
    *       exactly delta until numCycle >= maxCycle. Then it will be
    *       0 until numCycle >= 2 * maxCycle, after wich it becomes
    *       negative. This does not look ok.
    */
   Real degeneps = delta * (1 - solver()->numCycle() / solver()->maxCycle());

   SSVector& upd = solver()->fVec().delta();
   Vector& vec = solver()->fVec();

   const Vector& up = solver()->ubBound();
   const Vector& low = solver()->lbBound();

   assert(delta > epsilon);
   assert(epsilon > 0);
   assert(solver()->maxCycle() > 0);

   max = val;
   lastshift = solver()->shift();

   solver()->fVec().delta().setup();

   if (max > epsilon)
   {
      // phase 1:
      maxDelta(
         &maxabs,             /* max abs value in upd */
         &max,                /* initial and chosen value */
         upd.size(),          /* # of indices in upd */
         upd.indexMem(),      /* nonzero indices in upd */
         upd.values(),        /* update vector for vec */
         vec.get_const_ptr(),         /* current vector */
         low.get_const_ptr(),                 /* lower bounds for vec */
         up.get_const_ptr(),                  /* upper bounds for vec */
         delta,               /* allowed bound violation */
         epsilon);             /* what is 0? */

      if (max == val)
         return -1;


      // phase 2:
      stab = 0;
      sel = -infinity;
      useeps = maxabs * epsilon * 0.001;
      if (useeps < epsilon)
         useeps = epsilon;
      for (j = upd.size() - 1; j >= 0; --j)
      {
         i = upd.index(j);
         x = upd[i];
         if (x > useeps)
         {
            y = up[i] - vec[i];
            if (y < -degeneps)
               solver()->shiftUBbound(i, vec[i]); // ensure simplex improvement
            else
            {
               y /= x;
               if (y <= max && y > sel - epsilon && x > stab)
               {
                  sel = y;
                  leave = i;
                  stab = x;
               }
            }
         }
         else if (x < -useeps)
         {
            y = low[i] - vec[i];
            if (y > degeneps)
               solver()->shiftLBbound(i, vec[i]); // ensure simplex improvement
            else
            {
               y /= x;
               if (y <= max && y > sel - epsilon && -x > stab)
               {
                  sel = y;
                  leave = i;
                  stab = -x;
               }
            }
         }
         else
            upd.clearNum(j);
      }
   }


   else if (max < -epsilon)
   {
      // phase 1:
      minDelta(
         &maxabs,             /* max abs value in upd */
         &max,                /* initial and chosen value */
         upd.size(),          /* # of indices in upd */
         upd.indexMem(),      /* nonzero indices in upd */
         upd.values(),        /* update vector for vec */
         vec.get_const_ptr(),                 /* current vector */
         low.get_const_ptr(),                 /* lower bounds for vec */
         up.get_const_ptr(),                  /* upper bounds for vec */
         delta,               /* allowed bound violation */
         epsilon);             /* what is 0? */
    
      if (max == val)
         return -1;

      // phase 2:
      stab = 0;
      sel = infinity;
      useeps = maxabs * epsilon * 0.001;
      if (useeps < epsilon)
         useeps = epsilon;
      for (j = upd.size() - 1; j >= 0; --j)
      {
         i = upd.index(j);
         x = upd[i];
         if (x < -useeps)
         {
            y = up[i] - vec[i];
            if (y < -degeneps)
               solver()->shiftUBbound(i, vec[i]);   // ensure simplex improvement
            else
            {
               y /= x;
               if (y >= max && y < sel + epsilon && -x > stab)
               {
                  sel = y;
                  leave = i;
                  stab = -x;
               }
            }
         }
         else if (x > useeps)
         {
            y = low[i] - vec[i];
            if (y > degeneps)
               solver()->shiftLBbound(i, vec[i]); // ensure simplex improvement
            else
            {
               y /= x;
               if (y >= max && y < sel + epsilon && x > stab)
               {
                  sel = y;
                  leave = i;
                  stab = x;
               }
            }
         }
         else
            upd.clearNum(j);
      }
   }

   else
      return -1;


   if (lastshift != solver()->shift())
      return selectLeave(val);
   assert(leave >= 0);

   val = sel;
   return leave;
}


SPxId SPxHarrisRT::selectEnter(Real& val)
{
   int i, j;
   SPxId enterId;
   Real stab, x, y;
   Real max = 0.0;
   Real sel = 0.0;
   Real lastshift;
   Real cuseeps;
   Real ruseeps;
   Real cmaxabs = 1;
   Real rmaxabs = 1;
   int pnr, cnr;

   Real minStability = 0.0001;
   Real epsilon = solver()->epsilon();
   Real delta = solver()->delta();
   /**@todo numCycle and maxCycle are integers. So degeneps will be 
    *       exactly delta until numCycle >= maxCycle. Then it will be
    *       0 until numCycle >= 2 * maxCycle, after wich it becomes
    *       negative. This does not look ok.
    */
   Real degeneps = delta * (1 - solver()->numCycle() / solver()->maxCycle());

   Vector& pvec = solver()->pVec();
   SSVector& pupd = solver()->pVec().delta();

   Vector& cvec = solver()->coPvec();
   SSVector& cupd = solver()->coPvec().delta();

   const Vector& upb = solver()->upBound();
   const Vector& lpb = solver()->lpBound();
   const Vector& ucb = solver()->ucBound();
   const Vector& lcb = solver()->lcBound();

   assert(delta > epsilon);
   assert(epsilon > 0);
   assert(solver()->maxCycle() > 0);

   solver()->coPvec().delta().setup();
   solver()->pVec().delta().setup();

   if (val > epsilon)
   {
      for(;;)
      {
         pnr = -1;
         cnr = -1;
         max = val;
         sel = val;
         lastshift = solver()->shift();
         assert(delta > epsilon);

         // phase 1:
         maxDelta(
            &rmaxabs,            /* max abs value in upd */
            &max,                /* initial and chosen value */
            pupd.size(),         /* # of indices in pupd */
            pupd.indexMem(),     /* nonzero indices in pupd */
            pupd.values(),       /* update vector for vec */
            pvec.get_const_ptr(),                /* current vector */
            lpb.get_const_ptr(),                 /* lower bounds for vec */
            upb.get_const_ptr(),                 /* upper bounds for vec */
            delta,               /* allowed bound violation */
            epsilon);             /* what is 0? */
            
         maxDelta(
            &cmaxabs,            /* max abs value in upd */
            &max,                /* initial and chosen value */
            cupd.size(),         /* # of indices in cupd */
            cupd.indexMem(),     /* nonzero indices in cupd */
            cupd.values(),       /* update vector for vec */
            cvec.get_const_ptr(),                /* current vector */
            lcb.get_const_ptr(),                 /* lower bounds for vec */
            ucb.get_const_ptr(),                 /* upper bounds for vec */
            delta,               /* allowed bound violation */
            epsilon);            /* what is 0? */

         if (max == val)
            return enterId;


         // phase 2:
         stab = 0;
         sel = -infinity;
         ruseeps = rmaxabs * 0.001 * epsilon;
         if (ruseeps < epsilon)
            ruseeps = epsilon;
         cuseeps = cmaxabs * 0.001 * epsilon;
         if (cuseeps < epsilon)
            cuseeps = epsilon;
         for (j = pupd.size() - 1; j >= 0; --j)
         {
            i = pupd.index(j);
            x = pupd[i];
            if (x > ruseeps)
            {
               y = upb[i] - pvec[i];
               if (y < -degeneps)
                  solver()->shiftUPbound(i, pvec[i] - degeneps);
               else
               {
                  y /= x;
                  if (y <= max && x >= stab)       // &&  y > sel-epsilon
                  {
                     enterId = solver()->id(i);
                     sel = y;
                     pnr = i;
                     stab = x;
                  }
               }
            }
            else if (x < -ruseeps)
            {
               y = lpb[i] - pvec[i];
               if (y > degeneps)
                  solver()->shiftLPbound(i, pvec[i] + degeneps);
               else
               {
                  y /= x;
                  if (y <= max && -x >= stab)      // &&  y > sel-epsilon
                  {
                     enterId = solver()->id(i);
                     sel = y;
                     pnr = i;
                     stab = -x;
                  }
               }
            }
            else
            {
               DEBUG( std::cerr << "removing value " << pupd[i] << std::endl; );
               pupd.clearNum(j);
            }
         }
         for (j = cupd.size() - 1; j >= 0; --j)
         {
            i = cupd.index(j);
            x = cupd[i];
            if (x > cuseeps)
            {
               y = ucb[i] - cvec[i];
               if (y < -degeneps)
                  solver()->shiftUCbound(i, cvec[i] - degeneps);
               else
               {
                  y /= x;
                  if (y <= max && x >= stab)       // &&  y > sel-epsilon
                  {
                     enterId = solver()->coId(i);
                     sel = y;
                     cnr = j;
                     stab = x;
                  }
               }
            }
            else if (x < -cuseeps)
            {
               y = lcb[i] - cvec[i];
               if (y > degeneps)
                  solver()->shiftLCbound(i, cvec[i] + degeneps);
               else
               {
                  y /= x;
                  if (y <= max && -x >= stab)      // &&  y > sel-epsilon
                  {
                     enterId = solver()->coId(i);
                     sel = y;
                     cnr = j;
                     stab = -x;
                  }
               }
            }
            else
            {
               DEBUG( std::cerr << "removing value " << cupd[i] << std::endl; );
               cupd.clearNum(j);
            }
         }

         if (lastshift == solver()->shift())
         {
            if (cnr >= 0)
            {
               if (solver()->isBasic(enterId))
               {
                  cupd.clearNum(cnr);
                  continue;
               }
               else
                  break;
            }
            else if (pnr >= 0)
            {
               pvec[pnr] = solver()->vector(pnr) * cvec;
               if (solver()->isBasic(enterId))
               {
                  pupd.setValue(pnr, 0);
                  continue;
               }
               else
               {
                  x = pupd[pnr];
                  if (x > 0)
                  {
                     sel = upb[pnr] - pvec[pnr];
                     if (x < minStability && sel < delta)
                     {
                        minStability /= 2;
                        solver()->shiftUPbound(pnr, pvec[pnr]);
                        continue;
                     }
                  }
                  else
                  {
                     sel = lpb[pnr] - pvec[pnr];
                     if (-x < minStability && -sel < delta)
                     {
                        minStability /= 2;
                        solver()->shiftLPbound(pnr, pvec[pnr]);
                        continue;
                     }
                  }
                  sel /= x;
               }
            }
            else
            {
               val = 0;
               enterId.inValidate();
               return enterId;
            }

            if (sel > max)             // instability detected => recompute
               continue;               // ratio test with corrected value
            break;
         }
      }
   }
   else if (val < -epsilon)
   {
      for(;;)
      {
         pnr = -1;
         cnr = -1;
         max = val;
         sel = val;
         lastshift = solver()->shift();
         assert(delta > epsilon);


         // phase 1:
         minDelta
         (
            &rmaxabs,            /* max abs value in upd */
            &max,                /* initial and chosen value */
            pupd.size(),         /* # of indices in pupd */
            pupd.indexMem(),     /* nonzero indices in pupd */
            pupd.values(),       /* update vector for vec */
            pvec.get_const_ptr(),                /* current vector */
            lpb.get_const_ptr(),                 /* lower bounds for vec */
            upb.get_const_ptr(),                 /* upper bounds for vec */
            delta,               /* allowed bound violation */
            epsilon);             /* what is 0? */

         minDelta
         (
            &cmaxabs,            /* max abs value in upd */
            &max,                /* initial and chosen value */
            cupd.size(),         /* # of indices in cupd */
            cupd.indexMem(),     /* nonzero indices in cupd */
            cupd.values(),       /* update vector for vec */
            cvec.get_const_ptr(),                /* current vector */
            lcb.get_const_ptr(),                 /* lower bounds for vec */
            ucb.get_const_ptr(),                 /* upper bounds for vec */
            delta,               /* allowed bound violation */
            epsilon);             /* what is 0? */

         if (max == val)
            return enterId;


         // phase 2:
         stab = 0;
         sel = infinity;
         ruseeps = rmaxabs * epsilon * 0.001;
         cuseeps = cmaxabs * epsilon * 0.001;
         for (j = pupd.size() - 1; j >= 0; --j)
         {
            i = pupd.index(j);
            x = pupd[i];
            if (x > ruseeps)
            {
               y = lpb[i] - pvec[i];
               if (y > degeneps)
                  solver()->shiftLPbound(i, pvec[i]);  // ensure simplex improvement
               else
               {
                  y /= x;
                  if (y >= max && x > stab)        // &&  y < sel+epsilon
                  {
                     enterId = solver()->id(i);
                     sel = y;
                     pnr = i;
                     stab = x;
                  }
               }
            }
            else if (x < -ruseeps)
            {
               y = upb[i] - pvec[i];
               if (y < -degeneps)
                  solver()->shiftUPbound(i, pvec[i]);  // ensure simplex improvement
               else
               {
                  y /= x;
                  if (y >= max && -x > stab)       // &&  y < sel+epsilon
                  {
                     enterId = solver()->id(i);
                     sel = y;
                     pnr = i;
                     stab = -x;
                  }
               }
            }
            else
            {
               DEBUG( std::cerr << "removing value " << pupd[i] << std::endl; );
               pupd.clearNum(j);
            }
         }
         for (j = cupd.size() - 1; j >= 0; --j)
         {
            i = cupd.index(j);
            x = cupd[i];
            if (x > cuseeps)
            {
               y = lcb[i] - cvec[i];
               if (y > degeneps)
                  solver()->shiftLCbound(i, cvec[i]);  // ensure simplex improvement
               else
               {
                  y /= x;
                  if (y >= max && x > stab)        // &&  y < sel+epsilon
                  {
                     enterId = solver()->coId(i);
                     sel = y;
                     cnr = j;
                     stab = x;
                  }
               }
            }
            else if (x < -cuseeps)
            {
               y = ucb[i] - cvec[i];
               if (y < -degeneps)
                  solver()->shiftUCbound(i, cvec[i]);  // ensure simplex improvement
               else
               {
                  y /= x;
                  if (y >= max && -x > stab)       // &&  y < sel+epsilon
                  {
                     enterId = solver()->coId(i);
                     sel = y;
                     cnr = j;
                     stab = -x;
                  }
               }
            }
            else
            {
               DEBUG( std::cerr << "removing value " << x << std::endl; );
               cupd.clearNum(j);
            }
         }

         if (lastshift == solver()->shift())
         {
            if (cnr >= 0)
            {
               if (solver()->isBasic(enterId))
               {
                  cupd.clearNum(cnr);
                  continue;
               }
               else
                  break;
            }
            else if (pnr >= 0)
            {
               pvec[pnr] = solver()->vector(pnr) * cvec;
               if (solver()->isBasic(enterId))
               {
                  pupd.setValue(pnr, 0);
                  continue;
               }
               else
               {
                  x = pupd[pnr];
                  if (x > 0)
                  {
                     sel = lpb[pnr] - pvec[pnr];
                     if (x < minStability && -sel < delta)
                     {
                        minStability /= 2;
                        solver()->shiftLPbound(pnr, pvec[pnr]);
                        continue;
                     }
                  }
                  else
                  {
                     sel = upb[pnr] - pvec[pnr];
                     if (-x < minStability && sel < delta)
                     {
                        minStability /= 2;
                        solver()->shiftUPbound(pnr, pvec[pnr]);
                        continue;
                     }
                  }
                  sel /= x;
               }
            }
            else
            {
               val = 0;
               enterId.inValidate();
               return enterId;
            }
            if (sel < max)             // instability detected => recompute
               continue;               // ratio test with corrected value
            break;
         }
      }
   }
   assert(max * val >= 0);
   assert(enterId.type() != SPxId::INVALID);

   val = sel;

   return enterId;
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
