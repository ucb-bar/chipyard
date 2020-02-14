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
#pragma ident "@(#) $Id: spxfastrt.h,v 1.11 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

/**@file  spxfastrt.h
 * @brief Fast shifting ratio test.
 */
#ifndef _SPXFASTRT_H_
#define _SPXFASTRT_H_

#include <assert.h>

#include "spxdefines.h"
#include "spxratiotester.h"

namespace soplex
{

/**@brief   Fast shifting ratio test.
   @ingroup Algo
   
   Class #SPxFastRT is an implementation class of #SPxRatioTester providing
   fast and stable ratio test. Stability is achieved by allowing some
   infeasibility to ensure numerical stability such as the Harris procedure.
   Performance is acchieved by skipping the second phase is the first phase
   allready shows a stable enough pivot.

   See #SPxRatioTester for a class documentation.
*/
class SPxFastRT : public SPxRatioTester
{
private:
   /// minimum stability parameter for stopping after phase 1.
   Real minStab;
   /// |value| < epsilon is considered 0.
   Real epsilon;
   /// currently allowed infeasibility.
   Real delta;
   /// initially allowed infeasibility.
   Real delta0;

   /// resets tolerances.
   void resetTols();
   /// relaxes stability requirements.
   void relax();
   /// tightens stability requirements.
   void tighten();

   /// Max phase 1 value.
   /** Computes the maximum value \p val that could be used for updating \p upd
       such that it would still fullfill the upper and lower bounds \p up and
       \p low, respectively, within #delta. Return value is the index where the
       minimum value is encounterd. At the same time the maximum absolute value
       of \p upd.delta() is computed and returned in \p abs. Internally all
       loops are started at \p start and incremented by \p incr.
    */
   int maxDelta(Real& val, Real& p_abs, UpdateVector& upd,
      Vector& low, Vector& up, int start, int incr);
   ///
   int maxDelta(Real& val, Real& p_abs);
   ///
   SPxId maxDelta(int& nr, Real& val, Real& p_abs);

   /// Min phase 1 value.
   /** Computes the minimum value \p val that could be used for updating \p upd
       such that it would still fullfill the upper and lower bounds \p up and
       \p low, respectively, within #delta. Return value is the index where the
       minimum value is encounterd. At the same time the maximum absolute value
       of \p upd.delta() is computed and returned in \p abs. Internally all
       loops are started at \p start and incremented by \p incr.
   */
   int minDelta(Real& val, Real& p_abs, UpdateVector& upd,
      Vector& low, Vector& up, int start, int incr);
   
   ///
   int minDelta(Real& val, Real& p_abs,
      UpdateVector& upd, Vector& low, Vector& up)
   {
      return minDelta(val, p_abs, upd, low, up, 0, 1);
   }
   ///
   int minDelta(Real& val, Real& p_abs);
   ///
   SPxId minDelta(int& nr, Real& val, Real& p_abs);
   
   /// selects stable index for maximizing ratio test.
   /** Selects form all update values \p val < \p max the one with the largest
       value of \p upd.delta() which must be greater than \p stab and is
       returned in \p stab. The index is returned as well as the corresponding
       update value \p val. Internally all loops are started at \p start and
       incremented by \p incr.
   */
   int maxSelect(Real& val, Real& stab, Real& best, Real& bestDelta, 
      Real max, const UpdateVector& upd, const Vector& low, 
      const Vector& up, int start = 0, int incr = 1);
   ///
   int maxSelect(Real& val, Real& stab, Real& bestDelta, Real max);
   ///
   SPxId maxSelect(int& nr, Real& val, Real& stab,
      Real& bestDelta, Real max);

   /// selects stable index for minimizing ratio test.
   /** Select form all update values \p val > \p max the one with the largest
       value of \p upd.delta() which must be greater than \p stab and is
       returned in \p stab. The index is returned as well as the corresponding
       update value \p val. Internally all loops are started at \p start and
       incremented by \p incr.
   */
   int minSelect(Real& val, Real& stab, Real& best, Real& bestDelta,
      Real max, const UpdateVector& upd, const Vector& low,
      const Vector& up, int start = 0, int incr = 1);

   int minSelect(Real& val, Real& stab,
      Real& bestDelta, Real max);

   SPxId minSelect(int& nr, Real& val, Real& stab,
      Real& bestDelta, Real max);

   ///
   int minReleave(Real& sel, int leave, Real maxabs);
   /// numerical stability tests.
   /** Tests whether the selected leave index needs to be discarded (and do so)
       and the ratio test is to be recomputed.
   */
   int maxReleave(Real& sel, int leave, Real maxabs);

   ///
   int minShortLeave(Real& sel, int leave, Real /*max*/, Real p_abs);
   /// tests for stop after phase 1.
   /** Tests whether a shortcut after phase 1 is feasible for the 
       selected leave
       pivot. In this case return the update value in \p sel.
   */
   int maxShortLeave(Real& sel, int leave, Real /*max*/, Real p_abs);

   ///
   int minReenter(Real& sel, Real /*max*/, Real maxabs,
                          SPxId id, int nr);
   /// numerical stability check.
   /** Tests whether the selected enter \p id needs to be discarded (and do so)
       and the ratio test is to be recomputed.
   */
   int maxReenter(Real& sel, Real /*max*/, Real maxabs,
                          SPxId id, int nr);

   /**@todo the documentation seems to be incorrect. 
            No parameter \p sel exists.
    */
   /// tests for stop after phase 1.
   /** Tests whether a shortcut after phase 1 is feasible for 
       the selected enter
       pivot. In this case return the update value in \p sel.
   */
   int shortEnter(SPxId& enterId, int nr, Real max, Real maxabs);

public:
   ///
   virtual void load(SoPlex* solver);
   ///
   virtual int selectLeave(Real& val);
   ///
   virtual SPxId selectEnter(Real& val);
   ///
   virtual void setType(SoPlex::Type);
   /// default constructor
   SPxFastRT() 
      : SPxRatioTester()
   {}
};
} // namespace soplex
#endif // _SPXFASTRT_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
