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
#pragma ident "@(#) $Id: spxsteeppr.cpp,v 1.18 2002/03/21 16:06:19 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "message.h"
#include "spxsteeppr.h"
#include "random.h"

namespace soplex
{

#define EQ_PREF 1000

void SPxSteepPR::clear()
{
   thesolver = 0;
   prefSetup = 0;
}

void SPxSteepPR::load(SoPlex* base)
{
   thesolver = base;

   if (base)
   {
      workVec.clear();
      workVec.reDim(base->dim());
      workRhs.clear();
      workRhs.reDim(base->dim());

      leavePref.reSize(base->dim());
      coPref.reSize (base->dim());
      pref.reSize (base->coDim());
      prefSetup = 0;
   }
}

void SPxSteepPR::setType(SoPlex::Type type)
{
   int i;

   workRhs.epsilon = thesolver->epsilon();

   pref.reSize (thesolver->coDim());
   coPref.reSize(thesolver->dim());
   setupPrefs(type);

   if (setup == DEFAULT)
   {
      if (type == SoPlex::ENTER)
      {
         coPenalty.reDim(thesolver->dim());
         for (i = thesolver->dim() - 1; i >= 0; --i)
            // coPenalty[i] = 10;
            coPenalty[i] = 2;
         penalty.reDim(thesolver->coDim());
         for (i = thesolver->coDim() - 1; i >= 0; --i)
            // penalty[i] = 10;
            penalty[i] = 1;
         // penalty[i] = 1 + thesolver->vector(i).size() / thesolver->dim();
      }
      else
      {
         assert(type == SoPlex::LEAVE);
         coPenalty.reDim(thesolver->dim());
         for (i = thesolver->dim() - 1; i >= 0; --i)
         {
            // coPenalty[i] = 1;
            SPxId id = thesolver->basis().baseId(i);
            int n = thesolver->number(id);
            if (thesolver->isId(id))
               leavePref[i] = pref[n];
            else
               leavePref[i] = coPref[n];
            coPenalty[i] = 1 + thesolver->basis().baseVec(i).size()
                           / Real(thesolver->dim());
         }
      }
   }
   else
   {
      std::cerr << 
         "sorry, no exact setup for steepest edge multipliers implemented\n";

      if (type == SoPlex::ENTER)
      {
         coPenalty.reDim(thesolver->dim());
         for (i = thesolver->dim() - 1; i >= 0; --i)
            coPenalty[i] = 1;
         penalty.reDim(thesolver->coDim());
         for (i = thesolver->coDim() - 1; i >= 0; --i)
            penalty[i] = 1 + thesolver->vector(i).length2();
      }
      else
      {
         assert(type == SoPlex::LEAVE);
         coPenalty.reDim(thesolver->dim());
         for (i = thesolver->dim() - 1; i >= 0; --i)
         {
            coPenalty[i] = 1 + thesolver->basis().baseVec(i).size()
                           / Real(thesolver->dim());
         }
      }
   }

   workVec.clear();
   workRhs.clear();
}

void SPxSteepPR::setupPrefs(Real mult, Real /*tie*/, Real /*cotie*/,
                             Real shift, Real coshift,
                             int rs, int cs, int re, int ce)
{
   Real *p;
   Real *cp;
   Real *end;
   // Real rtie, ctie;
   Real rshift, cshift;
   int i;

   if (thesolver->rep() == SoPlex::COLUMN)
   {
      cp = pref.get_ptr();
      p = coPref.get_ptr();
      // ctie = tie;
      // rtie = cotie;
      cshift = shift;
      rshift = coshift;
   }
   else
   {
      p = pref.get_ptr();
      cp = coPref.get_ptr();
      // rtie = tie;
      // ctie = cotie;
      rshift = shift;
      cshift = coshift;
   }

   if (re < 0)
      re = thesolver->nRows();
   for (i = re; --i >= rs;)
   {
      p[i] = rshift;
      //      p[i] += rtie * thesolver->rowVector(i).size() / Real(thesolver->nCols());
      //      p[i] += EQ_PREF * (thesolver->rhs(i) == thesolver->lhs(i));
      //      p[i] += EQ_PREF * (thesolver->rhs(i) >=  infinity
      //                     &&  thesolver->lhs(i) <= -infinity);
   }

   if (ce < 0)
      ce = thesolver->nCols();
   for (i = ce; --i >= cs;)
   {
      cp[i] = cshift;
      //      cp[i] += ctie * thesolver->colVector(i).size() / Real(thesolver->nRows());
      //      cp[i] += EQ_PREF * (thesolver->upper(i) == thesolver->lower(i));
      //      cp[i] += EQ_PREF * (thesolver->upper(i) >=  infinity
      //                      &&  thesolver->lower(i) <= -infinity);
   }


   i = 0;
   cp = coPref.get_ptr();
   end = cp + coPref.size();
   while (cp < end)
      *cp++ *= 1.0 - mult * i++;

   p = pref.get_ptr();
   end = p + pref.size();
   i = pref.size();
   while (p < end)
      *p++ *= 1.0 + mult * i--;
}

void SPxSteepPR::setupPrefs(SoPlex::Type tp)
{
   if (tp != prefSetup)
   {
      Real mult = 1e-8 / Real(1 + thesolver->dim() + thesolver->coDim());
      if (tp == SoPlex::ENTER)
         setupPrefs(-mult, -1e-5, -1e-5, 1, 1);
      else
         setupPrefs(mult, 1e-5, 1e-5, 1, 1);
      prefSetup = tp;
   }
}

void SPxSteepPR::setRep(SoPlex::Representation)
{
   if (workVec.dim() != thesolver->dim())
   {
      DVector tmp = penalty;
      penalty = coPenalty;
      coPenalty = tmp;

      workVec.clear();
      workVec.reDim(thesolver->dim());
   }
}

void SPxSteepPR::left4X(int n, SPxId id, int start, int incr)
{
   assert(thesolver->type() == SoPlex::LEAVE);

   if (id.isValid())
   {
      int i, j;
      Real x;
      // Real               delta         = 0.1;   // thesolver->epsilon();
      Real delta = 0.1 + 1.0 / thesolver->basis().iteration();
      Real* coPenalty_ptr = coPenalty.get_ptr();
      const Real* workVec_ptr = workVec.get_const_ptr();
      const Real* rhoVec = thesolver->fVec().delta().values();
      Real rhov_1 = 1 / rhoVec[n];
      Real beta_q = thesolver->coPvec().delta().length2()
                      * rhov_1 * rhov_1;

      assert(rhoVec[n] >= theeps || -rhoVec[n] >= theeps);

      //  Update #coPenalty# vector
      const IdxSet& rhoIdx = thesolver->fVec().idx();
      int len = thesolver->fVec().idx().size();
      for (i = len - 1 - start; i >= 0; i -= incr)
      {
         j = rhoIdx.index(i);
         x = coPenalty_ptr[j] += rhoVec[j]
            * (beta_q * rhoVec[j] - 2 * rhov_1 * workVec_ptr[j]);
         if (x < delta)
            // coPenalty_ptr[j] = delta / (1+delta-x);
            coPenalty_ptr[j] = delta;
         else if (x >= infinity)
            coPenalty_ptr[j] = 1 / theeps;
      }

      coPenalty_ptr[n] = beta_q;
      //@ coPenalty_ptr[n] = 0.999*beta_q;
      //@ coPenalty_ptr[n] = 1.001*beta_q;
   }

}

void SPxSteepPR::left4(int n, SPxId id)
{
   //  Update preference multiplier in #leavePref#
   if (thesolver->isId(id))
      leavePref[n] = pref[thesolver->number(id)];
   else if (thesolver->isCoId(id))
      leavePref[n] = coPref[thesolver->number(id)];

   left4X(n, id, 0, 1);
}

int SPxSteepPR::selectLeaveX(Real& best, int start, int incr)
{
   const Real* coPenalty_ptr = coPenalty.get_const_ptr();
   const Real* fTest = thesolver->fTest().get_const_ptr();
   //    const Real* low   = thesolver->lbBound();
   //    const Real* up    = thesolver->ubBound();
   const Real* p = leavePref.get_const_ptr();

   Real x;
   int selIdx;

   best = -infinity;
   selIdx = -1;

   //for(int i = 0; i < thesolver->dim() - start; i += incr)
 
   for (int i = thesolver->dim() - 1 - start; i >= 0; i -= incr)
   {
      x = fTest[i];

      if (x < -theeps)
      {         
         /**@todo this was an assert! is an assertion correct?*/
         // assert(coPenalty_ptr[i] >= theeps);
#ifndef NDEBUG
         if( coPenalty_ptr[i] < theeps )
         {
            std::cerr << "SPxSteepPR::selectLeaveX(): This shall not be!"
                      << std::endl;
            std::cerr << "i=" << i
                      << " x=" << x
                      << " coPenalty_ptr[i]=" << coPenalty_ptr[i]
                      << " theeps=" << theeps << std::endl;
            x = x * x / theeps * p[i];
         }
         else
#endif
            x = x * x / coPenalty_ptr[i] * p[i];
         if (x > best)
         {
            best = x;
            selIdx = i;
         }
      }
   }

   /*
       if(selIdx >= 0)
           std::cerr << fTest[selIdx] << std::endl;
    */ 
   return selIdx;
}

int SPxSteepPR::selectLeave()
{
   assert(isConsistent());
   Real best;

   lastIdx = selectLeaveX(best);
   if (lastIdx >= 0)
   {
      thesolver->basis().coSolve(thesolver->coPvec().delta(),
                                 thesolver->unitVector(lastIdx));
      workRhs.epsilon = accuracy;
      workRhs.setup_and_assign(thesolver->coPvec().delta());
      thesolver->setup4solve(&workVec, &workRhs);
   }

   /*
       if(thesolver->basis().iteration() < 10)
       {
           std::cerr.precision(16);
           std::cerr << lastIdx
                << '\t' << thesolver->fTest()[lastIdx]
                << '\t' << leavePref[lastIdx]
                << '\t' << coPenalty[lastIdx]
                << std::endl;
       }
    */

   return lastIdx;
}


/* Entering Simplex
 */
void SPxSteepPR::entered4X(
   SPxId, int n, int start2, int incr2, int start1, int incr1)
{
   assert(thesolver->type() == SoPlex::ENTER);

   if (n >= 0 && n < thesolver->dim())
   {
      Real delta = 2 + 1.0 / thesolver->basis().iteration();
      Real* coPenalty_ptr = coPenalty.get_ptr();
      Real* penalty_ptr = penalty.get_ptr();
      const Real* workVec_ptr = workVec.get_const_ptr();
      const Real* pVec = thesolver->pVec().delta().values();
      const IdxSet& pIdx = thesolver->pVec().idx();
      const Real* coPvec = thesolver->coPvec().delta().values();
      const IdxSet& coPidx = thesolver->coPvec().idx();
      Real xi_p = 1 / thesolver->fVec().delta()[n];
      int i, j;
      Real xi_ip, x;

      assert(thesolver->fVec().delta()[n] > thesolver->epsilon()
              || thesolver->fVec().delta()[n] < -thesolver->epsilon());

      for (j = coPidx.size() - 1 - start1; j >= 0; j -= incr1)
      {
         i = coPidx.index(j);
         xi_ip = xi_p * coPvec[i];
         x = coPenalty_ptr[i] += xi_ip * (xi_ip * pi_p - 2 * workVec_ptr[i]);
         /*
         if(x < 1)
             coPenalty_ptr[i] = 1 / (2-x);
         */
         if (x < delta)
            coPenalty_ptr[i] = delta;
         // coPenalty_ptr[i] = 1;
         else if (x > infinity)
            coPenalty_ptr[i] = 1 / thesolver->epsilon();
      }

      for (j = pIdx.size() - 1 - start2; j >= 0; j -= incr2)
      {
         i = pIdx.index(j);
         xi_ip = xi_p * pVec[i];
         x = penalty_ptr[i] += xi_ip * (xi_ip * pi_p
            - 2 * (thesolver->vector(i) * workVec));
         /*
         if(x < 1)
             penalty_ptr[i] = 1 / (2-x);
         */
         if (x < delta)
            penalty_ptr[i] = delta;
         // penalty_ptr[i] = 1;
         else if (x > infinity)
            penalty_ptr[i] = 1 / thesolver->epsilon();
      }
   }

   /*@
       if(thesolver->isId(id))
           penalty[   thesolver->number(id) ] *= 1.0001;
       else if(thesolver->isCoId(id))
           coPenalty[ thesolver->number(id) ] *= 1.0001;
   */
}

void SPxSteepPR::entered4(SPxId id, int n)
{
   entered4X(id, n, 0, 1, 0, 1);
}

SPxId SPxSteepPR::selectEnterX(
   Real& best, int start1, int incr1, int start2, int incr2)
{
   /*
       std::cerr << "selectEnter " << start1 << '(' << incr1 << ")\t"
                              << start2 << '(' << incr2 << ")\n";
    */
   const Real* p = pref.get_const_ptr();
   const Real* cp = coPref.get_const_ptr();
   const Real* test = thesolver->test().get_const_ptr();
   const Real* coTest = thesolver->coTest().get_const_ptr();
   const Real* penalty_ptr = penalty.get_const_ptr();
   const Real* coPenalty_ptr = coPenalty.get_const_ptr();
   Real x;
   int i, end;

   SPxId selId;
   best = -infinity;

   for (end = thesolver->coDim(), i = start2; i < end; i += incr2)
   {
      x = test[i];
      if (x < -theeps)
      {
         x *= x / penalty_ptr[i];
         x *= p[i];
         // x *= 1 + p[i];
         if (x > best)
         {
            best = x;
            selId = thesolver->id(i);
         }
      }
   }
   incr2 = end;

   for (end = thesolver->dim(), i = start1; i < end; i += incr1)
   {
      x = coTest[i];
      if (x < -theeps)
      {
         x *= x / coPenalty_ptr[i];
         x *= cp[i];
         // x *= 1 + cp[i];
         if (x > best)
         {
            best = x;
            selId = thesolver->coId(i);
         }
      }
   }

   assert(isConsistent());
   return selId;
}

SPxId SPxSteepPR::selectEnter()
{
   Real best;
   lastId = SPxSteepPR::selectEnterX(best);

   if (lastId.isValid())
   {
      SSVector& delta = thesolver->fVec().delta();

      thesolver->basis().solve4update(delta, thesolver->vector(lastId));

      // workRhs.epsilon = 0.1*accuracy;
      workRhs.epsilon = accuracy;
      workRhs.setup_and_assign(delta);
      pi_p = 1 + delta.length2();

      thesolver->setup4coSolve(&workVec, &workRhs);
   }

   return lastId;
}

void SPxSteepPR::addedVecs(int n)
{
   n = penalty.dim();
   pref.reSize (thesolver->coDim());
   penalty.reDim(thesolver->coDim());

   if (thesolver->type() == SoPlex::ENTER)
   {
      setupPrefs(thesolver->type());
      for (; n < penalty.dim(); ++n)
         penalty[n] = 2;
   }
   prefSetup = 0;
}

void SPxSteepPR::addedCoVecs(int n)
{
   n = coPenalty.dim();

   leavePref.reSize(thesolver->dim());
   coPref.reSize (thesolver->dim());
   setupPrefs(thesolver->type());

   workVec.reDim (thesolver->dim());
   coPenalty.reDim (thesolver->dim());
   for (; n < coPenalty.dim(); ++n)
      coPenalty[n] = 1;
   prefSetup = 0;
}

void SPxSteepPR::removedVec(int i)
{
   assert(thesolver != 0);
   penalty[i] = penalty[penalty.dim()];
   penalty.reDim(thesolver->coDim());
   prefSetup = 0;
}

void SPxSteepPR::removedVecs(const int perm[])
{
   assert(thesolver != 0);
   if (thesolver->type() == SoPlex::ENTER)
   {
      int i;
      int j = penalty.dim();
      for (i = 0; i < j; ++i)
         if (perm[i] >= 0)
            penalty[perm[i]] = penalty[i];
   }
   penalty.reDim(thesolver->coDim());
   prefSetup = 0;
}

void SPxSteepPR::removedCoVec(int i)
{
   assert(thesolver != 0);
   coPenalty[i] = coPenalty[coPenalty.dim()];
   coPenalty.reDim(thesolver->dim());
   prefSetup = 0;
}

void SPxSteepPR::removedCoVecs(const int perm[])
{
   assert(thesolver != 0);
   int i;
   int j = coPenalty.dim();
   for (i = 0; i < j; ++i)
      if (perm[i] >= 0)
         coPenalty[perm[i]] = coPenalty[i];
   coPenalty.reDim(thesolver->dim());
   prefSetup = 0;
}

bool SPxSteepPR::isConsistent() const
{
   if (thesolver != 0 && thesolver->type() == SoPlex::LEAVE && setup == EXACT)
   {
      int i;
      SSVector tmp(thesolver->dim(), thesolver->epsilon());
      Real x;
      for (i = thesolver->dim() - 1; i >= 0; --i)
      {
         thesolver->basis().coSolve(tmp, thesolver->unitVector(i));
         x = coPenalty[i] - tmp.length2();
         if (x > thesolver->delta() || -x > thesolver->delta())
         {
            std::cerr << "x[" << i << "] = " << x << std::endl;
         }
      }
   }

   if (thesolver != 0 && thesolver->type() == SoPlex::ENTER)
   {
      int i;
      for (i = thesolver->dim() - 1; i >= 0; --i)
         if (coPenalty[i] < thesolver->epsilon())
            return MSGinconsistent("SPxSteepPR");

      for (i = thesolver->coDim() - 1; i >= 0; --i)
         if (penalty[i] < thesolver->epsilon())
            return MSGinconsistent("SPxSteepPR");
   }
   return true;
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
