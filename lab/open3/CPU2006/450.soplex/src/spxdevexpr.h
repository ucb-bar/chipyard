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
#pragma ident "@(#) $Id: spxdevexpr.h,v 1.13 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

/**@file  spxdevexpr.h
 * @brief Devex pricer.
 */
#ifndef _SPXDEVEXPR_H_
#define _SPXDEVEXPR_H_

#include <assert.h>

#include "spxdefines.h"
#include "spxpricer.h"

namespace soplex
{

/**@brief   Devex pricer.
   @ingroup Algo

   The Devex Pricer for SoPlex implements an approximate steepest edge pricing,
   that does without solving an extra linear system and computing the scalar
   products.

   See #SPxPricer for a class documentation.

   @todo There seem to be problems with this pricer especially on the 
         greenbe[ab] problems with the entering algorithm 
         (row representation?).
*/
class SPxDevexPR : public SPxPricer
{
private:
   Real  last;           ///< penalty, selected at last iteration.
   DVector penalty;        ///< vector of pricing penalties.
   DVector coPenalty;      ///< vector of pricing penalties.

   ///
   int selectLeaveX(Real& best, int start = 0, int incr = 1);
   ///
   void left4X(int n, SPxId id, int start, int incr);
   ///
   SPxId selectEnterX(Real& best, 
      int start1 = 0, int incr1 = 1, int start2 = 0, int incr2 = 1);
   ///
   void entered4X(SPxId id, int n, 
      int start1, int incr1, int start2, int incr2);

public:
   ///
   virtual void load(SoPlex* base);
   ///
   virtual void setType(SoPlex::Type);
   ///
   virtual void setRep(SoPlex::Representation);
   ///
   virtual int selectLeave();
   ///
   virtual void left4(int n, SPxId id);
   ///
   virtual SPxId selectEnter();
   ///
   virtual void entered4(SPxId id, int n);
   /// \p n vectors have been added to loaded LP.
   virtual void addedVecs (int n);
   /// \p n covectors have been added to loaded LP.
   virtual void addedCoVecs(int n);
   ///
   virtual bool isConsistent() const;
   /// default constructor
   SPxDevexPR() 
      : SPxPricer("Devex")
   {}   
};

} // namespace soplex
#endif // _SPXDEVEXPR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
