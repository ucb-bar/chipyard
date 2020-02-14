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
#pragma ident "@(#) $Id: spxdefaultrt.h,v 1.10 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

/**@file  spxdefaultrt.h
 * @brief Textbook ratio test for #SoPlex.
 */
#ifndef _SPXDEFAULTRT_H_
#define _SPXDEFAULTRT_H_


#include <assert.h>

#include "spxdefines.h"
#include "spxratiotester.h"

namespace soplex
{

/**@brief   Textbook ratio test for #SoPlex.
   @ingroup Algo
   
   Class #SPxDefaultRT provides an implementation of the textbook ratio test
   as a derived class of #SPxRatioTester. This class is not intended for
   reliably solving LPs (even though it does the job for ``numerically simple''
   LPs). Instead, it should serve as a demonstration of how to write ratio
   tester classes.

   See #SPxRatioTester for a class documentation.
*/
class SPxDefaultRT : public SPxRatioTester
{
private:
   ///
   int selectLeaveX(Real& val, int start, int incr);
   ///
   SPxId selectEnterX(Real& val, 
      int start1, int incr1, int start2, int incr2);

public:
   ///
   virtual int selectLeave(Real& val);
   ///
   virtual SPxId selectEnter(Real& val);
   /// default constructor
   SPxDefaultRT() 
      : SPxRatioTester()
   {}
};

} // namespace soplex
#endif // _SPXDEFAULTRT_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
