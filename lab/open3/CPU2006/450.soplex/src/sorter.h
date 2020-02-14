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
#pragma ident "@(#) $Id: sorter.h,v 1.5 2002/01/31 08:19:27 bzfkocht Exp $"
#endif


/**@file  sorter.h
 * @brief Generic QuickSort implementation.
 */
#ifndef _SORTER_H_
#define _SORTER_H_

#include <assert.h>

namespace soplex
{
/// Generic QuickSort implementation.
/** This template function sorts an array \p t holding \p n elements of
    type #T using \p compare for comparisions. Class #COMPARATOR must provide
    an overloaded #operator()(const T& t1,const T& t2), that returns
    - < 0, if \p t1 is to appear before \p t2,
    - = 0, if \p t1 and \p t2 can appear in any order, or
    - > 0, if \p t1 is to appear after \p t2.
*/

template < class T, class COMPARATOR >
void sorter_qsort(T* t, int end, COMPARATOR& compare, int start = 0)
{
   int i0, i1, j;
   Real c;

   T work, mid, tmp;

   work = t[start];
   t[start] = t[(start + end) / 2];
   t[(start + end) / 2] = work;
   
   mid = t[start];
   work = t[end - 1];
   
   for (i0 = i1 = start, j = end - 1; i1 < j;)
   {
      c = compare(mid, work);
      if (c > 0)
      {
         tmp = t[i0];
         t[i0] = work;
         i0++;
         i1++;
         work = t[i1];
         t[i1] = tmp;
      }
      else if (c < 0)
      {
         t[j] = work;
         --j;
         work = t[j];
      }
      else
      {
         i1++;
         tmp = t[i1];
         t[i1] = work;
         work = tmp;
      }
   }
   
   if (start < i0 - 1)
      sorter_qsort(t, i0, compare, start);
   if (i1 + 1 < end)
      sorter_qsort(t, end, compare, i1 + 1);
}


} // namespace soplex
#endif // _SORTER_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
