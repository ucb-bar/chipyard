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
#pragma ident "@(#) $Id: idlist.h,v 1.16 2002/03/03 13:50:31 bzfkocht Exp $"
#endif

/**@file  idlist.h
 * @brief Generic Real linked list.
 */
#ifndef _IDLIST_H_
#define _IDLIST_H_

#include <assert.h>

#include "spxdefines.h"
#include "islist.h"

namespace soplex
{
/**@brief   Elements for #IdList%s.
   @ingroup Elementary

   #IdElement%s are derived from the template parameter class #T and can hence
   be used as such. The additional methods #next() and #prev() provid access
   to the links for the list. They may freely be used by the programmer as long
   as an #IdElement is not member of a #IdList. In this case, the #IdList
   controls members #next() and #prev(). However, #IdList should provide
   enough functionality for the user not to requirer any modification to these
   members.
 */
/* The use of this->the_last and this->the_first instead of just the_last
 * and the_first is bcause the HP aCC Compiler claims that according to the
 * Standard these otherwise could not be seen. An since I ws not able to 
 * even identify a hint on this in the Draft Standard we just do it, so
 * the HP compiler is happy since it will not hurt the others.
 */
template < class T >
class IdElement : public T
{
   IdElement<T>* theprev;   ///< pointer to previous element in the #IdList
   IdElement<T>* thenext;   ///< pointer to next element in the #IdList
public:
   ///
   IdElement<T>*& next()
   {
      return thenext;
   }
   /// returns the next element in the #IdList.
   IdElement<T>*const& next() const
   {
      return thenext;
   }

   ///
   IdElement<T>*& prev()
   {
      return theprev;
   }
   /// returns the previous element in the #IdList.
   IdElement<T>*const& prev() const
   {
      return theprev;
   }

   /// default constructor.
   IdElement()
      : theprev(0)
      , thenext(0)
   {}

   /// copy constructor.
   /** Only the element itself is copied, while the links to the previous and
       the next list element are set to zero pointers.
   */
   IdElement(const T& old)
      : T(old)
      , theprev(0)
      , thenext(0)
   {}
};


/**@brief   Generic Real linked list.
   @ingroup Elementary

   Class #IdList implements an intrusive Real linked list as a #template
   class.  As such, the list elements must provide the links themselfs. For
   conveniance, we also provide class #IdElement that adds both links to an
   arbitrary class as #template parameter.
 */
template < class T >
class IdList : public IsList<T>
{
public:

   /**@name Access */
   //@{
   /// returns first element in list.
   T* first() const
   {
      return static_cast<T*>(this->the_first);
   }

   /// returns last element in list.
   T* last() const
   {
      return static_cast<T*>(this->the_last);
   }

   /// returns successor of \p elem or 0, if \p elem is the last element.
   T* next(const T *elem) const
   {
      return (elem == last()) ? 0 : elem->next();
   }

   /// returns predecessor of \p elem or 0, if \p elem is the first element.
   T* prev(const T *elem) const
   {
      return (elem == first()) ? 0 : elem->prev();
   }
   //@}


   /**@name Extension */
   //@{
   /// appends \p elem to end of list.
   void append (T* elem)
   {
      if (last())
      {
         last()->next() = elem;
         elem->prev() = last();
      }
      else
         this->the_first = elem;
      this->the_last = elem;
   }

   /// prepends \p elem at beginnig of list.
   void prepend(T* elem)
   {
      if (first())
      {
         elem->next() = first();
         first()->prev() = elem;
      }
      else
         this->the_last = elem;
      this->the_first = elem;
   }

   /// inserts \p elem after \p after.
   void insert (T* elem, T* after)
   {
      assert(find(after));
      if (after == last())
         append(elem);
      else
      {
         elem->next() = after->next();
         elem->prev() = after;
         after->next() = elem->next()->prev() = elem;
      }
   }

   /// appends \p list to end of list.
   void append (IdList<T>& list)
   {
      if (list.first())
      {
         append(list.first());
         this->the_last = list.last();
      }
   }

   /// prepends \p list at beginnig of list.
   void prepend(IdList<T>& list)
   {
      if (list.first())
      {
         prepend(list.last());
         this->the_first = list.the_first;
      }
   }

   /// inserts \p list after \p after.
   void insert (IdList<T>& list, T* after)
   {
      assert(find(after));
      if (list.first())
      {
         list.last()->next() = after->next();
         list.first()->prev() = after;
         after->next() = list.first();
         if (after == last())
            this->the_last = list.last();
         else
            list.last()->next()->prev() = list.last();
      }
   }
   //@}

   /**@name Removal */
   //@{
   /// removes element following \p after.
   void remove_next(T* after)
   {
      remove(next(after));
   }

   /// removes \p elem from list.
   void remove(T* elem)
   {
      if (elem == first())
      {
         this->the_first = next(elem);
         if (first() == 0)
            this->the_last = 0;
      }
      else if (elem == last())
         this->the_last = elem->prev();
      else
      {
         elem->next()->prev() = elem->prev();
         elem->prev()->next() = elem->next();
      }
   }

   /// removes sublist \p list.
   void remove(IdList<T>& list)
   {
      if (first() != 0 && list.first() != 0)
      {
         assert(find(list.first()));
         assert(find(list.last()));
         if (first() == list.first())
         {
            if (last() == list.last())
               this->the_first = this->the_last = 0;
            else
               this->the_first = list.last()->next();
         }
         else if (last() == list.last())
            this->the_last = list.last()->prev();
         else
         {
            T* after = first();
            for (; after->next() != list.first(); after = after->next())
              ;
            if (last() == list.last())
               this->the_last = after;
            else
               after->next() = list.last()->next();
         }
      }
   }
   //@}


   /**@name Miscellaneous */
   //@{
   /// adjusts list pointers to a new memory address.
   /** When all elements have been moved in memory (e.g. because of
       reallocation) with a fixed offset \p delta, the list will be reset
       to the new adresses.
   */
   void move(ptrdiff_t delta)
   {
      if (this->the_first)
      {
         T* elem;
         IsList<T>::move(delta);
         for (elem = last(); elem; elem = prev(elem))
            if (elem != first())
               elem->prev() = reinterpret_cast<T*>(
                  reinterpret_cast<char*>(elem->prev()) + delta);
      }
   }

   /// consistency check.
   bool isConsistent() const
   {
      for (T * it = first(); it; it = next(it))
      {
         if (it != first() && it->prev()->next() != it)
            return MSGinconsistent("IdList");

         if (it != last() && it->next()->prev() != it)
            return MSGinconsistent("IdList");
      }
      return IsList<T>::isConsistent();
   }
   //@}

   /**@name Constructors / Destructors */
   //@{
   /// default constructor.
   /** The default constructor may also be used to construct a sublist, by
       providing a \p first and a \p last element. Element \p last must be a
       successor of \p first.
    */
   IdList(T* pfirst = 0, T* plast = 0)
      : IsList<T>(pfirst, plast)
   {}

   //@}
};

} // namespace soplex
#endif // _IDLIST_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
