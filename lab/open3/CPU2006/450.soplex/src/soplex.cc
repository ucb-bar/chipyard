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
#pragma ident "@(#) $Id: soplex.cpp,v 1.56 2002/04/06 13:05:02 bzfkocht Exp $"
#endif

//#define DEBUGGING 1

#include <assert.h>
#include <iostream>
#include <fstream>

#include "spxdefines.h"
#include "soplex.h"
#include "spxpricer.h"
#include "spxratiotester.h"
#include "spxstarter.h"
#include "spxscaler.h"
#include "spxsimplifier.h"


namespace soplex
{
#define MAXIMUM(x,y)        ((x)>(y) ? (x) : (y))

bool SoPlex::read(std::istream& in, NameSet* rowNames, 
                  NameSet* colNames, DIdxSet* intVars)
{
   METHOD( "SoPlex::read()" );
   clear();
   unInit();
   unLoad();

   if (thepricer)
      thepricer->clear();

   if (theratiotester)
      theratiotester->clear();

   if (!SPxLP::read(in, rowNames, colNames, intVars))
      return false;

   SPxBasis::load(this);

   int tmp = coDim() / (20 * dim()) + 1;
   coVecDim = coDim() / tmp + 1;

   return true;
}

void SoPlex::reLoad()
{
   METHOD( "SoPlex::reLoad()" );
   unInit();
   unLoad();
   theLP = this;
   if (thepricer)
      thepricer->clear();
   if (theratiotester)
      theratiotester->clear();
}

void SoPlex::loadLP(const SPxLP& lp)
{
   METHOD( "SoPlex::loadLP()" );
   clear();
   unInit();
   unLoad();
   if (thepricer)
      thepricer->clear();
   if (theratiotester)
      theratiotester->clear();
   SPxLP::operator=(lp);
   reDim();
   SPxBasis::load(this);
}

void SoPlex::setSolver(SLinSolver* slu)
{
   METHOD( "SoPlex::setSolver()" );
   SPxBasis::loadSolver(slu);
}

void SoPlex::loadBasis(const SPxBasis::Desc& p_desc)
{
   METHOD( "SoPlex::loadBasis()" );
   unInit();
   if (SPxBasis::status() == SPxBasis::NO_PROBLEM)
      SPxBasis::load(this);
   SPxBasis::loadDesc(p_desc);
}

void SoPlex::setPricer(SPxPricer* x)
{
   METHOD( "SoPlex::setPricer()" );
   if (x != 0)
   {
      setPricing(FULL);
      if (isInitialized() && x != thepricer)
         x->load(this);
      else
         x->clear();
   }
   if (thepricer && thepricer != x)
      thepricer->clear();
   thepricer = x;
}

void SoPlex::setTester(SPxRatioTester* x)
{
   METHOD( "SoPlex::setTester()" );
   if (x)
   {
      if (isInitialized() && x != theratiotester)
         x->load(this);
      else
         x->clear();
   }
   if (theratiotester && theratiotester != x)
      theratiotester->clear();
   theratiotester = x;
}

void SoPlex::setStarter(SPxStarter* x)
{
   METHOD( "SoPlex::setStarter()" );
   thestarter = x;
}

void SoPlex::setScaler(SPxScaler* x)
{
   METHOD( "SoPlex::setScaler()" );
   thescaler = x;
}

void SoPlex::setSimplifier(SPxSimplifier* x)
{
   METHOD( "SoPlex::setSimplifier()" );
   thesimplifier = x;
}


void SoPlex::setType(Type tp)
{
   METHOD( "SoPlex::setType()" );
   if (isInitialized() && theType != tp)
   {
      theType = tp;
      init();
   }
   else
   {
      theType = tp;

      if (!matrixIsSetup)
      {
         SPxBasis::load(this);
         // SPxBasis::load(desc());
         // not needed, because load(this) allready loads descriptor
      }
      factorized = false;
      m_numCycle = 0;
   }
   if ((thepricer != 0) && (thepricer->solver() == this))
      thepricer->setType(tp);
   if ((theratiotester != 0) && (theratiotester->solver() == this))
      theratiotester->setType(tp);

   DEBUG({ std::cerr << "switching to " 
                     << static_cast<const char*>((tp == LEAVE)
                                                 ? "leaving" : "entering")
                     << " algorithm" << std::endl; });
}

void SoPlex::setRep(Representation p_rep)
{
   METHOD( "SoPlex::setRep()" );
   if (p_rep == COLUMN)
   {
      thevectors = colset();
      thecovectors = rowset();
      theFrhs = &primRhs;
      theFvec = &primVec;
      theCoPrhs = &dualRhs;
      theCoPvec = &dualVec;
      thePvec = &addVec;
      theRPvec = theCoPvec;
      theCPvec = thePvec;
      theUbound = &theUCbound;
      theLbound = &theLCbound;
      theCoUbound = &theURbound;
      theCoLbound = &theLRbound;
   }
   else
   {
      assert(p_rep == ROW);

      thevectors = rowset();
      thecovectors = colset();
      theFrhs = &dualRhs;
      theFvec = &dualVec;
      theCoPrhs = &primRhs;
      theCoPvec = &primVec;
      thePvec = &addVec;
      theRPvec = thePvec;
      theCPvec = theCoPvec;
      theUbound = &theURbound;
      theLbound = &theLRbound;
      theCoUbound = &theUCbound;
      theCoLbound = &theLCbound;
   }
   therep = p_rep;
   unInit();
   reDim();

   if (SPxBasis::status() > SPxBasis::NO_PROBLEM)
   {
      SPxBasis::setRep();
      SPxBasis::loadDesc(desc());
   }

   if (thepricer && thepricer->solver() == this)
      thepricer->setRep(p_rep);
}

void SoPlex::init()
{
   METHOD( "SoPlex::init()" );
   assert(thepricer != 0);
   assert(theratiotester != 0);

   if (!initialized)
   {
      initialized = true;
      reDim();
      if (SPxBasis::status() <= SPxBasis::NO_PROBLEM || solver() != this)
         SPxBasis::load(this);
      initialized = false;
   }
   if (!matrixIsSetup)
      SPxBasis::loadDesc(desc());
   factorized = false;
   m_numCycle = 0;

   if (type() == ENTER)
   {
      if (rep() == COLUMN)
      {
         setPrimalBounds();
         setStatus(SPxBasis::PRIMAL);
      }
      else
      {
         setDualRowBounds();
         setStatus(SPxBasis::DUAL);
      }
      setEnterBounds();
      computeEnterCoPrhs();
   }
   else
   {
      if (rep() == ROW)
      {
         setPrimalBounds();
         setStatus(SPxBasis::PRIMAL);
      }
      else
      {
         setDualColBounds();
         setStatus(SPxBasis::DUAL);
      }
      setLeaveBounds();
      computeLeaveCoPrhs();
   }

   SPxBasis::coSolve(*theCoPvec, *theCoPrhs);
   computePvec();

   computeFrhs();
   SPxBasis::solve(*theFvec, *theFrhs);

   theShift = 0;
   if (type() == ENTER)
   {
      shiftFvec();
      computeCoTest();
      computeTest();
   }
   else
   {
      shiftPvec();
      computeFtest();
   }
   lastShift = theShift + delta();

   if (!initialized)
   {
      // if(thepricer->solver() != this)
      thepricer->load(this);
      // if(theratiotester->solver() != this)
      theratiotester->load(this);
      initialized = true;
   }
}

void SoPlex::setPricing(Pricing pr)
{
   METHOD( "SoPlex::setPricing()" );
   thePricing = pr;
   if (initialized && type() == ENTER)
   {
      computePvec();
      computeCoTest();
      computeTest();
   }
}

/*
    The following method resizes all vectors and arrays of |SoPlex|
    (excluding inherited vectors).
 */
void SoPlex::reDim()
{
   METHOD( "SoPlex::reDim()" );
   int newdim = (rep() == ROW) ? SPxLP::nCols() : SPxLP::nRows();

   if (dim() > 0 && coDim() > 0)
   {
      int tmp = coDim() / (20 * dim()) + 1;
      coVecDim = coDim() / tmp + 1;
   }

   if (newdim > unitVecs.size())
   {
      unitVecs.reSize (newdim);
      while (newdim-- > 0)
         unitVecs[newdim] = newdim;
   }

   if (isInitialized())
   {
      theFrhs->reDim(dim());
      theFvec->reDim(dim());
      thePvec->reDim(coDim());

      theCoPrhs->reDim(dim());
      theCoPvec->reDim(dim());

      theTest.reDim(coDim());
      theCoTest.reDim(dim());

      theURbound.reDim(SPxLP::nRows());
      theLRbound.reDim(SPxLP::nRows());
      theUCbound.reDim(SPxLP::nCols());
      theLCbound.reDim(SPxLP::nCols());
      theUBbound.reDim(dim());
      theLBbound.reDim(dim());
   }
}

bool SoPlex::readBasisFile(
   const char*    filename, 
   const NameSet& rowNames,
   const NameSet& colNames)
{
   METHOD( "SoPlex::readBasisFile()" );
   std::ifstream file(filename);

   if (!file)
      return false;
 
   return readBasis(file, rowNames, colNames);
}

bool SoPlex::readFile( 
   const char* filename, 
   NameSet*    rowNames,
   NameSet*    colNames, 
   DIdxSet*    intVars)
{
   METHOD( "SoPlex::readFile()" );
   std::ifstream file(filename);

   if (!file)
      return false;

   return read(file, rowNames, colNames, intVars);
}

void SoPlex::dumpFile(const char* filename) const
{
   METHOD( "SoPlex::dumpFile()" );
   std::ofstream file(filename);

   if (file.good())
      file << *this;
}

void SoPlex::clear()
{
   METHOD( "SoPlex::clear()" );
   unitVecs.reSize(0);

   dualRhs.clear();
   dualVec.clear();
   primRhs.clear();
   primVec.clear();
   addVec.clear();
   theURbound.clear();
   theLRbound.clear();
   theUCbound.clear();
   theLCbound.clear();
   theTest.clear();
   theCoTest.clear();

   if (thesimplifier)
      thesimplifier->unload();

   unInit();
   SPxLP::clear();
   setStatus(SPxBasis::NO_PROBLEM);
   SPxBasis::reDim();
}

void SoPlex::clearUpdateVecs(void)
{
   METHOD( "SoPlex::clearUpdateVecs()" );
   theFvec->clearUpdate();
   thePvec->clearUpdate();
   theCoPvec->clearUpdate();
   solveVector2 = 0;
   coSolveVector2 = 0;
}

/*
    When the basis matrix factorization is recomputed from scratch, we also
    recompute the vectors.
 */
void SoPlex::factorize()
{
   METHOD( "SoPlex::factorize()" );
   SPxBasis::factorize();

   if (SPxBasis::status() >= SPxBasis::REGULAR)
   {
      // #undef       NDEBUG
#ifndef NDEBUG
      DVector ftmp(fVec());
      DVector ptmp(pVec());
      DVector ctmp(coPvec());
      testVecs();
#endif  // NDEBUG

      if (type() == LEAVE)
      {
         SPxBasis::solve (*theFvec, *theFrhs);
         SPxBasis::coSolve(*theCoPvec, *theCoPrhs);
      }

#ifndef NDEBUG
      ftmp -= fVec();
      ptmp -= pVec();
      ctmp -= coPvec();
      if (ftmp.length() > delta())
      {
         DEBUG( std::cerr << "fVec:   " << ftmp.length() << std::endl; );
         ftmp = fVec();
         multBaseWith(ftmp);
         ftmp -= fRhs();
         if (ftmp.length() > delta())
            std::cerr << iteration() << ": fVec error = " 
                      << ftmp.length() << std::endl;
      }
      if (ctmp.length() > delta())
      {
         DEBUG( std::cerr << "coPvec: " << ctmp.length() << std::endl; );
         ctmp = coPvec();
         multWithBase(ctmp);
         ctmp -= coPrhs();
         if (ctmp.length() > delta())
            std::cerr << iteration() << ": coPvec error = " 
                      << ctmp.length() << std::endl;
      }
      if (ptmp.length() > delta())
      {
         DEBUG( std::cerr << "pVec:   " << ptmp.length() << std::endl; );
      }
#endif  // NDEBUG

      if (type() == ENTER)
      {
         computeCoTest();
         /*
                     if(pricing() == FULL)
                     {
                         computePvec();
                         computeTest();
                     }
         */
      }
      else
      {
         computeFtest();
         //          computePvec();
      }

   }
}

Real SoPlex::maxInfeas() const
{
   METHOD( "SoPlex::maxInfeas()" );
   int i;
   Real inf = 0.0;

   if (type() == ENTER)
   {
      for(i = 0; i < dim(); i++)
      {
         if ((*theFvec)[i] > theUBbound[i])
            inf = MAXIMUM(inf, (*theFvec)[i] - theUBbound[i]);
         if (theLBbound[i] > (*theFvec)[i])
            inf = MAXIMUM(inf, theLBbound[i] - (*theFvec)[i]);
      }
   }
   else
   {
      assert(type() == LEAVE);

      for(i = 0; i < dim(); i++)
      {
         if ((*theCoPvec)[i] > (*theCoUbound)[i])
            inf = MAXIMUM(inf, (*theCoPvec)[i] - (*theCoUbound)[i]);
         if ((*theCoLbound)[i] > (*theCoPvec)[i])
            inf = MAXIMUM(inf, (*theCoLbound)[i] - (*theCoPvec)[i]);
      }
      for(i = 0; i < coDim(); i++)
      {
         if ((*thePvec)[i] > (*theUbound)[i])
            inf = MAXIMUM(inf, (*thePvec)[i] - (*theUbound)[i]);
         else if ((*thePvec)[i] < (*theLbound)[i])
            inf = MAXIMUM(inf, (*theLbound)[i] - (*thePvec)[i]);
      }
   }

   return inf;
}

Real SoPlex::nonbasicValue() const
{
   METHOD( "SoPlex::nonbasicValue()" );

   int i;
   Real val = 0;
   const SPxBasis::Desc& ds = desc();

   if (rep() == COLUMN)
   {
      if (type() == LEAVE)
      {
         for (i = nCols() - 1; i >= 0; --i)
         {
            switch (ds.colStatus(i))
            {
            case SPxBasis::Desc::P_ON_UPPER :
               val += theUCbound[i] * SPxLP::upper(i);
               //@ val += maxObj(i) * SPxLP::upper(i);
               break;
            case SPxBasis::Desc::P_ON_LOWER :
               val += theLCbound[i] * SPxLP::lower(i);
               //@ val += maxObj(i) * SPxLP::lower(i);
               break;
            case SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER :
               val += maxObj(i) * SPxLP::lower(i);
               break;
            default:
               break;
            }
         }
         for (i = nRows() - 1; i >= 0; --i)
         {
            switch (ds.rowStatus(i))
            {
            case SPxBasis::Desc::P_ON_UPPER :
               val += theLRbound[i] * SPxLP::rhs(i);
               break;
            case SPxBasis::Desc::P_ON_LOWER :
               val += theURbound[i] * SPxLP::lhs(i);
               break;
            default:
               break;
            }
         }
      }
      else
      {
         assert(type() == ENTER);
         for (i = nCols() - 1; i >= 0; --i)
         {
            switch (ds.colStatus(i))
            {
            case SPxBasis::Desc::P_ON_UPPER :
               val += maxObj(i) * theUCbound[i];
               break;
            case SPxBasis::Desc::P_ON_LOWER :
               val += maxObj(i) * theLCbound[i];
               break;
            case SPxBasis::Desc::P_ON_UPPER + SPxBasis::Desc::P_ON_LOWER :
               assert(theLCbound[i] == theUCbound[i]);
               val += maxObj(i) * theLCbound[i];
               break;
            default:
               break;
            }
         }
      }
   }
   else
   {
      assert(rep() == ROW);
      assert(type() == ENTER);
      for (i = nCols() - 1; i >= 0; --i)
      {
         switch (ds.colStatus(i))
         {
         case SPxBasis::Desc::D_ON_UPPER :
            val += theUCbound[i] * lower(i);
            break;
         case SPxBasis::Desc::D_ON_LOWER :
            val += theLCbound[i] * upper(i);
            break;
         case SPxBasis::Desc::D_ON_BOTH :
            val += theLCbound[i] * upper(i);
            val += theUCbound[i] * lower(i);
            break;
         default:
            break;
         }
      }
      for (i = nRows() - 1; i >= 0; --i)
      {
         switch (ds.rowStatus(i))
         {
         case SPxBasis::Desc::D_ON_UPPER :
            val += theURbound[i] * lhs(i);
            break;
         case SPxBasis::Desc::D_ON_LOWER :
            val += theLRbound[i] * rhs(i);
            break;
         case SPxBasis::Desc::D_ON_BOTH :
            val += theLRbound[i] * rhs(i);
            val += theURbound[i] * lhs(i);
            break;
         default:
            break;
         }
      }
   }

   return val;
}

Real SoPlex::value() const
{
   METHOD( "SoPlex::value()" );
   Real x;

   /**@todo patch suggests returning infinity instead of initializing 
    */
   if (!isInitialized())      
      (const_cast<SoPlex*>(this))->init();

   if (rep() == ROW)
   {
      if (type() == LEAVE)
         x = SPxLP::spxSense() * (coPvec() * fRhs());
      else
         x = SPxLP::spxSense() * (nonbasicValue() + (coPvec() * fRhs()));
   }
   else
      x = SPxLP::spxSense() * (nonbasicValue() + fVec() * coPrhs());

   if (thesimplifier)
      return thesimplifier->value(x);

   return x;
}

void SoPlex::setDelta(Real d)
{
   METHOD( "SoPlex::setDelta()" );
   thedelta = d;
}

SoPlex::SoPlex(
   Type            p_type, 
   Representation  p_rep,
   SPxPricer*      pric, 
   SPxRatioTester* rt,
   SPxStarter*     start, 
   SPxScaler*      scaler, 
   SPxSimplifier*  simpli)
   : theType (p_type)
   , thePricing(FULL)
   , maxIters (-1)
   , maxTime (infinity)
   , maxValue(infinity)
   , m_status(UNKNOWN)
   , theShift (0)
   , m_maxCycle(100)
   , m_numCycle(0)
   , initialized (false)
   , solveVector2 (0)
   , coSolveVector2(0)
   , cacheProductFactor(4.0)
   , unitVecs (0)
   , primVec (0, Param::epsilon())
   , dualVec (0, Param::epsilon())
   , addVec (0, Param::epsilon())
   , thepricer (pric)
   , theratiotester(rt)
   , thestarter (start)
   , thescaler (scaler)
   , thesimplifier (simpli)
{
   METHOD( "SoPlex::SoPlex()" );
   setRep (p_rep);
   setDelta (DEFAULT_BND_VIOL);
   theLP = this;
   coVecDim = 400;
}

/* We forbid the copyconstructor and the assignment operator
 * untill we are sure the work and we have an idea what exactly
 * they should be used to. Why would somebody copy an SoPlex Object?
 */
#if 0 
SoPlex::SoPlex(const SoPlex& old)
   : SPxLP (old)
   , SPxBasis (old)
   , theType (old.theType)
   , thePricing (old.thePricing)
   , therep (old.therep)  /// ??? siehe unten
   , maxIters (old.maxIters)
   , maxTime (old.maxTime)
   , maxValue(old.maxValue)
   , theShift (old.theShift)
   , m_maxCycle (old.m_maxCycle)
   , m_numCycle (old.m_numCycle)
   , initialized (old.initialized)
   , solveVector2 (0)
   , coSolveVector2 (0)
   , cacheProductFactor(old.cacheProductFactor)
   , unitVecs (old.unitVecs)
   , primRhs (old.primRhs)
   , primVec (old.primVec)
   , dualRhs (old.dualRhs)
   , dualVec (old.dualVec)
   , addVec (old.addVec)
   , theURbound (old.theURbound)
   , theLRbound (old.theLRbound)
   , theUCbound (old.theUCbound)
   , theLCbound (old.theLCbound)
   , theUBbound (old.theUBbound)
   , theLBbound (old.theLBbound)
   , theCoTest (old.theCoTest)
   , theTest (old.theTest)
   , thepricer (old.thepricer)
   , theratiotester (old.theratiotester)
   , thestarter (old.thestarter)
   , thescaler (old.thescaler)
   , thesimplifier (old.thesimplifier)
{
   METHOD( "SoPlex::SoPlex()" );
   setRep (old.rep());
   setDelta(old.thedelta);
   coVecDim = 400;
   theLP = this;
}

SoPlex& SoPlex::operator=(const SoPlex& old)
{
   METHOD( "SoPlex::operator=()" );
   *(static_cast<SPxLP*>(this)) = old;
   *(static_cast<SPxBasis*>(this)) = old;

   therep = old.therep;
   unitVecs = old.unitVecs;
   theCoTest = old.theCoTest;
   theTest = old.theTest;
   theType = old.theType;
   thePricing = old.thePricing;
   primRhs = old.primRhs;
   primVec = old.primVec;
   dualRhs = old.dualRhs;
   dualVec = old.dualVec;
   addVec = old.addVec;
   theURbound = old.theURbound;
   theLRbound = old.theLRbound;
   theUCbound = old.theUCbound;
   theLCbound = old.theLCbound;
   theUBbound = old.theUBbound;
   theLBbound = old.theLBbound;
   m_maxCycle = old.m_maxCycle;
   m_numCycle = old.m_numCycle;
   theShift = old.theShift;
   initialized = old.initialized;
   cacheProductFactor = old.cacheProductFactor;
   thepricer = old.thepricer;
   theratiotester = old.theratiotester;
   thestarter = old.thestarter;
   thescaler = old.thescaler;
   thesimplifier = old.thesimplifier;
   solveVector2 = 0;
   coSolveVector2 = 0;

   setRep (old.rep());
   setDelta(old.thedelta);

   theLP = this;
   return *this;
}
#endif // no copy constructor and assignment operator

bool SoPlex::isConsistent() const
{
   METHOD( "SoPlex::isConsistent()" );
   if (epsilon() < 0)
      return MSGinconsistent("SoPlex");

   if (primVec.delta().epsilon != dualVec.delta().epsilon)
      return MSGinconsistent("SoPlex");

   if (dualVec.delta().epsilon != addVec.delta().epsilon)
      return MSGinconsistent("SoPlex");

   if (unitVecs.size() < ((rep() == ROW) ? SPxLP::nCols() : SPxLP::nRows()))
      return MSGinconsistent("SoPlex");

   if (initialized)
   {
      if (theFrhs->dim() != dim())
         return MSGinconsistent("SoPlex");
      if (theFvec->dim() != dim())
         return MSGinconsistent("SoPlex");

      if (theCoPrhs->dim() != dim())
         return MSGinconsistent("SoPlex");
      if (thePvec->dim() != coDim())
         return MSGinconsistent("SoPlex");
      if (theCoPvec->dim() != dim())
         return MSGinconsistent("SoPlex");

      if (theTest.dim() != coDim())
         return MSGinconsistent("SoPlex");
      if (theCoTest.dim() != dim())
         return MSGinconsistent("SoPlex");

      if (theURbound.dim() != SPxLP::nRows())
         return MSGinconsistent("SoPlex");
      if (theLRbound.dim() != SPxLP::nRows())
         return MSGinconsistent("SoPlex");
      if (theUCbound.dim() != SPxLP::nCols())
         return MSGinconsistent("SoPlex");
      if (theLCbound.dim() != SPxLP::nCols())
         return MSGinconsistent("SoPlex");
      if (theUBbound.dim() != dim())
         return MSGinconsistent("SoPlex");
      if (theLBbound.dim() != dim())
         return MSGinconsistent("SoPlex");
   }

   if (rep() == COLUMN)
   {
      if(thecovectors != 
         reinterpret_cast<const SVSet*>(static_cast<const LPRowSet*>(this)) 
         || thevectors != 
         reinterpret_cast<const SVSet*>(static_cast<const LPColSet*>(this)) 
         || theFrhs != &primRhs ||
         theFvec != &primVec ||
         theCoPrhs != &dualRhs ||
         theCoPvec != &dualVec ||
         thePvec != &addVec ||
         theRPvec != theCoPvec ||
         theCPvec != thePvec ||
         theUbound != &theUCbound ||
         theLbound != &theLCbound ||
         theCoUbound != &theURbound ||
         theCoLbound != &theLRbound)
         return MSGinconsistent("SoPlex");
   }
   else
   {
      if (thecovectors 
         != reinterpret_cast<const SVSet*>(static_cast<const LPColSet*>(this))
         || thevectors 
         != reinterpret_cast<const SVSet*>(static_cast<const LPRowSet*>(this))
         || theFrhs != &dualRhs ||
         theFvec != &dualVec ||
         theCoPrhs != &primRhs ||
         theCoPvec != &primVec ||
         thePvec != &addVec ||
         theRPvec != thePvec ||
         theCPvec != theCoPvec ||
         theUbound != &theURbound ||
         theLbound != &theLRbound ||
         theCoUbound != &theUCbound ||
         theCoLbound != &theLCbound)
         return MSGinconsistent("SoPlex");
   }

   return SPxLP::isConsistent()
          && primRhs.isConsistent()
          && primVec.isConsistent()
          && dualRhs.isConsistent()
          && dualVec.isConsistent()
          && addVec.isConsistent()
          && theTest.isConsistent()
          && theCoTest.isConsistent()
          && theURbound.isConsistent()
          && theLRbound.isConsistent()
          && theUCbound.isConsistent()
          && theLCbound.isConsistent()
          && SPxBasis::isConsistent()
         ;
}

int SoPlex::nofNZEs() const
{
   METHOD( "SoPlex::nofNZEs()" );
   int n = 0;
   for (int i = nCols(); --i >= 0;)
      n += colVector(i).size();
   return n;
}

void SoPlex::setTerminationTime(Real p_time)
{
   METHOD( "SoPlex::setTerminationTime()" );
   if( p_time < 0.0 )
      p_time = infinity;
   maxTime = p_time;
}

Real SoPlex::terminationTime() const
{
   METHOD( "SoPlex::terminationTime()" );
   return maxTime;
}

void SoPlex::setTerminationIter(int p_iteration)
{
   METHOD( "SoPlex::setTerminationIter()" );
   if( p_iteration < 0 )
      p_iteration = -1;
   maxIters = p_iteration;
}

int SoPlex::terminationIter() const
{
   METHOD( "SoPlex::terminationIter()" );
   return maxIters;
}

/**@todo Terminationvalue should be implemented. The Problem is that
 *       with allowing bound violations (shifting) it is quite 
 *       difficult to determine if we allready reached the limit.
 */
void SoPlex::setTerminationValue(Real /*p_value*/)
{
   METHOD( "SoPlex::setTerminationValue()" );
   std::cerr << "setTerminationValue not yet implemented" << std::endl;
   //maxValue = p_value;
}

Real SoPlex::terminationValue() const
{
   METHOD( "SoPlex::terminationValue()" );
   return maxValue;
}
   
SoPlex::VarStatus
SoPlex::basisStatusToVarStatus( SPxBasis::Desc::Status stat ) const
{
   METHOD( "SoPlex::VarStatus()" );
   VarStatus vstat;

   switch( stat )
   {
   case SPxBasis::Desc::P_ON_LOWER:
      vstat = ON_LOWER;
      break;
   case SPxBasis::Desc::P_ON_UPPER:
      vstat = ON_UPPER;
      break;
   case SPxBasis::Desc::P_FIXED:
      vstat = FIXED;
      break;
   case SPxBasis::Desc::P_FREE:
      vstat = ZERO;
      break;
   case SPxBasis::Desc::D_ON_UPPER:
   case SPxBasis::Desc::D_ON_LOWER:
   case SPxBasis::Desc::D_ON_BOTH:
   case SPxBasis::Desc::D_UNDEFINED:
   case SPxBasis::Desc::D_FREE:
      vstat = BASIC;
      break;
   default:
      std::cerr << "ERROR: unknown basis status (" << stat << ")" << std::endl;
      abort();
   }
   return vstat;
}

SPxBasis::Desc::Status
SoPlex::varStatusToBasisStatusRow( int row, SoPlex::VarStatus stat ) const
{
   METHOD( "SoPlex::varStatusToBasisStatusRow()" );
   SPxBasis::Desc::Status rstat;

   switch( stat )
   {
   case FIXED :
      assert(rhs(row) == lhs(row));
      rstat = SPxBasis::Desc::P_FIXED;
      break;
   case ON_UPPER :
      assert(rhs(row) < infinity);
      rstat = lhs(row) < rhs(row)
         ? SPxBasis::Desc::P_ON_UPPER
         : SPxBasis::Desc::P_FIXED;
      break;
   case ON_LOWER :
      assert(lhs(row) > -infinity);
      rstat = lhs(row) < rhs(row)
         ? SPxBasis::Desc::P_ON_LOWER
         : SPxBasis::Desc::P_FIXED;
      break;
   case ZERO :
      assert(lhs(row) <= -infinity && rhs(row) >= infinity);
      rstat = SPxBasis::Desc::P_FREE;
      break;
   case BASIC :
      rstat = dualRowStatus(row);
      break;
   default:
      std::cerr << "ERROR: unknown VarStatus (" << int(stat)
                << ")" << std::endl;
      abort();
   }
   return rstat;
}

SPxBasis::Desc::Status 
SoPlex::varStatusToBasisStatusCol( int col, SoPlex::VarStatus stat ) const
{
   METHOD( "SoPlex::varStatusToBasisStatusCol()" );
   SPxBasis::Desc::Status cstat;

   switch( stat )
   {
   case FIXED :
      assert(upper(col) == lower(col));
      cstat = SPxBasis::Desc::P_FIXED;
      break;
   case ON_UPPER :
      assert(upper(col) < infinity);
      cstat = lower(col) < upper(col)
         ? SPxBasis::Desc::P_ON_UPPER
         : SPxBasis::Desc::P_FIXED;
      break;
   case ON_LOWER :
      assert(lower(col) > -infinity);
      cstat = lower(col) < upper(col)
         ? SPxBasis::Desc::P_ON_LOWER
         : SPxBasis::Desc::P_FIXED;
      break;
   case ZERO :
      assert(lower(col) <= -infinity && upper(col) >= infinity);
      cstat = SPxBasis::Desc::P_FREE;
      break;
   case BASIC :
      cstat = dualColStatus(col);
      break;
   default:
      std::cerr << "ERROR: unknown VarStatus (" << int(stat)
                << ")" << std::endl;
      abort();
   }
   return cstat;
}

SoPlex::VarStatus SoPlex::getBasisRowStatus( int row ) const
{
   METHOD( "SoPlex::VarStatus()" );
   assert( 0 <= row && row < nRows() );
   return basisStatusToVarStatus( desc().rowStatus( row ) );
}

SoPlex::VarStatus SoPlex::getBasisColStatus( int col ) const
{
   METHOD( "SoPlex::VarStatus()" );
   assert( 0 <= col && col < nCols() );
   return basisStatusToVarStatus( desc().colStatus( col ) );
}

SoPlex::Status SoPlex::getBasis(VarStatus row[], VarStatus col[]) const
{
   METHOD( "SoPlex::Status()" );
   const SPxBasis::Desc& d = desc();
   int i;

   if (col)
      for (i = nCols() - 1; i >= 0; --i)
         col[i] = basisStatusToVarStatus( d.colStatus(i) );

   if (row)
      for (i = nRows() - 1; i >= 0; --i)
         row[i] = basisStatusToVarStatus( d.rowStatus(i) );

   return status();
}

void SoPlex::setBasis(const VarStatus p_rows[], const VarStatus p_cols[])
{
   METHOD( "SoPlex::setBasis()" );
   if (SPxBasis::status() == SPxBasis::NO_PROBLEM)
      SPxBasis::load(this);

   SPxBasis::Desc ds = desc();
   int i;

   for(i = 0; i < nRows(); i++)
      ds.rowStatus(i) = varStatusToBasisStatusRow( i, p_rows[i] );

   for(i = 0; i < nCols(); i++)
      ds.colStatus(i) = varStatusToBasisStatusCol( i, p_cols[i] );

   loadBasis(ds);
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
