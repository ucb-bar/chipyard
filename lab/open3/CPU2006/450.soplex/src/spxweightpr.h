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
#pragma ident "@(#) $Id: spxweightpr.h,v 1.12 2002/03/21 16:06:19 bzfkocht Exp $"
#endif

/**@file  spxweightpr.h
 * @brief Weighted pricing.
 */
#ifndef _SPXWEIGHTPR_H_
#define _SPXWEIGHTPR_H_

#include "spxdefines.h"
#include "spxpricer.h"

namespace soplex
{

/**@brief   Weighted pricing.
   @ingroup Algo
      
   Class #SPxWeightPR is an implemantation class of #SPxPricer that uses
   weights for columns and rows for selecting the Simplex pivots. The weights
   are computed by methods #computeCP() and #computeRP() which may be
   overridden by derived classes.
   
   The weights are interpreted as follows: The higher a value is, the more
   likely the corresponding row or column is set on one of its bounds.
   
   See #SPxPricer for a class documentation.
*/
class SPxWeightPR : public SPxPricer
{
private:
   DVector cPenalty;               // column penalties
   DVector rPenalty;               // row    penalties
   DVector leavePenalty;           // penalties for leaveing alg

   const Real* coPenalty;
   const Real* penalty;

   Real objlength;              // length of objective vector.

   /// compute leave penalties.
   void computeLeavePenalty(int start, int end);
   /// compute weights for columns.
   void computeCP(int start, int end);
   /// compute weights for rows.
   void computeRP(int start, int end);

public:
   ///
   virtual void load(SoPlex* base);
   ///
   void setType(SoPlex::Type tp);
   ///
   void setRep(SoPlex::Representation rep);
   ///
   virtual int selectLeave();
   ///
   virtual SPxId selectEnter();
   ///
   virtual void addedVecs (int n);
   ///
   virtual void addedCoVecs(int n);
   ///
   virtual void removedVec(int i);
   ///
   virtual void removedCoVecs(const int perm[]);
   ///
   virtual void removedVecs(const int perm[]);
   ///
   virtual void removedCoVec(int i);
   ///
   virtual bool isConsistent() const;

   /// default constructor
   SPxWeightPR() 
      : SPxPricer("Weight")
   {}   
};
} // namespace soplex
#endif // _SPXWEIGHTPR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
