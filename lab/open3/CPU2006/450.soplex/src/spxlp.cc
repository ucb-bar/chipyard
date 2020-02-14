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
#pragma ident "@(#) $Id: spxlp.cpp,v 1.17 2002/03/03 13:50:34 bzfkocht Exp $"
#endif

#include <stdio.h>

#include "spxdefines.h"
#include "spxlp.h"
#include "message.h"

namespace soplex
{
void SPxLP::getRow(int i, LPRow& row) const
{
   METHOD( "SPxLP::getRow()" );
   row.setLhs(lhs(i));
   row.setRhs(rhs(i));
   row.setRowVector(DSVector(rowVector(i)));
}

void SPxLP::getRows(int start, int end, LPRowSet& p_set) const
{
   METHOD( "SPxLP::getRows()" );
   p_set.clear();
   for(int i = start; i <= end; i++)
      p_set.add(lhs(i), rowVector(i), rhs(i));
}

void SPxLP::getCol(int i, LPCol& col) const
{
   METHOD( "SPxLP::getCol()" );
   col.setUpper(upper(i));
   col.setLower(lower(i));
   col.setObj(spxSense() * obj(i));
   col.setColVector(colVector(i));
}

void SPxLP::getCols(int start, int end, LPColSet& p_set) const
{
   METHOD( "SPxLP::getCols()" );
   p_set.clear();
   for(int i = start; i <= end; i++)
      p_set.add(obj(i), lower(i), colVector(i), upper(i));
}

void SPxLP::getObj(Vector& p_obj) const
{
   METHOD( "SPxLP::getObj()" );
   p_obj = LPColSet::obj();
   if (spxSense() == MINIMIZE)
      p_obj *= -1;
}

void SPxLP::doAddRow(const LPRow& row)
{
   METHOD( "SPxLP::doAddRow()" );
   int idx = nRows();
   int oldColNumber = nCols();
   const SVector& vec = row.rowVector();

   LPRowSet::add(row);

   // now insert nonzeros to column file also
   for (int j = vec.size() - 1; j >= 0; --j)
   {
      Real val = vec.value(j);
      int i = vec.index(j);
      if (i >= nCols())      // create new columns if required
      {
         LPCol empty;
         for (int k = nCols(); k <= i; ++k)
            LPColSet::add(empty);
         // doAddCol(empty);
      }

      assert(i < nCols());
      LPColSet::add2(i, 1, &idx, &val);
   }

   addedRows(1);
   addedCols( nCols() - oldColNumber );
}

void SPxLP::doAddCol(const LPCol& col)
{
   METHOD( "SPxLP::doAddCol()" );
   int idx = nCols();
   int oldRowNumber = nRows();
   const SVector& vec = col.colVector();

   LPColSet::add(col);
   LPColSet::obj(idx) *= thesense;

   // now insert nonzeros to row file also
   for (int j = vec.size() - 1; j >= 0; --j)
   {
      Real val = vec.value(j);
      int i = vec.index(j);
      if (i >= nRows())              // create new rows if required
      {
         LPRow empty;
         for (int k = nRows(); k <= i; ++k)
            LPRowSet::add(empty);
         // doAddRow(empty);
      }

      assert(i < nRows());
      LPRowSet::add2(i, 1, &idx, &val);
   }

   addedCols(1);
   addedRows( nRows() - oldRowNumber );
}

void SPxLP::added2Set(SVSet& p_set, const SVSet& p_add, int n)
{
   METHOD( "SPxLP::added2Set()" );
   if( n == 0 )
      return;

   int i, j, end, tot;
   DataArray < int > moreArray(p_set.num());
   int* more = moreArray.get_ptr();

   for (i = p_set.num() - 1; i >= 0; --i)
      more[i] = 0;

   for (tot = 0, i = p_add.num() - n, end = p_add.num(); i < end; ++i)
   {
      const SVector& vec = p_add[i];
      tot += vec.size();
      for (j = vec.size() - 1; j >= 0; --j)
         more[ vec.index(j) ]++;
   }

   if (p_set.memMax() < tot)
      p_set.memRemax(tot);

   for (i = p_set.num() - 1; i >= 0; --i)
   {
      j = p_set[i].size();
      p_set.xtend(p_set[i], j + more[i]);
      p_set[i].set_size( j + more[i] );
      more[i] = j;
   }

   for (i = p_add.num() - n; i < p_add.num(); ++i)
   {
      const SVector& vec = p_add[i];
      for (j = vec.size() - 1; j >= 0; --j)
      {
         int k = vec.index(j);
         int m = more[k]++;
         SVector& l_xtend = p_set[k];
         l_xtend.index(m) = i;
         l_xtend.value(m) = vec.value(j);
      }
   }
}

void SPxLP::doAddRows(const LPRowSet& p_set)
{
   METHOD( "SPxLP::doAddRows()" );
   int i, j, k, ii, idx;
   SVector* col;
   DataArray < int > newCols(nCols());
   int oldRowNumber = nRows();
   int oldColNumber = nCols();

   if (&p_set != this)
      LPRowSet::add(p_set);
   assert(LPRowSet::isConsistent());
   assert(LPColSet::isConsistent());

   // count additional nonzeros per column
   for (i = nCols() - 1; i >= 0; --i)
      newCols[i] = 0;
   for (i = p_set.num() - 1; i >= 0; --i)
   {
      const SVector& vec = p_set.rowVector(i);
      for (j = vec.size() - 1; j >= 0; --j)
      {
         ii = vec.index(j);
         if (ii >= nCols()) // create new columns if required
         {
            LPCol empty;
            newCols.reSize(ii + 1);
            for (k = nCols(); k <= ii; ++k)
            {
               newCols[k] = 0;
               // doAddCol(empty);
               LPColSet::add(empty);
            }
         }
         assert(ii < nCols());
         newCols[ii]++;
      }
   }

   // extend columns as required
   for( i = 0; i < nCols(); ++i )
   {
      if (newCols[i] > 0)
      {
         int len = newCols[i] + colVector(i).size();
         LPColSet::xtend(i, len);
         colVector_w(i).set_size( len );
      }
   }

   // insert new elements to column file
   for (i = nRows() - 1; i >= oldRowNumber; --i)
   {
      const SVector& vec = rowVector(i);
      for (j = vec.size() - 1; j >= 0; --j)
      {
         k = vec.index(j);
         col = &colVector_w(k);
         idx = col->size() - newCols[k];
         assert(newCols[k] > 0);
         newCols[k]--;
         col->index(idx) = i;
         col->value(idx) = vec.value(j);
      }
   }
#ifndef NDEBUG
   for( i = 0; i < nCols(); ++i )
      assert( newCols[i] == 0 );
#endif

   assert( p_set.num() == nRows() - oldRowNumber );
   addedRows( nRows() - oldRowNumber );
   addedCols( nCols() - oldColNumber );
}

void SPxLP::doAddCols(const LPColSet& p_set)
{
   METHOD( "SPxLP::doAddCols()" );
   int i, j;
   int oldColNumber = nCols();
   int oldRowNumber = nRows();
   DataArray < int > newRows(nRows());

   if (&p_set != this)
      LPColSet::add(p_set);
   assert(LPColSet::isConsistent());
   assert(LPRowSet::isConsistent());

   // count additional nonzeros per row
   for (i = nRows() - 1; i >= 0; --i)
      newRows[i] = 0;
   for (i = p_set.num() - 1; i >= 0; --i)
   {
      const SVector& vec = p_set.colVector(i);
      for (j = vec.size() - 1; j >= 0; --j)
      {
         int l = vec.index(j);
         if (l >= nRows())  // create new rows if required
         {
            LPRow empty;
            newRows.reSize(l + 1);
            for (int k = nRows(); k <= l; ++k)
            {
               newRows[k] = 0;
               // doAddRow(empty);
               LPRowSet::add(empty);
            }

         }
         assert(l < nRows());
         newRows[l]++;
      }
   }

   // extend rows as required
   for( i = 0; i < nRows(); ++i )
   {
      if (newRows[i] > 0)
      {
         int len = newRows[i] + rowVector(i).size();
         LPRowSet::xtend(i, len);
         rowVector_w(i).set_size( len );
      }
   }

   // insert new elements to row file
   for (i = oldColNumber; i < nCols(); ++i)
   {
      LPColSet::obj(i) *= thesense;
      const SVector& vec = colVector(i);
      for (j = vec.size() - 1; j >= 0; --j)
      {
         int k = vec.index(j);
         SVector& row = rowVector_w(k);
         int idx = row.size() - newRows[k];
         assert(newRows[k] > 0);
         newRows[k]--;
         row.index(idx) = i;
         row.value(idx) = vec.value(j);
      }
   }
#ifndef NDEBUG
   for( i = 0; i < nRows(); ++i )
      assert( newRows[i] == 0 );
#endif
   assert(SPxLP::isConsistent());

   assert( p_set.num() == nCols() - oldColNumber );
   addedCols( nCols() - oldColNumber );
   addedRows( nRows() - oldRowNumber );
}

void SPxLP::addRows(SPxRowId id[], const LPRowSet& p_set)
{
   METHOD( "SPxLP::isConsistent()" );
   int i = nRows();
   addRows(p_set);
   for (int j = 0; i < nRows(); ++i, ++j)
      id[j] = rId(i);
}

void SPxLP::addCols(SPxColId id[], const LPColSet& p_set)
{
   METHOD( "SPxLP::addCols()" );
   int i = nCols();
   addCols(p_set);
   for (int j = 0; i < nCols(); ++i, ++j)
      id[j] = cId(i);
}

void SPxLP::doRemoveRow(int j)
{
   METHOD( "SPxLP::doRemoveRow()" );
   const SVector& vec = rowVector(j);
   int i;

   // remove row vector from column file
   for (i = vec.size() - 1; i >= 0; --i)
   {
      SVector& remvec = colVector_w(vec.index(i));
      remvec.remove(remvec.number(j));
   }

   int idx = nRows() - 1;
   if (j != idx)              // move last row to removed position
   {
      const SVector& l_vec = rowVector(idx);
      for (i = l_vec.size() - 1; i >= 0; --i)
      {
         SVector& movevec = colVector_w(l_vec.index(i));
         movevec.index(movevec.number(idx)) = j;
      }
   }

   LPRowSet::remove(j);
}

void SPxLP::doRemoveCol(int j)
{
   METHOD( "SPxLP::doRemoveCol()" );
   const SVector& vec = colVector(j);
   int i;

   // remove column vector from row file
   for (i = vec.size() - 1; i >= 0; --i)
   {
      SVector& remvec = rowVector_w(vec.index(i));
      remvec.remove(remvec.number(j));
   }

   int idx = nCols() - 1;
   if (j != idx)              // move last column to removed position
   {
      const SVector& l_vec = colVector(idx);
      for (i = l_vec.size() - 1; i >= 0; --i)
      {
         SVector& movevec = rowVector_w(l_vec.index(i));
         movevec.index(movevec.number(idx)) = j;
      }
   }

   LPColSet::remove(j);
}

void SPxLP::doRemoveRows(int perm[])
{
   METHOD( "SPxLP::doRemoveRows()" );
   int j = nCols();

   LPRowSet::remove(perm);
   for (int i = 0; i < j; ++i)
   {
      SVector& vec = colVector_w(i);
      for (int k = vec.size() - 1; k >= 0; --k)
      {
         int idx = vec.index(k);
         if (perm[idx] < 0)
            vec.remove(k);
         else
            vec.index(k) = perm[idx];
      }
   }
}

void SPxLP::doRemoveCols(int perm[])
{
   METHOD( "SPxLP::doRemoveCols()" );
   int j = nRows();

   LPColSet::remove(perm);
   for (int i = 0; i < j; ++i)
   {
      SVector& vec = rowVector_w(i);
      for (int k = vec.size() - 1; k >= 0; --k)
      {
         int idx = vec.index(k);
         if (perm[idx] < 0)
            vec.remove(k);
         else
            vec.index(k) = perm[idx];
      }
   }
}

void SPxLP::removeRows(SPxRowId id[], int n, int perm[])
{
   METHOD( "SPxLP::removeRows()" );
   if (perm == 0)
   {
      DataArray < int > p(nRows());
      removeRows(id, n, p.get_ptr());
      return;
   }
   for (int i = nRows() - 1; i >= 0; --i)
      perm[i] = i;
   while (n--)
      perm[number(id[n])] = -1;
   removeRows(perm);
}

void SPxLP::removeRows(int nums[], int n, int perm[])
{
   METHOD( "SPxLP::removeRows()" );
   if (perm == 0)
   {
      DataArray < int > p(nRows());
      removeRows(nums, n, p.get_ptr());
      return;
   }
   for (int i = nRows() - 1; i >= 0; --i)
      perm[i] = i;
   while (n--)
      perm[nums[n]] = -1;
   removeRows(perm);
}

void SPxLP::removeRowRange(int start, int end, int perm[])
{
   METHOD( "SPxLP::removeRowRange()" );
   if (perm == 0)
   {
      int i = end - start + 1;
      DataArray < int > p(i);
      while (--i >= 0)
         p[i] = start + i;
      removeRows(p.get_ptr(), end - start + 1);
      return;
   }
   int i;
   for (i = 0; i < start; ++i)
      perm[i] = i;
   for (; i <= end; ++i)
      perm[i] = -1;
   for (; i < nRows(); ++i)
      perm[i] = i;
   removeRows(perm);
}

void SPxLP::removeCols(SPxColId id[], int n, int perm[])
{
   METHOD( "SPxLP::removeCols()" );
   if (perm == 0)
   {
      DataArray < int > p(nCols());
      removeCols(id, n, p.get_ptr());
      return;
   }
   for (int i = nCols() - 1; i >= 0; --i)
      perm[i] = i;
   while (n--)
      perm[number(id[n])] = -1;
   removeCols(perm);
}

void SPxLP::removeCols(int nums[], int n, int perm[])
{
   METHOD( "SPxLP::removeCols()" );
   if (perm == 0)
   {
      DataArray < int > p(nCols());
      removeCols(nums, n, p.get_ptr());
      return;
   }
   for (int i = nCols() - 1; i >= 0; --i)
      perm[i] = i;
   while (n--)
      perm[nums[n]] = -1;
   removeCols(perm);
}

void SPxLP::removeColRange(int start, int end, int perm[])
{
   METHOD( "SPxLP::removeColRange()" );
   if (perm == 0)
   {
      int i = end - start + 1;
      DataArray < int > p(i);
      while (--i >= 0)
         p[i] = start + i;
      removeCols(p.get_ptr(), end - start + 1);
      return;
   }
   int i;
   for (i = 0; i < start; ++i)
      perm[i] = i;
   for (; i <= end; ++i)
      perm[i] = -1;
   for (; i < nCols(); ++i)
      perm[i] = i;
   removeCols(perm);
}

void SPxLP::clear()
{
   METHOD( "SPxLP::clear()" );
   LPRowSet::clear();
   LPColSet::clear();
   thesense = MAXIMIZE;
}

void SPxLP::changeObj(const Vector& newObj)
{
   METHOD( "SPxLP::changeObj()" );
   assert(maxObj().dim() == newObj.dim());
   LPColSet::obj() = newObj;
   LPColSet::obj() *= spxSense();
   assert(isConsistent());
}

void SPxLP::changeObj(int i, Real newVal)
{
   METHOD( "SPxLP::changeObj()" );
   LPColSet::obj(i) = spxSense() * newVal;
   assert(isConsistent());
}

void SPxLP::changeLower(const Vector& newLower)
{
   METHOD( "SPxLP::changeLower()" );
   assert(lower().dim() == newLower.dim());
   LPColSet::lower() = newLower;
   assert(isConsistent());
}

void SPxLP::changeLower(int i, Real newLower)
{
   METHOD( "SPxLP::changeLower()" );
   LPColSet::lower(i) = newLower;
   assert(isConsistent());
}

void SPxLP::changeUpper(const Vector& newUpper)
{
   METHOD( "SPxLP::changeUpper()" );
   assert(upper().dim() == newUpper.dim());
   LPColSet::upper() = newUpper;
   assert(isConsistent());
}

void SPxLP::changeUpper(int i, Real newUpper)
{
   METHOD( "SPxLP::changeUpper()" );
   LPColSet::upper(i) = newUpper;
   assert(isConsistent());
}

void SPxLP::changeLhs(const Vector& newLhs)
{
   METHOD( "SPxLP::changeLhs()" );
   assert(lhs().dim() == newLhs.dim());
   LPRowSet::lhs() = newLhs;
   assert(isConsistent());
}

void SPxLP::changeBounds(const Vector& newLower, const Vector& newUpper)
{
   METHOD( "SPxLP::changeBounds()" );
   changeLower(newLower);
   changeUpper(newUpper);
   assert(isConsistent());
}

void SPxLP::changeBounds(int i, Real newLower, Real newUpper)
{
   METHOD( "SPxLP::changeBounds()" );
   changeLower(i, newLower);
   changeUpper(i, newUpper);
   assert(isConsistent());
}

void SPxLP::changeLhs(int i, Real newLhs)
{
   METHOD( "SPxLP::changeLhs()" );
   LPRowSet::lhs(i) = newLhs;
   assert(isConsistent());
}

void SPxLP::changeRhs(const Vector& newRhs)
{
   METHOD( "SPxLP::changeRhs()" );
   assert(rhs().dim() == newRhs.dim());
   LPRowSet::rhs() = newRhs;
   assert(isConsistent());
}

void SPxLP::changeRhs(int i, Real newRhs)
{
   METHOD( "SPxLP::changeRhs()" );
   LPRowSet::rhs(i) = newRhs;
   assert(isConsistent());
}

void SPxLP::changeRange(const Vector& newLhs, const Vector& newRhs)
{
   METHOD( "SPxLP::changeRange()" );
   changeLhs(newLhs);
   changeRhs(newRhs);
   assert(isConsistent());
}

void SPxLP::changeRange(int i, Real newLhs, Real newRhs)
{
   METHOD( "SPxLP::changeRange()" );
   changeLhs(i, newLhs);
   changeRhs(i, newRhs);
   assert(isConsistent());
}


void SPxLP::changeRow(int n, const LPRow& newRow)
{
   METHOD( "SPxLP::changeRow()" );
   int j;
   SVector& row = rowVector_w(n);
   for (j = row.size() - 1; j >= 0; --j)
   {
      SVector& col = colVector_w(row.index(j));
      col.remove(col.number(n));
   }
   row.clear();

   changeLhs(n, newRow.lhs());
   changeRhs(n, newRow.rhs());
   const SVector& newrow = newRow.rowVector();
   for (j = newrow.size() - 1; j >= 0; --j)
   {
      int idx = newrow.index(j);
      Real val = newrow.value(j);
      LPRowSet::add2(n, 1, &idx, &val);
      LPColSet::add2(idx, 1, &n, &val);
   }
   assert(isConsistent());
}

void SPxLP::changeCol(int n, const LPCol& newCol)
{
   METHOD( "SPxLP::changeCol()" );
   int j;
   SVector& col = colVector_w(n);
   for (j = col.size() - 1; j >= 0; --j)
   {
      SVector& row = rowVector_w(col.index(j));
      row.remove(row.number(n));
   }
   col.clear();

   changeUpper(n, newCol.upper());
   changeLower(n, newCol.lower());
   changeObj (n, newCol.obj());
   const SVector& newcol = newCol.colVector();
   for (j = newcol.size() - 1; j >= 0; --j)
   {
      int idx = newcol.index(j);
      Real val = newcol.value(j);
      LPColSet::add2(n, 1, &idx, &val);
      LPRowSet::add2(idx, 1, &n, &val);
   }
   assert(isConsistent());
}

void SPxLP::changeElement(int i, int j, Real val)
{
   METHOD( "SPxLP::changeElement()" );
   SVector& row = rowVector_w(i);
   SVector& col = colVector_w(j);

   if (isNotZero(val))
   {
      if (row.number(j) >= 0)
      {
         row.value(row.number(j)) = val;
         col.value(col.number(i)) = val;
      }
      else
      {
         LPRowSet::add2(i, 1, &j, &val);
         LPColSet::add2(j, 1, &i, &val);
      }
   }
   else if (row.number(j) >= 0)
   {
      row.remove(row.number(j));
      col.remove(col.number(i));
   }
   assert(isConsistent());
}

bool SPxLP::isConsistent() const
{
   METHOD( "SPxLP::isConsistent()" );
   int i, j, n;

   for (i = nCols() - 1; i >= 0; --i)
   {
      const SVector& v = colVector(i);
      for (j = v.size() - 1; j >= 0; --j)
      {
         const SVector& w = rowVector(v.index(j));
         n = w.number(i);
         if (n < 0)
            return MSGinconsistent("SPxLP");
         if (v.value(j) != w.value(n))
            return MSGinconsistent("SPxLP");
      }
   }

   for (i = nRows() - 1; i >= 0; --i)
   {
      const SVector& v = rowVector(i);
      for (j = v.size() - 1; j >= 0; --j)
      {
         const SVector& w = colVector(v.index(j));
         n = w.number(i);
         if (n < 0)
            return MSGinconsistent("SPxLP");
         if (v.value(j) != w.value(n))
            return MSGinconsistent("SPxLP");
      }
   }
   return LPRowSet::isConsistent() && LPColSet::isConsistent();
}
} // namespace soplex

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
