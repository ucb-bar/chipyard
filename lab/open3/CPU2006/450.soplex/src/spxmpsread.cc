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
#pragma ident "@(#) $Id: spxmpsread.cpp,v 1.26 2002/04/09 19:00:15 bzfkocht Exp $"
#endif

/**@file  spxmpsread.cpp
 * @brief Read LP from MPS format file.
 */
//#define DEBUGGING 1

#include <assert.h>
#include <string.h>
#include <iostream>

#include "spxdefines.h"
#include "spxlp.h"
#include "mpsinput.h"

#define INIT_COLS     10000       ///< initialy allocated columns.
#define INIT_NZOS     100000      ///< initialy allocated non zeros.

namespace soplex
{ 

/// Process NAME section.
static void readName(MPSInput& mps)
{
   do
   {
      // This has to be the Line with the NAME section.
      if (!mps.readLine() 
         || (mps.field0() == 0) || strcmp(mps.field0(), "NAME"))
         break;

      // Sometimes the name is omitted.
      mps.setProbName((mps.field1() == 0) ? "_MPS_" : mps.field1());

      VERBOSE2({ std::cout << "Problem name   : " << mps.probName()
                           << std::endl; });
 
      // This hat to be a new section
      if (!mps.readLine() || (mps.field0() == 0))
         break;

      if (!strcmp(mps.field0(), "ROWS"))
         mps.setSection(MPSInput::ROWS);
      else if (!strcmp(mps.field0(), "OBJSEN"))
         mps.setSection(MPSInput::OBJSEN);
      else if (!strcmp(mps.field0(), "OBJNAME"))
         mps.setSection(MPSInput::OBJNAME);
      else
         break;

      return;
   }
   while(false);

   mps.syntaxError();
}

/// Process OBJSEN section. This Section is an ILOG extension.
static void readObjsen(MPSInput& mps)
{
   do
   {
      // This has to be the Line with MIN or MAX.
      if (!mps.readLine() || (mps.field1() == 0))
         break;

      if (strcmp(mps.field1(), "MIN"))
         mps.setObjSense(SPxLP::MINIMIZE);
      else if (strcmp(mps.field1(), "MAX"))
         mps.setObjSense(SPxLP::MAXIMIZE);
      else
         break;

      // Look for ROWS or OBJNAME Section
      if (!mps.readLine() || (mps.field0() == 0))
         break;

      if (!strcmp(mps.field0(), "ROWS"))
         mps.setSection(MPSInput::ROWS);
      else if (!strcmp(mps.field0(), "OBJNAME"))
         mps.setSection(MPSInput::OBJNAME);
      else
         break;

      return;
   }
   while(false);

   mps.syntaxError();
}

/// Process OBJNAME section. This Section is an ILOG extension.
static void readObjname(MPSInput& mps)
{
   do
   {
      // This has to be the Line with the name.
      if (!mps.readLine() || (mps.field1() == 0))
         break;

      mps.setObjName(mps.field1());

      // Look for ROWS Section
      if (!mps.readLine() || (mps.field0() == 0))
         break;

      if (strcmp(mps.field0(), "ROWS"))
         break;

      mps.setSection(MPSInput::ROWS);
      return;
   }
   while(false);

   mps.syntaxError();
}

/// Process ROWS section. 
static void readRows(
   MPSInput& mps,
   LPRowSet& rset,
   NameSet&  rnames)
{
   LPRow row;

   while(mps.readLine())
   {
      if (mps.field0() != 0)
      {
         VERBOSE2({ std::cout << "Objective name : " << mps.objName()
                              << std::endl; });

         if (strcmp(mps.field0(), "COLUMNS"))
            break;

         mps.setSection(MPSInput::COLUMNS);
         return;
      }
      if (*mps.field1() == 'N')
      {
         if (*mps.objName() == '\0')
            mps.setObjName(mps.field2());
      }
      else
      {
         if (rnames.has(mps.field2()))
            break;

         rnames.add(mps.field2());
            
         switch(*mps.field1())
         {
         case 'G' :
            row.setLhs(0.0);
            row.setRhs(infinity);
            break;
         case 'E' :
            row.setLhs(0.0);
            row.setRhs(0.0);
            break;
         case 'L' :
            row.setLhs(-infinity);
            row.setRhs(0.0);
            break;
         default :
            mps.syntaxError();
            return;
         }
         rset.add(row);
      }
      assert((*mps.field1() == 'N') 
         || (rnames.number(mps.field2()) == rset.num() - 1));
   }
   mps.syntaxError();
}

/// Process COLUMNS section. 
static void readCols(
   MPSInput& mps,
   LPRowSet& rset,
   NameSet&  rnames,
   LPColSet& cset,
   NameSet&  cnames,
   DIdxSet*  intvars)
{
   Real     val;
   int      idx;
   char     colname[MPSInput::MAX_LINE_LEN] = { '\0' };
   LPCol    col(rset.num());
   DSVector vec;

   col.setObj(0.0);
   vec.clear();

   while(mps.readLine())
   {
      if (mps.field0() != 0)
      {
         if (strcmp(mps.field0(), "RHS"))
            break;

         if (colname[0] != '\0')
         {
            col.setColVector(vec);
            cset.add(col);
         }
         mps.setSection(MPSInput::RHS);
         return;
      }
      if ((mps.field1() == 0) || (mps.field2() == 0) || (mps.field3() == 0))
         break;

      // new column?
      if (strcmp(colname, mps.field1()))
      {
         // first column?
         if (colname[0] != '\0')
         {
            col.setColVector(vec);
            cset.add(col);
         }
         strcpy(colname, mps.field1());
         cnames.add(colname);
         vec.clear();
         col.setObj(0.0);
         col.setLower(0.0);
         col.setUpper(infinity);

         if (mps.isInteger())
         {
            assert(cnames.number(colname) == cset.num());

            if (intvars != 0)
               intvars->addIdx(cnames.number(colname));

            // For Integer variable the default bounds are 0/1 
            col.setUpper(1.0);
         }
      }
      val = atof(mps.field3());

      if (!strcmp(mps.field2(), mps.objName()))
         col.setObj(val);
      else 
      {
         if ((idx = rnames.number(mps.field2())) < 0)
            mps.entryIgnored("Column", mps.field1(), "row", mps.field2());
         else
            if (val != 0.0)
               vec.add(idx, val);
      }
      if (mps.field5() != 0)
      {
         assert(mps.field4() != 0);

         val = atof(mps.field5());

         if (!strcmp(mps.field4(), mps.objName()))
            col.setObj(val);
         else 
         {
            if ((idx = rnames.number(mps.field4())) < 0)
               mps.entryIgnored("Column", mps.field1(), "row", mps.field4());
            else
               if (val != 0.0)
                  vec.add(idx, val);
         }
      }
   }
   mps.syntaxError();
}

/// Process RHS section. 
static void readRhs(
   MPSInput& mps,
   LPRowSet& rset,
   NameSet&  rnames)
{
   char   rhsname[MPSInput::MAX_LINE_LEN] = { '\0' };
   int    idx;
   Real val;

   while(mps.readLine())
   {
      if (mps.field0() != 0)
      {
         VERBOSE2({ std::cout << "RHS name       : " << rhsname 
                              << std::endl; });

         if (!strcmp(mps.field0(), "RANGES"))
            mps.setSection(MPSInput::RANGES);
         else if (!strcmp(mps.field0(), "BOUNDS"))
            mps.setSection(MPSInput::BOUNDS);
         else if (!strcmp(mps.field0(), "ENDATA"))
            mps.setSection(MPSInput::ENDATA);
         else
            break;

         return;
      }
      if (((mps.field2() != 0) && (mps.field3() == 0))
         || ((mps.field4() != 0) && (mps.field5() == 0)))
         mps.insertName("_RHS_");
      
      if ((mps.field1() == 0) || (mps.field2() == 0) || (mps.field3() == 0))
         break;

      if (*rhsname == '\0')
         strcpy(rhsname, mps.field1());
      
      if (!strcmp(rhsname, mps.field1()))
      {
         if ((idx = rnames.number(mps.field2())) < 0)
            mps.entryIgnored("RHS", mps.field1(), "row", mps.field2());
         else
         {
            val = atof(mps.field3());

            // LE or EQ
            if (rset.rhs(idx) < infinity)
               rset.rhs(idx) = val;
            // GE or EQ
            if (rset.lhs(idx) > -infinity)
               rset.lhs(idx) = val;
         }
      }
      if (mps.field5() != 0)
      {
         if ((idx = rnames.number(mps.field4())) < 0)
            mps.entryIgnored("RHS", mps.field1(), "row", mps.field4());
         else
         {
            val = atof(mps.field5());
         
            // LE or EQ
            if (rset.rhs(idx) < infinity)
               rset.rhs(idx) = val;
            // GE or EQ
            if (rset.lhs(idx) > -infinity)
               rset.lhs(idx) = val;
         }
      }
   }
   mps.syntaxError();
}

/// Process RANGES section. 
static void readRanges(
   MPSInput& mps,
   LPRowSet& rset,
   NameSet&  rnames)
{
   char   rngname[MPSInput::MAX_LINE_LEN] = { '\0' };
   int    idx;
   Real val;

   while(mps.readLine())
   {
      if (mps.field0() != 0)
      {
         VERBOSE2({ std::cout << "Range name     : " << rngname
                              << std::endl; });

         if (!strcmp(mps.field0(), "BOUNDS"))
            mps.setSection(MPSInput::BOUNDS);
         else if (!strcmp(mps.field0(), "ENDATA"))
            mps.setSection(MPSInput::ENDATA);
         else
            break;

         return;
      }
      if (((mps.field2() != 0) && (mps.field3() == 0))
         || ((mps.field4() != 0) && (mps.field5() == 0)))
         mps.insertName("_RNG_");

      if ((mps.field1() == 0) || (mps.field2() == 0) || (mps.field3() == 0))
         break;

      if (*rngname == '\0')
         strcpy(rngname, mps.field1());

      /* The rules are:
       * Row Sign   LHS             RHS
       * ----------------------------------------
       *  G   +/-   rhs             rhs + |range|
       *  L   +/-   rhs - |range|   rhs
       *  E   +     rhs             rhs + range
       *  E   -     rhs + range     rhs 
       * ----------------------------------------
       */  
      if (!strcmp(rngname, mps.field1()))
      {
         if ((idx = rnames.number(mps.field2())) < 0)
            mps.entryIgnored("Range", mps.field1(), "row", mps.field2());
         else
         {
            val = atof(mps.field3());

            // EQ
            if (  (rset.lhs(idx) > -infinity) 
               && (rset.rhs(idx) <  infinity))
            {
               assert(rset.lhs(idx) == rset.rhs(idx));

               if (val >= 0)
                  rset.rhs(idx) += val;
               else
                  rset.lhs(idx) += val;
            }
            else
            {
               // GE 
               if (rset.lhs(idx) > -infinity)
                  rset.rhs(idx)  = rset.lhs(idx) + fabs(val);
               else // LE
                  rset.lhs(idx)  = rset.rhs(idx) - fabs(val);
            }
         }
         if (mps.field5() != 0)
         {
            if ((idx = rnames.number(mps.field4())) < 0)
               mps.entryIgnored("Range", mps.field1(), "row", mps.field4());
            else
            {
               val = atof(mps.field5());

               // EQ
               if (  (rset.lhs(idx) > -infinity) 
                  && (rset.rhs(idx) <  infinity))
               {
                  assert(rset.lhs(idx) == rset.rhs(idx));

                  if (val >= 0)
                     rset.rhs(idx) += val;
                  else
                     rset.lhs(idx) += val;
               }
               else
               {
                  // GE 
                  if (rset.lhs(idx) > -infinity)
                     rset.rhs(idx)  = rset.lhs(idx) + fabs(val);
                  else // LE
                     rset.lhs(idx)  = rset.rhs(idx) - fabs(val);
               }
            }
         }
      }
   }
   mps.syntaxError();
}

/// Process BOUNDS section. 
static void readBounds(
   MPSInput& mps,
   LPColSet& cset,
   NameSet&  cnames,
   DIdxSet*  intvars)
{
   char   bndname[MPSInput::MAX_LINE_LEN] = { '\0' };
   int    idx;
   Real val;

   while(mps.readLine())
   {
      if (mps.field0() != 0)
      {
         VERBOSE2({ std::cout << "Bound name     : " << bndname
                              << std::endl; });

         if (strcmp(mps.field0(), "ENDATA"))
            break;

         mps.setSection(MPSInput::ENDATA);
         return;
      }
      // Is the value field used ?
      if (  (!strcmp(mps.field1(), "LO"))
         || (!strcmp(mps.field1(), "UP"))
         || (!strcmp(mps.field1(), "FX"))
         || (!strcmp(mps.field1(), "UI")))
      {
         if ((mps.field3() != 0) && (mps.field4() == 0))
            mps.insertName("_BND_", true);
      }
      else
      {
         if ((mps.field2() != 0) && (mps.field3() == 0))
            mps.insertName("_BND_", true);
      }

      if ((mps.field1() == 0) || (mps.field2() == 0) || (mps.field3() == 0))
         break;

      if (*bndname == '\0')
         strcpy(bndname, mps.field2());
      
      // Only read the first Bound in section
      if (!strcmp(bndname, mps.field2()))
      {
         if ((idx = cnames.number(mps.field3())) < 0)
            mps.entryIgnored("column", mps.field3(), "bound", bndname);
         else
         {
            val = (mps.field4() == 0) ? 0.0 : atof(mps.field4());

            switch(*mps.field1())
            {
            case 'L':
               cset.lower(idx) = val;
               
               // ILOG extension (Integer Lower Bound)
               if ((intvars != 0) && (mps.field1()[1] == 'I'))
                  intvars->addIdx(idx);
               break;
            case 'U':
               cset.upper(idx) = val;
               
               // ILOG extension (Integer Upper Bound)
               if ((intvars != 0) && (mps.field1()[1] == 'I'))
                  intvars->addIdx(idx);
               break;
            case 'F':
               if (mps.field1()[1] == 'X')
               {
                  cset.lower(idx) = val;
                  cset.upper(idx) = val;
               }
               else
               {
                  cset.lower(idx) = -infinity;
                  cset.upper(idx) = infinity;
               }
               break;
            case 'M':
               cset.lower(idx) = -infinity;
               break;
            case 'P':
               cset.upper(idx) = infinity;
               break;
            case 'B' : // Ilog extension (Binary)
               cset.lower(idx) = 0.0;
               cset.upper(idx) = 1.0;
               
               if (intvars != 0)
                  intvars->addIdx(idx);
               break;
            default:
               mps.syntaxError();
               return;
            }
         }
      }
   }
   mps.syntaxError();
}

/// Read LP in "MPS File Format".
/** 
 *  The specification is taken from the
 *
 *  IBM Optimization Library Guide and Reference
 *
 *  Online available at http://www.software.ibm.com/sos/features/libuser.htm
 *
 *  and from the 
 *
 *  ILOG CPLEX 7.0 Reference Manual, Appendix E, Page 531.
 *
 *  This routine should read all valid MPS format files. 
 *  What it will not do, is find all cases where a file is ill formed. 
 *  If this happens it may complain and read nothing or read "something".
 *
 *  @return true if the file was read correctly.
 */  
bool SPxLP::readMPS(
   std::istream& p_input, 
   NameSet*      p_rnames,          ///< row names.
   NameSet*      p_cnames,          ///< column names.
   DIdxSet*      p_intvars)         ///< integer variables.
{
   LPRowSet& rset = *this;
   LPColSet& cset = *this;
   NameSet*  rnames;                ///< row names.
   NameSet*  cnames;                ///< column names.

   cnames = (p_cnames != 0) 
      ? p_cnames : new NameSet();

   cnames->clear();

   rnames = (p_rnames != 0)
      ? p_rnames : new NameSet();

   rnames->clear();

   SPxLP::clear(); // clear the LP.

   cset.memRemax(INIT_NZOS);
   cset.reMax(INIT_COLS);

   MPSInput mps(p_input);

   readName(mps);

   if (mps.section() == MPSInput::OBJSEN)
      readObjsen(mps);

   if (mps.section() == MPSInput::OBJNAME)
      readObjname(mps);

   if (mps.section() == MPSInput::ROWS)
      readRows(mps, rset, *rnames);

   addedRows(rset.num());

   if (mps.section() == MPSInput::COLUMNS)
      readCols(mps, rset, *rnames, cset, *cnames, p_intvars);

   if (mps.section() == MPSInput::RHS)
      readRhs(mps, rset, *rnames);

   if (mps.section() == MPSInput::RANGES)
      readRanges(mps, rset, *rnames);

   if (mps.section() == MPSInput::BOUNDS)
      readBounds(mps, cset, *cnames, p_intvars);

   if (mps.section() != MPSInput::ENDATA)
      mps.syntaxError();

   if (mps.hasError())
      clear();
   else
   {
      changeSense(mps.objSense());

      VERBOSE2({
         std::cout << "Objective sense: " 
                   << ((mps.objSense() == MINIMIZE) ? "Minimize" : "Maximize") 
                   << std::endl;         
      });

      added2Set(
         *(reinterpret_cast<SVSet*>(static_cast<LPRowSet*>(this))), 
         *(reinterpret_cast<SVSet*>(static_cast<LPColSet*>(this))), 
         cset.num());
      addedCols(cset.num());
      assert(isConsistent());
   }

   if (p_cnames == 0) 
      delete cnames;
   if (p_rnames == 0)
      delete rnames;

   return !mps.hasError();
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
