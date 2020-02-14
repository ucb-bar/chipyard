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
#pragma ident "@(#) $Id: spxquality.cpp,v 1.2 2002/04/04 19:36:51 bzfkocht Exp $"
#endif

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "soplex.h"
#include "spxscaler.h"

namespace soplex
{

void SoPlex::qualConstraintViolation(double& maxviol, double& sumviol) const
{
   maxviol = 0.0;
   sumviol = 0.0;

   DVector solu( nCols() );

   getPrimal( solu );

   for( int row = 0; row < nRows(); ++row )
   {
      const SVector& rowvec = rowVector( row );

      double val = 0.0;         

      for( int col = 0; col < rowvec.size(); ++col )
         val += rowvec.value( col ) * solu[rowvec.index( col )];

      double viol = 0.0;

      assert(lhs( row ) <= rhs( row ));

      if (val < lhs( row )) 
         viol = fabs(val - lhs( row ));
      else
         if (val > rhs( row ))
            viol = fabs(val - rhs( row ));

      if (viol > maxviol)
         maxviol = viol;

      sumviol += viol;
   }
}

void SoPlex::qualConstraintViolationUnscaled(
   double& maxviol, double& sumviol) const
{
   maxviol = 0.0;
   sumviol = 0.0;

   if (thescaler == 0)
      return;

   DVector usol( nCols() );
   DVector ulhs( nRows() );
   DVector urhs( nRows() );

   getPrimal( usol );

   thescaler->unscaleSolution( usol );
   thescaler->unscaledLhs( ulhs );
   thescaler->unscaledRhs( urhs );

   for( int row = 0; row < nRows(); ++row )
   {
      DSVector urowvec;

      thescaler->unscaledRowVector( row, urowvec );

      double val = 0.0;         

      for( int col = 0; col < urowvec.size(); ++col )
         val += urowvec.value( col ) * usol[urowvec.index( col )];

      double viol = 0.0;

      assert( ulhs[row] <= urhs[row] );

      if( val < ulhs[row] ) 
         viol = fabs(val - ulhs[row] );
      else
         if( val > urhs[row] )
            viol = fabs( val - urhs[row] );

      if (viol > maxviol)
         maxviol = viol;

      sumviol += viol;
   }
}

void SoPlex::qualBoundViolation(
   double& maxviol, double& sumviol) const
{
   maxviol = 0.0;
   sumviol = 0.0;

   DVector solu( nCols() );

   getPrimal( solu );

   for( int col = 0; col < nCols(); ++col )
   {
      assert( lower( col ) <= upper( col ));

      double viol = 0.0;

      if (solu[col] < lower( col ))
         viol = fabs( solu[col] - lower( col ));
      else
         if (solu[col] > upper( col ))
            viol = fabs( solu[col] - upper( col ));
         
      if (viol > maxviol)
         maxviol = viol;

      sumviol += viol;
   }
}

void SoPlex::qualBoundViolationUnscaled(
   double& maxviol, double& sumviol) const
{
   maxviol = 0.0;
   sumviol = 0.0;

   if (thescaler == 0)
      return;

   DVector usol  ( nCols() );
   DVector ulower( nCols() );
   DVector uupper( nCols() );

   getPrimal( usol );

   thescaler->unscaleSolution( usol );
   thescaler->unscaledLower( ulower );
   thescaler->unscaledUpper( uupper );

   for( int col = 0; col < nCols(); ++col )
   {
      assert( ulower[col] <= uupper[col] );

      double viol = 0.0;

      if (usol[col] < ulower[col])
         viol = fabs( usol[col] - ulower[col]);
      else
         if (usol[col] > uupper[col])
            viol = fabs( usol[col] - uupper[col] );
         
      if (viol > maxviol)
         maxviol = viol;

      sumviol += viol;
   }
}

void SoPlex::qualSlackViolation(double& maxviol, double& sumviol) const
{
   maxviol = 0.0;
   sumviol = 0.0;

   DVector solu( nCols() );
   DVector slacks( nRows() );

   getPrimal( solu );
   getSlacks( slacks );

   for( int row = 0; row < nRows(); ++row )
   {
      const SVector& rowvec = rowVector( row );

      double val = 0.0;         

      for( int col = 0; col < rowvec.size(); ++col )
         val += rowvec.value( col ) * solu[rowvec.index( col )];

      double viol = fabs(val - slacks[row]);

      if (viol > maxviol)
         maxviol = viol;

      sumviol += viol;
   }
}

// This should be computed freshly.
void SoPlex::qualRdCostViolation(double& maxviol, double& sumviol) const
{
   maxviol = 0.0;
   sumviol = 0.0;

   // TODO:   y = c_B * B^-1  => coSolve(y, c_B)
   //         redcost = c_N - yA_N 
   // solve system "x = e_i^T * B^-1" to get i'th row of B^-1
   // DVector y( nRows() );
   // basis().coSolve( x, spx->unitVector( i ) );

   DVector rdcost( nCols() );

   for( int col = 0; col < nCols(); ++col)
   {
      double viol = 0.0;

      if (spxSense() == SPxLP::MINIMIZE)
      {
         if (rdcost[col] < 0.0)
            viol = -rdcost[col];
      }
      else
      {
         if (rdcost[col] > 0.0)
            viol = rdcost[col];
      }
      if (viol > maxviol)
         maxviol = viol;

      sumviol += viol;
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
