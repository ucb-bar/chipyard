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
#pragma ident "@(#) $Id: enter.cpp,v 1.16 2002/03/21 16:06:17 bzfkocht Exp $"
#endif

// #define DEBUGGING 1

/*      \SubSection{Updating the Basis for Entering Variables}
 */
#include <assert.h>

#include "spxdefines.h"
#include "soplex.h"
#include "spxratiotester.h"

namespace soplex
{

/*
In the entering simplex algorithms (i.e. iteratively a vector is selected to
{\em enter} the simplex basis as in the dual rowwise and primal columnwise case)
let $A$ denote the current basis, $x$ and entering vector and $f$ the
feasibility vector. For a feasible basis $l \le f \le u$ holds.  For the rowwise
case $f$ is obtained by solving $f^T = c^T A^{-1}$, wherease in columnwisecase
$f = A^{-1} b$.
 
Let us further consider the rowwise case. Exchanging $x$ with the $i$-th
vector of $A$ yields
\begin{equation}\label{update.eq}
    A^{(i)} = E_i A \hbox{, with } E_i = I + e_i (x^T A^{-1} - e_i^T).
\end{equation}
With $E_i^{-1} = I + e_i \frac{e_i^T - \delta^T}{\delta}$, $\delta^T = x^T A^{-1}$ one
gets the new feasibility vector
\begin{eqnarray*}
        (f^{(i)})^T
    &=& c^T (A^{(i)})^{-1}      \\
    &=& c^T A^{-1} + c^T A^{-1} e_i \frac{e_i^T - \delta^T}{\delta_i} \\
    &=& f^T + \frac{f_i}{\delta_i} e_i^T - \frac{f_i}{\delta_i} \delta^T.       \\
\end{eqnarray*}
The selection of the leaving vector $i^*$ for the basis must ensure, that for
all $j \ne i^*$ $f^{(i^*)}_j$ remains within its bounds $l_j$ and $u_j$.
 */


/*
    Testing all values of |pVec| against its bounds. If $i$, say, is violated
    the violation is saved as negative value in |theTest[i]|.
 */
Real SoPlex::test(int i, SPxBasis::Desc::Status stat) const
{
   METHOD( "SoPlex::test()" );
   assert(type() == ENTER);
   assert(!isBasic(stat));

   Real x;

   switch (stat)
   {
   case SPxBasis::Desc::D_FREE:
   case SPxBasis::Desc::D_ON_BOTH:
      assert(rep() == ROW);
      x = (*thePvec)[i] - lhs(i);
      if (x < 0)
         return x;
      // no break: next is else case
   case SPxBasis::Desc::D_ON_LOWER:
      assert(rep() == ROW);
      return rhs(i) - (*thePvec)[i];
   case SPxBasis::Desc::D_ON_UPPER:
      assert(rep() == ROW);
      return (*thePvec)[i] - lhs(i);

   case SPxBasis::Desc::P_ON_UPPER:
      assert(rep() == COLUMN);
      return maxObj(i) - (*thePvec)[i];
   case SPxBasis::Desc::P_ON_LOWER:
      assert(rep() == COLUMN);
      return (*thePvec)[i] - maxObj(i);
   case SPxBasis::Desc::P_FREE :
      x = maxObj(i) - (*thePvec)[i];
      return (x < 0) ? x : -x;

   default:
      return 0;
   }
}

void SoPlex::computeTest()
{
   METHOD( "SoPlex::computeTest()" );
   int i;
   const SPxBasis::Desc& ds = desc();

   for (i = coDim() - 1; i >= 0; --i)
   {
      SPxBasis::Desc::Status stat = ds.status(i);
      if (isBasic(stat))
         theTest[i] = 0;
      else
         theTest[i] = test(i, stat);
   }
}

Real SoPlex::computePvec(int i)
{
   METHOD( "SoPlex::computePvec()" );
   return (*thePvec)[i] = vector(i) * (*theCoPvec);
}

Real SoPlex::computeTest(int i)
{
   METHOD( "SoPlex::computeTest()" );
   SPxBasis::Desc::Status stat = desc().status(i);
   if (isBasic(stat))
      return theTest[i] = 0;
   else
      return theTest[i] = test(i, stat);
}

/*
    Testing all values of #coPvec# against its bounds. If $i$, say, is violated
    the violation is saved as negative value in |theCoTest[i]|.
 */
Real SoPlex::coTest(int i, SPxBasis::Desc::Status stat) const
{
   METHOD( "SoPlex::coTest()" );
   assert(type() == ENTER);
   assert(!isBasic(stat));

   Real x;

   switch (stat)
   {
   case SPxBasis::Desc::D_FREE:
   case SPxBasis::Desc::D_ON_BOTH :
      assert(rep() == ROW);
      x = (*theCoPvec)[i] - SPxLP::lower(i);
      if (x < 0)
         return x;
      // no break: next is else case
   case SPxBasis::Desc::D_ON_LOWER:
      assert(rep() == ROW);
      return SPxLP::upper(i) - (*theCoPvec)[i];
   case SPxBasis::Desc::D_ON_UPPER:
      assert(rep() == ROW);
      return (*theCoPvec)[i] - SPxLP::lower(i);

   case SPxBasis::Desc::P_ON_UPPER:
      assert(rep() == COLUMN);
      return (*theCoPvec)[i] - 0;             // slacks !
   case SPxBasis::Desc::P_ON_LOWER:
      assert(rep() == COLUMN);
      return 0 - (*theCoPvec)[i];             // slacks !

   default:
      return 0;
   }
}

void SoPlex::computeCoTest()
{
   METHOD( "SoPlex::computeCoTest()" );
   int i;
   const SPxBasis::Desc& ds = desc();

   for (i = dim() - 1; i >= 0; --i)
   {
      SPxBasis::Desc::Status stat = ds.coStatus(i);
      if (isBasic(stat))
         theCoTest[i] = 0;
      else
         theCoTest[i] = coTest(i, stat);
   }
}


/*
    The following methods require propersy initialized vectors |fVec| and
    #coPvec#.
 */
void SoPlex::updateTest()
{
   METHOD( "SoPlex::updateTest()" );
   thePvec->delta().setup();

   const IdxSet& idx = thePvec->idx();
   const SPxBasis::Desc& ds = desc();

   int i;
   for (i = idx.size() - 1; i >= 0; --i)
   {
      int j = idx.index(i);
      SPxBasis::Desc::Status stat = ds.status(j);
      if (!isBasic(stat))
         theTest[j] = test(j, stat);
      else
         theTest[j] = 0;
   }
}

void SoPlex::updateCoTest()
{
   METHOD( "SoPlex::updateCoTest()" );
   theCoPvec->delta().setup();

   const IdxSet& idx = theCoPvec->idx();
   const SPxBasis::Desc& ds = desc();

   int i;
   for (i = idx.size() - 1; i >= 0; --i)
   {
      int j = idx.index(i);
      SPxBasis::Desc::Status stat = ds.coStatus(j);
      if (!isBasic(stat))
         theCoTest[j] = coTest(j, stat);
      else
         theCoTest[j] = 0;
   }
}



/*  \Section{Compute statistics on entering variable}
    Here is a list of variables relevant when including |Id| to the basis.
    They are computed by |computeEnterStats()|.
 */
void SoPlex::getEnterVals
(
   SPxId enterId,
   Real& enterTest,
   Real& enterUB,
   Real& enterLB,
   Real& enterVal,
   Real& enterMax,
   Real& enterPric,
   SPxBasis::Desc::Status& enterStat,
   Real& enterRO
)
{
   METHOD( "SoPlex::getEnterVals()" );
   int enterIdx;
   SPxBasis::Desc& ds = desc();

   if (enterId.isSPxColId())
   {
      enterIdx = number(SPxColId(enterId));
      enterStat = ds.colStatus(enterIdx);
      assert(!isBasic(enterStat));

      /*      For an #Id# to enter the basis we better recompute the Test value.
       */
      if (rep() == COLUMN)
      {
         computePvec(enterIdx);
         enterTest = computeTest(enterIdx);
         theTest[enterIdx] = 0;
      }
      else
      {
         enterTest = coTest()[enterIdx];
         theCoTest[enterIdx] = 0;
      }

      switch (enterStat)
      {
         // primal/columnwise cases:
      case SPxBasis::Desc::P_ON_UPPER :
         assert( rep() == COLUMN );
         enterUB = theUCbound[enterIdx];
         enterLB = theLCbound[enterIdx];
         enterVal = enterUB;
         enterMax = enterLB - enterUB;
         enterPric = (*thePvec)[enterIdx];
         enterRO = maxObj(enterIdx);
         if( enterLB <= -infinity )
            ds.colStatus(enterIdx) = SPxBasis::Desc::D_ON_LOWER;
         else if( EQ( enterLB, enterUB ) )
            ds.colStatus(enterIdx) = SPxBasis::Desc::D_FREE;
         else
            ds.colStatus(enterIdx) = SPxBasis::Desc::D_ON_BOTH;
         break;
      case SPxBasis::Desc::P_ON_LOWER :
         assert( rep() == COLUMN );
         enterUB = theUCbound[enterIdx];
         enterLB = theLCbound[enterIdx];
         enterVal = enterLB;
         enterMax = enterUB - enterLB;
         enterPric = (*thePvec)[enterIdx];
         enterRO = maxObj(enterIdx);
         if( enterUB >= infinity )
            ds.colStatus(enterIdx) = SPxBasis::Desc::D_ON_UPPER;
         else if( EQ( enterLB, enterUB ) )
            ds.colStatus(enterIdx) = SPxBasis::Desc::D_FREE;
         else
            ds.colStatus(enterIdx) = SPxBasis::Desc::D_ON_BOTH;
         break;
      case SPxBasis::Desc::P_FREE :
         assert( rep() == COLUMN );
         enterUB = theUCbound[enterIdx];
         enterLB = theLCbound[enterIdx];
         enterVal = 0;
         enterPric = (*thePvec)[enterIdx];
         enterRO = maxObj(enterIdx);
         ds.colStatus(enterIdx) = SPxBasis::Desc::D_UNDEFINED;
         enterMax = (enterRO > 0) ? infinity : -infinity;
         break;

         // dual/rowwise cases:
      case SPxBasis::Desc::D_ON_UPPER :
         assert( rep() == ROW );
         assert(theUCbound[enterIdx] < infinity);
         enterUB = theUCbound[enterIdx];
         enterLB = -infinity;
         enterMax = -infinity;
         enterVal = enterUB;
         enterPric = (*theCoPvec)[enterIdx];
         enterRO = SPxLP::lower(enterIdx);
         ds.colStatus(enterIdx) = SPxBasis::Desc::P_ON_LOWER;
         break;
      case SPxBasis::Desc::D_ON_LOWER :
         assert( rep() == ROW );
         assert(theLCbound[enterIdx] > -infinity);
         enterLB = theLCbound[enterIdx];
         enterUB = infinity;
         enterMax = infinity;
         enterVal = enterLB;
         enterPric = (*theCoPvec)[enterIdx];
         enterRO = SPxLP::upper(enterIdx);
         ds.colStatus(enterIdx) = SPxBasis::Desc::P_ON_UPPER;
         break;
      case SPxBasis::Desc::D_FREE:
         assert( rep() == ROW );
         assert(SPxLP::lower(enterIdx) == SPxLP::upper(enterIdx));
         enterUB = infinity;
         enterLB = -infinity;
         enterVal = 0;
         enterRO = SPxLP::upper(enterIdx);
         enterPric = (*theCoPvec)[enterIdx];
         if (enterPric > enterRO)
            enterMax = infinity;
         else
            enterMax = -infinity;
         ds.colStatus(enterIdx) = SPxBasis::Desc::P_FIXED;
         break;
      case SPxBasis::Desc::D_ON_BOTH :
         assert( rep() == ROW );
         enterPric = (*theCoPvec)[enterIdx];
         if (enterPric > SPxLP::upper(enterIdx))
         {
            enterLB = theLCbound[enterIdx];
            enterUB = infinity;
            enterMax = infinity;
            enterVal = enterLB;
            enterRO = SPxLP::upper(enterIdx);
            ds.colStatus(enterIdx) = SPxBasis::Desc::P_ON_UPPER;
         }
         else
         {
            enterUB = theUCbound[enterIdx];
            enterVal = enterUB;
            enterRO = SPxLP::lower(enterIdx);
            enterLB = -infinity;
            enterMax = -infinity;
            ds.colStatus(enterIdx) = SPxBasis::Desc::P_ON_LOWER;
         }
         break;
      default:
         abort();
      }
      DEBUG({ std::cout << "SoPlex::getEnterVals() : col " << enterIdx
                        << ": " << enterStat
                        << " -> " << ds.colStatus(enterIdx)
                        << std::endl; });
   }

   else
   {
      assert(enterId.isSPxRowId());
      enterIdx = number(SPxRowId(enterId));
      enterStat = ds.rowStatus(enterIdx);
      assert(!isBasic(enterStat));

      /*      For an #Id# to enter the basis we better recompute the Test value.
       */
      if (rep() == ROW)
      {
         computePvec(enterIdx);
         enterTest = computeTest(enterIdx);
         theTest[enterIdx] = 0;
      }
      else
      {
         enterTest = coTest()[enterIdx];
         theCoTest[enterIdx] = 0;
      }

      switch (enterStat)
      {
         // primal/columnwise cases:
      case SPxBasis::Desc::P_ON_UPPER :
         assert( rep() == COLUMN );
         enterUB = theURbound[enterIdx];
         enterLB = theLRbound[enterIdx];
         enterVal = enterLB;
         enterMax = enterUB - enterLB;
         enterPric = (*theCoPvec)[enterIdx];
         enterRO = 0;
         if( enterUB >= infinity )
            ds.rowStatus(enterIdx) = SPxBasis::Desc::D_ON_LOWER;
         else if( EQ( enterLB, enterUB ) )
            ds.rowStatus(enterIdx) = SPxBasis::Desc::D_FREE;
         else
            ds.rowStatus(enterIdx) = SPxBasis::Desc::D_ON_BOTH;
         break;
      case SPxBasis::Desc::P_ON_LOWER :
         assert( rep() == COLUMN );
         enterUB = theURbound[enterIdx];
         enterLB = theLRbound[enterIdx];
         enterVal = enterUB;
         enterMax = enterLB - enterUB;
         enterPric = (*theCoPvec)[enterIdx];
         enterRO = 0;
         if( enterLB <= -infinity )
            ds.rowStatus(enterIdx) = SPxBasis::Desc::D_ON_UPPER;
         else if( EQ( enterLB, enterUB ) )
            ds.rowStatus(enterIdx) = SPxBasis::Desc::D_FREE;
         else
            ds.rowStatus(enterIdx) = SPxBasis::Desc::D_ON_BOTH;
         break;
      case SPxBasis::Desc::P_FREE :
         assert( rep() == COLUMN );
#if 1
         abort();
#else
         std::cerr << __FILE__ << __LINE__ << "ERROR: not yet debugged!\n";
         enterPric = (*theCoPvec)[enterIdx];
         enterRO = 0;
         ds.rowStatus(enterIdx) = SPxBasis::Desc::D_UNDEFINED;
#endif
         break;

         // dual/rowwise cases:
      case SPxBasis::Desc::D_ON_UPPER :
         assert( rep() == ROW );
         assert(theURbound[enterIdx] < infinity);
         enterUB = theURbound[enterIdx];
         enterLB = -infinity;
         enterVal = enterUB;
         enterMax = -infinity;
         enterPric = (*thePvec)[enterIdx];
         enterRO = lhs(enterIdx);
         ds.rowStatus(enterIdx) = SPxBasis::Desc::P_ON_LOWER;
         break;
      case SPxBasis::Desc::D_ON_LOWER :
         assert( rep() == ROW );
         assert(theLRbound[enterIdx] > -infinity);
         enterLB = theLRbound[enterIdx];
         enterUB = infinity;
         enterVal = enterLB;
         enterMax = infinity;
         enterPric = (*thePvec)[enterIdx];
         enterRO = rhs(enterIdx);
         ds.rowStatus(enterIdx) = SPxBasis::Desc::P_ON_UPPER;
         break;
      case SPxBasis::Desc::D_FREE:
         assert( rep() == ROW );
         assert(rhs(enterIdx) == lhs(enterIdx));
         enterUB = infinity;
         enterLB = -infinity;
         enterVal = 0;
         enterPric = (*thePvec)[enterIdx];
         enterRO = rhs(enterIdx);
         enterMax = (enterPric > enterRO) ? infinity : -infinity;
         ds.rowStatus(enterIdx) = SPxBasis::Desc::P_FIXED;
         break;
      case SPxBasis::Desc::D_ON_BOTH :
         assert( rep() == ROW );
         enterPric = (*thePvec)[enterIdx];
         if (enterPric > rhs(enterIdx))
         {
            enterLB = theLRbound[enterIdx];
            enterVal = enterLB;
            enterUB = infinity;
            enterMax = infinity;
            enterRO = rhs(enterIdx);
            ds.rowStatus(enterIdx) = SPxBasis::Desc::P_ON_UPPER;
         }
         else
         {
            enterUB = theURbound[enterIdx];
            enterVal = enterUB;
            enterLB = -infinity;
            enterMax = -infinity;
            enterRO = lhs(enterIdx);
            ds.rowStatus(enterIdx) = SPxBasis::Desc::P_ON_LOWER;
         }
         break;

      default:
         abort();
      }
      DEBUG({ std::cout << "SoPlex::getEnterVals() : row " << enterIdx
                        << ": " << enterStat
                        << " -> " << ds.rowStatus(enterIdx)
                        << std::endl; });
   }
}

/*      process leaving variable
 */
void SoPlex::getEnterVals2
(
   int leaveIdx,
   Real enterMax,
   Real& leavebound
)
{
   METHOD( "SoPlex::getEnterVals2()" );
   int idx;
   SPxBasis::Desc& ds = desc();
   SPxId leftId = baseId(leaveIdx);

   if (leftId.isSPxRowId())
   {
      idx = number(SPxRowId(leftId));
      SPxBasis::Desc::Status leaveStat = ds.rowStatus(idx);

      switch (leaveStat)
      {
      case SPxBasis::Desc::P_FIXED :
         assert(rep() == ROW);
         abort();
      case SPxBasis::Desc::P_ON_UPPER :
         assert(rep() == ROW);
         leavebound = theLBbound[leaveIdx];
         theLRbound[idx] = leavebound;
         ds.rowStatus(idx) = dualRowStatus(idx);
         break;
      case SPxBasis::Desc::P_ON_LOWER :
         assert(rep() == ROW);
         leavebound = theUBbound[leaveIdx];
         theURbound[idx] = leavebound;
         ds.rowStatus(idx) = dualRowStatus(idx);
         break;
      case SPxBasis::Desc::P_FREE :
         assert(rep() == ROW);
#if 1
         abort();
#else
         std::cerr << __FILE__ << __LINE__ << "ERROR: not yet debugged!\n";
         if ((*theCoPvec)[leaveIdx] - theLBbound[leaveIdx] <
              theUBbound[leaveIdx] - (*theCoPvec)[leaveIdx])
         {
            leavebound = theLBbound[leaveIdx];
            theLRbound[idx] = leavebound;
         }
         else
         {
            leavebound = theUBbound[leaveIdx];
            theURbound[idx] = leavebound;
         }
         ds.rowStatus(idx) = SPxBasis::Desc::D_UNDEFINED;
         break;
#endif

         // primal/columnwise cases:
      case SPxBasis::Desc::D_UNDEFINED :
         assert(rep() == COLUMN);
         abort();
      case SPxBasis::Desc::D_FREE :
         assert(rep() == COLUMN);
         if (theFvec->delta()[leaveIdx] * enterMax < 0)
            leavebound = theUBbound[leaveIdx];
         else
            leavebound = theLBbound[leaveIdx];
         theLRbound[idx] = leavebound;
         theURbound[idx] = leavebound;
         ds.rowStatus(idx) = SPxBasis::Desc::P_FIXED;
         break;
      case SPxBasis::Desc::D_ON_UPPER :
         assert(rep() == COLUMN);
         leavebound = theUBbound[leaveIdx];
         theURbound[idx] = leavebound;
         ds.rowStatus(idx) = SPxBasis::Desc::P_ON_LOWER;
         break;
      case SPxBasis::Desc::D_ON_LOWER :
         assert(rep() == COLUMN);
         leavebound = theLBbound[leaveIdx];
         theLRbound[idx] = leavebound;
         ds.rowStatus(idx) = SPxBasis::Desc::P_ON_UPPER;
         break;
      case SPxBasis::Desc::D_ON_BOTH :
         assert(rep() == COLUMN);
         if (enterMax * theFvec->delta()[leaveIdx] < 0)
         {
            leavebound = theUBbound[leaveIdx];
            theURbound[idx] = leavebound;
            ds.rowStatus(idx) = SPxBasis::Desc::P_ON_LOWER;
         }
         else
         {
            leavebound = theLBbound[leaveIdx];
            theLRbound[idx] = leavebound;
            ds.rowStatus(idx) = SPxBasis::Desc::P_ON_UPPER;
         }
         break;

      default:
         abort();
      }
      DEBUG({ std::cout << "SoPlex::getEnterVals2(): row " << idx
                        << ": " << leaveStat
                        << " -> " << ds.rowStatus(idx)
                        << std::endl; });
   }

   else
   {
      assert(leftId.isSPxColId());
      idx = number(SPxColId(leftId));
      SPxBasis::Desc::Status leaveStat = ds.colStatus(idx);

      switch (leaveStat)
      {
      case SPxBasis::Desc::P_ON_UPPER :
         assert(rep() == ROW);
         leavebound = theLBbound[leaveIdx];
         theLCbound[idx] = leavebound;
         ds.colStatus(idx) = dualColStatus(idx);
         break;
      case SPxBasis::Desc::P_ON_LOWER :
         assert(rep() == ROW);
         leavebound = theUBbound[leaveIdx];
         theUCbound[idx] = leavebound;
         ds.colStatus(idx) = dualColStatus(idx);
         break;
      case SPxBasis::Desc::P_FREE :
         assert(rep() == ROW);
         if (theFvec->delta()[leaveIdx] * enterMax > 0)
         {
            leavebound = theLBbound[leaveIdx];
            theLCbound[idx] = leavebound;
         }
         else
         {
            leavebound = theUBbound[leaveIdx];
            theUCbound[idx] = leavebound;
         }
         ds.colStatus(idx) = SPxBasis::Desc::D_UNDEFINED;
         break;
      case SPxBasis::Desc::P_FIXED:
         assert(rep() == ROW);
         abort();

         // primal/columnwise cases:
      case SPxBasis::Desc::D_FREE :
         assert(rep() == COLUMN);
         if (theFvec->delta()[leaveIdx] * enterMax > 0)
            leavebound = theLBbound[leaveIdx];
         else
            leavebound = theUBbound[leaveIdx];
         theUCbound[idx] =
            theLCbound[idx] = leavebound;
         ds.colStatus(idx) = SPxBasis::Desc::P_FIXED;
         break;
      case SPxBasis::Desc::D_ON_UPPER :
         assert(rep() == COLUMN);
         leavebound = theLBbound[leaveIdx];
         theLCbound[idx] = leavebound;
         ds.colStatus(idx) = SPxBasis::Desc::P_ON_LOWER;
         break;
      case SPxBasis::Desc::D_ON_LOWER :
         assert(rep() == COLUMN);
         leavebound = theUBbound[leaveIdx];
         theUCbound[idx] = leavebound;
         ds.colStatus(idx) = SPxBasis::Desc::P_ON_UPPER;
         break;
      case SPxBasis::Desc::D_ON_BOTH :
      case SPxBasis::Desc::D_UNDEFINED :
         assert(rep() == COLUMN);
         if (enterMax * theFvec->delta()[leaveIdx] < 0)
         {
            leavebound = theUBbound[leaveIdx];
            theUCbound[idx] = leavebound;
            ds.colStatus(idx) = SPxBasis::Desc::P_ON_UPPER;
         }
         else
         {
            leavebound = theLBbound[leaveIdx];
            theLCbound[idx] = leavebound;
            ds.colStatus(idx) = SPxBasis::Desc::P_ON_LOWER;
         }
         break;

      default:
         abort();
      }
      DEBUG({ std::cout << "SoPlex::getEnterVals2(): col " << idx
                        << ": " << leaveStat
                        << " -> " << ds.colStatus(idx)
                        << std::endl; });
   }
}


void
SoPlex::ungetEnterVal(
   SPxId enterId,
   SPxBasis::Desc::Status enterStat,
   Real leaveVal,
   const SVector& vec
)
{
   METHOD( "SoPlex::ungetEnterVal()" );
   int enterIdx;
   SPxBasis::Desc& ds = desc();

   if (enterId.isSPxColId())
   {
      enterIdx = number(SPxColId(enterId));
      if (enterStat == SPxBasis::Desc::P_ON_UPPER)
         ds.colStatus(enterIdx) = SPxBasis::Desc::P_ON_LOWER;
      else
         ds.colStatus(enterIdx) = SPxBasis::Desc::P_ON_UPPER;
      theFrhs->multAdd(leaveVal, vec);
   }
   else
   {
      enterIdx = number(SPxRowId(enterId));
      assert(enterId.isSPxRowId());
      if (enterStat == SPxBasis::Desc::P_ON_UPPER)
         ds.rowStatus(enterIdx) = SPxBasis::Desc::P_ON_LOWER;
      else
         ds.rowStatus(enterIdx) = SPxBasis::Desc::P_ON_UPPER;
      (*theFrhs)[enterIdx] += leaveVal;
   }
   if (isId(enterId))
      theTest[enterIdx] = 0;
   else
      theCoTest[enterIdx] = 0;
}

void SoPlex::rejectEnter(
   SPxId enterId,
   Real enterTest,
   SPxBasis::Desc::Status enterStat
)
{
   METHOD( "SoPlex::rejectEnter()" );
   int enterIdx = number(enterId);
   if (isId(enterId))
   {
      theTest[enterIdx] = enterTest;
      desc().status(enterIdx) = enterStat;
   }
   else
   {
      theCoTest[enterIdx] = enterTest;
      desc().coStatus(enterIdx) = enterStat;
   }
}

int SoPlex::enter(SPxId& enterId)
{
   METHOD( "SoPlex::enter()" );
   assert(enterId.isValid());
   assert(type() == ENTER);
   assert(initialized);

   Real enterTest;      // correct test value of entering var
   Real enterUB;        // upper bound of entering variable
   Real enterLB;        // lower bound of entering variable
   Real enterVal;       // current value of entering variable
   Real enterMax;       // maximum value for entering shift
   Real enterPric;      // priced value of entering variable
   SPxBasis::Desc::Status enterStat;      // status of entering variable
   Real enterRO;        // rhs/obj of entering variable
   const SVector* enterVec = enterVector(enterId);

   getEnterVals(enterId, enterTest, enterUB, enterLB,
                enterVal, enterMax, enterPric, enterStat, enterRO);
   //@ if(enterTest > ((theShift>epsilon()) ? -delta() : -epsilon()))
   if (enterTest > -epsilon())
   {
      rejectEnter(enterId, enterTest, enterStat);
      change(-1, enterId, enterVec);
      DEBUG( std::cerr << "rejecting false enter pivot" << std::endl; );
      return 1;
   }

   /*  Before performing the actual basis update, we must determine, how this
       is to be accomplished.
    */
   if (theFvec->delta().isSetup() && theFvec->delta().size() == 0)
      SPxBasis::solve4update(theFvec->delta(), *enterVec);
#ifndef NDEBUG
   else
   {
      DVector tmp(dim());
      tmp = reinterpret_cast<DVector&>(theFvec->delta());
      multBaseWith(tmp);
      tmp -= *enterVec;
      if (tmp.length() > delta())
         std::cerr << "fVec updated error = " << tmp.length() << std::endl;
   }
#endif  // NDEBUG

   if (m_numCycle > m_maxCycle)
   {
      if (-enterMax > 0)
         perturbMaxEnter();
      else
         perturbMinEnter();
   }

   Real leaveVal = -enterMax;
   int leaveIdx = theratiotester->selectLeave(leaveVal);


   /*
       We now tried to find a variable to leave the basis. If one has been
       found, a regular basis update is to be performed.
    */
   if (leaveIdx >= 0)
   {
      if (leaveVal < delta() && leaveVal > -delta())
         m_numCycle += (theUBbound[leaveIdx] != theLBbound[leaveIdx])
                       && (enterStat != Desc::P_FREE)
                       && (enterStat != Desc::D_FREE);
      else
         m_numCycle /= 2;

      // setup for updating the copricing vector
      if (coSolveVector2)
         SPxBasis::coSolve(theCoPvec->delta(), *coSolveVector2,
                            unitVecs[leaveIdx], *coSolveVector2rhs);
      else
         SPxBasis::coSolve(theCoPvec->delta(), unitVecs[leaveIdx]);

      (*theCoPrhs)[leaveIdx] = enterRO;
      theCoPvec->value() = (enterRO - enterPric)
                           / theFvec->delta()[leaveIdx];

      if (theCoPvec->value() > epsilon() || theCoPvec->value() < -epsilon())
      {
         if (pricing() == FULL)
         {
            thePvec->value() = theCoPvec->value();
            setupPupdate();
         }
         doPupdate();
      }
      assert(thePvec->isConsistent());
      assert(theCoPvec->isConsistent());

      Real leavebound;             // bound on which leaving variable moves
      getEnterVals2(leaveIdx, enterMax, leavebound);

      //  process entering variable
      theUBbound[leaveIdx] = enterUB;
      theLBbound[leaveIdx] = enterLB;

      //  compute tests:
      updateCoTest();
      if (pricing() == FULL)
         updateTest();


      // update feasibility vectors
      theFvec->value() = leaveVal;
      theFvec->update();
      (*theFvec)[leaveIdx] = enterVal - leaveVal;

      if (leavebound > epsilon() || leavebound < -epsilon())
         theFrhs->multAdd(-leavebound, baseVec(leaveIdx));

      if (enterVal > epsilon() || enterVal < -epsilon())
         theFrhs->multAdd(enterVal, *enterVec);


      //  change basis matrix
      change(leaveIdx, enterId, enterVec, &(theFvec->delta()));
   }


   /*  No leaving vector could be found that would yield a stable pivot step.
    */
   else if (leaveVal != -enterMax)
   {
      rejectEnter(enterId, 0.01*enterTest - 2*delta(), enterStat);
      change(-1, enterId, enterVec);
   }

   /*
       No leaving vector has been selected from the basis. However, if the
       shift amount for |fVec| is bounded, we are in the case, that the
       entering variable is moved from one bound to its other, before any of
       the basis feasibility variables reaches their bound. This may only
       happen in primal/columnwise case with upper and lower bounds on
       variables.
    */
   else if (leaveVal < infinity && leaveVal > -infinity)
   {
      assert(rep() == COLUMN);
      assert(leaveVal == -enterMax);

      change(-1, enterId, enterVec);

      theFvec->value() = leaveVal;
      theFvec->update();

      ungetEnterVal(enterId, enterStat, leaveVal, *enterVec);
   }

   /*
       No variable could be selected to leave the basis and even the entering
       variable is unbounded --- this is a failiour.
    */
   else
   {
      /* The following line originally was in the "lastUpdate() > 1" case;
         we need it in the INFEASIBLE/UNBOUNDED case, too, to have the
         basis descriptor at the correct size. */
      rejectEnter(enterId, enterTest, enterStat);
      if (lastUpdate() > 1)
      {
         std::cout << "AHA!!" << std::endl;
         factorize();
         return enter(enterId);
      }

      SPxId none;
      change(-1, none, 0);
      if (rep() != COLUMN)
         setStatus(SPxBasis::INFEASIBLE);
      else
         setStatus(SPxBasis::UNBOUNDED);
      return 0;
   }

   return 1;
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
