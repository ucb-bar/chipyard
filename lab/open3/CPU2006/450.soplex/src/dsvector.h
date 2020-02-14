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
#pragma ident "@(#) $Id: dsvector.h,v 1.9 2002/03/03 13:50:31 bzfkocht Exp $"
#endif

/**@file  dsvector.h
 * @brief Dynamic sparse vectors.
 */
#ifndef _DSVECTOR_H_
#define _DSVECTOR_H_

#include <assert.h>

#include "spxdefines.h"
#include "svector.h"
#include "ssvector.h"

namespace soplex
{
/**@brief   Dynamic sparse vectors.
   @ingroup Algebra

   Class DSVector implements dynamic sparse vectors, i.e. SVector%s
   with an automatic memory management. This allows the user to freely add()
   as many nonzeros to a DSVector as desired, without any precautions.
   For saving memory method setMax() allows to reduce memory consumption to
   the amount really required.

   @todo Both DSVector and SVector have a member variable that points to
         allocated memory. This seems not to make too much sense.
 */
class DSVector : public SVector
{
   friend class SLinSolver;

private:
   Element* theelem;       ///< here is where the memory is

   /// allocate memory for \p n nonzeros. 
   void allocMem(int n);

   /// make sure there is room for \p n new nonzeros.
   void makeMem(int n)
   {
      if (max() - size() < ++n)
         setMax(size() + n);
   }

public:
   /// assignment operator from semi sparse vector.
   DSVector& operator=(const SSVector& sv)
   {
      int n = sv.size();
      clear();
      makeMem(n);
      SVector::operator=(sv);
      return *this;
   }
   /// assignment operator from sparse vector.
   DSVector& operator=(const SVector& sv)
   {
      int n = sv.size();
      clear();
      makeMem(n);
      SVector::operator=(sv);
      return *this;
   }
   /// assignment operator.
   DSVector& operator=(const DSVector& sv)
   {
      if (this != &sv)
      {
         int n = sv.size();
         clear();
         makeMem(n);
         SVector::operator=(sv);
      }
      return *this;
   }
   /// assignment operator from vector.
   DSVector& operator=(const Vector& vec);

   /// append nonzeros of \p sv.
   void add(const SVector& sv)
   {
      int n = sv.size();
      clear();
      makeMem(n);
      SVector::add(sv);
   }

   /// append one nonzero \p (i,v).
   void add(int i, Real v)
   {
      makeMem(1);
      SVector::add(i, v);
   }

   /// append \p n nonzeros.
   void add(int n, const int i[], const Real v[])
   {
      makeMem(n);
      SVector::add(n, i, v);
   }

   /// reset nonzero memory to >= \p newmax.
   /** This methods resets the memory consumption of the DIdxSet to
    *  \p newmax. However, if \p newmax < size(), it is reset to size()
    *  only.
    */
   void setMax(int newmax = 1);

   /// copy constructor from vector.
   explicit DSVector(const Vector& vec);
   /// copy constructor from sparse vector.
   explicit DSVector(const SVector& old);
   /// copy constructor from semi sparse vector.
   explicit DSVector(const SSVector& old);

   /// copy constructor.
   DSVector(const DSVector& old);

   /// default constructor.
   /** Creates a DSVector ready to hold \p n nonzeros. However, the memory is
    *  automatically enlarged, if more nonzeros are added to the DSVector.
    */
   explicit DSVector(int n = 8);

   /// destructor.
   ~DSVector();

   /// consistency check.
   bool isConsistent() const;
};
} // namespace soplex
#endif // _DSVECTOR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------

