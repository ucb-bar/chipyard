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
#pragma ident "@(#) $Id: spxweightst.h,v 1.9 2002/03/21 16:06:19 bzfkocht Exp $"
#endif


/**@file  spxweightst.h
 * @brief Weighted start basis.
 */
#ifndef _SPXWEIGHTST_H_
#define _SPXWEIGHTST_H_


#include <assert.h>

#include "spxdefines.h"
#include "spxstarter.h"
#include "dataarray.h"

namespace soplex
{

/**@brief   Weighted start basis.
   @ingroup Algo

   Class #SPxWeightST is an implementation of a #SPxStarter for generating a
   Simplex starting basis. Using method #setupWeights() it sets up arrays
   #weight and #coWeight, or equivalently #rowWeight and #colWeight.
   (#rowWeight and #colWeight are just pointers initialized to #weight and
   #coWeight according to the representation of #SoPlex \p base passed to
   method #generate().) 
   
   The weight values are then used to setup a starting basis for the LP:
   vectors with low values are likely to become dual (i.e. basic for a column
   basis) and such with high values are likely to become primal (i.e. nonbasic
   for a column basis).
   
   However, if a variable having an upper and lower bound is to become primal,
   there is still a choice for setting it either to its upper or lower bound.
   Members #rowRight and #colUp are used to determine where to set a primal
   variable. If #rowRight[i] is set to a nonzero value, the right-hand side
   inequality is set tightly for the \p i 'th to become primal. Analogously, If
   #colUp[j] is nonzero, the \p j 'th variable will be set to its upper bound
   if it becomes primal.
*/
class SPxWeightST : public SPxStarter
{
   DataArray < int > forbidden;

   DataArray < Real > * weight;
   DataArray < Real > * coWeight;
   void setPrimalStatus(SPxBasis::Desc&, const SoPlex&, const SPxId&);

protected:
   /// weight value for LP rows.
   DataArray < Real > rowWeight;
   /// weight value for LP columns.
   DataArray < Real > colWeight;

   /// set variable to rhs?.
   DataArray < bool > rowRight;
   /// set primal variable to upper bound.
   DataArray < bool > colUp;

   /// sets up variable weights.
   /** This method is called in order to setup the weights for all
       variables. It has been declared #virtual in order to allow for
       derived classes to compute other weight values.
   */
   virtual void setupWeights(SoPlex& base);

public:
   /// generates start basis for loaded basis.
   void generate(SoPlex& base);

   /// consistency check.
   bool isConsistent() const;

   /// default constructor.
   SPxWeightST()
   {}
};

} // namespace soplex
#endif // _SPXWEIGHTST_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
