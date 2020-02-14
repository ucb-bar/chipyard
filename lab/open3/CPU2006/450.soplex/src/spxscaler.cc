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
#pragma ident "@(#) $Id: spxscaler.cpp,v 1.2 2002/04/04 19:36:51 bzfkocht Exp $"
#endif

/**@file  spxscaler.cpp
 * @brief LP scaling base class.
 */
#include <assert.h>

#include "spxscaler.h"
#include "spxlp.h"

namespace soplex
{

std::ostream& operator<<(std::ostream& s, const SPxScaler& sc)
{
   int i;

   s << sc.getName() << " scaler:";

   if (sc.m_lp == 0)
      s << "not initialised" << std::endl;
   else
   {
      s << std::endl;
      s << "colscale = [ ";
      for( i = 0; i < sc.m_lp->nCols(); i++ )
         s << sc.m_colscale[i] << " ";
      s << "]" << std::endl;
      
      s << "rowscale = [ ";
      for( i = 0; i < sc.m_lp->nRows(); i++ )
         s << sc.m_rowscale[i] << " ";
      s << "]" << std::endl;
   }
   return s;
}

SPxScaler::SPxScaler(
   const char* name, 
   bool        colFirst, 
   bool        doBoth) 
   : m_name(name)
   , m_lp(0)
   , m_colFirst(colFirst)
   , m_doBoth(doBoth)
{}

SPxScaler::~SPxScaler()
{
   m_name = 0;
   m_lp   = 0;
}   

const char* SPxScaler::getName() const
{
   return m_name;
}

void SPxScaler::setOrder(bool colFirst)
{
   m_colFirst = colFirst;
}

void SPxScaler::setBoth(bool both)
{
   m_doBoth = both;
}

void SPxScaler::setLP(SPxLP* lp)
{
   assert(lp != 0);

   if (m_lp != lp)
   {
      m_lp = lp;

      m_colscale.reSize(m_lp->nCols());
      m_rowscale.reSize(m_lp->nRows());
   }
   int i;

   for( i = 0; i < m_lp->nCols(); i++ )
      m_colscale[i] = 1.0;

   for( i = 0; i < m_lp->nRows(); i++ )
      m_rowscale[i] = 1.0;
}

void SPxScaler::unscale()
{
   assert(m_lp != 0);
   assert(m_lp->isConsistent());

   int i;
   int j;

   for( i = 0; i < m_lp->nRows(); ++i )
   {
      SVector& vec = m_lp->rowVector_w(i);

      for( j = 0; j < vec.size(); ++j )
         vec.value(j) /= m_colscale[vec.index(j)];

      double y = 1.0 / m_rowscale[i];

      vec *= y;

      if (m_lp->rhs(i) < infinity)
         m_lp->rhs(i) *= y;

      if (m_lp->lhs(i) > -infinity)
         m_lp->lhs(i) *= y;
   }
   for( i = 0; i < m_lp->nCols(); ++i )
   {
      SVector& vec = m_lp->colVector_w(i);

      vec *= 1.0 / m_colscale[i];

      for( j = 0; j < vec.size(); ++j )
         vec.value(j) /= m_rowscale[vec.index(j)];

      m_lp->maxObj(i) /= m_colscale[i];

      if (m_lp->upper(i) < infinity)
         m_lp->upper(i) *= m_colscale[i];

      if (m_lp->lower(i) > -infinity)
         m_lp->lower(i) *= m_colscale[i];
   }
   assert(m_lp->isConsistent());
}

void SPxScaler::unscaleSolution(Vector& usol) const
{
   assert(m_lp       != 0);
   assert(usol.dim() == m_lp->nCols());

   for( int i = 0; i < m_lp->nCols(); ++i )
      usol[i] *= m_colscale[i];
}

void SPxScaler::unscaledMaxObj(Vector& uobj) const
{
   assert(m_lp       != 0);
   assert(uobj.dim() == m_lp->nCols());

   uobj = m_lp->maxObj();

   for( int i = 0; i < m_lp->nCols(); ++i )
      uobj[i] *= 1.0 / m_colscale[i];   
}

void SPxScaler::unscaledLower(Vector& ulower) const
{
   assert(m_lp         != 0);
   assert(ulower.dim() == m_lp->nCols());

   ulower = m_lp->lower();

   for( int i = 0; i < m_lp->nCols(); ++i )
      if (ulower[i] > -infinity)
         ulower[i] *= m_colscale[i];   
}

void SPxScaler::unscaledUpper(Vector& uupper) const
{
   assert(m_lp         != 0);
   assert(uupper.dim() == m_lp->nCols());

   uupper = m_lp->upper();

   for( int i = 0; i < m_lp->nCols(); ++i )
      if (uupper[i] < infinity)
         uupper[i] *= m_colscale[i];   
}

void SPxScaler::unscaledLhs(Vector& ulhs) const
{ 
   assert(m_lp       != 0);
   assert(ulhs.dim() == m_lp->nRows());

   ulhs = m_lp->lhs();

   for( int i = 0; i < m_lp->nRows(); ++i )
      if (ulhs[i] > -infinity)
         ulhs[i] *= 1.0 / m_rowscale[i];   
}

void SPxScaler::unscaledRhs(Vector& urhs) const
{
   assert(m_lp       != 0);
   assert(urhs.dim() == m_lp->nRows());

   urhs = m_lp->rhs();

   for( int i = 0; i < m_lp->nRows(); ++i )
      if (urhs[i] < infinity)
         urhs[i] *= 1.0 / m_rowscale[i];   
}

void SPxScaler::unscaledRowVector(int row, DSVector& uvec) const
{
   assert(m_lp != 0);
   assert(row  >= 0);
   assert(row  <  m_lp->nRows());

   uvec  = m_lp->rowVector( row );
   uvec *= 1.0 / m_rowscale[row];
   
   for( int i = 0; i < uvec.size(); ++i )
   {
      assert(uvec.index(i) < m_lp->nCols());
      
      uvec.value(i) *= 1.0 / m_colscale[uvec.index(i)];   
   }
}

void SPxScaler::unscaledColVector(int col, DSVector& uvec) const
{
   assert(m_lp != 0);
   assert(col  >= 0);
   assert(col  <  m_lp->nCols());

   uvec  = m_lp->colVector( col );
   uvec *= 1.0 / m_colscale[col];

   for( int i = 0; i < uvec.size(); ++i )
   {
      assert(uvec.index(i) < m_lp->nRows());
      
      uvec.value(i) *= 1.0 / m_rowscale[uvec.index(i)];   
   }
}

bool SPxScaler::isConsistent() const
{
   return m_colscale.isConsistent() && m_rowscale.isConsistent();
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


