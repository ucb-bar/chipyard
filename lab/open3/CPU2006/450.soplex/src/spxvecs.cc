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
#pragma ident "@(#) $Id: spxvecs.cpp,v 1.19 2002/04/06 13:05:02 bzfkocht Exp $"
#endif

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "soplex.h"

namespace soplex
{
/*  Initialize Vectors
 
    Computing the right hand side vector for the feasibility vector depends on
    the chosen representation and type of the basis.
 
    In columnwise case, |theFvec| = $x_B = A_B^{-1} (- A_N x_N)$, where $x_N$
    are either upper or lower bounds for the nonbasic variables (depending on
    the variables |Status|). If these values remain unchanged throughout the
    simplex algorithm, they may be taken directly from LP. However, in the
    entering type algorith they are changed and, hence, retreived from the
    column or row upper or lower bound vectors.
 
    In rowwise case, |theFvec| = $\pi^T_B = (c^T - 0^T A_N) A_B^{-1}$. However,
    this applies only to leaving type algorithm, where no bounds on dual
    variables are altered. In entering type algorithm they are changed and,
    hence, retreived from the column or row upper or lower bound vectors.
 */
void SoPlex::computeFrhs()
{
   METHOD( "SoPlex::computeFrhs()" );
   if (rep() == COLUMN)
   {
      theFrhs->clear();

      if (type() == LEAVE)
      {
         computeFrhsXtra();

         for(int i = 0; i < nRows(); i++)
         {
            Real x;

            SPxBasis::Desc::Status stat = desc().rowStatus(i);

            if (!isBasic(stat))
            {
               switch (stat)
               {
                  // columnwise cases:
               case SPxBasis::Desc::P_FREE :
                  continue;

               case (SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER) :
                  assert(lhs(i) == rhs(i));
                  /*FALLTHROUGH*/
               case SPxBasis::Desc::P_ON_UPPER :
                  x = rhs(i);
                  break;
               case SPxBasis::Desc::P_ON_LOWER :
                  x = lhs(i);
                  break;

               default:
                  std::cerr << __FILE__ << __LINE__ 
                            << " ERROR: inconsistent basis must not happen!\n";
                  abort();
               }
               assert(x < infinity);
               assert(x > -infinity);
               (*theFrhs)[i] += x;                         // slack !
            }
         }
      }
      else
      {
         computeFrhs1(*theUbound, *theLbound);
         computeFrhs2(*theCoUbound, *theCoLbound);
      }
   }
   else
   {
      assert(rep() == ROW);

      if (type() == ENTER)
      {
         theFrhs->clear();
         computeFrhs1(*theUbound, *theLbound);
         computeFrhs2(*theCoUbound, *theCoLbound);
         *theFrhs += maxObj();
      }
      else
         *theFrhs = maxObj();
   }
}

void SoPlex::computeFrhsXtra()
{
   METHOD( "SoPlex::computeFrhsXtra()" );
   assert(rep() == COLUMN);
   assert(type() == LEAVE);
   Real x;
   int i;

   for (i = nCols() - 1; i >= 0; --i)
   {
      SPxBasis::Desc::Status stat = desc().colStatus(i);

      if (!isBasic(stat))
      {
         switch (stat)
         {
            // columnwise cases:
         case SPxBasis::Desc::P_FREE :
            continue;

         case (SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER) :
            assert(SPxLP::lower(i) == SPxLP::upper(i));
            /*FALLTHROUGH*/
         case SPxBasis::Desc::P_ON_UPPER :
            x = SPxLP::upper(i);
            break;
         case SPxBasis::Desc::P_ON_LOWER :
            x = SPxLP::lower(i);
            break;

         default:
            std::cerr << __FILE__ << __LINE__ 
                      << " ERROR: inconsistent basis must not happen!\n";
            abort();
         }
         assert(x < infinity);
         assert(x > -infinity);
         if (isNotZero(x))
            theFrhs->multAdd(-x, vector(i));
      }
   }
}


/*
    This methods subtracts $A_N x_N$ or $\pi_N^T A_N$ from |theFrhs| as
    specified by the |Status| of all nonbasic variables. The values of $x_N$ or
    $\pi_N$ are taken from the passed arrays.
 */
void SoPlex::computeFrhs1(
   const Vector& ufb,    ///< upper feasibility bound for variables
   const Vector& lfb)    ///< lower feasibility bound for variables
{
   METHOD( "SoPlex::computeFrhs1()" );
   Real x;
   int i;
   const SPxBasis::Desc& ds = desc();

   for (i = coDim() - 1; i >= 0; --i)
   {
      SPxBasis::Desc::Status stat = ds.status(i);
      if (!isBasic(stat))
      {
         switch (stat)
         {
         case SPxBasis::Desc::D_FREE :
         case SPxBasis::Desc::D_UNDEFINED :
         case SPxBasis::Desc::P_FREE :
            continue;

         case SPxBasis::Desc::P_ON_UPPER :
         case SPxBasis::Desc::D_ON_UPPER :
            x = ufb[i];
            break;
         case SPxBasis::Desc::P_ON_LOWER :
         case SPxBasis::Desc::D_ON_LOWER :
            x = lfb[i];
            break;

         case (SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER) :
         case (SPxBasis::Desc::D_ON_UPPER + SPxBasis::Desc::D_ON_LOWER) :
            assert(lfb[i] == ufb[i]);
            x = lfb[i];
            break;

         default:
            std::cerr << __FILE__ << __LINE__ 
                      << "ERROR: inconsistent basis must not happen!\n";
            abort();
         }
         assert(x < infinity);
         assert(x > -infinity);
         if (isNotZero(x))
            theFrhs->multAdd(-x, vector(i));
      }
   }
}

/*
    This methods subtracts $A_N x_N$ or $\pi_N^T A_N$ from |theFrhs| as
    specified by the |Status| of all nonbasic variables. The values of $x_N$ or
    $\pi_N$ are taken from the passed arrays.
 */
void SoPlex::computeFrhs2(
   const Vector& coufb,   ///< upper feasibility bound for covariables
   const Vector& colfb)   ///< lower feasibility bound for covariables
{
   METHOD( "SoPlex::computeFrhs2()" );
   Real x;
   int i;
   const SPxBasis::Desc& ds = desc();

   for(i = 0; i < dim(); i++)
   {
      SPxBasis::Desc::Status stat = ds.coStatus(i);
      if (!isBasic(stat))
      {
         switch (stat)
         {
         case SPxBasis::Desc::D_FREE :
         case SPxBasis::Desc::D_UNDEFINED :
         case SPxBasis::Desc::P_FREE :
            continue;

         case SPxBasis::Desc::P_ON_LOWER :            // negative slack bounds!
         case SPxBasis::Desc::D_ON_UPPER :
            x = coufb[i];
            break;
         case SPxBasis::Desc::P_ON_UPPER :            // negative slack bounds!
         case SPxBasis::Desc::D_ON_LOWER :
            x = colfb[i];
            break;
         case SPxBasis::Desc::P_FIXED :
         case SPxBasis::Desc::D_ON_BOTH :

            if (colfb[i] != coufb[i])
            {
               std::cerr << "Frhs2: " << stat << " " 
                         << colfb[i] << " " << coufb[i]
                         << " shouln't be" << std::endl;
            }
            //assert(colfb[i] == coufb[i]);
            x = colfb[i];
            break;

         default:
            std::cerr << __FILE__ << __LINE__ 
                      << "ERROR: inconsistent basis must not happen!\n";
            abort();
         }
         assert(x < infinity);
         assert(x > -infinity);
         (*theFrhs)[i] -= x;
      }
   }
}

/*
    Computing the right hand side vector for |theCoPvec| depends on
    the type of the simplex algorithm. In entering algorithms, the
    values are taken from the inequality's right handside or the
    column's objective value.
    
    In contrast to this leaving algorithms take the values from vectors
    |theURbound| and so on.
    
    We reflect this difference by providing two pairs of methods
    |computeEnterCoPrhs(n, stat)| and |computeLeaveCoPrhs(n, stat)|. The first
    pair operates for entering algorithms, while the second one is intended for
    leaving algorithms.  The return value of these methods is the right hand
    side value for the $n$-th row or column id, respectively, if it had the
    passed |Status| for both.
 
    Both methods are again split up into two methods named |...4Row(i,n)| and
    |...4Col(i,n)|, respectively. They do their job for the |i|-th basis
    variable, being the |n|-th row or column.  
*/
void SoPlex::computeEnterCoPrhs4Row(int i, int n)
{
   METHOD( "SoPlex::computeEnterCoPrhs4Row()" );
   assert(baseId(i).isSPxRowId());
   assert(number(SPxRowId(baseId(i))) == n);

   switch (desc().rowStatus(n))
   {
   // rowwise representation:
   case SPxBasis::Desc::P_ON_LOWER + SPxBasis::Desc::P_ON_UPPER :
      assert(lhs(n) > -infinity);
      assert(rhs(n) == lhs(n));
      /*FALLTHROUGH*/
   case SPxBasis::Desc::P_ON_UPPER :
      assert(rep() == ROW);
      assert(rhs(n) < infinity);
      (*theCoPrhs)[i] = rhs(n);
      break;
   case SPxBasis::Desc::P_ON_LOWER :
      assert(rep() == ROW);
      assert(lhs(n) > -infinity);
      (*theCoPrhs)[i] = lhs(n);
      break;

      // columnwise representation:
      // slacks must be left 0!
   default:
      (*theCoPrhs)[i] = 0;
      break;
   }
}

void SoPlex::computeEnterCoPrhs4Col(int i, int n)
{
   METHOD( "SoPlex::computeEnterCoPrhs4Col()" );
   assert(baseId(i).isSPxColId());
   assert(number(SPxColId(baseId(i))) == n);
   switch (desc().colStatus(n))
   {
      // rowwise representation:
   case SPxBasis::Desc::P_ON_LOWER + SPxBasis::Desc::P_ON_UPPER :
      assert(SPxLP::upper(n) == SPxLP::lower(n));
      assert(SPxLP::lower(n) > -infinity);
      /*FALLTHROUGH*/
   case SPxBasis::Desc::P_ON_UPPER :
      assert(rep() == ROW);
      assert(SPxLP::upper(n) < infinity);
      (*theCoPrhs)[i] = SPxLP::upper(n);
      break;
   case SPxBasis::Desc::P_ON_LOWER :
      assert(rep() == ROW);
      assert(SPxLP::lower(n) > -infinity);
      (*theCoPrhs)[i] = SPxLP::lower(n);
      break;

      // columnwise representation:
   case SPxBasis::Desc::D_UNDEFINED :
   case SPxBasis::Desc::D_ON_UPPER + SPxBasis::Desc::D_ON_LOWER :
   case SPxBasis::Desc::D_ON_UPPER :
   case SPxBasis::Desc::D_ON_LOWER :
   case SPxBasis::Desc::D_FREE :
      assert(rep() == COLUMN);
      (*theCoPrhs)[i] = maxObj(n);
      break;

   default:             // variable left 0
      (*theCoPrhs)[i] = 0;
      break;
   }
}

void SoPlex::computeEnterCoPrhs()
{
   METHOD( "SoPlex::computeEnterCoPrhs()" );
   assert(type() == ENTER);

   for (int i = dim() - 1; i >= 0; --i)
   {
      SPxId l_id = baseId(i);
      if (l_id.isSPxRowId())
         computeEnterCoPrhs4Row(i, number(SPxRowId(l_id)));
      else
         computeEnterCoPrhs4Col(i, number(SPxColId(l_id)));
   }
}

void SoPlex::computeLeaveCoPrhs4Row(int i, int n)
{
   METHOD( "SoPlex::computeLeaveCoPrhs4Row()" );
   assert(baseId(i).isSPxRowId());
   assert(number(SPxRowId(baseId(i))) == n);
   switch (desc().rowStatus(n))
   {
   case SPxBasis::Desc::D_ON_LOWER + SPxBasis::Desc::D_ON_UPPER :
   case SPxBasis::Desc::P_ON_LOWER + SPxBasis::Desc::P_ON_UPPER :
      assert(theLRbound[n] > -infinity);
      assert(theURbound[n] == theLRbound[n]);
      /*FALLTHROUGH*/
   case SPxBasis::Desc::D_ON_UPPER :
   case SPxBasis::Desc::P_ON_UPPER :
      assert(theURbound[n] < infinity);
      (*theCoPrhs)[i] = theURbound[n];
      break;

   case SPxBasis::Desc::D_ON_LOWER :
   case SPxBasis::Desc::P_ON_LOWER :
      assert(theLRbound[n] > -infinity);
      (*theCoPrhs)[i] = theLRbound[n];
      break;

   default:
      (*theCoPrhs)[i] = 0;
      break;
   }
}

void SoPlex::computeLeaveCoPrhs4Col(int i, int n)
{
   METHOD( "SoPlex::computeLeaveCoPrhs4Col()" );
   assert(baseId(i).isSPxColId());
   assert(number(SPxColId(baseId(i))) == n);
   switch (desc().colStatus(n))
   {
   case SPxBasis::Desc::D_UNDEFINED :
   case SPxBasis::Desc::D_ON_LOWER + SPxBasis::Desc::D_ON_UPPER :
   case SPxBasis::Desc::P_ON_LOWER + SPxBasis::Desc::P_ON_UPPER :
      assert(theLCbound[n] > -infinity);
      assert(theUCbound[n] == theLCbound[n]);
      /*FALLTHROUGH*/
   case SPxBasis::Desc::D_ON_LOWER :
   case SPxBasis::Desc::P_ON_UPPER :
      assert(theUCbound[n] < infinity);
      (*theCoPrhs)[i] = theUCbound[n];
      break;
   case SPxBasis::Desc::D_ON_UPPER :
   case SPxBasis::Desc::P_ON_LOWER :
      assert(theLCbound[n] > -infinity);
      (*theCoPrhs)[i] = theLCbound[n];
      break;

   default:
      (*theCoPrhs)[i] = maxObj(n);
      //      (*theCoPrhs)[i] = 0;
      break;
   }
}

void SoPlex::computeLeaveCoPrhs()
{
   METHOD( "SoPlex::computeLeaveCoPrhs()" );
   assert(type() == LEAVE);

   for (int i = dim() - 1; i >= 0; --i)
   {
      SPxId l_id = baseId(i);
      if (l_id.isSPxRowId())
         computeLeaveCoPrhs4Row(i, number(SPxRowId(l_id)));
      else
         computeLeaveCoPrhs4Col(i, number(SPxColId(l_id)));
   }
}


/*
    When computing the copricing vector, we expect the pricing vector to be
    setup correctly. Then computing the copricing vector is nothing but
    computing all scalar products of the pricing vector with the vectors of the
    LPs constraint matrix.
 */
void SoPlex::computePvec()
{
   METHOD( "SoPlex::computePvec()" );
   int i;

   for (i = coDim() - 1; i >= 0; --i)
      (*thePvec)[i] = vector(i) * (*theCoPvec);
}

void SoPlex::setupPupdate(void)
{
   METHOD( "SoPlex::setupPupdate()" );
   SSVector& p = thePvec->delta();
   SSVector& c = theCoPvec->delta();

   if (c.isSetup())
   {
      if (c.size() < 0.95 * theCoPvec->dim())
         p.assign2product4setup(*thecovectors, c);
      else
         p.assign2product(c, *thevectors);
   }
   else
   {
      p.assign2productAndSetup(*thecovectors, c);
   }

   p.setup();
}

void SoPlex::doPupdate(void)
{
   METHOD( "SoPlex::doPupdate()" );
   theCoPvec->update();
   if (pricing() == FULL)
   {
      // thePvec->delta().setup();
      thePvec->update();
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
