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
#pragma ident "@(#) $Id: spxsolver.h,v 1.5 2002/01/31 08:19:29 bzfkocht Exp $"
#endif

/**@file  spxsolver.h
 * @brief preconfigured #SoPlex LP-solver.
 */
#ifndef _SPXSOLVER_H_
#define _SPXSOLVER_H_

#include <assert.h>

#include "spxsteeppr.h"
#include "spxfastrt.h"
#include "spxweightst.h"
#include "slufactor.h"

namespace soplex
{
/**@brief   Preconfigured #SoPlex LP-solver.
   @ingroup Algo
*/
class SPxSolver : public SoPlex
{
private:
   SPxFastRT   rt;  ///< fast ratio test
   SPxSteepPR  pr;  ///< steepest edge pricing
   SPxWeightST st;  ///< weight starter
   SLUFactor   slu; ///< LU Factorisation

public:
   virtual int getFactorCount() const
   {
      return slu.getFactorCount();
   }
   void setUtype(SLUFactor::UpdateType tp)
   {
      slu.setUtype(tp);
   }

   /// default construtor.
   explicit SPxSolver(
      Type type = LEAVE, SoPlex::Representation rep = SoPlex::COLUMN);
};
} // namespace soplex
#endif // _SPXSOLVER_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
