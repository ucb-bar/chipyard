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
#pragma ident "@(#) $Id: spxparmultpr.h,v 1.11 2002/03/21 16:06:19 bzfkocht Exp $"
#endif

/**@file  spxparmultpr.h
 * @brief Partial multiple pricing.
 */
#ifndef _SPXPARMULTPR_H_
#define _SPXPARMULTPR_H_

#include <assert.h>

#include "spxdefines.h"
#include "spxpricer.h"
#include "dataarray.h"
#include "array.h"
#include "ssvector.h"

namespace soplex
{

struct SPxParMultPr_Tmp
{
   SPxId id;
   Real test;
};

/**@brief   Partial multiple pricing.
   @ingroup Algo

   Class #SPxParMultPr is an implementation class for #SPxPricer implementing
   Dantzig's default pricing strategy with partial multiple pricing.
   Partial multiple pricing applies to the #ENTER%ing Simplex only. A set of
   #partialSize eligible pivot indices is selected (partial pricing). In the
   following Simplex iterations pricing is restricted to these indices
   (multiple pricing) until no more eligable pivots are available. Partial
   multiple pricing significantly reduces the computation time for computing
   the matrix-vector-product in the Simplex algorithm.

   See #SPxPricer for a class documentation.
*/
class SPxParMultPR : public SPxPricer
{
private:
   DataArray < SPxParMultPr_Tmp > pricSet;
   int multiParts;
   int used;
   int min;
   int last;
   int count;

public:
   /**@todo make this member variable private (or protected) */
   /// Set size for partial pricing.
   static int partialSize;

   ///
   virtual void load(SoPlex* solver);
   ///
   virtual void setType(SoPlex::Type tp);
   ///
   virtual int selectLeave();
   ///
   virtual SPxId selectEnter();
   /// default constructor
   SPxParMultPR() 
      : SPxPricer("ParMult")
   {}   
};


} // namespace soplex
#endif // _SPXPARMULTPRR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
