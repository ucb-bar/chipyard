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
#pragma ident "@(#) $Id: spxaggregatesm.h,v 1.9 2002/03/03 13:50:33 bzfkocht Exp $"
#endif

/**@file  spxaggregatesm.h
 * @brief LP variable aggregation.
 */
#ifndef _SPXAGGREGATESM_H_
#define _SPXAGGREGATESM_H_

#include <assert.h>

#include "spxdefines.h"
#include "spxsimplifier.h"

namespace soplex
{
/** @brief
    @ingroup Algo

    This #SPxSimplifier does variable aggregation.
 */
class SPxAggregateSM : public SPxSimplifier
{
private:
   Real stability;   ///< stability factor, e.g. 0.01.   
   Real maxFill;     ///< ???  

   /// ???
   int eliminate(const SVector& row, Real b);

public:
   /// Aggregate variable.
   int simplify();

   /// Undo #simplify().
   void unsimplify();

   /// objective value for unsimplified LP.
   Real value(Real x)
   {
      return x + lp->spxSense()*delta;
   }
};
} // namespace soplex
#endif // _SPXAGGREGATESM_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
