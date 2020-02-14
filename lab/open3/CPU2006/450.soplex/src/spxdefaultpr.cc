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
#pragma ident "@(#) $Id: spxdefaultpr.cpp,v 1.11 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

#include <assert.h>
#include <iostream>

#define EQ_PREF 1000

#include "spxdefines.h"
#include "spxdefaultpr.h"

namespace soplex
{
int SPxDefaultPR::selectLeaveX(int start, int incr)
{
   assert(thesolver != 0);

   //    const Real* up  = thesolver->ubBound();
   //    const Real* low = thesolver->lbBound();

   Real best = -theeps;
   int  n    = -1;

   for(int i = thesolver->dim() - start - 1; i >= 0; i -= incr)
   {
      Real x = thesolver->fTest()[i];

      if (x < -theeps)
      {
         // x *= EQ_PREF * (1 + (up[i] == low[i]));
         if (x < best)
         {
            n = i;
            best = x;
         }
      }
   }
   return n;
}

int SPxDefaultPR::selectLeave()
{
   return selectLeaveX(0, 1);
}

SPxId SPxDefaultPR::selectEnterX(int start1, int incr1,
                                     int start2, int incr2)
{
   assert(thesolver != 0);

   // const SPxBasis::Desc&    ds   = thesolver->basis().desc();

   SPxId id;
   int        i;
   Real     best = -theeps;

   for (i = thesolver->dim() - start1 - 1; i >= 0; i -= incr1) 
   {
      Real x = thesolver->coTest()[i];

      if (x < -theeps)
      {
         // x *= EQ_PREF * (1 + (ds.coStatus(i) == SPxBasis::Desc::P_FREE
         //                || ds.coStatus(i) == SPxBasis::Desc::D_FREE));
         if (x < best)
         {
            id = thesolver->coId(i);
            best = x;
         }
      }
   }

   for (i = thesolver->coDim() - start2 - 1; i >= 0; i -= incr2)
   {
      Real x = thesolver->test()[i];

      if (x < -theeps)
      {
         // x *= EQ_PREF * (1 + (ds.status(i) == SPxBasis::Desc::P_FREE
         //                || ds.status(i) == SPxBasis::Desc::D_FREE));
         if (x < best)
         {
            id = thesolver->id(i);
            best = x;
         }
      }
   }
   return id;
}

SPxId SPxDefaultPR::selectEnter()
{
   return selectEnterX(0, 1, 0, 1);
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
