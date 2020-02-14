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
#pragma ident "@(#) $Id: spxsolve.cpp,v 1.49 2002/04/06 13:05:02 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#if defined(__riscv) && !defined(__linux)
/* FIXME: Avoid writing output file */
#undef SPEC_CPU
#endif

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "soplex.h"
#include "spxpricer.h"
#include "spxratiotester.h"
#include "spxstarter.h"
#include "spxscaler.h"
#include "spxsimplifier.h"

namespace soplex
{
/**@todo After solve() returned, the algorithm type may have changed.
 *       This may be a problem if solve() is called again.
 */
SoPlex::Status SoPlex::solve()
{
   METHOD( "SoPlex::solve()" );
   SPxId enterId;
   int leaveNum;

   if (dim() <= 0 && coDim() <= 0) // no problem loaded
      return ERROR;

   if (slinSolver() == 0) // linear system solver is required.
      return ERROR;

   if (thesimplifier != 0)
   {
      thesimplifier->load(this);

      switch (thesimplifier->simplify())
      {
      case 1:
         setStatus(SPxBasis::UNBOUNDED);
         return UNBOUNDED;
      case - 1:
         setStatus(SPxBasis::INFEASIBLE);
         return INFEASIBLE;
      default:
         break;
      }
   }
   if (thescaler != 0)
   {
      thescaler->setLP(this);
      thescaler->scale();
   }
   if (thepricer == 0) // pricer is required.
      return ERROR;

   theTime.reset();
   theTime.start();

   m_numCycle = 0;
   iterCount  = 0;

   if (!isInitialized())
   {
      /*
      if(SPxBasis::status() <= NO_PROBLEM)
          SPxBasis::load(this);
       */
      if (thestarter != 0 && status() != REGULAR)  // no basis and no starter.
         thestarter->generate(*this);              // generate start basis.
   }

   /* Originally, init() was only called in the "!isInitialized()" case,
      but the update of theFvec, theFrhs, ... in SoPlex::localAddRows() and
      SoPlex::localAddCols() doesn't seem to work. Therefore, we call
      init() here to set up all the vectors correctly.
   */
   init();

   thepricer->setEpsilon(delta());
   setType(type());

   VERBOSE3({
      std::cerr << "starting value = " << value() << std::endl;
      std::cerr << "starting shift = " << shift() << std::endl;
   });
   DEBUG( desc().dump(); );

   if (SPxBasis::status() == SPxBasis::OPTIMAL)
      setStatus(SPxBasis::REGULAR);

   m_status   = RUNNING;
   bool stop  = terminate();
   leaveCount = 0;
   enterCount = 0;

   while (!stop)
   {
      if (type() == ENTER)
      {
         VERBOSE3({
            std::cerr << "Enter iteration: " << iteration()
                      << ", Value = " << value()
                      << ", Shift = " << shift() << std::endl;
         });

         do
         {
            enterId = thepricer->selectEnter();
            if (!enterId.isValid())
            {
               factorize();
               enterId = thepricer->selectEnter();
               if (!enterId.isValid())
                  break;
            }
            enter(enterId);
            assert((testBounds(), 1));
            thepricer->entered4(lastEntered(), lastIndex());
            stop = terminate();
            clearUpdateVecs();
            enterCount += (lastIndex() >= 0);
            //@ assert(isConsistent());
         }
         while (!stop);

         VERBOSE3({
            std::cerr << "Enter finished. iteration: " << iteration() 
                      << ", value: " << value()
                      << ", shift: " << shift()
                      << ", epsilon: " << epsilon()
                      << ", stop: " << stop
                      << ", basis status: " << int(SPxBasis::status())
                      << ", solver status: " << int(m_status) << std::endl;
         });

         if (!stop)
         {
            if (shift() <= epsilon())
            {
               // factorize();
               unShift();

               VERBOSE3({
                  std::cerr << "maxInfeas: " << maxInfeas()
                            << ", shift: " << shift()
                            << ", delta: " << delta() << std::endl;
               });

               if (maxInfeas() + shift() <= delta())
               {
                  setStatus(SPxBasis::OPTIMAL);
                  m_status = OPTIMAL;
                  break;
               }
            }
            setType(LEAVE);
         }
      }
      else
      {
         assert(type() == LEAVE);

         VERBOSE3({
            std::cerr << "Leave Iteration: " << iteration()
                      << ", Value = " << value()
                      << ", Shift = " << shift() << std::endl;
         });

         do
         {
            leaveNum = thepricer->selectLeave();
            if (leaveNum < 0)
            {
               factorize();
               leaveNum = thepricer->selectLeave();
               if (leaveNum < 0)
                  break;
            }
            leave(leaveNum);
            assert((testBounds(), 1));
            thepricer->left4(lastIndex(), lastLeft());
            stop = terminate();
            clearUpdateVecs();
            leaveCount += lastEntered().isValid();
            //@ assert(isConsistent());
         }
         while (!stop);

         VERBOSE3({
            std::cerr << "Leave finished. iteration: " << iteration() 
                      << ", value: " << value()
                      << ", shift: " << shift()
                      << ", epsilon: " << epsilon()
                      << ", stop: " << stop
                      << ", basis status: " << int(SPxBasis::status())
                      << ", solver status: " << int(m_status) << std::endl;
         });

         if (!stop)
         {
            if (shift() <= epsilon())
            {
               // factorize();
               unShift();

               VERBOSE3({
                  std::cerr << "maxInfeas: " << maxInfeas()
                            << ", shift: " << shift()
                            << ", delta: " << delta() << std::endl;
               });

               if (maxInfeas() + shift() <= delta())
               {
                  setStatus(SPxBasis::OPTIMAL);
                  m_status = OPTIMAL;
                  break;
               }
            }
            setType(ENTER);
         }
      }
   }
   theTime.stop();

   if (m_status == RUNNING)
      m_status = ERROR;

#if defined(SPEC_CPU)
   if (!out_file.is_open())
   {
      out_file.open(out_filename, std::ios::app);
      if (!out_file.is_open())
      {
         std::cerr << "Couldn't open \"" << out_filename << "\" for appending." << std::endl;
         exit(1);
      }
   }
   VERBOSE1({
      std::cout << "Finished solving (status=" << int(status());
      out_file  << "Total iters=" << iterCount;
      out_file  << "; Iterations by algorithm type: leave=" << leaveCount
                << ", enter=" << enterCount << std::endl;
      if( status() == OPTIMAL )
         std::cout << ", objValue=" << value();
      std::cout << ")" << std::endl;
   });
#else
   VERBOSE1({
      std::cout << "Finished solving (status=" << int(status());
      std::cout << ", iters=" << iterCount
                << ", leave=" << leaveCount
                << ", enter=" << enterCount;
      if( status() == OPTIMAL )
         std::cout << ", objValue=" << value();
      std::cout << ")" << std::endl;
   });
#endif /* !SPEC_CPU */

#ifndef NDEBUG
   /* check, if solution is really feasible */
   if( status() == OPTIMAL )
   {
      int    c;
      double val;
      DVector sol( nCols() );

      getPrimal( sol );

      for(int row = 0; row < nRows(); ++row )
      {
         const SVector& rowvec = rowVector( row );
         val = 0.0;         
         for( c = 0; c < rowvec.size(); ++c )
            val += rowvec.value( c ) * sol[rowvec.index( c )];

         if( LT( val, lhs( row ), delta() ) ||
             GT( val, rhs( row ), delta() ) )
         {
            std::cerr << "Warning! Constraint " << row
                      << " is violated by solution" << std::endl;
            std::cerr << "   lhs:" << lhs( row )
                      << " <= val:" << val
                      << " <= rhs:" << rhs( row ) << std::endl;

            if( type() == LEAVE && isRowBasic( row ) )
            {
               // find basis variable
               for( c = 0; c < nRows(); ++c )
                  if (basis().baseId(c).isSPxRowId()     
                     && (number(basis().baseId(c)) == row))
                     break;

               assert( c < nRows() );

               std::cerr << "   basis idx:" << c
                         << " fVec:" << fVec()[c]
                         << " fRhs:" << fRhs()[c]
                         << " fTest:" << fTest()[c] << std::endl;
            }
         }
      }
      for(int col = 0; col < nCols(); ++col )
      {
         if( LT( sol[col], lower( col ), delta() ) ||
             GT( sol[col], upper( col ), delta() ) )
         {
            std::cerr << "Warning! Bound for column " << col
                      << " is violated by solution" << std::endl;
            std::cerr << "   lower:" << lower( col )
                      << " <= val:" << sol[col]
                      << " <= upper:" << upper( col ) << std::endl;

            if( type() == LEAVE && isColBasic( col ) )
            {
               for( c = 0; c < nRows() ; ++c)
                  if ( basis().baseId( c ).isSPxColId()    
                     && ( number( basis().baseId( c ) ) == col ))
                     break;

               assert( c < nRows() );
               std::cerr << "   basis idx:" << c
                         << " fVec:" << fVec()[c]
                         << " fRhs:" << fRhs()[c]
                         << " fTest:" << fTest()[c] << std::endl;
            }
         }
      }
   }
#endif   
   return status();
}

void SoPlex::testVecs()
{
   METHOD( "SoPlex::testVecs()" );
   int i;
   DVector tmp(dim());

   tmp = *theCoPvec;
   multWithBase(tmp);
   tmp -= *theCoPrhs;
   if (tmp.length() > delta())
   {
      VERBOSE3({ std::cout << iteration() << ":\tcoP error = \t"
                           << tmp.length() << std::endl; });
      tmp.clear();
      SPxBasis::coSolve(tmp, *theCoPrhs);
      multWithBase(tmp);
      tmp -= *theCoPrhs;

      VERBOSE3( std::cout << "\t\t\t" << tmp.length() << std::endl; );

      tmp.clear();
      SPxBasis::coSolve(tmp, *theCoPrhs);
      tmp -= *theCoPvec;
      
      VERBOSE3( std::cout << "\t\t\t" << tmp.length() << std::endl; );
   }

   tmp = *theFvec;
   multBaseWith(tmp);
   tmp -= *theFrhs;
   if (tmp.length() > delta())
   {
      VERBOSE3({ std::cout << iteration() << ":\t  F error = \t"
                           << tmp.length() << std::endl; });
      tmp.clear();
      SPxBasis::solve(tmp, *theFrhs);
      tmp -= *theFvec;

      VERBOSE3( std::cout << "\t\t\t" << tmp.length() << std::endl; );
   }

#ifndef NDEBUG
   if (type() == ENTER)
   {
      for (i = 0; i < dim(); ++i)
      {
         if (theCoTest[i] < -delta() && isCoBasic(i))
         {
            std::cerr << "testVecs: theCoTest: this shalt not be!"
                      << std::endl
                      << "  i=" << i 
                      << ", theCoTest[i]=" << theCoTest[i]
                      << ", delta()=" << delta() << std::endl;
         }
      }
      for (i = 0; i < coDim(); ++i)
      {
         if (theTest[i] < -delta() && isBasic(i))
         {
            std::cerr << "testVecs: theTest: this shalt not be!"
                      << std::endl
                      << "  i=" << i 
                      << ", theTest[i]=" << theTest[i]
                      << ", delta()=" << delta() << std::endl;
         }
      }
   }
#endif
}

bool SoPlex::terminate()
{
   METHOD( "SoPlex::terminate()" );
#ifndef NDEBUG
   testVecs();
#endif  // NDEBUG
#if 0
   int redo = dim();

   if (redo < 1000)
      redo = 1000;

   if (iteration() > 10 && iteration() % redo == 0)
   {
#ifndef NDEBUG
      DVector cr(*theCoPrhs);
      DVector fr(*theFrhs);
#endif  // !NDEBUG

      if (type() == ENTER)
         computeEnterCoPrhs();
      else
         computeLeaveCoPrhs();

      computeFrhs();

#ifndef NDEBUG
      cr -= *theCoPrhs;
      fr -= *theFrhs;
      if (cr.length() > delta())
         std::cerr << "unexpected change of coPrhs " 
                   << cr.length() << std::endl;
      if (fr.length() > delta())
         std::cerr << "unexpected change of   Frhs " 
                   << fr.length() << std::endl;
#endif  // !NDEBUG

      if (updateCount > 1)
         factorize();

      SPxBasis::coSolve(*theCoPvec, *theCoPrhs);
      SPxBasis::solve (*theFvec, *theFrhs);

      if (pricing() == FULL)
      {
         computePvec();
         if (type() == ENTER)
            computeTest();
      }
      if (shift() > 0)
         unShift();
   }
#endif
   if ( maxIters >= 0 && iterations() >= maxIters )
   {
      VERBOSE2({ std::cout << "Maximum number of iterations (" << maxIters
                           << ") reached" << std::endl; });
      m_status = ABORT_ITER;
      return true;
   }
   if ( maxTime >= 0 && maxTime < infinity && time() >= maxTime )
   {
      VERBOSE2({ std::cout << "Timelimit (" << maxTime
                           << ") reached" << std::endl; });
      m_status = ABORT_TIME;
      return true;   
   }
   if (maxValue < infinity)
   {
      /**@todo This code is *NOT* tested. */
         
      if( shift() < epsilon() && maxInfeas() + shift() <= delta() )
      {
         // SPxSense::MINIMIZE == -1, so we have sign = 1 on minimizing
         // rep() * type() > 0 == DUAL, -1 == PRIMAL.
         int sign = -1 * spxSense() * rep() * type();
         
         if( sign * (value() - maxValue) >= 0.0 )
         {
            VERBOSE2({ std::cout << "Objective value limit (" << maxValue
                                 << ") reached" << std::endl; });
            DEBUG({
               std::cerr << "Objective value limit reached" << std::endl
                         << " (value: " << value()
                         << ", limit: " << maxValue << ")" << std::endl
                         << " (spxSense: " << int(spxSense())
                         << ", rep: " << int(rep())
                         << ", type: " << int(type()) << std::endl;
            });
            
            m_status = ABORT_VALUE;
            return true;
         }
      }
   }

   if( SPxBasis::status() >= SPxBasis::OPTIMAL  ||
       SPxBasis::status() <= SPxBasis::SINGULAR )
   {
      m_status = UNKNOWN;
      return true;
   }
   return false;
}

SoPlex::Status SoPlex::getPrimal (Vector& p_vector) const
{
   METHOD( "SoPlex::getPrimal()" );

   assert(isInitialized());

   if (!isInitialized())
      return NOT_INIT;

   if (rep() == ROW)
      p_vector = coPvec();
   else
   {
      int i;
      const SPxBasis::Desc& ds = desc();
      for (i = nCols() - 1; i >= 0; --i)
      {
         switch (ds.colStatus(i))
         {
         case SPxBasis::Desc::P_ON_LOWER :
            p_vector[i] = SPxLP::lower(i);
            break;
         case SPxBasis::Desc::P_ON_UPPER :
         case SPxBasis::Desc::P_FIXED :
            p_vector[i] = SPxLP::upper(i);
            break;
         case SPxBasis::Desc::P_FREE :
            p_vector[i] = 0;
            break;
         case SPxBasis::Desc::D_FREE :
         case SPxBasis::Desc::D_ON_UPPER :
         case SPxBasis::Desc::D_ON_LOWER :
         case SPxBasis::Desc::D_ON_BOTH :
         case SPxBasis::Desc::D_UNDEFINED :
            break;
         default:
            abort();
         }
      }
      for (i = dim() - 1; i >= 0; --i)
      {
         if (baseId(i).isSPxColId())
            p_vector[ number(SPxColId(baseId(i))) ] = fVec()[i];
      }
   }
   return status();
}

SoPlex::Status SoPlex::getPrimalUnscaled (Vector& p_vector) const
{
   METHOD( "SoPlex::getPrimalUnscaled()" );

   Status stat = getPrimal( p_vector );

   if (thescaler != 0)
      thescaler->unscaleSolution( p_vector );

   return stat;
}

SoPlex::Status SoPlex::getDual (Vector& p_vector) const
{
   METHOD( "SoPlex::getDual()" );

   assert(isInitialized());

   if (!isInitialized())
      return NOT_INIT;

   if (rep() == ROW)
   {
      int i;
      p_vector.clear ();
      for (i = nCols() - 1; i >= 0; --i)
      {
         if (baseId(i).isSPxRowId())
            p_vector[ number(SPxRowId(baseId(i))) ] = fVec()[i];
      }
   }
   else
      p_vector = coPvec();

   p_vector *= spxSense();

   return status();
}

SoPlex::Status SoPlex::getRdCost (Vector& p_vector) const
{
   METHOD( "SoPlex::getRdCost()" );

   assert(isInitialized());

   if (!isInitialized())
      return NOT_INIT;

   if (rep() == ROW)
   {
      int i;
      p_vector.clear();
      if (spxSense() == SPxLP::MINIMIZE)
      {
         for (i = dim() - 1; i >= 0; --i)
         {
            if (baseId(i).isSPxColId())
               p_vector[ number(SPxColId(baseId(i))) ] = -fVec()[i];
         }
      }
      else
      {
         for (i = dim() - 1; i >= 0; --i)
         {
            if (baseId(i).isSPxColId())
               p_vector[ number(SPxColId(baseId(i))) ] = fVec()[i];
         }
      }
   }
   else
   {
      p_vector = maxObj();
      p_vector -= pVec();
      if (spxSense() == SPxLP::MINIMIZE)
         p_vector *= -1;
   }

   return status();
}

SoPlex::Status SoPlex::getSlacks (Vector& p_vector) const
{
   METHOD( "SoPlex::getSlacks()" );

   assert(isInitialized());

   if (!isInitialized())
      return NOT_INIT;

   if (rep() == COLUMN)
   {
      int i;
      const SPxBasis::Desc& ds = desc();
      for (i = nRows() - 1; i >= 0; --i)
      {
         switch (ds.rowStatus(i))
         {
         case SPxBasis::Desc::P_ON_LOWER :
            p_vector[i] = lhs(i);
            break;
         case SPxBasis::Desc::P_ON_UPPER :
         case SPxBasis::Desc::P_FIXED :
            p_vector[i] = rhs(i);
            break;
         case SPxBasis::Desc::P_FREE :
            p_vector[i] = 0;
            break;
         case SPxBasis::Desc::D_FREE :
         case SPxBasis::Desc::D_ON_UPPER :
         case SPxBasis::Desc::D_ON_LOWER :
         case SPxBasis::Desc::D_ON_BOTH :
         case SPxBasis::Desc::D_UNDEFINED :
            break;
         default:
            abort();
         }
      }
      for (i = dim() - 1; i >= 0; --i)
      {
         if (baseId(i).isSPxRowId())
            p_vector[ number(SPxRowId(baseId(i))) ] = -(*theFvec)[i];
      }
   }
   else
      p_vector = pVec();

   return status();
}

SoPlex::Status SoPlex::status() const
{
   METHOD( "SoPlex::status()" );
   switch( m_status )
   {
   case UNKNOWN:      
      switch (SPxBasis::status())
      {
      case SPxBasis::NO_PROBLEM :
         return NO_PROBLEM;
      case SPxBasis::SINGULAR :
         return SINGULAR;
      case SPxBasis::REGULAR :
      case SPxBasis::DUAL :
      case SPxBasis::PRIMAL :
         return UNKNOWN;
      case SPxBasis::OPTIMAL :
         return OPTIMAL;
      case SPxBasis::UNBOUNDED :
         return UNBOUNDED;
      case SPxBasis::INFEASIBLE :
         return INFEASIBLE;
      default:
         return ERROR;
      }
   case OPTIMAL:
      assert( SPxBasis::status() == SPxBasis::OPTIMAL );
      /*lint -fallthrough*/
   case ABORT_TIME:
   case ABORT_ITER:
   case ABORT_VALUE:
   case RUNNING:
   case REGULAR:
   case NOT_INIT:
   case ERROR:
      return m_status;
   default:
      return ERROR;
   }
}

SoPlex::Status SoPlex::getResult(
   Real* p_value,
   Vector* p_primal,
   Vector* p_slacks,
   Vector* p_dual,
   Vector* reduCosts) const
{
   METHOD( "SoPlex::getResult()" );
   if (p_value)
      *p_value = this->value();
   if (p_primal)
      getPrimal(*p_primal);
   if (p_slacks)
      getSlacks(*p_slacks);
   if (p_dual)
      getDual(*p_dual);
   if (reduCosts)
      getRdCost(*reduCosts);
   return status();
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
