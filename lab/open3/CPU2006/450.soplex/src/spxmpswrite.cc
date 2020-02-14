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
#pragma ident "@(#) $Id: spxmpswrite.cpp,v 1.5 2002/03/11 17:43:57 bzfkocht Exp $"
#endif

/**@file  spxmpswrite.cpp
 * @brief Write LP as MPS format file.
 */
//#define DEBUGGING 1

#include <assert.h>
#include <stdio.h>
#include <iostream>

#include "spxdefines.h"
#include "spxlp.h"

namespace soplex
{
static void writeRecord(
   std::ostream&  os, 
   const char*    indicator,
   const char*    name,
   const char*    name1  = 0,
   const Real     value1 = 0.0,
   const char*    name2  = 0,
   const Real     value2 = 0.0) 
{
   char buf[81];

   sprintf(buf, " %-2.2s %-8.8s",
      (indicator == 0) ? "" : indicator,
      (name == 0)      ? "" : name);

   os << buf;

   if (name1 != 0)
   {
      sprintf(buf, "  %-8.8s  %12.9g", name1, value1);
      os << buf;

      if (name2 != 0)
      {
         sprintf(buf, "   %-8.8s  %12.9g", name2, value2);
         os << buf;
      }
   }
   os << std::endl;
}

static Real getRHS(Real left, Real right)
{
   Real rhsval;

   if (left > -infinity) /// This includes ranges
      rhsval = left;
   else if (right <  infinity)
      rhsval = right;
   else
      abort();

   return rhsval;
}

static const char* getRowName(
   const SPxLP&   lp,
   int            idx,
   const NameSet* rnames, 
   char*          buf)
{
   assert(buf != 0);
   assert(idx >= 0);
   assert(idx <  lp.nRows());

   if (rnames != 0) 
   {
      DataKey key = lp.rId(idx);

      if (rnames->has(key))
         return (*rnames)[key];
   }
   sprintf(buf, "C%d_", idx);
   
   return buf;
}
   
static const char* getColName(
   const SPxLP&   lp,
   int            idx,
   const NameSet* cnames, 
   char*          buf)
{
   assert(buf != 0);
   assert(idx >= 0);
   assert(idx <  lp.nCols());

   if (cnames != 0) 
   {
      DataKey key = lp.cId(idx);

      if (cnames->has(key))
         return (*cnames)[key];
   }
   sprintf(buf, "x%d_", idx);
   
   return buf;
}
   
/// Write LP in "MPS File Format".
/** @note There will always be a BOUNDS section, even if there are no bounds.
 */
void SPxLP::writeMPS(
   std::ostream&  p_output, 
   const NameSet* p_rnames,          ///< row names.
   const NameSet* p_cnames,          ///< column names.
   const DIdxSet* p_intvars)         ///< integer variables.
   const
{
   METHOD("writeMPS");

   const char*    indicator;
   char           name [16];
   char           name1[16];
   char           name2[16];
   bool           has_ranges = false;
   int            i;
   int            k;
   
   // --- NAME Section ---
   p_output << "NAME          MPSDATA" << std::endl;

   // --- ROWS Section ---
   p_output << "ROWS" << std::endl;

   for(i = 0; i < nRows(); i++)
   {
      if (lhs(i) == rhs(i))
         indicator = "E";
      else if ((lhs(i) > -infinity) && (rhs(i) < infinity))
      {
         indicator  = "E";
         has_ranges = true;
      }
      else if (lhs(i) > -infinity)
         indicator = "G";
      else if (rhs(i) <  infinity)
         indicator = "L";
      else
         abort();

      writeRecord(p_output, indicator, getRowName(*this, i, p_rnames, name)); 
   }
   writeRecord(p_output, "N", "MINIMIZE"); 
   
   // --- COLUMNS Section ---
   p_output << "COLUMNS" << std::endl;

   bool has_intvars = (p_intvars != 0) && (p_intvars->size() > 0);

   for(int j = 0; j < (has_intvars ? 2 : 1); j++)
   {
      bool is_intrun = has_intvars && (j == 1);

      if (is_intrun)
         p_output << "    MARK0001  'MARKER'                 'INTORG'" 
                  << std::endl;

      for(i = 0; i < nCols(); i++)
      {
         bool is_intvar = has_intvars && (p_intvars->number(i) >= 0);

         if (  ( is_intrun && !is_intvar)
            || (!is_intrun &&  is_intvar))
             continue;

         const SVector& col = colVector(i);
         int colsize2       = (col.size() / 2) * 2;

         assert(colsize2 % 2 == 0);

         for(k = 0; k < colsize2; k += 2)
            writeRecord(p_output, 0, 
               getColName(*this, i,                p_cnames, name),
               getRowName(*this, col.index(k),     p_rnames, name1),
               col.value(k),
               getRowName(*this, col.index(k + 1), p_rnames, name2),
               col.value(k + 1));

         if (colsize2 != col.size())
            writeRecord(p_output, 0,
               getColName(*this, i,            p_cnames, name),
               getRowName(*this, col.index(k), p_rnames, name1),
               col.value(k));

         if (isNotZero(maxObj(i)))
            writeRecord(p_output, 0, getColName(*this, i, p_cnames, name),
               "MINIMIZE", -maxObj(i));
      }
      if (is_intrun)
         p_output << "    MARK0001  'MARKER'                 'INTEND'"
                  << std::endl;
   }
   // --- RHS Section ---
   p_output << "RHS" << std::endl;

   i = 0;
   while(i < nRows())
   {
      Real rhsval1;
      Real rhsval2;

      for(; i < nRows(); i++)
         if ((rhsval1 = getRHS(lhs(i), rhs(i))) != 0.0)
            break;

      if (i < nRows())
      {
         for(k = i + 1; k < nRows(); k++)
            if ((rhsval2 = getRHS(lhs(k), rhs(k))) != 0.0)
               break;

         if (k < nRows())
            writeRecord(p_output, 0, "RHS", 
               getRowName(*this, i, p_rnames, name1),
               rhsval1, 
               getRowName(*this, k, p_rnames, name2),
               rhsval2);
         else
            writeRecord(p_output, 0, "RHS", 
               getRowName(*this, i, p_rnames, name1),
               rhsval1);

         i = k + 1;
      }
   }

   // --- RANGES Section ---
   if (has_ranges)
   {
      for(i = 0; i < nRows(); i++)
         if ((lhs(i) > -infinity) && (rhs(i) < infinity))
            writeRecord(p_output, "", "RANGE", 
               getRowName(*this, i, p_rnames, name1),
               rhs(i) - lhs(i));
   }
   // --- BOUNDS Section ---
   p_output << "BOUNDS" << std::endl;

   for(i = 0; i < nCols(); i++)
   {
      if (lower(i) == upper(i))
      {
         writeRecord(p_output, "FX", "BOUND", 
            getColName(*this, i, p_cnames, name1),
            lower(i));

         continue;
      }
      if ((lower(i) <= -infinity) && (upper(i) >= infinity))
      {
         writeRecord(p_output, "FR", "BOUND", 
            getColName(*this, i, p_cnames, name1));
         continue;
      }
      if (lower(i) != 0.0)
      {
         if (lower(i) > -infinity)
            writeRecord(p_output, "LO", "BOUND", 
               getColName(*this, i, p_cnames, name1),
               lower(i));
         else
            writeRecord(p_output, "MI", "BOUND", 
               getColName(*this, i, p_cnames, name1));
      }

      if (has_intvars && (p_intvars->number(i) >= 0))
      {
         // Integer variables have default upper bound 1.0
         if (upper(i) != 1.0)
            writeRecord(p_output, "UP", "BOUND", 
               getColName(*this, i, p_cnames, name1),
               upper(i));
      }
      else
      {
         // Continous variables have default upper bound infinity
         if (upper(i) < infinity)
            writeRecord(p_output, "UP", "BOUND", 
               getColName(*this, i, p_cnames, name1),
               upper(i));
      }
   }   
   // --- ENDATA Section ---
   p_output << "ENDATA" << std::endl;   
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

