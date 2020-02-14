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
#pragma ident "@(#) $Id: spxharrisrt.h,v 1.13 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

/**@file  spxharrisrt.h
 * @brief Harris pricing with shifting.
 */
#ifndef _SPXHARRISRT_H_
#define _SPXHARRISRT_H_

#include <assert.h>

#include "spxdefines.h"
#include "spxratiotester.h"

namespace soplex
{

/**@brief   Harris pricing with shifting.
   @ingroup Algo
   
   Class #SPxHarrisRT is a stable implementation of a #SPxRatioTester class
   along the lines of Harris' two phase algorithm. Additionally it uses
   shifting of bounds in order to avoid cycling.

   See #SPxRatioTester for a class documentation.
*/
/**@todo HarrisRT leads to cycling in dcmulti.sub.lp */
class SPxHarrisRT : public SPxRatioTester
{
private:
   int maxDelta(
      Real* /*max*/,       ///< max abs value in upd
      Real* val,           ///< initial and chosen value
      int num,               ///< # of indices in idx
      const int* idx,        ///< nonzero indices in upd
      const Real* upd,     ///< update vector for vec
      const Real* vec,     ///< current vector
      const Real* low,     ///< lower bounds for vec
      const Real* up,      ///< upper bounds for vec
      Real delta,          ///< allowed bound violation
      Real epsilon);       ///< what is 0?

   int minDelta(
      Real* /*max*/,       ///< max abs value in upd
      Real* val,           ///< initial and chosen value
      int num,               ///< of indices in idx
      const int* idx,        ///< nonzero indices in upd
      const Real* upd,     ///< update vector for vec
      const Real* vec,     ///< current vector
      const Real* low,     ///< lower bounds for vec
      const Real* up,      ///< upper bounds for vec
      Real delta,          ///< allowed bound violation
      Real epsilon);        ///< what is 0?

public:
   ///
   virtual int selectLeave(Real& val);
   ///
   virtual SPxId selectEnter(Real& val);
   /// default constructor
   SPxHarrisRT() 
      : SPxRatioTester()
   {}
};

} // namespace soplex
#endif // _SPXHARRISRT_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
