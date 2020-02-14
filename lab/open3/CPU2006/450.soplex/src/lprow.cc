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
#pragma ident "@(#) $Id: lprow.cpp,v 1.14 2002/03/11 17:43:56 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <stdlib.h>
#include <math.h>
#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "lprow.h"

namespace soplex
{
LPRow::Type LPRow::type() const
{
   if (rhs() >= infinity)
      return GREATER_EQUAL;
   if (lhs() <= -infinity)
      return LESS_EQUAL;
   if (lhs() == rhs())
      return EQUAL;
   return RANGE;
}

void LPRow::setType(
   LPRow::Type p_type)
{
   switch (p_type)
   {
   case LESS_EQUAL:
      left = -infinity;
      break;
   case EQUAL:
      if (lhs() > -infinity)
         right = lhs();
      else
         left = rhs();
      break;
   case GREATER_EQUAL:
      right = infinity;
      break;
   case RANGE:
      std::cerr << __FILE__ << __LINE__
                << "RANGE not supported in LPRow::setType()" << std::endl;
      abort();
   default:
      abort();
   }
}

Real LPRow::value() const
{
   assert(type() != RANGE);

   return (rhs() < infinity) ? rhs() : lhs();
}

LPRow::LPRow(const SVector& p_rowVector, LPRow::Type p_type, Real p_value)
   : vec(p_rowVector)
{
   switch (p_type)
   {
   case LESS_EQUAL:
      left = -infinity;
      right = p_value;
      break;
   case EQUAL:
      left = p_value;
      right = p_value;
      break;
   case GREATER_EQUAL:
      left = p_value;
      right = infinity;
      break;
   default:
      abort();
   }
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
