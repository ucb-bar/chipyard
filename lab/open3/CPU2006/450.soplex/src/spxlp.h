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
#pragma ident "@(#) $Id: spxlp.h,v 1.28 2002/04/04 14:59:04 bzfkocht Exp $"
#endif

/**@file  spxlp.h
 * @brief Saving LPs in a form suitable for SoPlex.
 */
#ifndef _SPXLP_H_
#define _SPXLP_H_

#include <assert.h>
#include <iostream>
#include <iomanip>

#include "spxdefines.h"
#include "datakey.h"
#include "spxid.h"
#include "dvector.h"
#include "svset.h"
#include "dataarray.h"
#include "lprow.h"
#include "lpcol.h"
#include "lprowset.h"
#include "lpcolset.h"
#include "nameset.h"

namespace soplex
{
class SoPlex;

/**@brief   Saving LPs in a form suitable for SoPlex.
   @ingroup Algo

   Class #SPxLP provides the data structures required for saving a 
   linear program in the form
   \f[
   \begin{array}{rl}
       \hbox{max}  & c^T x              \\
       \hbox{s.t.} & l_r \le Ax \le u_r \\
                   & l_c \le x \le u_c
   \end{array}
   \f]
   suitable for solving with #SoPlex. This includes:
   - SVSets for both, columns and rows
   - objective Vector
   - upper and lower bound Vectors for variables (\f$l_c\f$ and \f$u_c\f$)
   - upper and lower bound Vectors for inequalities (\f$l_r\f$ and \f$u_r\f$)
 
   Note, that the optimization sense is not saved directly. Instead, the
   objective function are multiplied by -1 to transform the LP to our standard
   form maximizing the objective function. However, the sense of the loaded LP
   can be retreived with method #spxSense().
 
   Further, equality constraints are modelled by \f$l_r = u_r\f$. 
   Analogously, fixed variables have \f$l_c = u_c\f$.
*/
class SPxLP : protected LPRowSet, protected LPColSet
{  
   friend class SPxBasis;
   friend class SPxScaler;
   friend class SPxEquili;
   friend int getmarsz (SoPlex*);
   friend int getmartz (SoPlex*);

   /// output operator.
   friend std::ostream& operator<<(std::ostream& os, const SPxLP& lp);

   /*
      \SubSection{Data structures and layout}
      #SPxLP%s are saved as an #SVSet for both, 
      the columns and rows. Note that this
      is redundant but eases the access.
   */
public:
   /// optimization sense.
   enum SPxSense
   {
      MAXIMIZE = 1,
      MINIMIZE = -1
   };
private:
   SPxSense thesense;   ///< optimization sense.

public:

   /**@name Inquiry */
   //@{
   /// returns number of rows in LP.
   int nRows() const
   {
      return LPRowSet::num();
   }

   /// returns number of columns in LP.
   int nCols() const
   {
      return LPColSet::num();
   }

   /// gets \p i 'th row.
   void getRow(int i, LPRow& row) const;

   /// gets row with identifier \p id.
   void getRow(SPxRowId id, LPRow& row) const
   {
      getRow(number(id), row);
   }

   /// gets rows \p start, ... \p end.
   void getRows(int start, int end, LPRowSet& set) const;

   /// gets row vector of row \p i.
   const SVector& rowVector(int i) const
   {
      return LPRowSet::rowVector(i);
   }

   /// gets row vector of row with identifier \p id.
   const SVector& rowVector(SPxRowId& id) const
   {
      return LPRowSet::rowVector(id);
   }

   /// returns right hand side vector.
   const Vector& rhs() const
   {
      return LPRowSet::rhs();
   }

   ///
   Real rhs(int i) const
   {
      return LPRowSet::rhs(i);
   }
   /// returns right hand side of row \p i.
   Real& rhs(int i)
   {
      return LPRowSet::rhs(i);
   }

   /// returns right hand side of row with identifier \p id.
   Real rhs(SPxRowId& id) const
   {
      return LPRowSet::rhs(id);
   }

   /// returns left hand side vector.
   const Vector& lhs() const
   {
      return LPRowSet::lhs();
   }

   ///
   Real lhs(int i) const
   {
      return LPRowSet::lhs(i);
   }
   /// returns left hand side of row \p i.
   Real& lhs(int i)
   {
      return LPRowSet::lhs(i);
   }

   /// returns left hand side of row with identifier \p id.
   Real lhs(SPxRowId& id) const
   {
      return LPRowSet::lhs(id);
   }

   /// returns the inequality type of the \p i'th LPRow.
   LPRow::Type rowType(int i) const
   {
      return LPRowSet::type(i);
   }

   /// returns the inequality type of the row with identifier \p key.
   LPRow::Type rowType(const SPxRowId& id) const
   {
      return LPRowSet::type(id);
   }

   /// gets \p i 'th column.
   void getCol(int i, LPCol& column) const;

   /// gets column with identifier \p id.
   void getCol(SPxColId id, LPCol& col) const
   {
      getCol(number(id), col);
   }

   /// gets columns \p start, ..., \p end.
   void getCols(int start, int end, LPColSet& set) const;

   /// returns column vector of column \p i.
   const SVector& colVector(int i) const
   {
      return LPColSet::colVector(i);
   }

   /// returns column vector of column with identifier \p id.
   const SVector& colVector(SPxColId& id) const
   {
      return LPColSet::colVector(id);
   }

   /// gets objective vector.
   void getObj(Vector& obj) const;

   /// returns objective value of column \p i.
   Real obj(int i) const
   {
      return spxSense() * maxObj(i);
   }

   /// returns objective value of column with identifier \p id.
   Real obj(SPxColId& id) const
   {
      return spxSense() * maxObj(id);
   }

   /// returns objective vector for maximization problem.
   /** Methods #maxObj() return the objective vector or its elements, after
       transformation to a maximization problem. Since this is how #SPxLP
       internally stores any LP these methods are generally faster. The
       following condition holds: #obj() = #spxSense() * maxObj().
   */
   const Vector& maxObj() const
   {
      return LPColSet::obj();
   }

   /// 
   Real maxObj(int i) const
   {
      return LPColSet::obj(i);
   }
   /// returns objective value of column \p i for maximization problem.
   Real& maxObj(int i)
   {
      return LPColSet::obj(i);
   }

   /// returns objective value of column with identifier \p id 
   /// for maximization problem.
   Real maxObj(SPxColId& id) const
   {
      return LPColSet::obj(id);
   }

   /// returns upper bound vector.
   const Vector& upper() const
   {
      return LPColSet::upper();
   }

   ///
   Real upper(int i) const
   {
      return LPColSet::upper(i);
   }
   /// returns upper bound of column \p i.
   Real& upper(int i)
   {
      return LPColSet::upper(i);
   }

   /// returns upper bound of column with identifier \p id.
   Real upper(SPxColId& id) const
   {
      return LPColSet::upper(id);
   }

   /// returns lower bound vector.
   const Vector& lower() const
   {
      return LPColSet::lower();
   }

   ///
   Real lower(int i) const
   {
      return LPColSet::lower(i);
   }
   /// returns lower bound of column \p i.
   Real& lower(int i)
   {
      return LPColSet::lower(i);
   }

   /// returns lower bound of column with identifier \p id.
   Real lower(SPxColId& id) const
   {
      return LPColSet::lower(id);
   }

   /// returns the optimization sense.
   SPxSense spxSense() const
   {
      return thesense;
   }

   /// returns the row number of the row with identifier \p id.
   int number(const SPxRowId& id) const
   {
      return LPRowSet::number(id);
   }

   /// returns the column number of the column with identifier \p id.
   int number(const SPxColId& id) const
   {
      return LPColSet::number(id);
   }

   /// returns the row or column number for identifier \p id.
   int number(const SPxId& id) const
   {
      return (id.type() == SPxId::COL_ID)
         ? LPColSet::number(id)
         : LPRowSet::number(id);
   }

   /// returns the row identifier for row \p n.
   SPxRowId rId(int n) const
   {
      return SPxRowId(LPRowSet::key(n));
   }

   /// returns the column identifier for column \p n.
   SPxColId cId(int n) const
   {
      return SPxColId(LPColSet::key(n));
   }
   //@}


   /**@name Extension */
   //@{
   ///
   virtual void addRow(const LPRow& row)
   {
      doAddRow(row);
   }
   /// adds \p row to #LPRowSet.
   virtual void addRow(SPxRowId& id, const LPRow& row)
   {
      addRow(row);
      id = rId(nRows() - 1);
   }

   ///
   virtual void addRows(const LPRowSet& pset)
   {
      doAddRows(pset);
   }
   /// adds all #LPRow%s of \p pset to #LPRowSet.
   virtual void addRows(SPxRowId id[], const LPRowSet& set);

   ///
   virtual void addCol(const LPCol& col)
   {
      doAddCol(col);
   }
   /// adds \p col to #LPColSet.
   virtual void addCol(SPxColId& id, const LPCol& col)
   {
      addCol(col);
      id = cId(nCols() - 1);
   }

   ///
   virtual void addCols(const LPColSet& pset)
   {
      doAddCols(pset);
   }
   /// adds all #LPCol%s of \p set to #LPColSet.
   virtual void addCols(SPxColId id[], const LPColSet& set);

   //@}


   /**@name Shrinking */
   //@{
   /// removes \p i 'th row.
   virtual void removeRow(int i)
   {
      doRemoveRow(i);
   }

   /// removes row with identifier \p id.
   virtual void removeRow(SPxRowId id)
   {
      removeRow(number(id));
   }

   /// removes multiple rows.
   /** This method removes all #LPRow%s from the #SPxLP with an
       index \p i such that \p perm[i] < 0. Upon completion, \p perm[i] >= 0
       indicates the new index where the \p i 'th #LPRow has been moved to
       due to this removal. Note, that \p perm must point to an array of at
       least #nRows() #int%s.
    */
   virtual void removeRows(int perm[])
   {
      doRemoveRows(perm);
   }

   ///
   virtual void removeRows(SPxRowId id[], int n, int perm[] = 0);
   /// removes \p n #LPRow%s.
   /** Removing multiple rows with one method invocation is available in
       two flavours. An array \p perm can be passed as third argument or
       not. If given, \p perm must be an array at least of size #nRows(). It
       is used to return the permutations resulting from this removal:
       \p perm[i] < 0 indicates, that the element to index \p i has been
       removed.  Otherwise, \p perm[i] is the new index of the element with
       index \p i before the removal.
    */
   virtual void removeRows(int nums[], int n, int perm[] = 0);

   /// removes rows from \p start to \p end (including both).
   virtual void removeRowRange(int start, int end, int perm[] = 0);

   /// removes \p i 'th column.
   virtual void removeCol(int i)
   {
      doRemoveCol(i);
   }

   /// removes column with identifier \p id.
   virtual void removeCol(SPxColId id)
   {
      removeCol(number(id));
   }

   /// removes multiple columns.
   /** This method removes all #LPCol%s from the #SPxLP with an
       index \p i such that \p perm[i] < 0. Upon completion, \p perm[i] >= 0
       indicates the new index where the \p i 'th #LPCol has been moved to
       due to this removal. Note, that \p perm must point to an array of at
       least #nCols() #int%s.
    */
   virtual void removeCols(int perm[])
   {
      doRemoveCols(perm);
   }

   ///
   virtual void removeCols(SPxColId id[], int n, int perm[] = 0);
   /// removes \p n #LPCol%s.
   /** Removing multiple columns with one method invocation is available in
       two flavours. An array \p perm can be passed as third argument or
       not. If given, \p perm must be an array at least of size #nCols(). It
       is used to return the permutations resulting from this removal:
       \p perm[i] < 0 indicates, that the element to index \p i has been
       removed.  Otherwise, \p perm[i] is the new index of the element with
       index \p i before the removal.
    */
   virtual void removeCols(int nums[], int n, int perm[] = 0);

   /// removes columns from \p start to \p end (including both).
   virtual void removeColRange(int start, int end, int perm[] = 0);

   /// clears the LP.
   virtual void clear();

   //@}


   /**@name IO */
   //@{
   /// reads a file from input stream \p in.
   virtual bool read (std::istream& in, 
      NameSet* rowNames = 0, NameSet* colNames = 0, DIdxSet* intVars = 0);

   /// reads a file in LP format from \p in.
   virtual bool readLPF (std::istream& in, 
      NameSet* rowNames = 0, NameSet* colNames = 0, DIdxSet* intVars = 0);

   /// reads a file in MPS format from \p in.
   virtual bool readMPS(std::istream& in, 
      NameSet* rowNames = 0, NameSet* colNames = 0, DIdxSet* intVars = 0);

   virtual void writeMPS(std::ostream& out, 
      const NameSet* rowNames, const NameSet* colNames, 
      const DIdxSet* p_intvars = 0) const;

   //@}


   /**@name Manipulation */
   //@{
   /// changes objective vector to \p newObj.
   virtual void changeObj(const Vector& newObj);

   /// changes \p i 'th objective vector element to \p newVal.
   virtual void changeObj(int i, Real newVal);

   /// change objective value of column with identifier \p id to \p newVal.
   virtual void changeObj(SPxColId id, Real newVal)
   {
      changeObj(number(id), newVal);
   }

   /// changes vector of lower bounds to \p newLower.
   virtual void changeLower(const Vector& newLower);

   /// changes \p i 'th lower bound to \p newLower.
   virtual void changeLower(int i, Real newLower);

   /// changes lower bound of column with identifier \p id to \p newLower.
   virtual void changeLower(SPxColId id, Real newLower)
   {
      changeLower(number(id), newLower);
   }

   /// changes vector of upper bounds to \p newUpper.
   virtual void changeUpper(const Vector& newUpper);

   /// changes \p i 'th upper bound to \p newUpper.
   virtual void changeUpper(int i, Real newUpper);

   /// changes upper bound of column with identifier \p id to \p newLower.
   virtual void changeUpper(SPxColId id, Real newUpper)
   {
      changeUpper(number(id), newUpper);
   }

   /// changes variable bounds to \p newLower and \p newUpper.
   virtual void changeBounds(const Vector& newLower, const Vector& newUpper);

   /// changes bounds of column \p i to \p newLower and \p newUpper.
   virtual void changeBounds(int i, Real newLower, Real newUpper);

   /// changes bounds of column with identifier \p id.
   virtual void changeBounds(SPxColId id, Real newLower, Real newUpper)
   {
      changeBounds(number(id), newLower, newUpper);
   }

   /// changes left hand side vector for constraints to \p newLhs.
   virtual void changeLhs(const Vector& newLhs);

   /// changes \p i 'th left hand side value to \p newLhs.
   virtual void changeLhs(int i, Real newLhs);

   /// changes left hand side value for row with identifier \p id.
   virtual void changeLhs(SPxRowId id, Real newLhs)
   {
      changeLhs(number(id), newLhs);
   }

   /// changes right hand side vector for constraints to \p newRhs.
   virtual void changeRhs(const Vector& newRhs);

   /// changes \p i 'th right hand side value to \p newRhs.
   virtual void changeRhs(int i, Real newRhs);

   /// changes right hand side value for row with identifier \p id.
   virtual void changeRhs(SPxRowId id, Real newRhs)
   {
      changeRhs(number(id), newRhs);
   }

   /// changes left and right hand side vectors.
   virtual void changeRange(const Vector& newLhs, const Vector& newRhs);

   /// changes left and right hand side of row \p i.
   virtual void changeRange(int i, Real newLhs, Real newRhs);

   /// changes left and right hand side of row with identifier \p id.
   virtual void changeRange(SPxRowId id, Real newLhs, Real newRhs)
   {
      changeRange(number(id), newLhs, newRhs);
   }

   /// replaces \p i 'th row of LP with \p newRow.
   virtual void changeRow(int i, const LPRow& newRow);

   /// replaces row with identifier \p id with \p newRow.
   virtual void changeRow(SPxRowId id, const LPRow& newRow)
   {
      changeRow(number(id), newRow);
   }

   /// replaces \p i 'th column of LP with \p newCol.
   virtual void changeCol(int i, const LPCol& newCol);

   /// replaces column with identifier \p id with \p newCol.
   virtual void changeCol(SPxColId id, const LPCol& newCol)
   {
      changeCol(number(id), newCol);
   }

   /// changes LP element (\p i, \p j) to \p val.
   virtual void changeElement(int i, int j, Real val);

   /// changes LP element identified by (\p rid, \p cid) to \p val.
   virtual void changeElement(SPxRowId rid, SPxColId cid, Real val)
   {
      changeElement(number(rid), number(cid), val);
   }

   /// changes optimization sense to \p sns.
   virtual void changeSense(SPxSense sns)
   {
      if (sns != thesense)
         LPColSet::obj() *= -1;
      thesense = sns;
   }
   //@}


   /**@name Miscellaneous */
   //@{
   /// consistency check.
   bool isConsistent() const;
   //@}

protected:
   /// returns the LP as a #LPRowSet.
   const LPRowSet* lprowset() const
   {
      return static_cast<const LPRowSet*>(this);
   }

   /// returns the LP as a #LPColSet.
   const LPColSet* lpcolset() const
   {
      return static_cast<const LPColSet*>(this);
   }

   /**todo What sense does it make to have private inheritance, if then
    *      we do something like this here?
    */
   SVSet* rowset()
   {
      return reinterpret_cast<SVSet*>(static_cast<LPRowSet*>(this));
   }
   SVSet* colset()
   {
      return reinterpret_cast<SVSet*>(static_cast<LPColSet*>(this));
   }

   //@Memo: These  methods are use for implemementing the public remove methods
   ///
   virtual void doRemoveRow(int i);
   ///
   virtual void doRemoveCols(int perm[]);
   ///
   virtual void doRemoveRows(int perm[]);
   ///
   virtual void doRemoveCol(int i);

   /// called after the last \p n rows have just been added.
   virtual void addedRows(int)
   {}
   /// called after the last \p n columns have just been added.
   virtual void addedCols(int)
   {}
   ///
   void added2Set(SVSet& set, const SVSet& add, int n);


private:
   SVector& colVector_w(int i)
   {
      return LPColSet::colVector_w(i);
   }
   SVector& rowVector_w(int i)
   {
      return LPRowSet::rowVector_w(i);
   }

   void doAddRow (const LPRow& row);
   void doAddRows(const LPRowSet& set);
   void doAddCol (const LPCol& col);
   void doAddCols(const LPColSet& set);

   /// This should return spxSense() * maxObj;
   /// returns objective vector.
   const Vector& obj() const;
#if 0
   {
      return LPColSet::obj();
   }
#endif

public:
   /**@name Constructors / Destructors */
   //@{
   /// default constructor.
   SPxLP()
   {
      SPxLP::clear(); // clear is virtual.
   }

   /// destructor.
   virtual ~SPxLP()
   {}

   /// assignment operator
   SPxLP& operator=(const SPxLP& old)
   {
      if (this != &old)
      {
         LPRowSet::operator=(old);
         LPColSet::operator=(old);
         thesense = old.thesense;
      }
      return *this;
   }

   //@}

};


} // namespace soplex
#endif  // _SPXLP_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
