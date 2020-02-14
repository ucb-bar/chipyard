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
#pragma ident "@(#) $Id: spxchangebasis.cpp,v 1.14 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <iostream>
#include <math.h>

#include "spxdefines.h"
#include "spxbasis.h"
#include "soplex.h"

namespace soplex
{

void SPxBasis::reDim()
{
   METHOD( "SPxBasis::reDim()" );

   assert(theLP != 0);

   DEBUG({ std::cout << "SPxBasis::reDim():"
                     << " matrixIsSetup=" << matrixIsSetup
                     << " fatorized=" << factorized
                     << std::endl; });

   thedesc.reSize (theLP->nRows(), theLP->nCols());

   if (theLP->dim() != matrix.size())
   {
      matrix.reSize (theLP->dim());
      theBaseId.reSize(theLP->dim());
      matrixIsSetup = false;
      factorized = false;
   }

   DEBUG({ std::cout << "SPxBasis::reDim(): -->"
                     << " matrixIsSetup=" << matrixIsSetup
                     << " fatorized=" << factorized
                     << std::endl; });

   assert( matrix.size()    >= theLP->dim() );
   assert( theBaseId.size() >= theLP->dim() );
}

void SPxBasis::addedRows(int n)
{
   METHOD( "SPxBasis::addedRows()" );
   assert(theLP != 0);

   if( n > 0 )
   {
      reDim();
      
      if (theLP->rep() == SoPlex::COLUMN)
      {
         /* I think, after adding rows in column representation,
            reDim should set these bools to false. */
         assert( !matrixIsSetup && !factorized );

         for (int i = theLP->nRows() - n; i < theLP->nRows(); ++i)
         {
            thedesc.rowStatus(i) = dualRowStatus(i);
            baseId(i) = theLP->SPxLP::rId(i);
         }

         /* ??? I think, this cannot happen. */
         /* if matrix was set up, load new basis vectors to the matrix */
         if (status() > NO_PROBLEM && matrixIsSetup)
            loadMatrixVecs();
      }
      else
      {
         assert(theLP->rep() == SoPlex::ROW);
         for (int i = theLP->nRows() - n; i < theLP->nRows(); ++i)
            thedesc.rowStatus(i) = dualRowStatus(i);
      }

      /* update basis status */
      switch (status())
      {
      case PRIMAL:
      case UNBOUNDED:
         setStatus(REGULAR);
         break;
      case OPTIMAL:
      case INFEASIBLE:
         setStatus(DUAL);
         break;
      case NO_PROBLEM:
      case SINGULAR:
      case REGULAR:
      case DUAL:
         break;
      default:
         std::cerr << "Unknown basis status!" << std::endl;
         abort();
      }
   }
}

void SPxBasis::removedRow(int i)
{
   METHOD( "SPxBasis::removedRow()" );
   assert(status() > NO_PROBLEM);
   assert(theLP != 0);

   if (theLP->rep() == SoPlex::ROW)
   {
      if (theLP->isBasic(thedesc.rowStatus(i)))
      {
         setStatus(NO_PROBLEM);
         factorized = false;
         DEBUG( std::cerr << "Are you sure, you wanna do that?\n"; );
      }

   }
   else
   {
      assert(theLP->rep() == SoPlex::COLUMN);
      factorized = false;
      if (!theLP->isBasic(thedesc.rowStatus(i)))
      {
         setStatus(NO_PROBLEM);
         DEBUG( std::cerr << "Are you sure, you wanna do that?\n"; );
      }

      else if (status() > NO_PROBLEM && matrixIsSetup)
      {
         for (int j = theLP->dim(); j >= 0; --j)
         {
            SPxId id = baseId(j);
            if (id.isSPxRowId()
                 && theLP->number(SPxRowId(id)) < 0)
            {
               baseId(j) = baseId(theLP->dim());
               if (j < theLP->dim())
                  matrix[j] = &theLP->vector(baseId(j));
               break;
            }
         }
      }
   }

   thedesc.rowStatus(i) = thedesc.rowStatus(theLP->nRows());
   reDim();
}

void SPxBasis::removedRows(int perm[])
{
   METHOD( "SPxBasis::removedRows()" );
   assert(status() > NO_PROBLEM);
   assert(theLP != 0);

   int i;
   int n = thedesc.nRows();

   if (theLP->rep() == SoPlex::ROW)
   {
      for (i = 0; i < n; ++i)
      {
         if (perm[i] != i)
         {
            if (perm[i] < 0)               // row got removed
            {
               if (theLP->isBasic(thedesc.rowStatus(i)))
               {
                  setStatus(NO_PROBLEM);
                  factorized = matrixIsSetup = false;
                  DEBUG( std::cerr << "Are you sure, you wanna do that?\n"; );
               }

            }
            else                            // row was moved
               thedesc.rowStatus(perm[i]) = thedesc.rowStatus(i);
         }
      }
   }
   else
   {
      assert(theLP->rep() == SoPlex::COLUMN);
      factorized = matrixIsSetup = false;
      for (i = 0; i < n; ++i)
      {
         if (perm[i] != i)
         {
            if (perm[i] < 0)               // row got removed
            {
               if (!theLP->isBasic(thedesc.rowStatus(i)))
                  setStatus(NO_PROBLEM);
            }
            else                            // row was moved
               thedesc.rowStatus(perm[i]) = thedesc.rowStatus(i);
         }
      }
   }

   reDim();
}


static SPxBasis::Desc::Status
primalColStatus(int i, const SPxLP* theLP)
{
   assert(theLP != 0);

   if (theLP->upper(i) < infinity)
   {
      if (theLP->lower(i) > -infinity)
      {
         if (theLP->lower(i) == theLP->SPxLP::upper(i))
            return SPxBasis::Desc::P_FIXED;
         /*
             else
                 return (-theLP->lower(i) < theLP->upper(i))
                             ? SPxBasis::Desc::P_ON_LOWER
                          : SPxBasis::Desc::P_ON_UPPER;
         */
         else if (theLP->maxObj(i) == 0)
            return (-theLP->lower(i) < theLP->upper(i))
               ? SPxBasis::Desc::P_ON_LOWER
               : SPxBasis::Desc::P_ON_UPPER;
         else
            return (theLP->maxObj(i) < 0)
               ? SPxBasis::Desc::P_ON_LOWER
               : SPxBasis::Desc::P_ON_UPPER;
      }
      else
         return SPxBasis::Desc::P_ON_UPPER;
   }
   else if (theLP->lower(i) > -infinity)
      return SPxBasis::Desc::P_ON_LOWER;
   else
      return SPxBasis::Desc::P_FREE;
}


void SPxBasis::addedCols(int n)
{
   METHOD( "SPxBasis::addedCols()" );
   assert(theLP != 0);

   if( n > 0 )
   {
      reDim();
      
      if (theLP->rep() == SoPlex::ROW)
      {
         /* I think, after adding columns in row representation,
            reDim should set these bools to false. */
         assert( !matrixIsSetup && !factorized );

         for (int i = theLP->nCols() - n; i < theLP->nCols(); ++i)
         {
            thedesc.colStatus(i) = primalColStatus(i, theLP);
            baseId(i) = theLP->SPxLP::cId(i);
         }

         /* ??? I think, this cannot happen. */
         /* if matrix was set up, load new basis vectors to the matrix */
         if (status() > NO_PROBLEM && matrixIsSetup)
            loadMatrixVecs();
      }
      else
      {
         assert(theLP->rep() == SoPlex::COLUMN);
         for (int i = theLP->nCols() - n; i < theLP->nCols(); ++i)
            thedesc.colStatus(i) = primalColStatus(i, theLP);
      }
         
      switch (status())
      {
      case DUAL:
      case INFEASIBLE:
         setStatus(REGULAR);
         break;
      case OPTIMAL:
      case UNBOUNDED:
         setStatus(PRIMAL);
         break;
      case NO_PROBLEM:
      case SINGULAR:
      case REGULAR:
      case PRIMAL:
         break;
      default:
         std::cerr << "Unknown basis status!" << std::endl;
         abort();
      }
   }
}

void SPxBasis::removedCol(int i)
{
   METHOD( "SPxBasis::removedCol()" );
   assert(status() > NO_PROBLEM);
   assert(theLP != 0);

   if (theLP->rep() == SoPlex::COLUMN)
   {
      if (theLP->isBasic(thedesc.colStatus(i)))
         setStatus(NO_PROBLEM);
   }
   else
   {
      assert(theLP->rep() == SoPlex::ROW);
      factorized = false;
      if (!theLP->isBasic(thedesc.colStatus(i)))
         setStatus(NO_PROBLEM);
      else if (status() > NO_PROBLEM)
      {
         assert( matrixIsSetup );
         for (int j = theLP->dim(); j >= 0; --j)
         {
            SPxId id = baseId(j);
            if (id.isSPxColId()
                 && theLP->number(SPxColId(id)) < 0)
            {
               baseId(j) = baseId(theLP->dim());
               if (j < theLP->dim())
                  matrix[j] = &theLP->vector(baseId(j));
               break;
            }
         }
      }
   }

   thedesc.colStatus(i) = thedesc.colStatus(theLP->nCols());
   reDim();
}

void SPxBasis::removedCols(int perm[])
{
   METHOD( "SPxBasis::removedCols()" );
   assert(status() > NO_PROBLEM);
   assert(theLP != 0);

   int i;
   int n = thedesc.nCols();

   if (theLP->rep() == SoPlex::COLUMN)
   {
      for (i = 0; i < n; ++i)
      {
         if (perm[i] < 0)           // column got removed
         {
            if (theLP->isBasic(thedesc.colStatus(i)))
               setStatus(NO_PROBLEM);
         }
         else                        // column was potentially moved
            thedesc.colStatus(perm[i]) = thedesc.colStatus(i);
      }
   }
   else
   {
      assert(theLP->rep() == SoPlex::ROW);
      factorized = matrixIsSetup = false;
      for (i = 0; i < n; ++i)
      {
         if (perm[i] != i)
         {
            if (perm[i] < 0)               // column got removed
            {
               if (!theLP->isBasic(thedesc.colStatus(i)))
                  setStatus(NO_PROBLEM);
            }
            else                            // column was moved
               thedesc.colStatus(perm[i]) = thedesc.colStatus(i);
         }
      }
   }

   reDim();
}


/**@todo is this correctly implemented?
 */
void SPxBasis::invalidate()
{
   METHOD( "SPxBasis::invalidate()" );
   factorized = matrixIsSetup = false;
}

/**@todo is this correctly implemented?
 */
void SPxBasis::changedRow(int /*row*/)
{
   METHOD( "SPxBasis::changedRow()" );
   invalidate();
}

/**@todo is this correctly implemented?
 */
void SPxBasis::changedCol(int /*col*/)
{
   METHOD( "SPxBasis::changedCol()" );
   invalidate();
}

/**@todo is this correctly implemented?
 */
void SPxBasis::changedElement(int /*row*/, int /*col*/)
{
   METHOD( "SPxBasis::changedElement()" );
   invalidate();
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
