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
#pragma ident "@(#) $Id: spxvectorst.h,v 1.5 2002/01/31 08:19:30 bzfkocht Exp $"
#endif

/**@file  spxvectorst.h
 * @brief Solution vector based start basis.
 */
#ifndef _SPXVECTORST_H_
#define _SPXVECTORST_H_

#include <assert.h>

#include "spxweightst.h"
#include "vector.h"

namespace soplex
{

/**@brief   Solution vector based start basis.
   @ingroup Algo

   This version of #SPxWeightST can be used to construct a starting basis for
   an LP to be solved with #SoPlex, if an approximate solution vector or dual
   vector (possibly optained by a heuristic) is available. This is done by
   setting up weights for the #SPxWeightST it is derived from.
   
   The primal vector to be used is loaded by calling method #primal() while
   #dual() setups for the dual vector. Methods #primal() or #dual() must be
   called \em before #generate() is called by #SoPlex to set up a
   starting basis. If more than one call of method #primal() or #dual()
   occurred only the most recent one is valid for generating the starting base.
*/
class SPxVectorST : public SPxWeightST
{
   enum { NONE, PVEC, DVEC} state;
   DVector vec;

protected:
   /// sets up variable weights.
   void setupWeights(SoPlex& base);

public:
   /// sets up primal solution vector.
   void primal(const Vector& v)
   {
      vec = v;
      state = PVEC;
   }

   /// sets up primal solution vector.
   void dual(const Vector& v)
   {
      vec = v;
      state = DVEC;
   }

   /// default constructor.
   SPxVectorST() : state(NONE)
   {}

};

} // namespace soplex
#endif // _SPXVECTORST_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
