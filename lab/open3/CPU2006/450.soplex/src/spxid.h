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
#pragma ident "@(#) $Id: spxid.h,v 1.1 2002/03/21 16:06:19 bzfkocht Exp $"
#endif

/**@file  spxid.h
 * @brief Row and columns Id's #SPxLP.
 */
#ifndef _SPXID_H_
#define _SPXID_H_

#include <assert.h>

#include "datakey.h"

namespace soplex
{
class SPxId;

/**@brief   Ids for LP columns.
 * @ingroup Algo
 *
 *  Class #SPxColId provides #DataSet::Key%s for the column 
 *  indices of an #SPxLP.
 */
class SPxColId : public DataKey
{
public:
   /// default constructor.
   SPxColId() 
   {}
   /// copy constructor from #DataKey.
   explicit SPxColId(const DataKey& p_key);
   /// copy constructor from #SPxId.
   explicit SPxColId(const SPxId& p_key);
};

/**@brief   Ids for LP rows.
 * @ingroup Algo
 *
 *  Class #SPxRowId provides #DataSet::Key%s for the row 
 *  indices of an #SPxLP.
 */
class SPxRowId : public DataKey
{
public:
   /// default constructor.
   SPxRowId() 
   {}
   /// copy constructor from #DataKey.
   explicit SPxRowId(const DataKey& p_key);
   /// copy constructor from #SPxId.
   explicit SPxRowId(const SPxId& p_key);
};

/**@brief   Generic Ids for LP rows or columns.
 * @ingroup Algo
 *
 *  Both, #SPxColId%s and #SPxRowId%s may be treated uniformly as
 *  #SPxId%s:
 *  
 *  Rows and columns are numbered from 0 to #num()-1 and 0 to #dim()-1
 *  respectively.  These numbers may be used to select individual rows or
 *  columns. However, these numbers may change if other rows or columns are
 *  added or removed.
 *  
 *  Further, each row or column of the problem matrix is assigned a
 *  #SPxRowId or #SPxColId, respectively. They are be used to select
 *  individual rows or columns just like numbers. In contrast to row and 
 *  column numbers, ids remain unchanged for the time a row or column 
 *  belongs to a #SPxLP, no matter what other rows or columns are added 
 *  to it or removed from it.
 */
class SPxId : public DataKey
{
public:
   /// type of the id.
   enum Type
   {
      ROW_ID  = -1,   ///< row identifier.
      INVALID = 0,    ///< invalid id.
      COL_ID  = 1     ///< column identifier.
   };

   /// returns the type of the id.
   Type type() const
   {
      return info ? (info < 0 ? ROW_ID : COL_ID) : INVALID;
   }
   /// returns TRUE iff the id is a valid column or row identifier.
   bool isValid() const
   {
      return info != 0;
   }
   /// makes the id invalid.
   void inValidate()
   {
      info = 0;
   }
   /// is id a row id?
   bool isSPxRowId() const
   {
      return info < 0;
   }
   /// is id a column id?
   bool isSPxColId() const
   {
      return info > 0;
   }
   ///
   SPxId& operator=(const SPxId& id)
   {
      if (this != &id)
         DataKey::operator= ( id );
      return *this;
   }
   ///
   SPxId& operator=(const SPxColId& cid)
   {
      DataKey::operator= ( cid );
      info = COL_ID;
      return *this;
   }
   /// assignment operator.
   SPxId& operator=(const SPxRowId& rid)
   {
      DataKey::operator= ( rid );
      info = ROW_ID;
      return *this;
   }
   /// default constructor. Constructs an invalid id.
   SPxId()
      : DataKey(INVALID, -1)
   {} 
   /// constructs an id out of a column identifier \p cid.
   explicit SPxId(const SPxColId& cid)
      : DataKey(COL_ID, cid.idx)
   {}
   /// constructs an id out of a row identifier \p rid.
   explicit SPxId(const SPxRowId& rid)
      : DataKey(ROW_ID, rid.idx) 
   {}
   /// equality operator.
   int operator==(const SPxId& id)
   {
      return (this == &id);
   }
   /// inequality operator.
   int operator!=(const SPxId& id)
   {
      return (this != &id);
   }
   /// less then operator
   bool operator<(const SPxId& id)
   {
      return getIdx() < id.getIdx();
   }
};

} // namespace soplex
#endif // _SPXID_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------














