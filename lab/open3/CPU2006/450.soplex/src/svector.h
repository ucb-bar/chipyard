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
#pragma ident "@(#) $Id: svector.h,v 1.21 2002/03/03 13:50:35 bzfkocht Exp $"
#endif

/**@file  svector.h
 * @brief Sparse vectors.
 */
#ifndef _SVECTOR_H_ 
#define _SVECTOR_H_

#include <iostream>
#include <assert.h>
#include <math.h>

#include "spxdefines.h"
#include "vector.h"

namespace soplex
{
/**@brief   Sparse vectors.
   @ingroup Algebra

   Class SVector provides packed sparse vectors. Such are a sparse vectors,
   with a storage scheme that keeps all data in one contiguous block of memory.
   This is best suited for using them for parallel computing on a distributed
   memory multiprocessor.
 
   SVector does not provide any memory management (this will be done by class
   DSVector). This means, that the constructor of SVector expects memory
   where to save the nonzeros. Further, adding nonzeros to an SVector may fail
   if no more memory is available for saving them (see also DSVector).
 
   When nonzeros are added to an SVector, they are appended to the set of
   nonzeros, i.e. they recieve numbers size(), size()+1 ... . An SVector
   can hold atmost max() nonzeros, where max() is given in the constructor.
   When removing nonzeros, the remaining nonzeros are renumbered. However, 
   only the numbers greater than the number of the first removed nonzero are
   affected.
 
   The following mathematical operations are provided by class SVector
   (SVector \p a, \p b, \p c; Real \p x): 

   <TABLE>
   <TR><TD>Operation</TD><TD>Description   </TD><TD></TD>&nbsp;</TR>
   <TR><TD>\c -=    </TD><TD>subtraction   </TD><TD>\c a \c -= \c b </TD></TR>
   <TR><TD>\c +=    </TD><TD>addition      </TD><TD>\c a \c += \c b </TD></TR>
   <TR><TD>\c *     </TD><TD>skalar product</TD>
       <TD>\c x = \c a \c * \c b </TD></TR>
   <TR><TD>\c *=    </TD><TD>scaling       </TD><TD>\c a \c *= \c x </TD></TR>
   <TR><TD>maxAbs() </TD><TD>infinity norm </TD>
       <TD>\c a.maxAbs() == \f$\|a\|_{\infty}\f$ </TD></TR>
   <TR><TD>length() </TD><TD>eucledian norm</TD>
       <TD>\c a.length() == \f$\sqrt{a^2}\f$ </TD></TR>
   <TR><TD>length2()</TD><TD>square norm   </TD>
       <TD>\c a.length2() == \f$a^2\f$ </TD></TR>
   </TABLE>
  
   Operators \c += and \c -= should be used with caution, since no efficient
   implementation is available. One should think of assigning the left handside
   vector to a dense Vector first and perform the addition on it. The same
   applies to the scalar product \c *.
 
   There are two numberings of the nonzeros of an SVector. First, an SVector
   is supposed to act like a linear algebra Vector. An \em index reffers to
   this view of an SVector: operator[]() is provided which return the value of
   the vector to the given index, i.e. 0 for all indeces not in the set of
   nonzeros.  The other view of SVector%s is that of a set of nonzeros. The
   nonzeros are numbered from 0 to size()-1. 
   Methods index(int n) and value(int n)
   allow to access the index and value of the \p n 'th nonzero. 
   \p n is referred to as the \em number of a nonzero.

   @todo SVector should get a new implementation.
         The trick to shift the storage by one element and then 
         store the actual and maximum size of the vector
         in m_elem[-1] is ugly. 
         Also there maybe a lot of memory lost due to padding the Element
         structure. A better idea seems to be 
         class SVector { int size; int used; int* idx; Real* val; };
         which for several reason could be faster or slower.
         If SVector is changed, also DSVector and SVSet have to be modified.
*/
class SVector
{
   friend class Vector;
   friend class SSVector;
   friend std::ostream& operator<<(std::ostream& os, const SVector& v);

public:
   /// Sparse vector nonzero element.
   /** SVector keep their nonzeros in an array of Element%s providing
    *  members for saving the nonzero's index and value.
    */
   struct Element
   {
      Real val;     ///< Value of nonzero element
      int  idx;     ///< Index of nonzero element
   };

private:
   /** An SVector keeps its data in an array of Element%s. The size and 
    *  maximum number of elements allowed is stored in the -1st Element 
    *  in its members #idx and #val respectively.
    */
   Element *m_elem;   ///< Array of Element%s.

public:
   /**@name Modification */
   //@{

   /// append one nonzero \p (i,v).
   void add(int i, Real v)
   {
      assert( m_elem != 0 );
      int n = size();
      m_elem[n].idx = i;
      m_elem[n].val = v;
      set_size( n + 1 );
      assert(size() <= max());
   }

   /// append nonzeros of \p sv.
   void add(const SVector& sv)
   {
      add(sv.size(), sv.m_elem);
   }

   /// append \p n nonzeros.
   void add(int n, const int i[], const Real v[]);

   /// append \p n nonzeros.
   void add(int n, const Element e[]);

   /// remove nonzeros \p n thru \p m.
   void remove(int n, int m);

   /// remove \p n 'th nonzero.
   void remove(int n)
   {
      assert(n < size() && n >= 0);
      set_size( size() - 1 );
      m_elem[n] = m_elem[size()];
   }
   /// remove all indices.
   void clear()
   {
      set_size(0);
   }
   /// sort nonzeros to increasing indices.
   void sort();
   //@}


   /**@name Inquiery */
   //@{
   /// number of used indeces.
   int size() const
   {
      if( m_elem != 0 )
         return m_elem[ -1].idx;
      else
         return 0;
   }

   /// maximal number indeces.
   int max() const
   {
      if( m_elem != 0 )
         return int(m_elem[ -1].val);
      else
         return 0;
   }

   /// maximal index.
   int dim() const;

   /// Number of index \p i.
   /** @return The number of the first index \p i. If no index \p i 
    *          is available in the IdxSet, -1 is returned. Otherwise, 
    *          index(number(i)) == i holds.
    */
   int number(int i) const
   {
      if( m_elem != 0 )
      {
         int n = size();
         Element* e = &(m_elem[n]);
         while (n--)
         {
            --e;
            if (e->idx == i)
               return n;
         }
      }
      return -1;
   }

   /// get value to index \p i.
   Real operator[](int i) const
   {
      int n = number(i);
      if (n >= 0)
         return m_elem[n].val;
      return 0;
   }

   /// get reference to the \p n 'th nonzero element.
   Element& element(int n)
   {
      assert(n >= 0 && n < max());
      return m_elem[n];
   }

   /// get \p n 'th nonzero element.
   const Element& element(int n) const
   {
      assert(n >= 0 && n < size());
      return m_elem[n];
   }

   /// get reference to index of \p n 'th nonzero.
   int& index(int n)
   {
      assert(n >= 0 && n < size());
      return m_elem[n].idx;
   }

   /// get index of \p n 'th nonzero.
   int index(int n) const
   {
      assert(n >= 0 && n < size());
      return m_elem[n].idx;
   }

   /// get reference to value of \p n 'th nonzero.
   Real& value(int n)
   {
      assert(n >= 0 && n < size());
      return m_elem[n].val;
   }

   /// get value of \p n 'th nonzero.
   Real value(int n) const
   {
      assert(n >= 0 && n < size());
      return m_elem[n].val;
   }
   //@}


   /**@name Mathematical Operations */
   //@{
   /// infinity norm.
   Real maxAbs() const;

   /// the absolut smalest element in the vector.
   Real minAbs() const;

   /// eucledian norm.
   Real length() const
   {
      return sqrt(length2());
   }

   /// squared eucledian norm.
   Real length2() const;

   /// scale with \p x.
   SVector& operator*=(Real x);

   /// inner product.
   Real operator*(const Vector& w) const
   {
      Real x = 0;
      int n = size();
      Element* e = m_elem;

      while (n--)
      {
         x += e->val * w[e->idx];
         e++;
      }
      return x;
   }
   //@}

   /**@name Miscellaneous*/
   //@{
   /// assignment operator from semi sparse vector.
   SVector& operator=(const SSVector& sv);
   /// assignment operator.
   SVector& operator=(const SVector& sv);
   /// assignment operator from vector.
   SVector& operator=(const Vector& sv);

   /// consistency check.
   bool isConsistent() const;

   /// default constructor.
   /** The constructor expects one memory block where to store the nonzero
    *  elements. This must passed to the constructor, where the \em number
    *  of Element%s needs that fit into the memory must be given and a
    *  pointer to the beginning of the memory block. Once this memory has
    *  been passed, it shall not be modified until the SVector is no
    *  longer used. Note, that when a memory block for \p n, say, Element%s
    *  has been passed, only \p n-1 are available for actually storing
    *  nonzeros. The remaining one is used for bookkeeping purposes.
    */
   SVector(int n = 0, Element* p_mem = 0)
   {
      setMem(n, p_mem);
   }
   /// get pointer to internal memory.
   Element* mem() const
   {
      return m_elem - 1;
   }
   /// set the size of the Vector,
   void set_size(int s)
   {
      assert(m_elem != 0);
      m_elem[ -1].idx = s;
   }
   /// set the maximum number of nonzeros in the Vector.   
   void set_max(int m)
   {
      assert(m_elem != 0);
      m_elem[ -1].val = m;
   }
   /// set the memory area where the nonzeros will be stored.
   void setMem(int n, Element* elmem)
   {
      assert(n >= 0);

      if (n > 0)
      {
         assert(elmem != 0);
         elmem->val = 0;        // for purify to shut up
         m_elem = &(elmem[1]);
         set_size( 0 );
         set_max ( n - 1 );
      }
      else
         m_elem = 0;
   }
   //@}
};

/// multiply Vector with \p and add a SVector. 
/** This is located in svector.h because it should be inlined and 
 *  the cross dependencys of Vector and SVector.
 * @todo Can we move this function to a better place?
 */
inline Vector& Vector::multAdd(Real x, const SVector& vec)
{
   assert(vec.dim() <= dim());

   for(int i = 0; i < vec.size(); i++)
      val[vec.m_elem[i].idx] += x * vec.m_elem[i].val;

   return *this;
}

} // namespace soplex
#endif  // _SVECTOR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
