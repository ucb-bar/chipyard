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
#pragma ident "@(#) $Id: spxsimplifier.h,v 1.9 2002/03/03 13:50:35 bzfkocht Exp $"
#endif

/**@file  spxsimplifier.h
 * @brief LP simplification base class.
 */
#ifndef _SPXSIMPLIFIER_H_
#define _SPXSIMPLIFIER_H_

#include <assert.h>

#include "spxdefines.h"
#include "spxlp.h"

namespace soplex
{
/**@brief   LP simplification abstract base class.
   @ingroup Algo

   Instances of classes derived from #SPxSimplifier may be loaded to #SoPlex in
   order to simplify LPs before solving them. #SoPlex# will #load()# itself to
   the #SPxSimplifier and then call #simplify(). Generally any #SPxLP can be
   loaded to a #SPxSimplifier for #simplify()%ing it. The simplification can
   be undone by calling #unsimplify().
*/
class SPxSimplifier
{
protected:
   SPxLP* lp;     ///< LP to work on.
   Real delta;  ///< Offset for the objective function.

public:
   SPxSimplifier() 
      : lp(0), delta(0)
   {}

   /// Load the #SPxLP to be simplified.
   virtual void load(SPxLP* p_lp)
   {
      assert(p_lp != 0);

      if (lp != p_lp)
      {
         lp    = p_lp;
         delta = 0;
      }
   }
   /// Unload the #SPxLP.
   virtual void unload()
   {
      lp = 0;
   }

   /// Simplify loaded #SPxLP. 
   /**
    * @return 
    *  <TABLE>
    *  <TR><TD> 0 </TD><TD>if this could be done,</TD></TR>
    *  <TR><TD> 1 </TD><TD>if primal unboundedness was detected or</TD></TR>
    *  <TR><TD>-1 </TD><TD>if primal infeasibility was detected.</TD></TR>
    *  </TABLE>
    */
   virtual int simplify() = 0;

   /// Unsimplify the loaded #SPxLP.
   virtual void unsimplify() = 0;

   /// objective value for unsimplified LP.
   /** The simplifyed LP may show other objective values than the
    *  original, if a constant part has been removed from the LP.
    *  This method returns the value for the original LP, for a
    *  value \p x of the simplified LP.
    */
   virtual Real value(Real x)
   {
      return x + delta;
   }

   /// consistency check
   bool isConsistent() const
   {
      return true;
   }
};
} // namespace soplex
#endif // _SPXSIMPLIFIER_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------

