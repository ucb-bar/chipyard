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
#pragma ident "@(#) $Id: spxsolver.cpp,v 1.8 2002/01/31 08:19:29 bzfkocht Exp $"
#endif

#include <iostream>

#include "spxsolver.h"

namespace soplex
{
SPxSolver::SPxSolver(Type p_type, SoPlex::Representation p_rep)
   : SoPlex(p_type, p_rep)
{
   SoPlex::setTester(&rt);
   SoPlex::setPricer(&pr);
   // SoPlex::setStarter(&st);
   // No starter at the moment.
   SoPlex::setStarter(0);
   SoPlex::setSolver(&slu);
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
