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
#pragma ident "@(#) $Id: idxset.h,v 1.7 2002/01/31 08:19:26 bzfkocht Exp $"
#endif

/**@file  idxset.h
 * @brief Set of indices.
 */
#ifndef _IDXSET_H_
#define _IDXSET_H_

#include <assert.h>

namespace soplex
{
/**@brief   Set of indices.
   @ingroup Elementary

   Class #IdxSet provides a set of indices. At construction it must be given
   an array of #int where to store the indice and its length. The array will
   from then on be managed by the #IdxSet.
   
   Indices are implicitely numbered from 0 thru #size()-1. They can be
   accessed (and altered) via method #index() with the desired index number as
   argument.  Range checking is performed in the debug version.
   
   Indices may be added or removed from the set, by calling #add() or
   #remove() methods, respectively. However, no #IdxSet can hold more then
   #max() indices, i.e. the number given at the constructor.
   
   When removing indices, the remaining ones are renumbered. However, all
   indices before the first removed index keep their number unchanged.

   The internal structure of an #IdxSet consists of an array #idx storing the
   indices, its length #len, and the actually used number of indices #num.
   The class #IdxSet doesn't allocate memory for the #idx array. Instead, the
   user has to provide an adequate buffer to the constructor.

   An #IdxSet cannot be extended to fit more than #max() elements. If
   neccessary, the user must explicitely provide the #IdxSet with a
   suitable memory. Alternatively, one can use #DIdxSet%s which provide
   the required memory managemant.
*/
class IdxSet
{
protected:
   int  num;           ///< number of used indices
   int  len;           ///< length of array #idx
   int* idx;           ///< array of indices

public:
   /// access \p n 'th index.
   int index(int n) const
   {
      assert(n >= 0 && n < size());
      return idx[n];
   }
   /// returns the number of used indices.
   int size() const
   {
      return num;
   }
   /// returns the maximal number of indices which can be stored in #IdxSet.
   int max() const
   {
      return len;
   }

   /// returns the maximal index.
   int dim() const;

   /// returns the position number of index \p i.
   /** Returns the number of the first index \p i. If no index \p i is
       available in the #IdxSet, -1 is returned. Otherwise,
       #index(number(i)) == \p i holds.
    */
   int number(int i) const;

   /// appends \p n uninitialized indices.
   void add(int n)
   {
      assert(n >= 0 && n + size() <= max());
      num += n;
   }

   /// appends all indices of \p set.
   void add(const IdxSet& set)
   {
      add(set.size(), set.idx);
   }

   /// appends \p n indices in \p i.
   void add(int n, const int i[]);

   /// appends index \p i.
   void addIdx(int i)
   {
      assert(size() < max());
      idx[num++] = i;
   }
   /// removes indices at position numbers \p n through \p m.
   void remove(int n, int m);

   /// removes \p n 'th index.
   void remove(int n)
   {
      /**@todo Shouldn't this be an assert instead of an if (see add()) */
      if (n < size() && n >= 0)
         idx[n] = idx[--num];
   }

   /// removes all indices.
   void clear()
   {
      num = 0;
   }

   /// constructor.
   /** The constructur receives the index memory \p imem to use for saving
       its indices. This must be large enough to fit \p n indices. \p l can
       be given to construct an #IdxSet initialized to the \p l first
       indices in \p imem.
    */
   IdxSet(int n, int imem[], int l = 0)
      : num(l), len(n), idx(imem)
   {
      assert(isConsistent());
   }

   /// default constructor.
   /** The default constructor creates an index set with an empty index
       space. You cannot store any indices in an #IdxSet created with
       the default constructor.
   */
   IdxSet()
      : num(0), len(0), idx(0)
   {
      assert(isConsistent());
   }

   /// assignment operator.
   /** The assignment operator copies all nonzeros of the right handside
       #IdxSet to the left one. This implies, that the latter must have
       enough index memory.
    */
   IdxSet& operator=(const IdxSet& set);

   /// consistency check.
   bool isConsistent() const;

private:
   /// no copy constructor.
   IdxSet(const IdxSet&);
};

} // namespace soplex
#endif // _IDXSET_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
