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
#pragma ident "@(#) $Id: spxequilisc.cpp,v 1.2 2002/04/10 07:13:17 bzfkocht Exp $"
#endif

/**@file  spxequilisc.cpp
 * @brief Equilibrium row/column scaling.
 */
#include <assert.h>

#include "spxequilisc.h"

namespace soplex
{
static const char* makename(bool colFirst, bool doBoth)
{
   const char* name;

   if (doBoth)
      name = colFirst ? "CR-Equilibrium" : "RC-Equilibrium";
   else
      name = colFirst ? "C-Equilibrium" : "R-Equilibrium";

   return name;
}

SPxEquili::SPxEquili(bool colFirst, bool doBoth)
   : SPxScaler(makename(colFirst, doBoth), colFirst, doBoth)
{}

void SPxEquili::scale() 
{
   assert(m_lp != 0);
   assert(m_lp->isConsistent());

   if (m_colFirst)
   {
      int i;

      for( i = 0; i < m_lp->nCols(); ++i )
      {
         SVector& vec = m_lp->colVector_w(i);
         Real     x   = vec.maxAbs();

         if (isZero(x))
            m_colscale[i] = 1.0;
         else
         {
            Real y           = 1.0 / x;
            m_colscale[i]    = y;
            vec             *= y;
            m_lp->maxObj(i) *= y;

            if (m_lp->upper(i) < infinity)
               m_lp->upper(i) *= x;
            if (m_lp->lower(i) > -infinity)
               m_lp->lower(i) *= x;
         }
      }
      
      for( i = 0; i < m_lp->nRows(); ++i )
      {
         SVector& vec = m_lp->rowVector_w(i);
         Real     x   = 0.0;
         Real     y;

         for( int j = 0; j < vec.size(); ++j )
         {
            vec.value(j) *= m_colscale[vec.index(j)];
            y             = fabs(vec.value(j));
            x             = (x < y) ? y : x;
         }
         if (isZero(x) || !m_doBoth)
            m_rowscale[i] = 1.0;
         else
         {
            y              = 1.0 / x;
            m_rowscale[i]  = y;
            vec           *= y;

            if (m_lp->rhs(i) < infinity)
               m_lp->rhs(i) *= y;
            if (m_lp->lhs(i) > -infinity)
               m_lp->lhs(i) *= y;
         }
      }
      if (m_doBoth)
      {
         for( i = 0; i < m_lp->nCols(); ++i )
         {
            SVector& vec = m_lp->colVector_w(i);
            
            for( int j = 0; j < vec.size(); ++j)
               vec.value(j) *= m_rowscale[vec.index(j)];
         }
      }
   }
   else
   {
      int i; 

      for( i = 0; i < m_lp->nRows(); ++i )
      {
         SVector& vec = m_lp->rowVector_w(i);
         Real     x   = vec.maxAbs();

         if (isZero(x))
            m_rowscale[i] = 1.0;
         else
         {
            Real y        = 1.0 / x;
            m_rowscale[i] = y;
            vec          *= y;

            if (m_lp->rhs(i) < infinity)
               m_lp->rhs(i) *= y;
            if (m_lp->lhs(i) > -infinity)
               m_lp->lhs(i) *= y;
         }
      }

      for( i = 0; i < m_lp->nCols(); ++i )
      {
         SVector& vec = m_lp->colVector_w(i);
         Real     x   = 0;
         Real     y;

         for( int j = 0; j < vec.size(); ++j)
         {
            vec.value(j) *= m_rowscale[vec.index(j)];
            y             = fabs(vec.value(j));
            x             = (x < y) ? y : x;
         }
         if (isZero(x) || !m_doBoth)
            m_colscale[i] = 1.0;
         else
         {
            y                = 1.0 / x;
            m_colscale[i]    = y;
            vec             *= y;
            m_lp->maxObj(i) *= y;

            if (m_lp->upper(i) < infinity)
               m_lp->upper(i) *= x;
            if (m_lp->lower(i) > -infinity)
               m_lp->lower(i) *= x;
         }
      }
      if (m_doBoth)
      {
         for( i = 0; i < m_lp->nRows(); ++i )
         {
            SVector& vec = m_lp->rowVector_w(i);
            
            for( int j = 0; j < vec.size(); ++j)
               vec.value(j) *= m_colscale[vec.index(j)];
         }
      }
   }
   assert(m_lp->isConsistent());
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
