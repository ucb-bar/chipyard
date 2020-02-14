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
#pragma ident "@(#) $Id: lprow.h,v 1.11 2002/03/11 11:41:56 bzfkocht Exp $"
#endif

/**@file  lprow.h
 * @brief (In)equality for LPs.
 */
#ifndef _LPROW_H_
#define _LPROW_H_

#include <assert.h>

#include "spxdefines.h"
#include "dsvector.h"

namespace soplex
{
/**@brief   (In)equality for LPs.
   @ingroup Algo

   Class LPRow provides constraints for linear programs in the form
   \f[
                       l \le a^Tx \le r,
   \f]
   where \em a is a DSVector. \em l is referred to as 
   %left hand side,
   \em r as %right hand side and \em a as \em row \em vector or 
   the constraint vector. \em l and \em r may also take values 
   \f$\pm\f$ #infinity. 
   This static member is predefined, but may be overridden to meet 
   the needs of the LP solver to be used.
 
    LPRow%s allow to specify regular inequalities of the form 
   \f[
                           a^Tx \sim \alpha,
   \f]
   where \f$\sim\f$ can take any value 
   of \f$\le, =, \ge\f$, by setting rhs and
   lhs to the same value or setting one of them to \f$\infty\f$.

   Since constraints in the regular form occur often, LPRow%s offers methods
   type() and value() for retreiving \f$\sim\f$ and \f$\alpha\f$ of 
   an LPRow in this form, respectively. Also, a constructor for 
   LPRow%s given in regular form is provided.
*/
class LPRow
{
private:
   Real   left;
   Real   right;
   DSVector vec;

public:
   /// (In)Equality of an LP row.
   /** LPRow%s may be of one of the above Types. This datatype may be
    *  used for constructing new LPRow%s in the regular form.
    */
   enum Type
   {                          
      LESS_EQUAL,          ///< \f$a^Tx \le \alpha\f$.   
      EQUAL,               ///< \f$a^Tx = \alpha\f$.   
      GREATER_EQUAL,       ///< \f$a^Tx \ge \alpha\f$.    
      RANGE                ///< \f$\lambda \le a^Tx \le \rho\f$.
   };

   /// get type of row.
   Type type() const;

   /// set type of (in)equality
   void setType(Type type);

   /// Right hand side value of (in)equality.
   /** This method returns \f$\alpha\f$ for a LPRow in regular form.
    *  However, value() may only be called for LPRow%s with
    *  type() != \c RANGE.
    */
   Real value() const;

   /// get left hand side of value.
   Real lhs() const
   {
      return left;
   }

   /// access left hand side value.
   void setLhs(Real p_left)
   {
      left = p_left;
   }

   /// get right hand side value.
   Real rhs() const
   {
      return right;
   }

   /// access right hand side value.
   void setRhs(Real p_right)
   {
      right = p_right;
   }

   /// get aconstraint row %vector
   const SVector& rowVector() const
   {
      return vec;
   }

   /// access constraint row %vector.
   void setRowVector(const DSVector& p_vec)
   {
      vec = p_vec;
   }

   /// Construct LPRow with a vector ready to hold \p defDim nonzeros
   explicit LPRow(int defDim = 0) 
      : left(0), right(infinity), vec(defDim)
   {}

   /// copy constructor
   LPRow(const LPRow& row) 
      : left(row.left), right(row.right), vec(row.vec)
   {}

   /// Construct LPRow with the given left-hand side, right-hand side
   /// and rowVector.
   LPRow(Real p_lhs, const SVector& p_rowVector, Real p_rhs)
      : left(p_lhs), right(p_rhs), vec(p_rowVector)
   {}

   /// Construct LPRow from passed \p rowVector, \p type and \p value
   LPRow(const SVector& rowVector, Type type, Real value);

   /// check consistency.
   bool isConsistent() const
   {
      return vec.isConsistent();
   }
};

} // namespace soplex
#endif // _LPROW_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
