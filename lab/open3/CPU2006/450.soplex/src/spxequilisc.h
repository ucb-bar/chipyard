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
#pragma ident "@(#) $Id: spxequilisc.h,v 1.2 2002/04/10 07:13:17 bzfkocht Exp $"
#endif

/**@file  spxequilisc.h
 * @brief LP euilibrium scaling.
 */
#ifndef _SPXEQUILI_H_
#define _SPXEQUILI_H_

#include <assert.h>

#include "spxdefines.h"
#include "spxscaler.h"
#include "dataarray.h"

namespace soplex
{
/**@brief Equilibrium row/column scaling.
   @ingroup Algo

   This #SPxScaler implementation performs equilibrium scaling of the 
   LPs rows and columns.

   @todo The type of scaling (row/column) is hard coded. This should
         be selectable.
*/
class SPxEquili : public SPxScaler
{
public:
   /// Scale the loaded #SPxLP.
   virtual void scale();

   /// default constructor.
   explicit SPxEquili(bool colFirst = true, bool doBoth = true);
};
} // namespace soplex
#endif // _SPXEQUILI_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
