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
#pragma ident "@(#) $Id: changesoplex.cpp,v 1.15 2002/03/11 17:43:55 bzfkocht Exp $"
#endif

// #define DEBUGGING 1


#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "soplex.h"
#include "spxpricer.h"
#include "spxratiotester.h"

namespace soplex
{

void SoPlex::localAddRows(int start)
{
   METHOD( "SoPlex::localAddRows()" );
   assert( start <= SPxLP::nRows() );

   /**@todo This method seems to be called, to update
    *       theFvec, theFrhs, ..., but a resolve after
    *       adding a row results in a failure.
    *       To fix this, we call init() before solving
    *       in spxsolve.cpp:solve(). In init(), the
    *       vectors are set up, so there is no need
    *       to update them here.
    */
   return;

#if 0
   if( start == SPxLP::nRows() )
      return;

   const SPxBasis::Desc& ds = desc();

   if (type() == ENTER)
   {
      if (rep() == COLUMN)
      {
         int i;
         for (i = start; i < SPxLP::nRows(); ++i)
         {
            theURbound[i] = -lhs(i);
            theLRbound[i] = -rhs(i);
            setEnterBound4Row(i, i);
            computeEnterCoPrhs4Row(i, i);
            // init #theFrhs[i]#:
            Real& v_rhs = (*theFrhs)[i];
            const SVector& row = rowVector(i); // ((const SoPlex*)this)->rowVector(i);
            v_rhs = 0;
            for (int j = row.size() - 1; j >= 0; --j)
            {
               int idx = row.index(j);
               switch (ds.colStatus(idx))
               {
               case Desc::P_ON_UPPER:
                  v_rhs += row.value(j) * theUCbound[idx];
                  break;
               case Desc::P_ON_LOWER:
               case Desc::P_FIXED:
                  v_rhs += row.value(j) * theLCbound[idx];
                  break;
               default:
                  break;
               }
            }
         }
         SPxBasis::solve (*theFvec, *theFrhs);
         SPxBasis::coSolve(*theCoPvec, *theCoPrhs);
         for (i = start; i < SPxLP::nRows(); ++i)
         {
            if (theUBbound[i] + delta() < (*theFvec)[i])
               shiftUBbound(i, (*theFvec)[i]);
            else if ((*theFvec)[i] < theLBbound[i] - delta())
               shiftLBbound(i, (*theFvec)[i]);
         }
         computePvec();
         computeCoTest();
         computeTest();
      }
      else
      {
         assert(rep() == ROW);
         for (int i = start; i < SPxLP::nRows(); ++i)
         {
            theURbound[i] = theLRbound[i] = 0;
            clearDualBounds(dualRowStatus(i),
                             theURbound[i], theLRbound[i]);
            (*thePvec)[i] = vector(i) * (*theCoPvec);
            theTest[i] = test(i, ds.status(i));
         }
      }
   }
   else
   {
      assert(type() == LEAVE);
      if (rep() == ROW)
      {
         for (int i = start; i < SPxLP::nRows(); ++i)
         {
            theURbound[i] = rhs(i);
            theLRbound[i] = lhs(i);
            (*thePvec)[i] = vector(i) * (*theCoPvec);
            if (theURbound[i] + delta() < (*thePvec)[i])
               shiftUPbound(i, (*thePvec)[i]);
            else if ((*thePvec)[i] < theLRbound[i] - delta())
               shiftLPbound(i, (*thePvec)[i]);
         }
      }
      else
      {
         assert(rep() == COLUMN);
         int i;
         for (i = start; i < SPxLP::nRows(); ++i)
         {
            theURbound[i] = theLRbound[i] = 0;
            clearDualBounds(ds.rowStatus(i),
                             theURbound[i], theLRbound[i]);
            setLeaveBound4Row(i, i);
            computeLeaveCoPrhs4Row(i, i);
            // init #theFrhs[i]#
            Real& v_rhs = (*theFrhs)[i];
            const SVector& row = rowVector(i); //((const SoPlex*)this)->rowVector(i);
            v_rhs = 0;
            for (int j = row.size() - 1; j >= 0; --j)
            {
               int idx = row.index(j);
               switch (ds.colStatus(idx))
               {
               case Desc::P_ON_UPPER:
                  v_rhs += row.value(j) * SPxLP::upper(idx);
                  break;
               case Desc::P_ON_LOWER:
               case Desc::P_FIXED:
                  v_rhs += row.value(j) * SPxLP::lower(idx);
                  break;
               default:
                  break;
               }
            }
         }
         SPxBasis::solve (*theFvec, *theFrhs);
         SPxBasis::coSolve(*theCoPvec, *theCoPrhs);
         for (i = start; i < SPxLP::nRows(); ++i)
         {
            if ((*theFvec)[i] > theUBbound[i])
               theCoTest[i] = theUBbound[i] - (*theFvec)[i];
            else
               theCoTest[i] = (*theFvec)[i] - theLBbound[i];
         }
      }
   }
#endif //0
}

void SoPlex::addedRows(int n)
{
   METHOD( "SoPlex::addedRows()" );
   SPxLP::addedRows(n);

   if( n > 0 )
   {
      reDim();
      
      if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
      {
         SPxBasis::addedRows(n);
         if (isInitialized())
         {
            localAddRows(nRows() - n);
            assert(thepricer != 0);
            if (rep() == ROW)
               thepricer->addedVecs(n);
            else
               thepricer->addedCoVecs(n);            
         }
      }
   }
   assert(isConsistent());
}


void SoPlex::localAddCols(int start)
{
   METHOD( "SoPlex::localAddCols()" );
   assert( start <= SPxLP::nCols() );

   /**@todo This method seems to be called, to update
    *       theFvec, theFrhs, ..., but a resolve after
    *       adding a row results in a failure.
    *       To fix this, we call init() before solving
    *       in spxsolve.cpp:solve(). In init(), the
    *       vectors are set up, so there is no need
    *       to update them here.
    */
   return;
#if 0
   if( start == SPxLP::nCols() )
      return;

   const SPxBasis::Desc& ds = desc();

   if (type() == ENTER)
   {
      if (rep() == COLUMN)
      {
         int reSolve = 0;
         int i;
         Real x;
         for (i = start; i < SPxLP::nCols(); ++i)
         {
            (*thePvec)[i] = vector(i) * (*theCoPvec);
            theTest[i] = test(i, ds.colStatus(i));
            theUCbound[i] = SPxLP::upper(i);
            theLCbound[i] = SPxLP::lower(i);
            switch (ds.colStatus(i))
            {
            case SPxBasis::Desc::P_ON_LOWER + SPxBasis::Desc::P_ON_UPPER :
               assert(SPxLP::lower(i) == SPxLP::upper(i));
               /*FALLTHROUGH*/
            case SPxBasis::Desc::P_ON_UPPER :
               x = SPxLP::upper(i);
               break;
            case SPxBasis::Desc::P_ON_LOWER :
               x = SPxLP::lower(i);
               break;
            default:
               x = 0;
               break;
            }
            if (x)
            {
               theFrhs->multAdd(-x, vector(i));
               reSolve = 1;
            }
         }
         if (reSolve)
         {
            SPxBasis::solve(*theFvec, *theFrhs);
            shiftFvec();
         }
      }
      else
      {
         int i;
         for (i = start; i < SPxLP::nCols(); ++i)
         {
            theUCbound[i] = theLCbound[i] = 0;
            (*theFrhs)[i] = SPxLP::spxSense() * SPxLP::obj(i);
            clearDualBounds(ds.colStatus(i),
                             theUCbound[i], theLCbound[i]);
            setEnterBound4Col(i, i);
            computeEnterCoPrhs4Col(i, i);
         }
         SPxBasis::coSolve(*theCoPvec, *theCoPrhs);
         computePvec();
         computeCoTest();
         computeTest();
         SPxBasis::solve(*theFvec, *theFrhs);
         for (i = start; i < SPxLP::nCols(); ++i)
         {
            if (theUBbound[i] + delta() < (*theFvec)[i])
               shiftUBbound(i, (*theFvec)[i]);
            if ((*theFvec)[i] < theLBbound[i] - delta())
               shiftLBbound(i, (*theFvec)[i]);
         }
      }
   }
   else
   {
      if (rep() == ROW)
      {
         int i;
         for (i = start; i < SPxLP::nCols(); ++i)
         {
            theUCbound[i] = SPxLP::upper(i);
            theLCbound[i] = SPxLP::lower(i);
            (*theFrhs)[i] = SPxLP::spxSense() * SPxLP::obj(i);
            setLeaveBound4Col(i, i);
            computeLeaveCoPrhs4Col(i, i);
         }
         SPxBasis::coSolve(*theCoPvec, *theCoPrhs);
         computePvec();
         //          shiftPvec();
         SPxBasis::solve(*theFvec, *theFrhs);
         for (i = start; i < SPxLP::nCols(); ++i)
         {
            if ((*theFvec)[i] > theUBbound[i])
               theCoTest[i] = theUBbound[i] - (*theFvec)[i];
            else
               theCoTest[i] = (*theFvec)[i] - theLBbound[i];
         }
      }
      else
      {
         Real x;
         int i;
         int reSolve = 0;
         for (i = start; i < SPxLP::nCols(); ++i)
         {
            theUCbound[i] = theLCbound[i] = -maxObj(i);
            clearDualBounds(ds.colStatus(i),
                             theLCbound[i], theUCbound[i]);
            theUCbound[i] *= -1;
            theLCbound[i] *= -1;

            (*thePvec)[i] = vector(i) * (*theCoPvec);
            if (theUCbound[i] + delta() < (*thePvec)[i])
               shiftUPbound(i, (*thePvec)[i]);
            if (theLCbound[i] - delta() > (*thePvec)[i])
               shiftLPbound(i, (*thePvec)[i]);

            switch (ds.colStatus(i))
            {
            case SPxBasis::Desc::P_ON_LOWER + SPxBasis::Desc::P_ON_UPPER :
               assert(SPxLP::lower(i) == SPxLP::upper(i));
               /*FALLTHROUGH*/
            case SPxBasis::Desc::P_ON_UPPER :
               x = SPxLP::upper(i);
               break;
            case SPxBasis::Desc::P_ON_LOWER :
               x = SPxLP::lower(i);
               break;
            default:
               x = 0;
               break;
            }
            if (x)
            {
               theFrhs->multAdd(-x, vector(i));
               reSolve = 1;
            }
         }
         if (reSolve)
         {
            SPxBasis::solve(*theFvec, *theFrhs);
            computeFtest();
         }
      }
   }
#endif //0
}

void SoPlex::addedCols(int n)
{
   METHOD( "SoPlex::addedCols()" );
   SPxLP::addedCols(n);

   if( n > 0 )
   {
      reDim();
      
      if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
      {
         SPxBasis::addedCols(n);
         if (isInitialized())
         {
            localAddCols(nCols() - n);
            assert(thepricer != 0);
            if (rep() == COLUMN)
               thepricer->addedVecs(n);
            else
               thepricer->addedCoVecs(n);
         }
      }
   }
   assert(isConsistent());
}
   
void SoPlex::doRemoveRow(int i)
{
   METHOD( "SoPlex::doRemoveRow()" );
   SPxLP::doRemoveRow(i);

   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      removedRow(i);
      unInit();
      if (isInitialized())
      {
         int n = SPxLP::nRows();

         theURbound[i] = theURbound[n];
         theLRbound[i] = theLRbound[n];
         if (rep() == ROW)
         {
            (*thePvec)[i] = (*thePvec)[n];
            if (type() == ENTER)
               theTest[i] = theTest[n];
            reDim();
            assert(thepricer != 0);
            thepricer->removedVec(i);
         }
         else
         {
            unInit();
         }
      }

      switch (SPxBasis::status())
      {
      case SPxBasis::DUAL:
      case SPxBasis::INFEASIBLE:
         setStatus(SPxBasis::REGULAR);
         break;
      case SPxBasis::OPTIMAL:
         setStatus(SPxBasis::PRIMAL);
         break;
      default:
         break;
      }
   }
}

void SoPlex::doRemoveRows(int perm[])
{
   METHOD( "SoPlex::doRemoveRows()" );
   int n = SPxLP::nRows();
   SPxLP::doRemoveRows(perm);

   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      removedRows(perm);
      unInit();
      if (isInitialized())
      {
         if (rep() == ROW)
         {
            if (type() == ENTER)
            {
               for (int i = 0; i < n; ++i)
                  if (perm[i] >= 0)
                  {
                     theURbound[perm[i]] = theURbound[i];
                     theLRbound[perm[i]] = theLRbound[i];
                     (*thePvec)[perm[i]] = (*thePvec)[i];
                     theTest[perm[i]] = theTest[i];
                  }
            }
            else
            {
               for (int i = 0; i < n; ++i)
                  if (perm[i] >= 0)
                  {
                     theURbound[perm[i]] = theURbound[i];
                     theLRbound[perm[i]] = theLRbound[i];
                     (*thePvec)[perm[i]] = (*thePvec)[i];
                  }
            }
            assert(thepricer != 0);
            thepricer->removedVecs(perm);
            reDim();
         }
         else
         {
            unInit();
         }
      }

      switch (SPxBasis::status())
      {
      case SPxBasis::DUAL:
      case SPxBasis::INFEASIBLE:
         setStatus(SPxBasis::REGULAR);
         break;
      case SPxBasis::OPTIMAL:
         setStatus(SPxBasis::PRIMAL);
         break;
      default:
         break;
      }
   }
}

void SoPlex::doRemoveCol(int i)
{
   METHOD( "SoPlex::doRemoveCol()" );
   SPxLP::doRemoveCol(i);

   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      removedCol(i);
      unInit();
      if (isInitialized())
      {
         int n = SPxLP::nCols();

         theUCbound[i] = theUCbound[n];
         theLCbound[i] = theLCbound[n];
         if (rep() == COLUMN)
         {
            (*thePvec)[i] = (*thePvec)[n];
            if (type() == ENTER)
               theTest[i] = theTest[n];
            assert(thepricer != 0);
            thepricer->removedVec(i);
            reDim();
         }
         else
         {
            unInit();
         }
      }

      switch (SPxBasis::status())
      {
      case SPxBasis::PRIMAL:
      case SPxBasis::UNBOUNDED:
         setStatus(SPxBasis::REGULAR);
         break;
      case SPxBasis::OPTIMAL:
         setStatus(SPxBasis::DUAL);
         break;
      default:
         break;
      }
   }
}

void SoPlex::doRemoveCols(int perm[])
{
   METHOD( "SoPlex::doRemoveCols()" );
   int n = SPxLP::nCols();
   SPxLP::doRemoveCols(perm);

   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      removedCols(perm);
      unInit();
      if (isInitialized())
      {
         if (rep() == COLUMN)
         {
            if (type() == ENTER)
            {
               for (int i = 0; i < n; ++i)
                  if (perm[i] >= 0)
                  {
                     theUCbound[perm[i]] = theUCbound[i];
                     theLCbound[perm[i]] = theLCbound[i];
                     (*thePvec)[perm[i]] = (*thePvec)[i];
                     theTest[perm[i]] = theTest[i];
                  }
            }
            else
            {
               for (int i = 0; i < n; ++i)
                  if (perm[i] >= 0)
                  {
                     theUCbound[perm[i]] = theUCbound[i];
                     theLCbound[perm[i]] = theLCbound[i];
                     (*thePvec)[perm[i]] = (*thePvec)[i];
                  }
            }
            assert(thepricer != 0);
            thepricer->removedVecs(perm);
            reDim();
         }
         else
         {
            unInit();
         }
      }

      switch (SPxBasis::status())
      {
      case SPxBasis::PRIMAL:
      case SPxBasis::UNBOUNDED:
         setStatus(SPxBasis::REGULAR);
         break;
      case SPxBasis::OPTIMAL:
         setStatus(SPxBasis::DUAL);
         break;
      default:
         break;
      }
   }
}

void SoPlex::changeObj(const Vector& newObj)
{
   METHOD( "SoPlex::changeObj()" );
   SPxLP::changeObj(newObj);
   unInit();
}

void SoPlex::changeObj(int i, Real newVal)
{
   METHOD( "SoPlex::changeObj()" );
   SPxLP::changeObj(i, newVal);
   unInit();
}

static void changeLowerStatus
(
   SPxBasis::Desc::Status& stat,
   Real newLower,
   Real upper,
   const SPxBasis& basis,
   int i
)
{
   DEBUG({ std::cout << "changeLowerStatus(): col " << i
                     << ": " << stat; });
   switch (stat)
   {
   case SPxBasis::Desc::P_ON_LOWER:
      if (newLower <= -infinity)
         stat = (upper >= infinity)
                ? SPxBasis::Desc::P_FREE
             : SPxBasis::Desc::P_ON_UPPER;
      else if (newLower == upper)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_ON_UPPER:
      if (newLower == upper)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_FREE:
      if (newLower > -infinity)
         stat = SPxBasis::Desc::P_ON_LOWER;
      break;
   case SPxBasis::Desc::P_FIXED:
      if (newLower != upper)
         stat = SPxBasis::Desc::P_ON_UPPER;
      break;
   case SPxBasis::Desc::D_FREE:
   case SPxBasis::Desc::D_ON_UPPER:
   case SPxBasis::Desc::D_ON_LOWER:
   case SPxBasis::Desc::D_ON_BOTH:
   case SPxBasis::Desc::D_UNDEFINED:
      stat = basis.dualColStatus(i);
      break;
   default:
      abort();
   }
   DEBUG( std::cout << " -> " << stat << std::endl; );
}

void SoPlex::changeLower(const Vector& newLower)
{
   METHOD( "SoPlex::changeLower()" );
   SPxLP::changeLower(newLower);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      for (int i = newLower.dim() - 1; i >= 0; --i)
         changeLowerStatus(
            desc().colStatus(i), newLower[i], SPxLP::upper(i), *this, i);
      unInit();
   }
}

void SoPlex::changeLower(int i, Real newLower)
{
   METHOD( "SoPlex::changeLower()" );
   SPxLP::changeLower(i, newLower);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      changeLowerStatus(
         desc().colStatus(i), newLower, SPxLP::upper(i), *this, i);
      unInit();
   }
}

static void changeUpperStatus
(
   SPxBasis::Desc::Status& stat,
   Real newUpper,
   Real lower,
   const SPxBasis& basis,
   int i
)
{
   DEBUG({ std::cout << "changeUpperStatus(): col " << i
                     << ": " << stat; });
   switch (stat)
   {
   case SPxBasis::Desc::P_ON_LOWER:
      if (newUpper == lower)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_ON_UPPER:
      if (newUpper >= infinity)
         stat = (lower <= -infinity)
            ? SPxBasis::Desc::P_FREE
            : SPxBasis::Desc::P_ON_LOWER;
      else if (newUpper == lower)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_FREE:
      if (newUpper < infinity)
         stat = SPxBasis::Desc::P_ON_UPPER;
      break;
   case SPxBasis::Desc::P_FIXED:
      if (newUpper != lower)
         stat = SPxBasis::Desc::P_ON_LOWER;
      break;
   case SPxBasis::Desc::D_FREE:
   case SPxBasis::Desc::D_ON_UPPER:
   case SPxBasis::Desc::D_ON_LOWER:
   case SPxBasis::Desc::D_ON_BOTH:
   case SPxBasis::Desc::D_UNDEFINED:
      stat = basis.dualColStatus(i);
      break;
   default:
      abort();
   }
   DEBUG( std::cout << " -> " << stat << std::endl; );
}

void SoPlex::changeUpper(const Vector& newUpper)
{
   METHOD( "SoPlex::changeUpper()" );
   SPxLP::changeUpper(newUpper);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      for (int i = newUpper.dim() - 1; i >= 0; --i)
         changeUpperStatus(
            desc().colStatus(i), newUpper[i], SPxLP::lower(i), *this, i);
      unInit();
   }
}

void SoPlex::changeUpper(int i, Real newUpper)
{
   METHOD( "SoPlex::changeUpper()" );
   SPxLP::changeUpper(i, newUpper);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      changeUpperStatus(
         desc().colStatus(i), newUpper, SPxLP::lower(i), *this, i);
      unInit();
   }
}

void SoPlex::changeBounds(const Vector& newLower, const Vector& newUpper)
{
   METHOD( "SoPlex::changeBounds()" );
   SPxLP::changeLower(newLower);
   SPxLP::changeUpper(newUpper);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      for (int i = newUpper.dim() - 1; i >= 0; --i)
      {
         changeUpperStatus(
            desc().colStatus(i), newUpper[i], SPxLP::lower(i), *this, i);
         changeLowerStatus(
            desc().colStatus(i), newLower[i], SPxLP::upper(i), *this, i);
      }
      unInit();
   }
}

void SoPlex::changeBounds(int i, Real newLower, Real newUpper)
{
   METHOD( "SoPlex::changeBounds()" );
   SPxLP::changeLower(i, newLower);
   SPxLP::changeUpper(i, newUpper);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      changeUpperStatus(
         desc().colStatus(i), newUpper, SPxLP::lower(i), *this, i);
      changeLowerStatus(
         desc().colStatus(i), newLower, SPxLP::upper(i), *this, i);
      unInit();
   }
}

static void changeLhsStatus
(
   SPxBasis::Desc::Status& stat,
   Real newLhs,
   Real rhs,
   const SPxBasis& basis,
   int i
)
{
   DEBUG({ std::cout << "changeLhsStatus()  : row " << i
                     << ": " << stat; });
   switch (stat)
   {
   case SPxBasis::Desc::P_ON_LOWER:
      if (newLhs <= -infinity)
         stat = (rhs >= infinity)
            ? SPxBasis::Desc::P_FREE
            : SPxBasis::Desc::P_ON_UPPER;
      else if (newLhs == rhs)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_ON_UPPER:
      if (newLhs == rhs)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_FREE:
      if (newLhs > -infinity)
         stat = SPxBasis::Desc::P_ON_LOWER;
      break;
   case SPxBasis::Desc::P_FIXED:
      if (newLhs != rhs)
         stat = SPxBasis::Desc::P_ON_UPPER;
      break;
   case SPxBasis::Desc::D_FREE:
   case SPxBasis::Desc::D_ON_UPPER:
   case SPxBasis::Desc::D_ON_LOWER:
   case SPxBasis::Desc::D_ON_BOTH:
   case SPxBasis::Desc::D_UNDEFINED:
      stat = basis.dualRowStatus(i);
      break;
   default:
      abort();
   }
   DEBUG( std::cout << " -> " << stat << std::endl; );
}

void SoPlex::changeLhs(const Vector& newLhs)
{
   METHOD( "SoPlex::changeLhs()" );
   SPxLP::changeLhs(newLhs);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      for (int i = nRows() - 1; i >= 0; --i)
         changeLhsStatus(desc().rowStatus(i), newLhs[i], rhs(i), *this, i);
      unInit();
   }
}

void SoPlex::changeLhs(int i, Real newLhs)
{
   METHOD( "SoPlex::changeLhs()" );
   SPxLP::changeLhs(i, newLhs);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      changeLhsStatus(desc().rowStatus(i), newLhs, rhs(i), *this, i);
      unInit();
   }
}

static void changeRhsStatus
(
   SPxBasis::Desc::Status& stat,
   Real newRhs,
   Real lhs,
   const SPxBasis& basis,
   int i
)
{
   DEBUG({ std::cout << "changeRhsStatus()  : row " << i
                     << ": " << stat; });
   switch (stat)
   {
   case SPxBasis::Desc::P_ON_UPPER:
      if (newRhs >= infinity)
         stat = (lhs <= -infinity)
            ? SPxBasis::Desc::P_FREE
            : SPxBasis::Desc::P_ON_LOWER;
      else if (newRhs == lhs)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_ON_LOWER:
      if (newRhs == lhs)
         stat = SPxBasis::Desc::P_FIXED;
      break;
   case SPxBasis::Desc::P_FREE:
      if (newRhs < infinity)
         stat = SPxBasis::Desc::P_ON_UPPER;
      break;
   case SPxBasis::Desc::P_FIXED:
      if (newRhs != lhs)
         stat = SPxBasis::Desc::P_ON_LOWER;
      break;
   case SPxBasis::Desc::D_FREE:
   case SPxBasis::Desc::D_ON_UPPER:
   case SPxBasis::Desc::D_ON_LOWER:
   case SPxBasis::Desc::D_ON_BOTH:
   case SPxBasis::Desc::D_UNDEFINED:
      stat = basis.dualRowStatus(i);
      break;
   default:
      abort();
   }
   DEBUG( std::cout << " -> " << stat << std::endl; );
}


void SoPlex::changeRhs(const Vector& newRhs)
{
   METHOD( "SoPlex::changeRhs()" );
   SPxLP::changeRhs(newRhs);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      for (int i = nRows() - 1; i >= 0; --i)
         changeRhsStatus(desc().rowStatus(i), newRhs[i], lhs(i), *this, i);
      unInit();
   }
}

void SoPlex::changeRhs(int i, Real newRhs)
{
   METHOD( "SoPlex::changeRhs()" );
   SPxLP::changeRhs(i, newRhs);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      changeRhsStatus(desc().rowStatus(i), newRhs, lhs(i), *this, i);
      unInit();
   }
}

void SoPlex::changeRange(const Vector& newLhs, const Vector& newRhs)
{
   METHOD( "SoPlex::changeRange()" );
   SPxLP::changeLhs(newLhs);
   SPxLP::changeRhs(newRhs);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      for (int i = nRows() - 1; i >= 0; --i)
      {
         changeLhsStatus(desc().rowStatus(i), newLhs[i], rhs(i), *this, i);
         changeRhsStatus(desc().rowStatus(i), newRhs[i], lhs(i), *this, i);
      }
      unInit();
   }
}

void SoPlex::changeRange(int i, Real newLhs, Real newRhs)
{
   METHOD( "SoPlex::changeRange()" );
   SPxLP::changeLhs(i, newLhs);
   SPxLP::changeRhs(i, newRhs);
   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      changeLhsStatus(desc().rowStatus(i), newLhs, rhs(i), *this, i);
      changeRhsStatus(desc().rowStatus(i), newRhs, lhs(i), *this, i);
      unInit();
   }
}

void SoPlex::changeRow(int i, const LPRow& newRow)
{
   METHOD( "SoPlex::changeRow()" );
   SPxLP::changeRow(i, newRow);
   unInit();
}

void SoPlex::changeCol(int i, const LPCol& newCol)
{
   METHOD( "SoPlex::changeCol()" );
   SPxLP::changeCol(i, newCol);
   unInit();
}

void SoPlex::changeElement(int i, int j, Real val)
{
   METHOD( "SoPlex::changeElement()" );
   SPxLP::changeElement(i, j, val);
   unInit();
}

void SoPlex::changeSense(SPxSense sns)
{
   METHOD( "SoPlex::changeSense()" );
   SPxLP::changeSense(sns);
   unInit();
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
