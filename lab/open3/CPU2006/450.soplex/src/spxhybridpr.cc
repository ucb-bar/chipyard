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
#pragma ident "@(#) $Id: spxhybridpr.cpp,v 1.20 2002/04/04 14:59:04 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <iostream>

#include "spxdefines.h"
#include "spxhybridpr.h"
#include "message.h"

namespace soplex
{

bool SPxHybridPR::isConsistent() const
{
   if (thesolver != 0 &&
      (thesolver != steep.solver() ||
         thesolver != devex.solver() ||
         thesolver != parmult.solver()))
      return MSGinconsistent("SPxHybridPR");

   return steep.isConsistent()
          && devex.isConsistent()
          && parmult.isConsistent();
}

void SPxHybridPR::load(SoPlex* p_solver)
{
   steep.load(p_solver);
   devex.load(p_solver);
   parmult.load(p_solver);
   thesolver = p_solver;
   setType(p_solver->type());
}

void SPxHybridPR::clear()
{
   steep.clear();
   devex.clear();
   parmult.clear();
   thesolver = 0;
}

void SPxHybridPR::setEpsilon(Real eps)
{
   steep.setEpsilon(eps);
   devex.setEpsilon(eps);
   parmult.setEpsilon(eps);
}

void SPxHybridPR::setType(SoPlex::Type tp)
{
   if (tp == SoPlex::LEAVE)
   {
      thepricer = &steep;
      thesolver->setPricing(SoPlex::FULL);
   }
   else
   {
      if (thesolver->dim() > hybridFactor * thesolver->coDim())
      {
         /**@todo I changed from devex to steepest edge pricing here 
          *       because of numerical difficulties, this should be 
          *       investigated.
          */
         // thepricer = &devex;
         thepricer = &steep;
         thesolver->setPricing(SoPlex::FULL);
      }
      else
      {
         thepricer = &parmult;
         thesolver->setPricing(SoPlex::PARTIAL);
      }
   }
   
   VERBOSE1({ std::cout << "switching to "
                        << thepricer->getName() << std::endl; });

   thepricer->setType(tp);
}

void SPxHybridPR::setRep(SoPlex::Representation rep)
{
   steep.setRep(rep);
   devex.setRep(rep);
   parmult.setRep(rep);
}

int SPxHybridPR::selectLeave()
{
   return thepricer->selectLeave();
}

void SPxHybridPR::left4(int n, SPxId id)
{
   thepricer->left4(n, id);
}

SPxId SPxHybridPR::selectEnter()
{
   return thepricer->selectEnter();
}

void SPxHybridPR::entered4(SPxId id, int n)
{
   thepricer->entered4(id, n);
}

void SPxHybridPR::addedVecs (int n)
{
   steep.addedVecs(n);
   devex.addedVecs(n);
   parmult.addedVecs(n);
}

void SPxHybridPR::addedCoVecs(int n)
{
   steep.addedCoVecs(n);
   devex.addedCoVecs(n);
   parmult.addedCoVecs(n);
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
