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
#pragma ident "@(#) $Id: spxparmultpr.cpp,v 1.11 2002/03/21 16:06:19 bzfkocht Exp $"
#endif

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "spxparmultpr.h"

namespace soplex
{
#define EQ_PREF 1000

int SPxParMultPR::partialSize = 17;

void SPxParMultPR::setType(SoPlex::Type tp)
{
   if (tp == SoPlex::ENTER)
   {
      used = 0;
      thesolver->setPricing(SoPlex::PARTIAL);
   }
   else
   {
      thesolver->setPricing(SoPlex::FULL);
   }

   last = 0;
   count = 1;
   min = partialSize / 2;
}

void SPxParMultPR::load(SoPlex* p_solver)
{
   thesolver = p_solver;
   multiParts = (thesolver->dim() + thesolver->coDim()) / partialSize + 1;
   pricSet.reSize(10 * partialSize);
}

SPxId SPxParMultPR::selectEnter()
{
   SPxId id;
   Real x;
   int i;
   int best = -1;
   //    const SPxBasis::Desc& ds   = thesolver->basis().desc();

   assert(thesolver != 0);
   int lastlast = -1;

   if (thesolver->pricing() == SoPlex::PARTIAL)
   {
      Real val;
      Real eps = -theeps;
      lastlast = last;
      count = 0;

      for (i = used - 1; i >= 0; --i)
      {
         int n = thesolver->number(pricSet[i].id);
         if (thesolver->isId(pricSet[i].id))
         {
            thesolver->computePvec(n);
            pricSet[i].test = val = thesolver->computeTest(n);
         }
         else
            pricSet[i].test = val = thesolver->coTest()[n];
         if (val >= eps)
            pricSet[i] = pricSet[--used];
      }

      while (pricSet.size() - used < partialSize)
      {
         best = 0;
         for (i = 1; i < used; ++i)
         {
            if (pricSet[i].test > pricSet[best].test)
               best = i;
         }
         pricSet[best] = pricSet[--used];
      }

      do
      {
         count++;
         last = (last + 1) % multiParts;
         for (i = thesolver->coDim() - last - 1;
               i >= 0; i -= multiParts)
         {
            thesolver->computePvec(i);
            x = thesolver->computeTest(i);
            if (x < eps)
            {
               pricSet[used].id = thesolver->id(i);
               pricSet[used].test = x;
               used++;
            }
         }

         for (i = thesolver->dim() - last - 1;
               i >= 0; i -= multiParts)
         {
            x = thesolver->coTest()[i];
            if (x < eps)
            {
               pricSet[used].id = thesolver->coId(i);
               pricSet[used].test = x;
               used++;
            }
         }
         assert(used < pricSet.size());
      }
      while (used < min && last != lastlast);

      if (used > 0)
      {
         min = (used + 1);
         if (min < 1)
            min = 1;
         if (min > partialSize)
            min = partialSize;
         best = 0;
         for (i = 1; i < used; ++i)
         {
            if (pricSet[i].test < pricSet[best].test)
               best = i;
         }
         id = pricSet[best].id;
      }
      return id;
   }

   else
   {
      assert(thesolver->pricing() == SoPlex::FULL);
      Real bestx = -theeps;
      for (i = thesolver->dim() - 1; i >= 0; --i)
      {
         x = thesolver->coTest()[i];
         // x *= EQ_PREF * (1 + (ds.coStatus(i) == SPxBasis::Desc::P_FREE
         //                || ds.coStatus(i) == SPxBasis::Desc::D_FREE));
         if (x < bestx)
         {
            id = thesolver->coId(i);
            bestx = thesolver->coTest()[i];
         }
      }

      for (i = thesolver->coDim() - 1; i >= 0; --i)
      {
         x = thesolver->test()[i];
         // x *= EQ_PREF * (1 + (ds.status(i) == SPxBasis::Desc::P_FREE
         //                || ds.status(i) == SPxBasis::Desc::D_FREE));
         if (x < bestx)
         {
            id = thesolver->id(i);
            bestx = thesolver->test()[i];
         }
      }

      return id;
   }
}

int SPxParMultPR::selectLeave()
{
   int i, n;
   Real x;
   Real best = -theeps;
   //    const Real* up  = thesolver->ubBound();
   //    const Real* low = thesolver->lbBound();

   assert(thesolver != 0);
   n = -1;
   for (i = thesolver->dim() - 1; i >= 0; --i)
   {
      x = thesolver->fTest()[i];
      // x *= EQ_PREF * (1 + (up[i] == low[i]));
      if (x < best)
      {
         n = i;
         best = thesolver->fTest()[i];
      }
   }

   return n;
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
