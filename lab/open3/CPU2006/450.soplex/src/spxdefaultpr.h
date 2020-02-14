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
#pragma ident "@(#) $Id: spxdefaultpr.h,v 1.10 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

/**@file  spxdefaultpr.h
 * @brief Default pricer.
 */
#ifndef _SPXDEFAULTPR_H_
#define _SPXDEFAULTPR_H_

#include <assert.h>

#include "spxpricer.h"

namespace soplex
{

/**@brief   Default pricer.
   @ingroup Algo

   Class #SPxDefaultPR is an implementation class for #SPxPricer implementing
   Dantzig's the default pricing strategy, i.e. maximal/minimal reduced cost or
   maximal violated constraint.

   See #SPxPricer for a class documentation.

   @todo This should be renamed to something like Danzig or Textbook pricing.
*/
class SPxDefaultPR : public SPxPricer
{
private:
   ///
   int selectLeaveX(int start, int incr);
   ///
   SPxId selectEnterX(int start1, int incr1, int start2, int incr2);

public:
   ///
   virtual int selectLeave();
   ///
   virtual SPxId selectEnter();

   /// default constructor
   SPxDefaultPR() 
      : SPxPricer("Danzig")
   {}   
};
} // namespace soplex
#endif // _SPXDEFAULTPRR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
