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
#pragma ident "@(#) $Id: updatevector.h,v 1.10 2002/04/09 07:07:48 bzfkocht Exp $"
#endif

/**@file  updatevector.h
 * @brief Dense vector with semi-sparse vector for updates
 */

#ifndef _UPDATEVECTOR_H_
#define _UPDATEVECTOR_H_

#include <assert.h>


#include "spxdefines.h"
#include "dvector.h"
#include "ssvector.h"

namespace soplex
{

/**@brief   Dense vector with semi-sparse vector for updates
   @ingroup Algebra

    In many algorithms vectors are updated in every iteration, by
    adding a multiple of another vector to it, i.e., given a vector \c
    x, a scalar \f$\alpha\f$ and another vector \f$\delta\f$, the
    update to \c x constists of substituting it by \f$x \leftarrow x +
    \alpha\cdot\delta\f$.
 
    While the update itself can easily be expressed with methods of
    the class Vector, it is often desirable to save the last update
    vector \f$\delta\f$ and value \f$\alpha\f$. This is provided by
    class UpdateVector.
 
    UpdateVector%s are derived from DVector and provide additional
    methods for saving and setting the multiplicator \f$\alpha\f$ and
    the update vector \f$\delta\f$. Further, it allows for efficient
    sparse updates, by providing an IdxSet idx() containing the
    nonzero indeces of \f$\delta\f$.  
*/
class UpdateVector : public DVector
{
   Real     theval;      ///< update multiplicator 
   SSVector thedelta;    ///< update vector

public:
   /// update multiplicator \f$\alpha\f$, writeable
   Real& value()
   {
      return theval;
   }
   /// update multiplicator \f$\alpha\f$
   Real value() const
   {
      return theval;
   }

   /// update vector \f$\delta\f$, writeable
   SSVector& delta()
   {
      return thedelta;
   }
   /// update vector \f$\delta\f$
   const SSVector& delta() const
   {
      return thedelta;
   }

   /// nonzero indeces of update vector \f$\delta\f$
   const IdxSet& idx() const
   {
      return thedelta.indices();
   }

   /// Perform the update
   /**  Add \c value() * \c delta() to the UpdateVector. Only the indeces 
    *  in idx() are affected. For all other indeces, delta() is asumed
    *  to be 0.
    */
   void update()
   {
      multAdd(theval, thedelta);
   }

   /// clear vector and update vector
   void clear()
   {
      DVector::clear();
      clearUpdate();
   }

   /// clear \f$\delta\f$, \f$\alpha\f$
   void clearUpdate()
   {
      thedelta.clear();
      theval = 0;
   }


   /// reset dimension
   void reDim(int newdim)
   {
      DVector::reDim(newdim);
      thedelta.reDim(newdim);
   }

   /// assignment from DVector
   UpdateVector& operator=(const DVector& rhs)
   {
      if ( this != & rhs )
         DVector::operator=(rhs);
      return *this;
   }

   /// assignment
   UpdateVector& operator=(const UpdateVector& rhs);

   /// default constructor.
   UpdateVector(int p_dim /*=0*/, Real p_eps /*=1e-16*/)
      : DVector (p_dim)
      , theval (0)
      , thedelta(p_dim, p_eps)
   {}

   /// 
   bool isConsistent() const;
};


} // namespace soplex
#endif // _UPDATEVECTOR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
