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
#pragma ident "@(#) $Id: spxbounds.cpp,v 1.12 2002/04/05 16:13:16 bzfkocht Exp $"
#endif

//#define DEBUGGING 1


/*  Import system include files
 */
#include <assert.h>
#include <iostream>

/*  and class header files
 */
#include "spxdefines.h"
#include "soplex.h"

namespace soplex
{
/*      \SubSection{Bounds}
 
    Setting up the feasiblity bound for normal primal variables is
    straightforward. However, slack variables need some more details on how we
    treate them. This is slightly different from usual textbook versions. Let
    $l_i \le A_i^T x \le u_i$. This can be transformed to $A_i^Tx + s_i = 0$,
    with $-u_i \le s_i \le -l_i$. Hence, with this definition of slack variables
    $s_i$, we can directly use vectors $l$ and $u$ as feasibility bounds.
 */
void SoPlex::setPrimalBounds()
{
   METHOD( "SoPlex::setPrimalBounds()" );
   theUCbound = SPxLP::upper();
   theLCbound = SPxLP::lower();
   if (rep() == ROW)
   {
      theURbound = rhs();
      theLRbound = lhs();
   }
   else
   {
      theURbound = lhs();
      theLRbound = rhs();
      theURbound *= -1;
      theLRbound *= -1;
   }
}

/*
    Seting up the basis for dual simplex requires to install upper and lower
    feasibility bounds for dual variables (|Lbound| and |Ubound|). Here is a
    list of how these must be set for inequalities of type $l \le a^Tx \le u$:
 
    \centerline{
        \begin{tabular}{cccc}
            $l$         &       $u$     & |Lbound|      & |Ubound|      \\
        \hline
        $-\infty=l$     & $u=\infty$    &       0       &       0       \\
        $-\infty<l$     & $u=\infty$    &       0       & $\infty$      \\
        $-\infty=l$     & $u<\infty$    & $-\infty$     &       0       \\
        \multicolumn{2}{c}{
        $-\infty<l \ne u<\infty$}       &       0       &       0       \\
        \multicolumn{2}{c}{
        $-\infty<l  =  u<\infty$}       & $-\infty$     & $\infty$      \\
        \end{tabular}
    }
 
    The case $l = -\infty$, $u = \infty$ occurs for unbounded primal variables.
    Such must be treated differently from the general case.
 
    Given possible upper and lower bounds to a dual variable with |Status stat|,
    this function clears the bounds according to |stat| by setting them to
    $\infty$ or $-\infty$, respectively.
 */
void SoPlex::clearDualBounds
(
   SPxBasis::Desc::Status stat,
   Real& upp,
   Real& lw
)
{
   METHOD( "SoPlex::clearDualBounds()" );
   switch (stat)
   {
   case SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER :
   case SPxBasis::Desc::D_FREE :
      upp = infinity;
      lw = -infinity;
      break;
   case SPxBasis::Desc::P_ON_UPPER :
   case SPxBasis::Desc::D_ON_LOWER :
      upp = infinity;
      break;
   case SPxBasis::Desc::P_ON_LOWER :
   case SPxBasis::Desc::D_ON_UPPER :
      lw = -infinity;
      break;

   default:
      break;
   }
}

void SoPlex::setDualColBounds()
{
   METHOD( "SoPlex::setDualColBounds()" );
   assert(rep() == COLUMN);
   int i;
   const SPxBasis::Desc& ds = desc();

   for (i = nRows() - 1; i >= 0; --i)
   {
      theURbound[i] = theLRbound[i] = 0;
      clearDualBounds(ds.rowStatus(i), theURbound[i], theLRbound[i]);
   }

   for (i = nCols() - 1; i >= 0; --i)
   {
      theUCbound[i] = theLCbound[i] = -maxObj(i);
      clearDualBounds(ds.colStatus(i),
                       theLCbound[i],               // exchanged ...
                       theUCbound[i]               // ... due to definition of slacks!
                    );
      theUCbound[i] *= -1;
      theLCbound[i] *= -1;
   }
}

void SoPlex::setDualRowBounds()
{
   METHOD( "SoPlex::setDualRowBounds()" );
   assert(rep() == ROW);

   int i;

   for (i = nRows() - 1; i >= 0; --i)
   {
      theURbound[i] = theLRbound[i] = 0;
      clearDualBounds(dualRowStatus(i), theURbound[i], theLRbound[i]);
   }

   for (i = nCols() - 1; i >= 0; --i)
   {
      theUCbound[i] = theLCbound[i] = 0;
      clearDualBounds(dualColStatus(i), theUCbound[i], theLCbound[i]);
   }
}


/*
    This sets up the bounds for basic variables for entering simplex algorithms.
    It requires, that all upper lower feasibility bounds have allready been
    setup. Method |setEnterBound4Row(i, n)| does so for the |i|-th basis
    variable being row index |n|. Equivalently, method
    |setEnterBound4Col(i, n)| does so for the |i|-th basis variable being
    column index |n|.
 */
void SoPlex::setEnterBound4Row(int i, int n)
{
   METHOD( "SoPlex::setEnterBound4Row()" );
   assert(baseId(i).isSPxRowId());
   assert(number(SPxRowId(baseId(i))) == n);
   switch (desc().rowStatus(n))
   {
   case SPxBasis::Desc::P_ON_LOWER :
      theLBbound[i] = -infinity;
      theUBbound[i] = theURbound[n];
      break;
   case SPxBasis::Desc::P_ON_UPPER :
      theLBbound[i] = theLRbound[n];
      theUBbound[i] = infinity;
      break;

   default:
      theUBbound[i] = theURbound[n];
      theLBbound[i] = theLRbound[n];
      break;
   }
}

void SoPlex::setEnterBound4Col(int i, int n)
{
   METHOD( "SoPlex::setEnterBound4Col()" );
   assert(baseId(i).isSPxColId());
   assert(number(SPxColId(baseId(i))) == n);
   switch (desc().colStatus(n))
   {
   case SPxBasis::Desc::P_ON_LOWER :
      theLBbound[i] = -infinity;
      theUBbound[i] = theUCbound[n];
      break;
   case SPxBasis::Desc::P_ON_UPPER :
      theLBbound[i] = theLCbound[n];
      theUBbound[i] = infinity;
      break;

   default:
      theUBbound[i] = theUCbound[n];
      theLBbound[i] = theLCbound[n];
      break;
   }
}

void SoPlex::setEnterBounds()
{
   METHOD( "SoPlex::setEnterBounds()" );
   int i;

   for (i = dim() - 1; i >= 0; --i)
   {
      SPxId l_id = baseId(i);
      if (l_id.isSPxRowId())
         setEnterBound4Row(i, number(SPxRowId(l_id)));
      else
         setEnterBound4Col(i, number(SPxColId(l_id)));
   }
}


/*
    This sets up the bounds for basic variables for leaving simplex algorithms.
    While method |setLeaveBound4Row(i,n)| does so for the |i|-th basic variable
    being the |n|-th row, |setLeaveBound4Col(i,n)| does so for the |i|-th basic
    variable being the |n|-th column.
 */
void SoPlex::setLeaveBound4Row(int i, int n)
{
   METHOD( "SoPlex::setLeaveBound4Row()" );
   assert(baseId(i).isSPxRowId());
   assert(number(SPxRowId(baseId(i))) == n);
   switch (desc().rowStatus(n))
   {
   case SPxBasis::Desc::P_ON_LOWER :
      theLBbound[i] = -infinity;
      theUBbound[i] = 0;
      break;
   case SPxBasis::Desc::P_ON_UPPER :
      theLBbound[i] = 0;
      theUBbound[i] = infinity;
      break;
   case SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER :
      theLBbound[i] = -infinity;
      theUBbound[i] = infinity;
      break;
   case SPxBasis::Desc::P_FREE :
      theLBbound[i] = theUBbound[i] = 0;
      break;

   default:
      assert(rep() == COLUMN);
      theLBbound[i] = -rhs(n);                // slacks !
      theUBbound[i] = -lhs(n);                // slacks !
      break;
   }
}

void SoPlex::setLeaveBound4Col(int i, int n)
{
   METHOD( "SoPlex::setLeaveBound4Col()" );
   assert(baseId(i).isSPxColId());
   assert(number(SPxColId(baseId(i))) == n);
   switch (desc().colStatus(n))
   {
   case SPxBasis::Desc::P_ON_LOWER :
      theLBbound[i] = -infinity;
      theUBbound[i] = 0;
      break;
   case SPxBasis::Desc::P_ON_UPPER :
      theLBbound[i] = 0;
      theUBbound[i] = infinity;
      break;
   case SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER :
      theLBbound[i] = -infinity;
      theUBbound[i] = infinity;
      break;
   case SPxBasis::Desc::P_FREE :
      theLBbound[i] = theUBbound[i] = 0;
      break;

   default:
      theUBbound[i] = SPxLP::upper(n);
      theLBbound[i] = SPxLP::lower(n);
      break;
   }
}

void SoPlex::setLeaveBounds()
{
   METHOD( "SoPlex::setLeaveBounds()" );
   int i;

   for (i = dim() - 1; i >= 0; --i)
   {
      SPxId l_id = baseId(i);
      if (l_id.isSPxRowId())
         setLeaveBound4Row(i, number(SPxRowId(l_id)));
      else
         setLeaveBound4Col(i, number(SPxColId(l_id)));
   }
}

void SoPlex::testBounds() const
{
   METHOD( "SoPlex::testBounds()" );
   Real l_max = (1 + iterCount) * delta();
   int i;

   if (type() == ENTER)
   {
      for( i = 0; i < dim(); ++i )
      {
         if ((*theFvec)[i] > theUBbound[i] + l_max
              //@ &&  theUBbound[i] != theLBbound[i])
           )
            std::cerr << i << ": invalid upper enter bound found ...\n";
         if ((*theFvec)[i] < theLBbound[i] - l_max
              //@ &&  theUBbound[i] != theLBbound[i])
           )
            std::cerr << i << ": invalid lower enter bound found ...\n";
      }
   }
   else
   {
      for( i = 0; i < dim(); ++i )
      {
         if ((*theCoPvec)[i] > (*theCoUbound)[i] + l_max
              //@ &&  (*theCoUbound)[i] != (*theCoLbound)[i])
           )
            std::cerr << i << ": invalid upper cobound found ...\n";
         if ((*theCoPvec)[i] < (*theCoLbound)[i] - l_max
              //@ &&  (*theCoUbound)[i] != (*theCoLbound)[i])
           )
            std::cerr << i << ": invalid lower cobound found ...\n";
      }
      for( i = 0; i < coDim(); ++i )
      {
         if ((*thePvec)[i] > (*theUbound)[i] + l_max
              //@ &&  (*theUbound)[i] != (*theLbound)[i])
           )
            std::cerr << i << ": invalid upper bound found ...\n";
         if ((*thePvec)[i] < (*theLbound)[i] - l_max
              //@ &&  (*theUbound)[i] != (*theLbound)[i])
           )
            std::cerr << i << ": invalid lower bound found ...\n";
      }
   }
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
