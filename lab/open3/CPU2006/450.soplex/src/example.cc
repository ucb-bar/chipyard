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
#pragma ident "@(#) $Id: example.cpp,v 1.39 2002/04/06 13:05:02 bzfkocht Exp $"
#endif

#if defined(__riscv) && !defined(__linux)
/* FIXME: Avoid writing output file */
#undef SPEC_CPU
#endif

#include <assert.h>
#include <math.h>
#include <string.h>
#include <iostream>
#include <iomanip>
#include <fstream>

#include "spxdefines.h"
#include "spxsolver.h"

#include "timer.h"
#include "spxpricer.h"
#include "spxdefaultpr.h"
#include "spxparmultpr.h"
#include "spxdevexpr.h"
#include "spxhybridpr.h"
#include "spxsteeppr.h"
#include "spxweightpr.h"
#include "spxratiotester.h"
#include "spxharrisrt.h"
#include "spxdefaultrt.h"
#include "spxfastrt.h"
#include "spxsimplifier.h"
#include "spxaggregatesm.h"
#include "spxredundantsm.h"
#include "spxrem1sm.h"
#include "spxgeneralsm.h"
#include "spxscaler.h"
#include "spxequilisc.h"
#include "spxsumst.h"
#include "spxweightst.h"
#include "spxvectorst.h"
#include "slufactor.h"

std::ofstream out_file;
char *out_filename;

using namespace soplex;

/** Here comes a simple derived class from #SoPlex, which uses #terminate() as
 *  callback method for outputting statistics.
 */
class MySoPlex : public SPxSolver
{
private:
   SLUFactor m_slu;
   int       m_iter_limit;

public:
   /// default constructor
   MySoPlex(Type p_type = LEAVE, Representation p_rep = COLUMN)
      : SPxSolver(p_type, p_rep)
   {}
   void displayQuality()
   {
      double maxviol;
      double sumviol;

      std::cout << "Violations (max/sum)" << std::endl;
                
      qualConstraintViolation(maxviol, sumviol);

      std::cout << "Constraints      :" 
                << std::setw(16) << maxviol << "  " 
                << std::setw(16) << sumviol << std::endl;

      qualConstraintViolationUnscaled(maxviol, sumviol);

      std::cout << "      (unscaled) :" 
                << std::setw(16) << maxviol << "  " 
                << std::setw(16) << sumviol << std::endl;

      qualBoundViolation(maxviol, sumviol);

      std::cout << "Bounds           :" 
                << std::setw(16) << maxviol << "  " 
                << std::setw(16) << sumviol << std::endl;

      qualBoundViolationUnscaled(maxviol, sumviol);

      std::cout << "      (unscaled) :" 
                << std::setw(16) << maxviol << "  " 
                << std::setw(16) << sumviol << std::endl;

      qualSlackViolation(maxviol, sumviol);

      std::cout << "Slacks           :" 
                << std::setw(16) << maxviol << "  " 
                << std::setw(16) << sumviol << std::endl;

      // qualRdCostViolation(maxviol, sumviol);

      //std::cout << "Reduced costs    :" 
      //          << std::setw(16) << maxviol << "  " 
      //          << std::setw(16) << sumviol << std::endl;
   }
};

int main(int argc, char **argv)
{
   const char* banner =
   "************************************************************************\n"
   "*                                                                      *\n"
   "*       SoPlex --- the Sequential object-oriented simPlex.             *\n"
   "*                  Release 1.2.1                                       *\n"
   "*    Copyright (C) 1997-1999 Roland Wunderling                         *\n"
   "*                  1997-2002 Konrad-Zuse-Zentrum                       *\n"
   "*                            fuer Informationstechnik Berlin           *\n"
   "*                                                                      *\n"
   "*  SoPlex is distributed under the terms of the ZIB Academic Licence.  *\n"
   "*  You should have received a copy of the ZIB Academic License         *\n"
   "*  along with SoPlex; If not email to soplex@zib.de.                   *\n"
   "*                                                                      *\n"
   "************************************************************************\n"
   ;

   const char* usage =
   "[options] LPfile\n\n"
   "          LPfile can be either in MPS or LPF format\n\n"
   "options:  (*) indicates default\n" 
   " -e        select leaving algorithm (default is entering)\n"
   " -r        select row wise representation (default is column)\n"
   " -i        select Eta-update (default is Forest-Tomlin)\n"
   " -x        output solution vector (works only together with -s0)\n"
   " -q        display solution quality\n"
   " -bBasfile read file with starting basis\n"
   " -lSec     set timelimit to Sec seconds\n"
   " -mIters   set iteration limit to Iter iterations\n"
   " -dDelta   set maximal allowed bound violation to Delta\n"
   " -zZero    set zero tolerance to Zero\n\n"
   " -vLevel   set verbosity Level [0-3], default 1\n"
   " -V        show program version\n"
   " -h        show this help\n"
   "Simplifier:     Scaler:         Starter:     Pricer:        Ratiotester:\n"
   " -s0 none*      -g0 none*        -c0 none    -p0 Textbook  -t0 Textbook\n"
   " -s1 General    -g1 Bi-Equi      -c1 Weight* -p1 ParMult   -t1 Harris\n"
   " -s2 Aggregate  -g2 Uni-Equi     -c2 Sum     -p2 Devex     -t2 Fast*\n"
   " -s3 Remove-1                    -c3 Vector  -p3 Hybrid\n"
   " -s4 Redundant                               -p4 Steep*\n"
   "                                             -p5 Weight\n" 
   ;

   const char*            filename;
   char*                  basisname      = 0;
   SoPlex::Type           type           = SoPlex::ENTER;
   SoPlex::Representation representation = SoPlex::COLUMN;
   SLUFactor::UpdateType  update         = SLUFactor::FOREST_TOMLIN;
   SPxSimplifier*         simplifier     = 0;
   SPxStarter*            starter        = 0;
   SPxPricer*             pricer         = 0;
   SPxRatioTester*        ratiotester    = 0;
   SPxScaler*             scaler         = 0;
   NameSet                rownames;
   NameSet                colnames;
   int                    starting       = 1;
   int                    pricing        = 4;
   int                    ratiotest      = 2;
   int                    scaling        = 0;
   int                    simplifing     = 0;
   int                    iterlimit      = -1;
   Real                   timelimit      = -1.0;
   Real                   delta          = DEFAULT_BND_VIOL;
   Real                   epsilon        = DEFAULT_EPS_ZERO;
   int                    verbose        = 1;
   bool                   print_solution = false;
   bool                   print_quality  = false;
   int                    precision;
   int                    optidx;

   for(optidx = 1; optidx < argc; optidx++)
   {
      if (*argv[optidx] != '-')
         break;

      switch(argv[optidx][1])
      {
      case 'b' :
         basisname = strcpy(
            new char[strlen(&argv[optidx][1]) + 1], &argv[optidx][1]); 
         break;
      case 'c' :
         starting = atoi(&argv[optidx][2]);
         break;
      case 'd' :
         delta = atof(&argv[optidx][2]);
         break;
      case 'e':
         type = SoPlex::LEAVE;
         break;
      case 'g' :
         scaling = atoi(&argv[optidx][2]);
         break;
      case 'i' :
         update = SLUFactor::ETA;
         break;
      case 'l' :
         timelimit = atof(&argv[optidx][2]);
         break;
      case 'm' :
         iterlimit = atoi(&argv[optidx][2]);
         break;
      case 'p' :
         pricing = atoi(&argv[optidx][2]);
         break;
      case 'q' :
         print_quality = true;
         break;
      case 'r' :
         representation = SoPlex::ROW;
         break;
      case 's' :
         simplifing = atoi(&argv[optidx][2]);
         break;
      case 't' :
         ratiotest = atoi(&argv[optidx][2]);
         break;
      case 'v' :
         verbose = atoi(&argv[optidx][2]);
         break;
      case 'V' :
         std::cout << banner << std::endl;
         exit(0);
      case 'x' :
         print_solution = true;
         break;
      case 'z' :
         epsilon = atof(&argv[optidx][2]);
         break;
      case 'h' :
      case '?' :
         std::cout << banner << std::endl;
         /*FALLTHROUGH*/
      default :
         std::cerr << "usage: " << argv[0] << " " << usage << std::endl;
         exit(0);
      }
   }
   if ((argc - optidx) < 1)
   {
      std::cerr << "usage: " << argv[0] << " " << usage << std::endl;
      exit(0);
   }
   filename  = argv[optidx];
#if defined(SPEC_CPU)
   out_filename = strcpy(new char[strlen(filename) + 6], filename);
   strcat(out_filename, ".info");
   out_file.open(out_filename, std::ios::trunc);
   if (!out_file.is_open())
   {
      std::cerr << "Couldn't open \"" << out_filename << "\" for writing." << std::endl;
      exit(1);
   }
#endif
   precision = int(-log10(delta)) + 1;

   Param::setEpsilon(epsilon);
   Param::setVerbose(verbose);

   std::cout.setf(std::ios::scientific | std::ios::showpoint);

   MySoPlex work(type, representation);

   work.setUtype(update);
   work.setTerminationTime(timelimit);
   work.setTerminationIter(iterlimit);
   work.setDelta(delta);

   std::cout << "Delta   = " << std::setw(16) << delta << std::endl
             << "Epsilon = " << std::setw(16) 
             << Param::epsilon() << std::endl;

   assert(work.isConsistent());

   std::cout << (type == SoPlex::ENTER ? "Entering" : "Leaving")
             << " algorithm" 
             << std::endl
             << (representation == SoPlex::ROW ? "Row" : "Column")
             << " representation" 
             << std::endl
             << (update == SLUFactor::ETA ? "Eta" : "Forest-Tomlin")
             << " update"
             << std::endl;

   switch(pricing)
   {
   case 5 :
      pricer = new SPxWeightPR;
      break;
   case 4 :
      pricer = new SPxSteepPR;
      break;
   case 3 :
      pricer = new SPxHybridPR;
      break;
   case 2 :
      pricer = new SPxDevexPR;
      break;
   case 1 :
      pricer = new SPxParMultPR;
      break;
   case 0 : 
      /*FALLTHROUGH*/
   default :
      pricer = new SPxDefaultPR;
      break;
   }
   work.setPricer(pricer);

   std::cout << pricer->getName() << " pricing" << std::endl;
   assert(work.isConsistent());

   switch(ratiotest)
   {
   case 2 :
      ratiotester = new SPxFastRT;
      std::cout << "Fast";
      break;
   case 1 :
      ratiotester = new SPxHarrisRT;
      std::cout << "Harris";
      break;
   case 0 :
      /*FALLTHROUGH*/
   default:
      ratiotester = new SPxDefaultRT;
      std::cout << "Default";
      break;
   }
   std::cout << " ratiotest" << std::endl;
   work.setTester(ratiotester);
   assert(work.isConsistent());

   switch(scaling)
   {
   case 2 :
      scaler = new SPxEquili(representation == SoPlex::COLUMN, false);
      break;
   case 1 :
      scaler = new SPxEquili(representation == SoPlex::COLUMN, true);
      break;
   case 0 : 
      /*FALLTHROUGH*/
   default :
      scaler = 0;
      std::cout << "No";
      break;
   }
   work.setScaler(scaler);

   if (scaler != 0) 
      std::cout << scaler->getName();

   std::cout << " scaling" << std::endl;
   assert(work.isConsistent());

   switch(simplifing)
   {
   case 4 : 
      simplifier = new SPxRedundantSM;
      std::cout << "Redundant";
      break;
   case 3 : 
      simplifier = new SPxRem1SM;
      std::cout << "Remove 1";
      break;
   case 2 :
      simplifier = new SPxAggregateSM;
      std::cout << "Aggregate";
      break;
   case 1 :
      simplifier = new SPxGeneralSM;
      std::cout << "General";
      break;
   case 0  :
      /*FALLTHROUGH*/
   default :
      std::cout << "No";
   }
   std::cout << " simplifier" << std::endl;
   work.setSimplifier(simplifier);
   assert(work.isConsistent());

   switch(starting)
   {
   case 3 :
      starter = new SPxVectorST;
      std::cout << "Vector";
      break;
   case 2 :
      starter = new SPxSumST;
      std::cout << "Sum";
      break;
   case 1 :
      starter = new SPxWeightST;
      std::cout << "Weight";
      break;
   case 0 :
      /*FALLTHROUGH*/
   default :
      std::cout << "No";
      break;
   }
   std::cout << " starter" << std::endl;
   work.setStarter(starter);
   assert(work.isConsistent());

   Timer timer;
   std::cout << "loading LP file " << filename << std::endl;

   if (!work.readFile(filename, &rownames, &colnames))
   {
      std::cout << "error while reading file \"" 
                << filename << "\"" << std::endl;
      exit(1);
   }
   assert(work.isConsistent());

   std::cout << "LP has " 
             << work.nRows() 
             << "\trows and\n       "
             << work.nCols() 
             << "\tcolumns" 
             << std::endl;

   // Should we read a basis ?
   if (basisname != 0)
   {
      if (!work.readBasisFile(basisname, rownames, colnames))
      {
         std::cout << "error while reading file \"" 
                   << basisname << "\"" << std::endl;
         exit(1);
      }
   }
   timer.start();
   std::cout << "solving LP" 
             << std::endl;

   work.solve();

   timer.stop();

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
   out_file << "Factorizations: " << work.getFactorCount() << std::endl;
#else
   std::cout << "Factorizations: " << work.getFactorCount() << std::endl;
#endif

   SoPlex::Status stat = work.status();

   switch (stat)
   {
   case SoPlex::OPTIMAL:
      std::cout << "solution value is: "
                << std::setprecision(precision)
                << work.value()
                << std::endl;

      if (print_quality)
         work.displayQuality();

      if (print_solution)
      {
         if (simplifier != 0)
         {
            std::cerr 
               << "Output of solution vector with simplifier not implemented"
               << " (use -s0 switch)."
               << std::endl;
         }      
         else
         {
            DVector objx(work.nCols());
            
            work.getPrimalUnscaled(objx);
            
            for(int i = 0; i < work.nCols(); i++)
               if (isNotZero(objx[i], epsilon))
                  std::cout << colnames[work.cId(i)] << "\t" 
                            << std::setw(16)
                            << std::setprecision(precision)
                            << objx[i] << std::endl;
            std::cout << "All other variable are zero." << std::endl;
         }
      }
      break;
   case SoPlex::UNBOUNDED:
      std::cout << "LP is unbounded";
      break;
   case SoPlex::INFEASIBLE:
      std::cout << "LP is infeasible";
      break;
   case SoPlex::ABORT_TIME:
      std::cout << "aborted due to time limit";
      break;
   case SoPlex::ABORT_ITER:
      std::cout << "aborted due to iteration limit"
                << std::endl 
                << "solution value is: "
                << std::setprecision(precision)
                << work.value()
                << std::endl;
      break;
   case SoPlex::ABORT_VALUE:
      std::cout << "aborted due to objective value limit";
      break;
   default:
      std::cout << "An error occurred during the solution process";
      break;
   }
   std::cout << std::endl;

   if (scaler != 0)
      delete scaler;
   if (simplifier != 0)
      delete simplifier;
   if (starter != 0)
      delete starter;

   assert(pricer != 0);
   delete pricer;

   assert(ratiotester != 0);
   delete ratiotester;

   if (basisname != 0)
      delete [] basisname;

   return 0;
}

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
