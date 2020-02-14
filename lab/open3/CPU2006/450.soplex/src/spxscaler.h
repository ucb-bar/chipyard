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
#pragma ident "@(#) $Id: spxscaler.h,v 1.2 2002/04/04 19:36:51 bzfkocht Exp $"
#endif

/**@file  spxscaler.h
 * @brief LP scaling base class.
 */
#ifndef _SPXSCALER_H_
#define _SPXSCALER_H_

#include <assert.h>

#include "spxdefines.h"
#include "dataarray.h"
#include "spxlp.h"

namespace soplex
{
/**@brief   LP scaler abstract base class.
   @ingroup Algo

   Instances of classes derived from #SPxScaler may be loaded to #SoPlex in
   order to scale LPs before solving them. #SoPlex# will #load()# itself to
   the #SPxScaler and then call #scale(). Generally any #SPxLP can be
   loaded to a #SPxScaler for #scale()%ing it. The scaling can
   be undone by calling #unscale().
*/
class SPxScaler
{
protected:
   const char*        m_name;      ///< Name of the scaler
   SPxLP*             m_lp;        ///< LP to work on.
   DataArray < Real > m_colscale;  ///< column scaleing factors
   DataArray < Real > m_rowscale;  ///< row scaleing factors
   bool               m_colFirst;  ///< do column (not row) scaleing last 
   bool               m_doBoth;    ///< do columns and rows

public:
   friend std::ostream& operator<<(std::ostream& s, const SPxScaler& sc);

   /// constructor
   explicit SPxScaler(const char* name, 
      bool colFirst = true, bool doBoth = true);
   /// destructor.
   virtual ~SPxScaler();
   /// get name of scaler.
   virtual const char* getName() const;
   /// set scaling order.
   virtual void setOrder(bool colFirst); 
   /// set wether column and row scaling should be performed.
   virtual void setBoth(bool both); 
   /// Load the #SPxLP to be simplified.
   virtual void setLP(SPxLP* lp);
   /// Scale loaded #SPxLP. 
   virtual void scale() = 0;
   /// Unscale the loaded #SPxLP.
   virtual void unscale();
   /// Unscale dense solution vector given in \p usol. 
   virtual void unscaleSolution(Vector& usol) const;
   /// Get unscaled maximization objective in \p uobj. 
   virtual void unscaledMaxObj(Vector& uobj) const;
   /// Get unscaled lower bounds vector in \p ulower. 
   virtual void unscaledLower(Vector& ulower) const;
   /// Get unscaled upper bounds vector in \p uupper. 
   virtual void unscaledUpper(Vector& uupper) const;
   /// Get unscaled LHS vector in \p ulhs. 
   virtual void unscaledLhs(Vector& ulhs) const;
   /// Get unscaled RHS vector in \p urhs. 
   virtual void unscaledRhs(Vector& urhs) const;
   /// Get unscaled row vector number \p row in \p uvec. 
   virtual void unscaledRowVector(int row, DSVector& uvec) const;
   /// Get unscaled col vector number \p col in \p uvec. 
   virtual void unscaledColVector(int col, DSVector& uvec) const;
   /// consistency check
   virtual bool isConsistent() const;
   
private:
   /// assignment operator is not implemented.
   SPxScaler& operator=(const SPxScaler& base);

   /// copy constructor is not implemented.
   SPxScaler(const SPxScaler& base);
};
} // namespace soplex
#endif // _SPXSIMPLIFIER_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------

