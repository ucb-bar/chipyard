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
#pragma ident "@(#) $Id: spxio.cpp,v 1.14 2002/03/03 13:50:34 bzfkocht Exp $"
#endif


//#define DEBUGGING 1

#include <iostream>
#include <stdio.h>
#include <assert.h>

#include "spxdefines.h"
#include "spxlp.h"

#include "dvector.h"
#include "dataarray.h"
#include "lprow.h"
#include "lpcol.h"
#include "lprowset.h"
#include "lpcolset.h"
#include "nameset.h"

namespace soplex
{
/**@param rowNames contains after the call the names of the constraints 
 *                 (rows) in the same order as the rows in the LP.
 *                 Constraints without a name (only possible with LPF 
 *                 files) are automatically assigned a name.
 *                 Maybe 0 if the names are not needed.
 * @param colNames contains after the call the names of the variables
 *                 (columns) in the same order as the columns in the LP.
 *                 Maybe 0 if the names are not needed.
 * @param intVars  contains after the call the indices of those variables
 *                 that where marked as beeing integer in the file.
 *                 Maybe 0 if the information is not needed.
 * @todo  Make sure the Id's in the NameSet%s are the same as in the LP.
 */
bool SPxLP::read(
   std::istream& is, 
   NameSet* rowNames,
   NameSet* colNames,
   DIdxSet* intVars)
{
   bool ok;
   char c;

   is.get(c);
   is.putback(c);

   /* MPS starts either with a comment mark '*' or with the keyword
    * 'NAME' at the first column.
    * LPF starts either with blanks, a comment mark '\' or with
    * the keyword "MAX" or "MIN" in upper or lower case.
    * There is no possible valid LPF file starting with a '*' or 'N'.
    */
   ok = ((c == '*') || (c == 'N'))
      ? readMPS(is, rowNames, colNames, intVars)
      : readLPF(is, rowNames, colNames, intVars);

   DEBUG( std::cerr << *this; );

   return ok;
}

static void dumpRows(std::ostream& s, const SPxLP& lp)
{
   int i;

   s << "\nSubject To\n";
   for (i = 0; i < lp.nRows(); ++i)
   {
      s << "  C" << (i + 1) << ": ";
      Real low;
      Real up;
      low = lp.lhs(i);
      up = lp.rhs(i);
      if (low > -infinity && up < infinity && low != up)
      {
         s << low << " <= " << lp.rowVector(i) << " <= " << up << '\n';
      }
      else if (low == up)
         s << lp.rowVector(i) << " = " << up << '\n';
      else if (low <= -infinity)
         s << lp.rowVector(i) << " <= " << up << '\n';
      else
         s << lp.rowVector(i) <<">= " << low << '\n';
   }
}

static void dumpBounds(std::ostream& s, const SPxLP& lp)
{
   int i;

   s << "Bounds\n";
   for (i = 0; i < lp.nCols(); ++i)
   {
      Real up;
      Real low;
      up = lp.upper(i);
      low = lp.lower(i);
      if (low == up)
         s << "  x" << i << " = " << up << '\n';
      else if (low > -infinity)
      {
         if (up < infinity)
            s << "  " << low << " <= x" << i
            << " <= " << up << '\n';
         else if (low != 0)
            s << "  x" << i <<">= " << low << '\n';
      }
      else if (up < infinity)
         s << "  x" << i << " <= " << up << '\n';
      else
         s << "  x" << i << " FREE\n";
   }
}

std::ostream& operator<<(std::ostream& s, const SPxLP& lp)
{
   int i, j;
   int sns = lp.spxSense();

   s << ((sns == SPxLP::MINIMIZE) ? "Minimize\n" : "Maximize\n");

   s << "  obj: ";
   for (i = j = 0; i < lp.nCols(); ++i)
   {
      Real obj = lp.obj(i);
      if (obj != 0)
      {
         if (j)
         {
            if (obj < 0)
               s << " - " << -obj << " x" << i;
            else
               s << " + " << obj << " x" << i;
         }
         else
            s << obj << " x" << i;
         j++;
         if (j % 5 == 0)
            s << "\n\t";
      }
   }
   dumpRows(s, lp);
   dumpBounds(s, lp);

   s << "End\n";
   return s;
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
