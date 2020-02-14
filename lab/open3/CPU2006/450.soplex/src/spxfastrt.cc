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
#pragma ident "@(#) $Id: spxfastrt.cpp,v 1.20 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <assert.h>
#include <stdio.h>
#include <iostream>
#include <iomanip>

#include "spxdefines.h"
#include "spxfastrt.h"

namespace soplex
{

//@ #define     MINSTAB thesolver->delta()
#define MINSTAB         1e-5
#define TRIES           2
#define SHORT           1e-5
#define DELTA_SHIFT     1e-5
#define EPSILON         1e-10

void SPxFastRT::resetTols()
{
   // epsilon = thesolver->epsilon();
   epsilon = EPSILON;
   /*
       if(thesolver->basis().stability() < 1e-4)
           epsilon *= 1e-4 / thesolver->basis().stability();
       std::cerr << "epsilon = " << epsilon << '\t';
       std::cerr << "delta   = " << delta   << '\t';
       std::cerr << "minStab = " << minStab << std::endl;
    */
}

void SPxFastRT::tighten()
{
   /*
   if((delta > 1.99 * DELTA_SHIFT  &&  thesolver->theShift < 1e-4) ||
       (delta > 1e-4   &&  thesolver->theShift > 1e-4))
    */
   // if(delta > 1.99 * DELTA_SHIFT)
   if (delta >= delta0 + DELTA_SHIFT)
   {
      delta -= DELTA_SHIFT;
      if (delta > 1e-4)
         delta -= 2 * DELTA_SHIFT;
   }

   if (minStab < MINSTAB)
   {
      minStab /= 0.90;
      if (minStab < 1e-6)
         minStab /= 0.90;
   }
}

void SPxFastRT::relax()
{
   minStab *= 0.95;
   delta += 3 * DELTA_SHIFT;
   // delta   += 2 * (thesolver->theShift > delta) * DELTA_SHIFT;
   //@ std::cerr << '\t' << minStab << '\t' << delta << std::endl;
}


static Real minStability(Real minStab, Real maxabs)
{
   if (maxabs < 1000.0)
      return minStab;
   return maxabs*minStab / 1000.0;
}

int SPxFastRT::maxDelta(
   Real& val,
   Real& p_abs,
   UpdateVector& update,
   Vector& lowBound,
   Vector& upBound,
   int start,
   int incr)
{
   int i, sel;
   Real x, y, max;
   Real u, l;

   Real l_delta = this->delta;
   // Real           delta01 = 0.5*l_delta;
   Real delta01 = 0;
   Real inf = infinity;
   Real mabs = p_abs;

   Real* up = upBound.get_ptr();
   Real* low = lowBound.get_ptr();
   const Real* vec = update.get_const_ptr();
   const Real* upd = update.delta().values();
   const int* idx = update.delta().indexMem();

   sel = -1;
   max = val;

   if (update.delta().isSetup())
   {
      const int* last = idx + update.delta().size();
      for (idx += start; idx < last; idx += incr)
      {
         i = *idx;
         x = upd[i];
         if (x > epsilon)
         {
            mabs = (x > mabs) ? x : mabs;
            u = up[i];
            if (u < inf)
            {
               y = u - vec[i];
               // x = ((1 - (y<=0)) * y + l_delta) / x;
               x = (y - (y <= 0) * (y + delta01) + l_delta) / x;
               if (x < max)
               {
                  max = x;
                  sel = i;
               }
            }
         }
         else if (x < -epsilon)
         {
            mabs = (-x > mabs) ? -x : mabs;
            l = low[i];
            if (l > -inf)
            {
               y = l - vec[i];
               // x = ((1 - (y>=0)) * y - l_delta) / x;
               x = (y - (y >= 0) * (y - delta01) - l_delta) / x;
               if (x < max)
               {
                  max = x;
                  sel = i;
               }
            }
         }
      }
   }
   else
   {
      int* l_idx = update.delta().altIndexMem();
      Real* uval = update.delta().altValues();
      const Real* uend = uval + update.dim();

      for (; uval < uend; ++uval)
      {
         if (*uval)
         {
            x = *uval;

            /**@todo The type of i is wrong, this should be ptrdiff_t, 
             *        but what exactly is done here anyway?
             */
            i = uval - upd;
            if (x > epsilon)
            {
               *l_idx++ = i;
               mabs = (x > mabs) ? x : mabs;
               u = up[i];
               if (u < inf)
               {
                  y = u - vec[i];
                  // x = ((1 - (y<=0)) * y + l_delta) / x;
                  x = (y - (y <= 0) * (y + delta01) + l_delta) / x;
                  if (x < max)
                  {
                     max = x;
                     sel = i;
                  }
               }
            }
            else if (x < -epsilon)
            {
               *l_idx++ = i;
               mabs = (-x > mabs) ? -x : mabs;
               l = low[i];
               if (l > -inf)
               {
                  y = l - vec[i];
                  // x = ((1 - (y>=0)) * y - l_delta) / x;
                  x = (y - (y >= 0) * (y - delta01) - l_delta) / x;
                  if (x < max)
                  {
                     max = x;
                     sel = i;
                  }
               }
            }
            else
               *uval = 0;
         }
      }
      update.delta().setSize(l_idx - update.delta().indexMem());
      update.delta().forceSetup();
   }

   val = max;
   p_abs = mabs;

   return sel;
}

int SPxFastRT::minDelta(
   Real& val,
   Real& p_abs,
   UpdateVector& update,
   Vector& lowBound,
   Vector& upBound,
   int start,
   int incr)
{
   int i, sel;
   Real x, y, max;
   Real u, l;

   Real l_delta = this->delta;
   // Real           delta01 = 0.5*l_delta;
   Real delta01 = 0;
   Real inf = infinity;
   Real mabs = p_abs;

   Real* up = upBound.get_ptr();
   Real* low = lowBound.get_ptr();
   const Real* vec = update.get_const_ptr();
   const Real* upd = update.delta().values();
   const int* idx = update.delta().indexMem();

   sel = -1;
   max = val;

   if (update.delta().isSetup())
   {
      const int* last = idx + update.delta().size();
      for (idx += start; idx < last; idx += incr)
      {
         i = *idx;
         x = upd[i];
         if (x > epsilon)
         {
            mabs = (x > mabs) ? x : mabs;
            l = low[i];
            if (l > -inf)
            {
               y = l - vec[i];
               // x = ((1 - (y>=0)) * y - l_delta) / x;
               x = (y - (y >= 0) * (y - delta01) - l_delta) / x;
               if (x > max)
               {
                  max = x;
                  sel = i;
               }
            }
         }
         else if (x < -epsilon)
         {
            mabs = (-x > mabs) ? -x : mabs;
            u = up[i];
            if (u < inf)
            {
               y = u - vec[i];
               // x = ((1 - (y<=0)) * y + l_delta) / x;
               x = (y - (y <= 0) * (y + delta01) + l_delta) / x;
               if (x > max)
               {
                  max = x;
                  sel = i;
               }
            }
         }
      }
   }
   else
   {
      int* l_idx = update.delta().altIndexMem();
      Real* uval = update.delta().altValues();
      const Real* uend = uval + update.dim();

      for (; uval < uend; ++uval)
      {
         if (*uval)
         {
            x = *uval;
            i = uval - upd;
            if (x > epsilon)
            {
               *l_idx++ = i;
               mabs = (x > mabs) ? x : mabs;
               l = low[i];
               if (l > -inf)
               {
                  y = l - vec[i];
                  // x = ((1 - (y>=0)) * y - l_delta) / x;
                  x = (y - (y >= 0) * (y - delta01) - l_delta) / x;
                  if (x > max)
                  {
                     max = x;
                     sel = i;
                  }
               }
            }
            else if (x < -epsilon)
            {
               *l_idx++ = i;
               mabs = (-x > mabs) ? -x : mabs;
               u = up[i];
               if (u < inf)
               {
                  y = u - vec[i];
                  // x = ((1 - (y<=0)) * y + l_delta) / x;
                  x = (y - (y <= 0) * (y + delta01) + l_delta) / x;
                  if (x > max)
                  {
                     max = x;
                     sel = i;
                  }
               }
            }
            else
               *uval = 0;
         }
      }
      update.delta().setSize(l_idx - update.delta().indexMem());
      update.delta().forceSetup();
   }

   val = max;
   p_abs = mabs;

   return sel;
}

int SPxFastRT::maxDelta(
   Real& val,
   Real& p_abs)
{
   return maxDelta(val, p_abs,
      thesolver->fVec(), thesolver->lbBound(), thesolver->ubBound(), 0, 1);
}

int SPxFastRT::minDelta(
   Real& val,
   Real& p_abs)
{
   return minDelta(val, p_abs,
      thesolver->fVec(), thesolver->lbBound(), thesolver->ubBound(), 0, 1);
}

SPxId SPxFastRT::maxDelta(
   int& nr,
   Real& max,
   Real& maxabs)
{
   int indc = maxDelta(max, maxabs,
      thesolver->coPvec(), thesolver->lcBound(), thesolver->ucBound(), 0, 1);
   int indp = maxDelta(max, maxabs,
      thesolver->pVec(), thesolver->lpBound(), thesolver->upBound(), 0, 1);

   if (indp >= 0)
   {
      nr = indp;
      return thesolver->id(indp);
   }
   if (indc >= 0)
   {
      nr = indc;
      return thesolver->coId(indc);
   }
   nr = -1;
   return SPxId();
}

SPxId SPxFastRT::minDelta(
   int& nr,
   Real& max,
   Real& maxabs)
{
   int indc = minDelta(max, maxabs,
      thesolver->coPvec(), thesolver->lcBound(), thesolver->ucBound(), 0, 1);
   int indp = minDelta(max, maxabs,
      thesolver->pVec(), thesolver->lpBound(), thesolver->upBound(), 0, 1);

   if (indp >= 0)
   {
      nr = indp;
      return thesolver->id(indp);
   }
   if (indc >= 0)
   {
      nr = indc;
      return thesolver->coId(indc);
   }
   nr = -1;
   return SPxId();
}


int SPxFastRT::minSelect(
   Real& val,
   Real& stab,
   Real& best,
   Real& bestDelta,
   Real max,
   const UpdateVector& update,
   const Vector& lowBound,
   const Vector& upBound,
   int start,
   int incr)
{
   int i;
   Real x, y;

   const Real* up = upBound.get_const_ptr();
   const Real* low = lowBound.get_const_ptr();
   const Real* vec = update.get_const_ptr();
   const Real* upd = update.delta().values();
   const int* idx = update.delta().indexMem();
   const int* last = idx + update.delta().size();

   int nr = -1;
   int bestNr = -1;

   for (idx += start; idx < last; idx += incr)
   {
      i = *idx;
      x = upd[i];
      if (x > stab)
      {
         y = (low[i] - vec[i]) / x;
         if (y >= max)
         {
            val = y;
            nr = i;
            stab = x;
         }
         else if (y < best)
         {
            best = y;
            bestNr = i;
         }
      }
      else if (x < -stab)
      {
         y = (up[i] - vec[i]) / x;
         if (y >= max)
         {
            val = y;
            nr = i;
            stab = -x;
         }
         else if (y < best)
         {
            best = y;
            bestNr = i;
         }
      }
   }

   if (nr < 0 && bestNr > 0)
   {
      if (upd[bestNr] < 0)
         bestDelta = up[bestNr] - vec[bestNr];
      else
         bestDelta = vec[bestNr] - low[bestNr];
   }
   return nr;
}

int SPxFastRT::maxSelect(
   Real& val,
   Real& stab,
   Real& best,
   Real& bestDelta,
   Real max,
   const UpdateVector& update,
   const Vector& lowBound,
   const Vector& upBound,
   int start,
   int incr)
{
   int i;
   Real x, y;

   const Real* up = upBound.get_const_ptr();
   const Real* low = lowBound.get_const_ptr();
   const Real* vec = update.get_const_ptr();
   const Real* upd = update.delta().values();
   const int* idx = update.delta().indexMem();
   const int* last = idx + update.delta().size();

   int nr = -1;
   int bestNr = -1;

   for (idx += start; idx < last; idx += incr)
   {
      i = *idx;
      x = upd[i];
      if (x > stab)
      {
         y = (up[i] - vec[i]) / x;
         if (y <= max)
         {
            val = y;
            nr = i;
            stab = x;
         }
         else if (y > best)
         {
            best = y;
            bestNr = i;
         }
      }
      else if (x < -stab)
      {
         y = (low[i] - vec[i]) / x;
         if (y <= max)
         {
            val = y;
            nr = i;
            stab = -x;
         }
         else if (y > best)
         {
            best = y;
            bestNr = i;
         }
      }
   }
   if (nr < 0 && bestNr > 0)
      bestDelta = (upd[bestNr] > 0)
         ? up[bestNr] - vec[bestNr]
         : vec[bestNr] - low[bestNr];

   return nr;
}


int SPxFastRT::maxSelect(
   Real& val,
   Real& stab,
   Real& bestDelta,
   Real max)
{
   Real best = -infinity;
   bestDelta = 0;
   return maxSelect(val, stab, best, bestDelta, max,
      thesolver->fVec(), thesolver->lbBound(), thesolver->ubBound(),  0, 1);
}

SPxId SPxFastRT::maxSelect(
   int& nr,
   Real& val,
   Real& stab,
   Real& bestDelta,
   Real max
)
{
   int indp, indc;
   Real best = -infinity;
   bestDelta = 0;
   indc = maxSelect(val, stab, best, bestDelta, max,
      thesolver->coPvec(), thesolver->lcBound(), thesolver->ucBound(), 0, 1);
   indp = maxSelect(val, stab, best, bestDelta, max,
      thesolver->pVec(), thesolver->lpBound(), thesolver->upBound(), 0, 1);

   if (indp >= 0)
   {
      nr = indp;
      return thesolver->id(indp);
   }
   if (indc >= 0)
   {
      nr = indc;
      return thesolver->coId(indc);
   }
   nr = -1;
   return SPxId();
}

int SPxFastRT::minSelect(
   Real& val,
   Real& stab,
   Real& bestDelta,
   Real max)
{
   Real best = infinity;
   bestDelta = 0;
   return minSelect(val, stab, best, bestDelta, max,
      thesolver->fVec(), thesolver->lbBound(), thesolver->ubBound(), 0, 1);
}

SPxId SPxFastRT::minSelect(
   int& nr,
   Real& val,
   Real& stab,
   Real& bestDelta,
   Real max)
{
   Real best = infinity;
   bestDelta = 0;
   int indc = minSelect(val, stab, best, bestDelta, max,
      thesolver->coPvec(), thesolver->lcBound(), thesolver->ucBound(), 0, 1);
   int indp = minSelect(val, stab, best, bestDelta, max,
      thesolver->pVec(), thesolver->lpBound(), thesolver->upBound(), 0, 1);

   if (indp >= 0)
   {
      nr = indp;
      return thesolver->id(indp);
   }
   if (indc >= 0)
   {
      nr = indc;
      return thesolver->coId(indc);
   }
   nr = -1;
   return SPxId();
}

/*
    Here comes our implementation of the Haris procedure improved by shifting
    bounds. The basic idea is to allow a slight infeasibility within |delta| to
    allow for more freedom when selecting the leaveing variable. This freedom
    may than be used for selecting numerical stable variables with great
    improves.
 
    The algorithms operates in two phases. In a first phase, the maximum |val|
    is determined, when in feasibility within |inf| is allowed. In the second
    phase, between all variables with values |< val| the one is selected which
    gives the best step forward in the simplex iteration. However, this may not
    allways yield an improvement. In that case, we shift the variable toward
    infeasibility and retry. This avoids cycling in the shifted LP.
 */
/**@todo suspicious: max is not used, but it looks like a used parameter 
 *       in selectLeave()
 */
int SPxFastRT::maxShortLeave(Real& sel, int leave, 
   Real /*max*/, Real p_abs)
{
   assert(leave >= 0);
   sel = thesolver->fVec().delta()[leave];
   if (sel > p_abs*SHORT || -sel > p_abs*SHORT)
   {
      if (sel > 0)
         sel = (thesolver->ubBound()[leave] - thesolver->fVec()[leave]) / sel;
      else
         sel = (thesolver->lbBound()[leave] - thesolver->fVec()[leave]) / sel;
      return 1;
   }
   return 0;
}

/**@todo suspicious: max is not used, but it looks like a used parameter 
 *       in selectLeave()
 */
int SPxFastRT::minShortLeave(Real& sel, int leave, 
   Real /*max*/, Real p_abs)
{
   assert(leave >= 0);
   sel = thesolver->fVec().delta()[leave];
   if (sel > p_abs*SHORT || -sel > p_abs*SHORT)
   {
      if (sel > 0)
         sel = (thesolver->lbBound()[leave] - thesolver->fVec()[leave]) / sel;
      else
         sel = (thesolver->ubBound()[leave] - thesolver->fVec()[leave]) / sel;
      return 1;
   }
   return 0;
}

int SPxFastRT::maxReleave(Real& sel, int leave, Real maxabs)
{
   UpdateVector& vec = thesolver->fVec();
   Vector& low = thesolver->lbBound();
   Vector& up = thesolver->ubBound();

   if (leave >= 0)
   {
      if (up[leave] > low[leave])
      {
         Real x = vec.delta()[leave];

         if (sel < -delta / maxabs)
         {
            sel = 0;
            if (x < 0)
               thesolver->shiftLBbound(leave, vec[leave]);
            else
               thesolver->shiftUBbound(leave, vec[leave]);
         }
      }
      else
      {
         sel = 0;
         thesolver->shiftLBbound(leave, vec[leave]);
         thesolver->shiftUBbound(leave, vec[leave]);
      }
   }
   else
      return 1;

   return 0;
}

int SPxFastRT::minReleave(Real& sel, int leave, Real maxabs)
{
   UpdateVector& vec = thesolver->fVec();
   Vector& low = thesolver->lbBound();
   Vector& up = thesolver->ubBound();

   if (leave >= 0)
   {
      if (up[leave] > low[leave])
      {
         Real x = vec.delta()[leave];

         if (sel > delta / maxabs)
         {
            if (x > 0)
            {
               thesolver->theShift += low[leave];
               sel = 0;
               low[leave] = vec[leave] + sel * x;
               thesolver->theShift -= low[leave];
            }
            else
            {
               thesolver->theShift -= up[leave];
               sel = 0;
               up[leave] = vec[leave] + sel * x;
               thesolver->theShift += up[leave];
            }
         }
      }
      else
      {
         sel = 0;
         if (vec[leave] < low[leave])
            thesolver->theShift += low[leave] - vec[leave];
         else
            thesolver->theShift += vec[leave] - up[leave];
         low[leave] = up[leave] = vec[leave];
      }
   }
   else
      return 1;

   return 0;
}

int SPxFastRT::selectLeave(Real& val)
{
   Real maxabs, max, sel;
   int leave = -1;
   int cnt = 0;

   resetTols();

   if (val > epsilon)
   {
      do
      {
         // phase 1:
         max = val;
         maxabs = 0;
         leave = maxDelta(max, maxabs);
         if (max == val)
            return -1;

         if (!maxShortLeave(sel, leave, max, maxabs))
         {
            // phase 2:
            Real stab, bestDelta;
            stab = 100 * minStability(minStab, maxabs);
            leave = maxSelect(sel, stab, bestDelta, max);
            if (bestDelta < DELTA_SHIFT*TRIES)
               cnt++;
            else
               cnt += TRIES;
         }
         if (!maxReleave(sel, leave, maxabs))
            break;
         relax();
      }
      while (cnt < TRIES);
   }
   else if (val < -epsilon)
   {
      do
      {
         max = val;
         maxabs = 0;
         leave = minDelta(max, maxabs);
         if (max == val)
            return -1;

         /**@todo Here is a ; above is none.
          *       In a first try the version with the ; runs better.
          *       minShortLeave changes sel. Have a look what happens
          *       if we drop the if above also.
          */
         // if (!
         minShortLeave(sel, leave, max, maxabs);
         // );
         {
            // phase 2:
            Real stab, bestDelta;
            stab = 100 * minStability(minStab, maxabs);
            leave = minSelect(sel, stab, bestDelta, max);
            if (bestDelta < DELTA_SHIFT*TRIES)
               cnt++;
            else
               cnt += TRIES;
         }
         if (!minReleave(sel, leave, maxabs))
            break;
         relax();
      }
      while (cnt < TRIES);
   }
   else
      return -1;

   VERBOSE3({
      if (leave >= 0)
         std::cout 
            << thesolver->basis().iteration() << "("
            << std::setprecision(6) << thesolver->value() << ","
            << std::setprecision(2) << thesolver->basis().stability() << "):"
            << leave << "\t"
            << std::setprecision(4) << sel << " "
            << std::setprecision(4) << thesolver->fVec().delta()[leave] << " "
            << std::setprecision(6) << maxabs 
            << std::endl;
      else
         std::cout << thesolver->basis().iteration() 
                   << ": skipping instable pivot"
                   << std::endl;
   });

   if (leave >= 0 || minStab > 2*solver()->epsilon())
   {
      val = sel;
      if (leave >= 0)
         tighten();
   }
   return leave;
}


/**@todo suspicious: max is not used, 
 *       but it looks like a used parameter in selectEnter()
 */
int SPxFastRT::maxReenter(Real& sel, Real /*max*/, Real maxabs,
                           SPxId id, int nr)
{
   Real x, d;
   Vector* up;
   Vector* low;

   UpdateVector& pvec = thesolver->pVec();
   SSVector& pupd = thesolver->pVec().delta();
   Vector& upb = thesolver->upBound();
   Vector& lpb = thesolver->lpBound();
   UpdateVector& cvec = thesolver->coPvec();
   SSVector& cupd = thesolver->coPvec().delta();
   Vector& ucb = thesolver->ucBound();
   Vector& lcb = thesolver->lcBound();

   if (thesolver->isCoId(id))
   {
      if (thesolver->isCoBasic(nr))
      {
         cupd.clearIdx(nr);
         return 1;
      }

      x = cvec[nr];
      d = cupd[nr];
      up = &ucb;
      low = &lcb;

      if (d < 0)
         sel = (lcb[nr] - cvec[nr]) / d;
      else
         sel = (ucb[nr] - cvec[nr]) / d;
   }

   else if (thesolver->isId(id))
   {
      pvec[nr] = thesolver->vector(nr) * cvec;
      if (thesolver->isBasic(nr))
      {
         pupd.clearIdx(nr);
         return 1;
      }

      x = pvec[nr];
      d = pupd[nr];
      up = &upb;
      low = &lpb;

      if (d < 0)
         sel = (lpb[nr] - pvec[nr]) / d;
      else
         sel = (upb[nr] - pvec[nr]) / d;
   }
   else
      return 1;

   if ((*up)[nr] != (*low)[nr])
   {
      if (sel < -delta / maxabs)
      {
         if (d > 0)
         {
            thesolver->theShift -= (*up)[nr];
            sel = 0;
            (*up)[nr] = x + sel * d;
            thesolver->theShift += (*up)[nr];
         }
         else
         {
            thesolver->theShift += (*low)[nr];
            sel = 0;
            (*low)[nr] = x + sel * d;
            thesolver->theShift -= (*low)[nr];
         }
      }
   }
   else
   {
      sel = 0;
      if (x > (*up)[nr])
         thesolver->theShift += x - (*up)[nr];
      else
         thesolver->theShift += (*low)[nr] - x;
      (*up)[nr] = (*low)[nr] = x;
   }

   return 0;
}

/**@todo suspicious: max is not used, but it looks 
 *       like a used parameter in selectEnter()
 */
int SPxFastRT::minReenter(Real& sel, Real /*max*/, Real maxabs,
                           SPxId id, int nr)
{
   Real x, d;
   Vector* up;
   Vector* low;

   UpdateVector& pvec = thesolver->pVec();
   SSVector& pupd = thesolver->pVec().delta();
   Vector& upb = thesolver->upBound();
   Vector& lpb = thesolver->lpBound();
   UpdateVector& cvec = thesolver->coPvec();
   SSVector& cupd = thesolver->coPvec().delta();
   Vector& ucb = thesolver->ucBound();
   Vector& lcb = thesolver->lcBound();

   if (thesolver->isCoId(id))
   {
      if (thesolver->isCoBasic(nr))
      {
         cupd.clearIdx(nr);
         return 1;
      }
      x = cvec[nr];
      d = cupd[nr];
      up = &ucb;
      low = &lcb;
      if (d > 0)
         sel = (thesolver->lcBound()[nr] - cvec[nr]) / d;
      else
         sel = (thesolver->ucBound()[nr] - cvec[nr]) / d;
   }

   else if (thesolver->isId(id))
   {
      pvec[nr] = thesolver->vector(nr) * cvec;
      if (thesolver->isBasic(nr))
      {
         pupd.clearIdx(nr);
         return 1;
      }
      x = pvec[nr];
      d = pupd[nr];
      up = &upb;
      low = &lpb;
      if (d > 0)
         sel = (thesolver->lpBound()[nr] - pvec[nr]) / d;
      else
         sel = (thesolver->upBound()[nr] - pvec[nr]) / d;
   }

   else
      return 1;

   if ((*up)[nr] != (*low)[nr])
   {
      if (sel > delta / maxabs)
      {
         if (d < 0)
         {
            thesolver->theShift -= (*up)[nr];
            sel = 0;
            (*up)[nr] = x + sel * d;
            thesolver->theShift += (*up)[nr];
         }
         else
         {
            thesolver->theShift += (*low)[nr];
            sel = 0;
            (*low)[nr] = x + sel * d;
            thesolver->theShift -= (*low)[nr];
         }
      }
   }
   else
   {
      sel = 0;
      if (x > (*up)[nr])
         thesolver->theShift += x - (*up)[nr];
      else
         thesolver->theShift += (*low)[nr] - x;
      (*up)[nr] = (*low)[nr] = x;
   }

   return 0;
}

int SPxFastRT::shortEnter(
   SPxId& enterId,
   int nr,
   Real max,
   Real maxabs
)
{
   if (thesolver->isCoId(enterId))
   {
      if (max != 0)
      {
         Real x = thesolver->coPvec().delta()[nr];
         if (x < maxabs * SHORT && -x < maxabs * SHORT)
            return 0;
      }
      return 1;
   }

   else if (thesolver->isId(enterId))
   {
      if (max != 0)
      {
         Real x = thesolver->pVec().delta()[nr];
         if (x < maxabs * SHORT && -x < maxabs * SHORT)
            return 0;
      }
      return 1;
   }

   return 0;
}

SPxId SPxFastRT::selectEnter(Real& val)
{
   SPxId enterId;
   Real max, sel;
   Real maxabs = 0.0;
   int nr;
   int cnt = 0;

   resetTols();
   sel = 0;

   if (val > epsilon)
   {
      do
      {
         maxabs = 0.0;
         max = val;

         enterId = maxDelta(nr, max, maxabs);
         if (!enterId.isValid())
            return enterId;
         assert(max >= 0.0);

         if (!shortEnter(enterId, nr, max, maxabs))
         {
            Real bestDelta, stab;
            // stab = minStab;
            stab = minStability(minStab, maxabs);
            enterId = maxSelect(nr, sel, stab, bestDelta, max);
            if (bestDelta < DELTA_SHIFT*TRIES)
               cnt++;
            else
               cnt += TRIES;
         }
         if (!maxReenter(sel, max, maxabs, enterId, nr))
            break;
         relax();
      }
      while (cnt < TRIES);
   }
   else if (val < -epsilon)
   {
      do
      {
         maxabs = 0.0;
         max = val;
         enterId = minDelta(nr, max, maxabs);
         if (!enterId.isValid())
            return enterId;
         assert(max <= 0.0);

         if (!shortEnter(enterId, nr, max, maxabs))
         {
            Real bestDelta, stab;
            // stab = minStab;
            stab = minStability(minStab, maxabs);
            enterId = minSelect(nr, sel, stab, bestDelta, max);
            if (bestDelta < DELTA_SHIFT*TRIES)
               cnt++;
            else
               cnt += TRIES;
         }
         if (!minReenter(sel, max, maxabs, enterId, nr))
            break;
         relax();
      }
      while (cnt < TRIES);
   }

   VERBOSE3({
      if (enterId.isValid())
         {
            Real x;
            if (thesolver->isCoId(enterId))
               x = thesolver->coPvec().delta()[ thesolver->number(enterId) ];
            else
               x = thesolver->pVec().delta()[ thesolver->number(enterId) ];
            std::cout << thesolver->basis().iteration() << ": " << sel
                      << '\t' << x << " (" << maxabs << ")" << std::endl;
         }
      else
         std::cout << thesolver->basis().iteration() 
                   << ": skipping instable pivot" << std::endl;
   });

   if (enterId.isValid() || minStab > 2*epsilon)
   {
      val = sel;
      if (enterId.isValid())
         tighten();
   }

   return enterId;
}

void SPxFastRT::load(SoPlex* spx)
{
   thesolver = spx;
   setType(spx->type());
}

/**@todo suspicious: Why is the type never used? 
 *       This holds for all implementations of SPxRatioTester!
 */
void SPxFastRT::setType(SoPlex::Type)
{
   minStab = MINSTAB;
   delta = thesolver->delta();

   /*
       resetTols();
       std::cerr << "epsilon = " << epsilon << '\t';
       std::cerr << "delta   = " << delta   << '\t';
       std::cerr << "minStab = " << minStab << std::endl;
    */

   if (delta > 1e-4)
      delta = 1e-4;

   delta0 = delta;
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
