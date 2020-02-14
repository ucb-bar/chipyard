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
#pragma ident "@(#) $Id: spxpricer.h,v 1.11 2002/04/04 14:59:04 bzfkocht Exp $"
#endif


/**@file  spxpricer.h
 * @brief Abstract pricer base class.
 */
#ifndef _SPXPRICE_H_
#define _SPXPRICE_H_

#include <assert.h>

#include "spxdefines.h"
#include "soplex.h"


namespace soplex
{

/**@todo document the member variables of derived classes of SPxPricer */

/**@brief   Abstract pricer base class.
   @ingroup Algo

   Class #SPxPricer is a pure virtual class defining the interface for pricer
   classes to be used by #SoPlex. The pricers task is to select a vector to
   enter or leave the simplex basis, depending on the chosen simplex type.
   
   An #SPxPricer is first #load%ed the #SoPlex object for which pricing is to
   be performed for. Then depending of the #SoPlex::Type, methods
   #selectEnter() and #entered4() (for #ENTER%ing Simplex) or #selectLeave()
   and #left4() (for #LEAVE%ing Simplex) are called by #SoPlex. The #SPxPricer
   object is informed of a change of the #SoPlex::Type by calling method
   #setType.
*/
class SPxPricer
{
protected:
   const char* m_name;
   SoPlex*     thesolver;
   Real        theeps;

public:
   /**@name Initialization */
   //@{
   /// get name of pricer.
   virtual const char* getName() const
   {
      return m_name;
   }

   /// loads LP.
   /** Loads the solver and LP for which pricing steps are to be performed.
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

   /// returns loaded #SoPlex object.
   virtual SoPlex* solver() const
   {
      return thesolver;
   }

   /// returns violation bound #epsilon.
   virtual Real epsilon() const
   {
      return theeps;
   }

   /// sets violation bound.
   /** Inequality violations are accepted, if their size is less than \p eps.
    */
   virtual void setEpsilon(Real eps)
   {
      assert(eps >= 0.0);

      theeps = eps;
   }

   /// sets pricing type.
   /** Informs pricer about (a change of) the loaded #SoPlex's #Type. In
       the sequel, only the corresponding select methods may be called.
    */
   virtual void setType(SoPlex::Type)
   {}

   /// sets basis representation.
   /** Informs pricer about (a change of) the loaded #SoPlex's
       #Representation.
   */
   virtual void setRep(SoPlex::Representation)
   {}
   //@}

   /**@name Pivoting */
   //@{
   /// returns selected index to leave basis.
   /** Selects the index of a vector to leave the basis. The selected index
       i, say, must be in the range 0 <= i < #solver()->dim() and its
       tested value must fullfill #solver()->test()[i] < -#epsilon().
    */
   virtual int selectLeave() = 0;

   /// performs leaving pivot.
   /** Method #left4() is called after each simplex iteration in #LEAVE
       mode. It informs the #SPxPricer that the \p n 'th variable has left
       the basis for \p id to come in at this position. When beeing called,
       all vectors of #SoPlex involved in such an entering update are
       setup correctly and may be accessed via the corresponding methods
       (i.e. #fVec(), #pVec(), etc.). In general, argument \p n will be
       the one returned by the #SPxPricer at the previous call to
       #selectLeave(). However, one can not rely on this.
    */
   virtual void left4(int /*n*/, SPxId /*id*/)
   {}

   /// selects Id to enter basis.
   /** Selects the #SPxId of a vector to enter the basis. The selected
       id, must not represent a basic index (i.e. #solver()->isBasic(id) must
       be false). However, the corresponding test value needs not to be less
       than #-epsilon(). If not, #SoPlex will discard the pivot.

       Note:
       When method #selectEnter() is called by the loaded #SoPlex
       object, all values from #coTest() are up to date. However, whether
       the elements of #test() are so depends on the #SoPlex::Pricing
       type.
    */
   virtual SPxId selectEnter() = 0;

   /// performs entering pivot.
   /** Method #entered4() is called after each simplex iteration in #ENTER
       mode. It informs the #SPxPricer that variable \p id has entered
       at the \p n 'th position. When beeing called, all vectors of #SoPlex
       involved in such an entering update are setup correctly and may be
       accessed via the corresponding methods (i.e. #fVec(), #pVec(),
       etc.). In general, argument \p id will be the one returned by the
       #SPxPricer at the previous call to #selectEnter(). However, one
       can not rely on this.
    */
   virtual void entered4(SPxId /*id*/, int /*n*/)
   {}
   //@}


   /**@name Extension */
   //@{
   /// \p n vectors have been added to loaded LP.
   virtual void addedVecs (int /*n*/)
   {}
   /// \p n covectors have been added to loaded LP.
   virtual void addedCoVecs(int /*n*/)
   {}
   //@}

   /**@name Shrinking */
   //@{
   /// vector \p i was removed from loaded LP.
   virtual void removedVec(int /*i*/)
   {}
   /// vectors given by \p perm have been removed from loaded LP.
   virtual void removedVecs(const int* /*perm*/)
   {}
   /// covector \p i was removed from loaded LP.
   virtual void removedCoVec(int /*i*/)
   {}
   /// covectors given by \p perm have been removed from loaded LP.
   virtual void removedCoVecs(const int* /*perm*/)
   {}
   //@}

   virtual bool isConsistent() const 
   {
      return thesolver != 0;
   }

   /**@name Constructors / Destructors */
   //@{
   /// constructor
   explicit SPxPricer(const char* p_name)
      : m_name(p_name)
      , thesolver(0)
      , theeps(0.0)
   {}

   /// destructor.
   virtual ~SPxPricer()
   {
      m_name    = 0;
      thesolver = 0;
   }
   //@}

};


} // namespace soplex
#endif // _SPXPRICER_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
