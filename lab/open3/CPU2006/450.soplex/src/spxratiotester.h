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
#pragma ident "@(#) $Id: spxratiotester.h,v 1.8 2002/03/21 16:06:19 bzfkocht Exp $"
#endif

/**@file  spxratiotester.h
 * @brief Abstract ratio test base class.
 */
#ifndef _SPXRATIOTESTER_H_
#define _SPXRATIOTESTER_H_


#include <assert.h>

#include "spxdefines.h"
#include "soplex.h"

namespace soplex
{

/**@brief Abstract ratio test base class.
   @ingroup Algo

   Class #SPxRatioTester is the virtual base class for computing the ratio
   test within the Simplex algorithm driven by #SoPlex. After a #SoPlex
   solver has been #load()%ed to an #SPxRatioTester, the solver calls
   #selectLeave() for computing the ratio test for the entering simplex and
   #selectEnter() for computing the ratio test in leaving simplex.
*/
class SPxRatioTester
{
protected:
   SoPlex* thesolver;

public:
   /// loads LP.
   /** Load the solver and LP for which pricing steps are to be performed.
    */
   virtual void load(SoPlex* p_solver)
   {
      thesolver = p_solver;
   }

   /// unloads LP.
   virtual void clear()
   {
      thesolver = 0;
   }

   /// returns loaded LP solver.
   virtual SoPlex* solver() const
   {
      return thesolver;
   }

   /// selects index to leave the basis.
   /** Method #selectLeave() is called by the loaded #SoPlex solver, when
       computing the entering simplex algorithm. It's task is to select and
       return the index of the basis variable that is to leave the basis.
       When beeing called, #fVec() fullfills the basic bounds #lbBound()
       and #ubBound() within #delta. #fVec().delta() is the vector by
       which #fVec() will be updated in this simplex step. Its nonzero
       indeces are stored in sorted order in #fVec().idx().
       
       If \p val > 0, \p val is the maximum allowed update value for #fVec(),
       otherwise the minimum. Method #selectLeave() must chose \p val of the
       same sign as passed, such that updating #fVec() by \p val yields a
       new vector that satisfies all basic bounds (within #delta). The
       returned index, must be the index of an element of #fVec(), that
       reaches one of its bounds with this update.
   */
   virtual int selectLeave(Real& val) = 0;

   /// selects variable Id to enter the basis.
   /** Method #selectEnter() is called by the loaded #SoPlex solver, when
       computing the leaving simplex algorithm. It's task is to select and
       return the #Id of the basis variable that is to enter the basis.
       When beeing called, #pVec() fullfills the bounds #lpBound() and
       #upBound() and #coPvec() bounds #lcBound() and #ucBound() within
       #delta, respectively. #pVec().delta() and #coPvec().delta() are
       the vectors by which #pVec() and #coPvec() will be updated in this
       simplex step. Their nonzero indeces are stored in sorted order in
       #pVec().idx() and #coPvec().idx().
       
       If \p val > 0, \p val is the maximum allowed update value for #pVec()
       and #coPvec(), otherwise the minimum. Method #selectEnter() must
       chose \p val of the same sign as passed, such that updating #pVec()
       and #coPvec() by \p val yields a new vector that satisfies all basic
       bounds (within #delta). The returned #Id, must be the #Id of an
       element of #pVec() or #coPvec(), that reaches one of its bounds
       with this update.
    */
   virtual SPxId selectEnter(Real& val) = 0;

   /// sets Simplex type.
   /** Informs pricer about (a change of) the loaded #SoPlex's #Type. In
       the sequel, only the corresponding select methods may be called.
   */
   virtual void setType(SoPlex::Type)
   {}
   /// default constructor
   SPxRatioTester()
      : thesolver(0)
   {}
   /// destructor.
   virtual ~SPxRatioTester()
   {
      thesolver = 0;
   }

};


} // namespace soplex
#endif // _SPXRATIOTESTER_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
