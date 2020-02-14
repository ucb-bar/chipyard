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
#pragma ident "@(#) $Id: array.h,v 1.15 2002/01/31 08:19:25 bzfkocht Exp $"
#endif

/**@file  array.h
 * @brief Save arrays of arbitrary types.
 */
#ifndef _ARRAY_H_
#define _ARRAY_H_

#include <assert.h>

#include "message.h"

namespace soplex
{
/**@brief   Save arrays of arbitrary types.
   @ingroup Elementary

   Class Array provides safe arrays of arbitrary type. Array elements are
   accessed just like ordinary C++ array elements by means of the index
   operator[](). Safety is provided by

    - automatic memory management in constructor and destructure
      preventing memory leaks
    - checking of array bound when accessing elements with the
      indexing operator[]() (only when compiled without \c -DNDEBUG).
 
    Moreover, Array%s may easily be extended by #insert%ing or
    #append%ing elements to the Array or shrunken by #remove%ing
    elements. Method #reSize(int n) resets the Array%s length to \p n
    thereby appending elements or truncating the Array to the
    required size.
 
    An Array is implemented in a C++ complient way with respect to
    how memory is managemed: Only operators ::new and ::delete are
    used for allocating memory. This involves some overhead for all
    methods effecting the length of an Array, i.e. all methods
    #insert, #append, #remove and #reSize. This involves
    allocating a new C++ array of the new size and copying all
    elements with the template parameters operator=().
 
    For this reason, it is not convenient to use class Array, if its elements
    are \ref DataObjects "Data Objects". In this case use class DataArray 
    instead.
 
    @see DataArray, \ref DataObjects "Data Objects" 
*/
template < class T >
class Array
{
protected:
   int num;     ///< the length of array #data 
   T*  data;    ///< the array of elements

public:
   /// reference \p n 'th element.
   T& operator[](int n)
   {
      assert(n >= 0 && n < size());
      return data[n];
   }
   /// reference \p n 'th element.
   const T& operator[](int n) const
   {
      assert(n >= 0 && n < size());
      return data[n];
   }

   /// append \p n uninitialized elements.
   void append(int n)
   {
      insert(size(), n);
   }
   /// append \p n elements from \p p_array.
   void append(int n, const T* p_array)
   {
      insert(size(), n, p_array);
   }
   /// append all elements from \p p_array.
   void append(const Array<T>& p_array)
   {
      insert(size(), p_array);
   }

   /// insert \p n uninitialized elements before \p i 'th element.
   void insert(int i, int n)
   {
      assert(i <= size());
      if (n > 0)
      {
         int k;
         T *olddata = data;
         data = new T[size() + n];
         assert(data != 0);
         if (size() > 0)
         {
            for (k = 0; k < i; ++k)
               data[k] = olddata[k];
            for (; k < size(); ++k)
               data[k + n] = olddata[k];
            delete[] olddata;
         }
         num += n;
      }
   }

   /// insert \p n elements from \p p_array before \p i 'th element.
   void insert(int i, int n, const T* p_array)
   {
      insert(i, n);
      for (n--; n >= 0; --n)
         data[n + i] = p_array[n];
   }

   /// insert all elements from \p p_array before \p i 'th element.
   void insert(int i, const Array<T>& p_array)
   {
      int n = p_array.size();
      insert(i, n);
      for (n--; n >= 0; --n)
         data[n + i] = p_array.data[n];
   }

   /// remove \p m elements starting at \p n.
   void remove(int n = 0, int m = 1)
   {
      assert(n >= 0 && m >= 0);
      if (m > 0 && n < size())
      {
         T *olddata = data;
         m -= (n + m <= size()) ? 0 : n + m - size();
         num -= m;
         if (num > 0)
         {
            int i;
            data = new T[num];
            for (i = 0; i < n; ++i)
               data[i] = olddata[i];
            for (; i < num; ++i)
               data[i] = olddata[i + m];
         }
         delete[] olddata;
      }
   }

   /// remove all elements.
   void clear()
   {
      if (num > 0)
      {
         num = 0;
         delete[] data;
      }
   }

   /// return the number of elements.
   int size() const
   {
      return num;
   }

   /// reset the number of elements.
   void reSize(int newsize)
   {
      if (newsize < size())
         remove(newsize, size() - newsize);
      else if (newsize > size())
         append(newsize - size());
   }

   /// assignment operator.
   /** Assigning an rvalue Array to an lvalue Array involves resizing
    *  the lvalue to the rvalues size() and copying all elements via
    *  the Array element's assignment operator=().
    */
   Array<T>& operator=(const Array<T>& rhs)
   {
      if (this != &rhs)
      {
         reSize(rhs.size());
         for (int i = 0; i < size(); ++i)
            data[i] = rhs.data[i];
      }
      return *this;
   }

   /// default constructor.
   /** The constructor allocates an Array of \p n uninitialized elements.
    */
   Array(int n = 0) 
      : data(0)
   {
      assert(n >= 0);
      num = n;
      if (num > 0)
      {
         data = new T[num];
         assert(data != 0);
      }
   }

   /// copy constructor
   Array(const Array<T>& old) 
      : num(old.num)
   {
      if (num > 0)
      {
         data = new T[num];
         assert(data != 0);
         *this = old;
      }
   }

   /// destructor
   ~Array()
   {
      if (num > 0)
         delete[] data;
   }

   /// consistency check
   bool isConsistent() const
   {
      if (num < 0 || (num > 0 && data == 0))
         return MSGinconsistent("Array");

      return true;
   }
};
} // namespace soplex
#endif // _ARRAY_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
