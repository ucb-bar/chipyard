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
#pragma ident "@(#) $Id: dataarray.h,v 1.20 2002/03/03 13:50:31 bzfkocht Exp $"
#endif

/**@file  dataarray.h
 * @brief Save arrays of data objects.
 */
#ifndef _DATAARRAY_H_
#define _DATAARRAY_H_

#include <assert.h>
#include <stddef.h>
#include <string.h>
#include <iostream>

#include "spxdefines.h"
#include "spxalloc.h"
#include "message.h"

namespace soplex
{
/**@brief   Safe arrays of data objects.
   @ingroup Elementary

   Class DataArray provides safe arrays of \ref DataObjects. For general
   C++ objects (i.e. no data objects) class Array is provided which
   manages memory in a C++ compliant way.
 
   The elements of an instance of DataArray can be accessed just like
   ordinary C++ array elements by means of the index operator[](). Safety is
   provided by

    - automatic memory management in constructor and destructor
      preventing memory leaks
    - checking of array bounds when accessing elements with the
      indexing operator[]() (only when compiled without \c -DNDEBUG).
 
   Moreover, DataArray%s may easily be extended by #insert%ing or #append%ing
   elements to the DataArray or shrunken by #remove%ing elements. Method
   #reSize(int n) resets the DataArray%s length to \p n thereby possibly
   appending elements or truncating the DataArray to the required size.
 
   A DataArray%s may be used as arguments for standard C functions requiring
   pointers through the use of get_ptr() and get_const_ptr().
 
   Internally, a DataArray object allocates a block of memory that fits up
   to max() elements, only size() of them are used. This makes extension
   and shrinking methods perform better.

   @see Array, \ref DataObjects "Data Objects"
*/
template < class T >
class DataArray
{
private:
   int thesize;           ///< number of used elements in array #data
   int themax;            ///< the length of array #data and
   T*  data;              ///< the array of elements

protected:
   /** When a DataArray is reSize()%d to more than max() elements, the
       new value for max() is not just set to the new size but rather to
       \p memFactor * \p size. This makes #reSize%ing perform better in codes
       where a DataArray is extended often by a small number of elements
       only.
    */
   Real memFactor;     ///< memory extension factor.

public:

   /// reference \p n 'th element.
   T& operator[](int n)
   {
      assert(n >= 0);
      assert(n < thesize);
      return data[n];
   }
   /// reference \p n 'th const element.
   const T& operator[](int n) const
   {
      assert(n >= 0);
      assert(n < thesize);
      return data[n];
   }

   /// reference last element.
   T& last()
   {
      assert(thesize > 0);
      return data[thesize -1];
   }
   /// reference last const element.
   const T& last() const
   {
      assert(thesize > 0);
      return data[thesize -1];
   }

   /// get a C pointer to the data.
   T* get_ptr()
   {
      return data;
   }
   /// get a const C pointer to the data.
   const T* get_const_ptr() const
   {
      return data;
   }

   /// append element \p t.
   void append(const T& t)
   {
      insert(thesize, 1, &t);
   }
   /// append \p n elements from \p t.
   void append(int n, const T t[])
   {
      insert(thesize, n, t);
   }
   /// append all elements from \p t.
   void append(const DataArray<T>& t)
   {
      insert(thesize, t);
   }

   /// insert \p n uninitialized elements before \p i 'th element.
   void insert(int i, int n)
   {
      int j = size();
      reSize(thesize + n);
      while (i < j--)
         data[j + n] = data[j];
   }

   /// insert \p n elements from \p t before \p i 'the element.
   void insert(int i, int n, const T t[])
   {
      if (n > 0)
      {
         insert(i, n);
         memcpy(&(data[i]), t, n*sizeof(T));
      }
   }

   /// insert all elements from \p t before \p i 'th element.
   void insert(int i, const DataArray<T>& t)
   {
      if (t.size())
      {
         insert(i, t.size());
         memcpy(&(data[i]), t.data, t.size()*sizeof(T));
      }
   }

   /// remove \p m elements starting at \p n.
   void remove(int n = 0, int m = 1)
   {
      assert(n < size() && n >= 0);
      if (n + m < size())
         memcpy(&(data[n]), &(data[n + m]), (size() - (n + m)) * sizeof(T));
      else
         m = size() - n;
      thesize -= m;
   }
   /// remove \p m last elements.
   void removeLast(int m = 1)
   {
      assert(m <= size() && m >= 0);
      thesize -= m;
   }
   /// remove all elements.
   void clear()
   {
      thesize = 0;
   }

   /// return nr. of elements.
   int size() const
   {
      return thesize;
   }

   /// reset size to \p newsize.
   /** Resizing a DataArray to less than the previous size, involves
       discarding its last elements. Resizing to a larger value involves
       adding uninitialized elements (similar to #append). If neccessary,
       also memory will be reallocated.
       @param newsize the new number of elements the array can hold.
    */
   void reSize(int newsize)
   {
      assert(memFactor >= 1);
      if (newsize > themax)
         reMax(int(memFactor * newsize), newsize);
      else if (newsize < 0)
         thesize = 0;
      else
         thesize = newsize;
   }

   /// return maximum number of elements.
   /** Even though the DataArray currently holds no more than size()
       elements, up to max() elements could be added without need to
       reallocated free store.
    */
   int max() const
   {
      return themax;
   }

   /// reset maximum number of elements.
   /** The value of max() is reset to \p newMax thereby setting size()
       to \p newSize. However, if \p newSize has a value \c < \c 0 (as the
       default argument does) size() remains unchanged and max() is set
       to MIN(size(), newMax). Hence, calling reMax() without the
       default arguments, will reduce the memory consumption to a minimum.
       In no instance max() will be set to a value less than 1 (even if
       specified).

       @return reMax returns the difference in bytes of the new and the old
               memory block, which can be used to update pointers pointing 
               to elements of the memory block.
    */
   ptrdiff_t reMax(int newMax = 1, int newSize = -1)
   {
      if (newSize >= 0)
         thesize = newSize;
      if (newMax < newSize)
         newMax = newSize;
      if (newMax < 1)
         newMax = 1;
      if (newMax == themax)
         return 0;
      themax = newMax;
      T* olddata = data;
      if (thesize <= 0)
      {
         spx_free(data);
         spx_alloc(data, themax);
      }
      else
         spx_realloc(data, themax);

      return reinterpret_cast<char*>(data) - reinterpret_cast<char*>(olddata);
   }
   /// assignment operator
   DataArray& operator=(const DataArray& rhs)
   {
      if (this != &rhs)
      {
         reSize(rhs.size());
         memcpy(data, rhs.data, size() * sizeof(T));
      }
      return *this;
   }

   /// consistency check
   bool isConsistent() const
   {
      if (  (data == 0)
         || (themax < 1)
         || (themax < thesize)
         || (thesize < 0)
         || (memFactor < 1.0))
         return MSGinconsistent("DataArray");

      return true;
   }

   /// copy constructor
   DataArray(const DataArray& old)
      : thesize(old.thesize)
      , themax (old.themax)
      , data (0)
      , memFactor (old.memFactor)
   {
      spx_alloc(data, max());

      if (thesize)
         memcpy(data, old.data, thesize * sizeof(T));

      assert(isConsistent());
   }

   /// default constructor.
   /** The constructor allocates an Array containing \p size uninitialized
       elements. The internal array is allocated to have \p max nonzeros,
       and the memory extension factor is set to \p fac.

       @param p_size number of unitialised elements.
       @param p_max  maximum number of elements the array can hold.
       @param p_fax  value for memFactor.
    */
   explicit DataArray(int p_size = 0, int p_max = 0, Real p_fac = 1.2)
      : data (0)
      , memFactor(p_fac)
   {
      thesize = (p_size < 0) ? 0 : p_size;
      if (p_max > thesize)
         themax = p_max;
      else
         themax = (thesize == 0) ? 1 : thesize;

      spx_alloc(data, themax);

      assert(isConsistent());
   }

   /// destructor
   ~DataArray()
   {
      spx_free(data);
   }
};

} // namespace soplex
#endif

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
