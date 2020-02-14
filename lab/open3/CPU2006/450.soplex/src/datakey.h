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
#pragma ident "@(#) $Id: datakey.h,v 1.7 2002/03/21 16:06:17 bzfkocht Exp $"
#endif

/**@file  datakey.h
 * @brief Entry identifier class for items of a #DataSet.
 */
#ifndef _DATAKEY_H_
#define _DATAKEY_H_

#include <assert.h>

namespace soplex
{
/**@brief   Entry identifier class for items of a #DataSet.
   @ingroup Elementary

   Every item in a #DataSet is assigned a #Key by which it can be
   accessed (using #DataSet::operator[]()). A #Key consists of an integer
   member #idx, which is a positive number for any valid #Key. No
   #Key::idx of an element in a #DataSet may exceed the sets #max().
   This property may be used to build arrays with additional information to
   the elements of a #DataSet.

   In addition, #Key%s provides member #info, where the programmer is
   free to store other information.
   
   Each #Key is unique for one #DataSet but different #DataSet%s may (and
   generally will) manage the same #Key%s. When an element is removed from
   a #DataSet its #Key may (and generally will) be reused for other
   elements added to the #DataSet later on.

   @todo data members should be private.
*/
class DataKey
{
protected:
public:
   signed int info: 8;                        ///< user information (8 bit)
   signed int idx : (8 * sizeof(int) - 8);    ///< (locally) unique key index

public:
   /// gets the index number (#idx) of the #Key.
   inline int getIdx() const
   {
      return idx;
   }
   /// sets the index number (#idx) of the #Key.
   inline void setIdx(int p_idx) 
   {
      idx = p_idx;
   }   
   /// returns TRUE, iff the #Key is valid.
   inline int isValid() const
   {
      return idx >= 0;
   }
   /// makes the #Key invalid and clears the #info field.
   inline void inValidate()
   {
      idx  = -1;
      info = 0;
   }
   /// Default constructor. Constructs an invalid #Key.
   DataKey() 
      : info(0), idx(-1) 
   {}

   // Constructor
   DataKey(int p_info, int p_idx)
      : info(p_info)
      , idx(p_idx)
   {}

   /// Assignment operator.
   DataKey& operator=(const DataKey& rhs)
   {
      info = rhs.info;
      idx  = rhs.idx;
      
      return *this;
   }
   /// Copy constructor.
   DataKey(const DataKey& old) 
      : info(old.info) 
      , idx(old.idx)
   {}
};

} // namespace soplex
#endif // _DATAKEY_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------














