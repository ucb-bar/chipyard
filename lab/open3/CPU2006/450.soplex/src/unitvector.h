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
#pragma ident "@(#) $Id: unitvector.h,v 1.8 2002/03/03 13:50:36 bzfkocht Exp $"
#endif

/**@file  unitvector.h
 * @brief Sparse vector \f$e_i\f$.
 */

#ifndef _UNITVECTOR_H_
#define _UNITVECTOR_H_

#include <assert.h>
#include "spxdefines.h"
#include "svector.h"

namespace soplex
{


/**@brief   Sparse vector \f$e_i\f$.
   @ingroup Algebra

   A UnitVector is an SVector that can take only one nonzero value with
   value 1 but arbitrary index.

   \todo Several SVector modification methods are still accessible for UnitVector. 
   They might be used to change the vector.

   \todo UnitVector memory management must be changed when SVector is redesigned.
*/
class UnitVector : public SVector
{
private:
   Element themem;  ///< memory for 1st sparse vector entry (size)
   Element themem1; ///< memory for 2nd sparse vector entry (idx,1.0)

public:
   /// returns value = 1
   /**\pre \c n must be 0.
    */
   /* ARGSUSED n */
   Real value(int n) const
   {
      assert( n == 0 );
      return 1;
   }

   /// construct \c i 'th unit vector.
   UnitVector(int i = 0)
      : SVector(2, &themem)
   {
      add(i, 1.0);
   }

   ///  copy constructor
   UnitVector(const UnitVector& rhs)
      : SVector(2, &themem)
      , themem (rhs.themem)
      , themem1(rhs.themem1)
   {}

   /// assignment
   UnitVector& operator=(const UnitVector& rhs)
   {
      if ( this != &rhs )
         themem1 = rhs.themem1;
      return *this;
   }

   /// consitency check
   bool isConsistent() const;
};


} // namespace soplex
#endif // _UNITVECTOR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
