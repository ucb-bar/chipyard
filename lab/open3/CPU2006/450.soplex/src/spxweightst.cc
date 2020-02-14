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
#pragma ident "@(#) $Id: spxweightst.cpp,v 1.14 2002/03/21 16:06:19 bzfkocht Exp $"
#endif

//#define DEBUGGING 1
//#define TEST 1

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "spxweightst.h"
#include "svset.h"
#include "sorter.h"

namespace soplex
{
#define EPS     1e-6
#define STABLE  1e-3

bool SPxWeightST::isConsistent() const
{
   return rowWeight.isConsistent()
          && colWeight.isConsistent()
          && rowRight.isConsistent()
          && colUp.isConsistent()
          && SPxStarter::isConsistent();
}

/* Generating the Starting Basis
   The generation of a starting basis works as follows: After setting up the
   preference arrays #weight# and #coWeight#, #Id#s are selected to be dual in
   a greedy manner.  Initially, the first #Id# is taken. Then the next #Id# is
   checked wheter its vector is linearly dependend of the vectors of the #Id#s
   allready selected.  If not, it is added to them. This is iterated until a
   full matrix has been constructed.
 
   Testing for linear independence is done very much along the lines of LU
   factorization. A vector is taken, and updated with all previous L-vectors.
   Then the maximal absolut element is selected as pivot element for computing
   the L-vector. This is stored for further processing.
 */

/*
    The following two functions set the status of |id| to primal or dual,
    respectively.
 */
void SPxWeightST::setPrimalStatus(
   SPxBasis::Desc& desc, 
   const SoPlex& base, 
   const SPxId& id)
{
   if (id.isSPxRowId())
   {
      int n = base.number(SPxRowId(id));

      if (base.rhs(n) >= infinity)
      {
         if (base.lhs(n) <= -infinity)
            desc.rowStatus(n) = SPxBasis::Desc::P_FREE;
         else
            desc.rowStatus(n) = SPxBasis::Desc::P_ON_LOWER;
      }
      else
      {
         if (base.lhs(n) <= -infinity)
            desc.rowStatus(n) = SPxBasis::Desc::P_ON_UPPER;
         else if (base.lhs(n) >= base.rhs(n) - base.epsilon())
            desc.rowStatus(n) = SPxBasis::Desc::P_FIXED;
         else if (rowRight[n])
            desc.rowStatus(n) = SPxBasis::Desc::P_ON_UPPER;
         else
            desc.rowStatus(n) = SPxBasis::Desc::P_ON_LOWER;
      }
   }
   else
   {
      int n = base.number(SPxColId(id));
      if (base.SPxLP::upper(n) >= infinity)
      {
         if (base.SPxLP::lower(n) <= -infinity)
            desc.colStatus(n) = SPxBasis::Desc::P_FREE;
         else
            desc.colStatus(n) = SPxBasis::Desc::P_ON_LOWER;
      }
      else
      {
         if (base.SPxLP::lower(n) <= -infinity)
            desc.colStatus(n) = SPxBasis::Desc::P_ON_UPPER;
         else if (base.SPxLP::lower(n) >= base.SPxLP::upper(n) - base.epsilon())
            desc.colStatus(n) = SPxBasis::Desc::P_FIXED;
         else if (colUp[n])
            desc.colStatus(n) = SPxBasis::Desc::P_ON_UPPER;
         else
            desc.colStatus(n) = SPxBasis::Desc::P_ON_LOWER;
      }
   }
}

static void setDualStatus(
   SPxBasis::Desc& desc, 
   const SoPlex& base, 
   const SPxId& id)
{
   if (id.isSPxRowId())
   {
      int n = base.number(SPxRowId(id));
      desc.rowStatus(n) = base.basis().dualRowStatus(n);
   }
   else
   {
      int n = base.number(SPxColId(id));
      desc.colStatus(n) = base.basis().dualColStatus(n);
   }
}

/*
   The following method initializes |pref| such that it contains the set of
   |SPxId|s ordered following |rowWeight| and |colWeight|. For the sorting
   we take the following approach: first we sort the rows, then the columns.
   Finally we perform a mergesort of both.
 */
struct Compare
{
   const SoPlex* base;
   const Real* weight;
   Real operator()(int i1, int i2)
   {
      return weight[i1] - weight[i2];
   }
};

static void initPrefs(DataArray < SPxId > & pref,
                       const SoPlex& base,
                       const DataArray < Real > & rowWeight,
                       const DataArray < Real > & colWeight
                    )
{
   int i, j, k;
   DataArray < int > row(base.nRows());
   DataArray < int > col(base.nCols());

   Compare compare;
   compare.base = &base;

   for (i = base.nRows(); i--;)
      row[i] = i;
   compare.weight = rowWeight.get_const_ptr();

   sorter_qsort(row.get_ptr(), row.size(), compare); // Sort rows

   for (i = base.nCols(); i--;)
      col[i] = i;
   compare.weight = colWeight.get_const_ptr();

   sorter_qsort(col.get_ptr(), col.size(), compare); // Sort column

   for (i = j = k = 0; k < pref.size();)            // merge sort
   {
      if (rowWeight[row[i]] < colWeight[col[j]])
      {
         pref[k++] = base.rId(row[i++]);
         if (i >= base.nRows())
            while (k < pref.size())
               pref[k++] = base.cId(col[j++]);
      }
      else
      {
         pref[k++] = base.cId(col[j++]);
         if (j >= base.nCols())
            while (k < pref.size())
               pref[k++] = base.rId(row[i++]);
      }
   }
   assert(i == base.nRows());
   assert(j == base.nCols());
}

void SPxWeightST::generate(SoPlex& base)
{
   SPxId tmpId;

   forbidden.reSize(base.dim());
   rowWeight.reSize(base.nRows());
   colWeight.reSize(base.nCols());
   rowRight.reSize (base.nRows());
   colUp.reSize (base.nCols());

   if (base.rep() == SoPlex::COLUMN)
   {
      weight   = &colWeight;
      coWeight = &rowWeight;
   }
   else
   {
      weight   = &rowWeight;
      coWeight = &colWeight;
   }
   assert(weight->size()   == base.coDim());
   assert(coWeight->size() == base.dim());

   setupWeights(base);

   SPxBasis::Desc desc;
   desc.reSize(base.nRows(), base.nCols());

   DataArray < SPxId > pref(base.nRows() + base.nCols());
   initPrefs(pref, base, rowWeight, colWeight);

   int i, deltai, j, sel;
   for (i = forbidden.size(); --i >= 0;)
      forbidden[i] = 0;

   if (base.rep() == SoPlex::COLUMN)
   {
      i = 0;
      deltai = 1;
   }
   else
   {
      i = pref.size() - 1;
      deltai = -1;
   }
   {
      int dim = base.dim();
      Real max = 0;

      for (; i >= 0 && i < pref.size(); i += deltai)
      {
         tmpId = pref[i];
         const SVector& bVec = base.vector(tmpId);
         sel = -1;
         if (bVec.size() == 1)
         {
            int idx = bVec.index(0);
            if (forbidden[idx] < 2)
            {
               sel = idx;
               dim += (forbidden[idx] > 0);
            }
         }
         else
         {
            max = bVec.maxAbs();

            int best = base.nRows();
            for (j = bVec.size(); --j >= 0;)
            {
               Real x = bVec.value(j);
               int k = bVec.index(j);
               int l = base.coVector(k).size();
               if (!forbidden[k] && (fabs(x) > STABLE * max) && (l < best))
               {
                  best = l;
                  sel = k;
               }
            }
         }

         if (sel >= 0)
         {
            DEBUG({
               if (pref[i].type() == SPxId::ROW_ID)
                  std::cerr << " r" << base.number(pref[i]);
               else
                  std::cerr << " c" << base.number(pref[i]);
            });

            forbidden[sel] = 2;
            if (base.rep() == SoPlex::COLUMN)
               setDualStatus(desc, base, pref[i]);
            else
               setPrimalStatus(desc, base, pref[i]);

            for (j = bVec.size(); --j >= 0;)
            {
               Real x = bVec.value(j);
               int k = bVec.index(j);
               if (!forbidden[k] && (x > EPS*max || -x > EPS*max))
               {
                  forbidden[k] = 1;
                  --dim;
               }
            }

            if (--dim == 0)
            {
               //@ for(++i; i < pref.size(); ++i)
               if (base.rep() == SoPlex::COLUMN)
               {
                  for (i += deltai; i >= 0 && i < pref.size(); i += deltai)
                     setPrimalStatus(desc, base, pref[i]);
                  for (i = forbidden.size(); --i >= 0;)
                  {
                     if (forbidden[i] < 2)
                        setDualStatus(desc, base, base.coId(i));
                  }
               }
               else
               {
                  for (i += deltai; i >= 0 && i < pref.size(); i += deltai)
                     setDualStatus(desc, base, pref[i]);
                  for (i = forbidden.size(); --i >= 0;)
                  {
                     if (forbidden[i] < 2)
                        setPrimalStatus(desc, base, base.coId(i));
                  }
               }
               break;
            }
         }
         else if (base.rep() == SoPlex::COLUMN)
            setPrimalStatus(desc, base, pref[i]);
         else
            setDualStatus(desc, base, pref[i]);
#ifndef NDEBUG
         {
            int n, m;
            for (n = 0, m = forbidden.size(); n < forbidden.size(); ++n)
               m -= (forbidden[n] != 0);
            assert(m == dim);
         }
#endif  // NDEBUG
      }
      assert(dim == 0);
   }

   base.loadBasis(desc);
#ifdef  TEST
   base.init();

   int changed = 0;
   const Vector& pvec = base.pVec();
   for (i = pvec.dim() - 1; i >= 0; --i)
   {
      if (desc.colStatus(i) == SPxBasis::Desc::P_ON_UPPER
           && base.lower(i) > -infinity
           && pvec[i] > base.maxObj(i))
      {
         changed = 1;
         desc.colStatus(i) = SPxBasis::Desc::P_ON_LOWER;
      }
      else if (desc.colStatus(i) == SPxBasis::Desc::P_ON_LOWER
                && base.upper(i) < infinity
                && pvec[i] < base.maxObj(i))
      {
         changed = 1;
         desc.colStatus(i) = SPxBasis::Desc::P_ON_UPPER;
      }
   }

   if (changed)
   {
      std::cout << "changed basis\n";
      base.loadBasis(desc);
   }
   else
      std::cout << "nothing changed\n";
#endif  // TEST
}


/* Computation of Weights
 */
void SPxWeightST::setupWeights(SoPlex& bse)
{
   const SoPlex& base = bse;
   const Vector& obj  = bse.maxObj();
   const Vector& low  = bse.SPxLP::lower();
   const Vector& up   = bse.SPxLP::upper();
   const Vector& rhs  = bse.rhs();
   const Vector& lhs  = bse.lhs();
   int    i;

   Real maxabs = 1.0;

   // find absolut biggest entry in bounds and left-/right hand side
   for (i = 0; i < bse.nCols(); i++)
   {
      if ((up[i] < infinity) && (fabs(up[i]) > maxabs))
         maxabs = fabs(up[i]);
      
      if ((low[i] > -infinity) && (fabs(low[i]) > maxabs))
         maxabs = fabs(low[i]);
   }
   for (i = 0; i < bse.nRows(); i++)
   {
      if ((rhs[i] < infinity) && (fabs(rhs[i]) > maxabs))
         maxabs = fabs(rhs[i]);
      
      if ((lhs[i] > -infinity) && (fabs(lhs[i]) > maxabs))
         maxabs = fabs(lhs[i]);
   }

   /**@todo The comments are wrong. The first is for dual simplex and
    *       the secound for primal one. Is anything else wrong?
    *       Also the values are nearly the same for both cases.
    *       Should this be ? Changed the values for
    *       r_fixed to 0 because of maros-r7. It is not clear why
    *       this makes a difference because all inequalites in that
    *       instance are of equality type.
    *       Why is rowRight sometimes not set?
    */
   if (bse.rep() * bse.type() > 0)            // primal simplex
   {
      const Real bx            = 1.0 / maxabs;
      const Real ax            = 1e-3 / obj.maxAbs();
      const Real nne           = ax / lhs.dim();  // 1e-4 * ax;
      const Real c_fixed       = 1e+5;
      const Real r_fixed       = 0; // TK20010103: was 1e+4 (maros-r7)
      const Real c_dbl_bounded = 1e+1;
      const Real r_dbl_bounded = 0;
      const Real c_bounded     = 1e+1;
      const Real r_bounded     = 0;
      const Real c_free        = -1e+4;
      const Real r_free        = -1e+5;

      for (i = bse.nCols() - 1; i >= 0; i--)
      {
         Real n = nne * (bse.colVector(i).size() - 1);
         Real x = ax * obj[i];
         Real u = bx * up [i];
         Real l = bx * low[i];

         if (up[i] < infinity)
         {
            if (fabs(low[i] - up[i]) < base.epsilon())
               colWeight[i] = c_fixed + n + fabs(x);
            else if (low[i] > -infinity)
            {
               colWeight[i] = c_dbl_bounded + l - u + n;

               l = fabs(l);
               u = fabs(u);

               if (u < l)
               {
                  colUp[i]      = true;
                  colWeight[i] += x;
               }
               else
               {
                  colUp[i]      = false;
                  colWeight[i] -= x;
               }
            }
            else
            {
               colWeight[i] = c_bounded - u + x + n;
               colUp[i]     = true;
            }
         }
         else
         {
            if (low[i] > -infinity)
            {
               colWeight[i] = c_bounded + l + n - x;
               colUp[i]     = false;
            }
            else
            {
               colWeight[i] = c_free + n - fabs(x);
            }
         }
      }

      for (i = bse.nRows() - 1; i >= 0; i--)
      {
         if (rhs[i] < infinity)
         {
            if (fabs(lhs[i] - rhs[i]) < base.epsilon())
            {
               rowWeight[i] = r_fixed;
            }
            else if (lhs[i] > -infinity)
            {
               Real u = bx * rhs[i];
               Real l = bx * lhs[i];

               rowWeight[i] = r_dbl_bounded + l - u;
               rowRight[i]  = fabs(u) < fabs(l);
            }
            else
            {
               rowWeight[i] = r_bounded - bx * rhs[i];
               rowRight[i]  = true;
            }
         }
         else
         {
            if (lhs[i] > -infinity)
            {
               rowWeight[i] = r_bounded + bx * lhs[i];
               rowRight[i]  = false;
            }
            else
            {
               rowWeight[i] = r_free;
            }
         }
      }
   }
   else
   {
      assert(bse.rep() * bse.type() < 0);           // dual simplex

      const Real ax            = 1.0  / obj.maxAbs();
      const Real bx            = 1e-2 / maxabs;
      const Real nne           = 1e-4 * bx;
      const Real c_fixed       = 1e+5;
      const Real r_fixed       = 1e+4;
      const Real c_dbl_bounded = 1;
      const Real r_dbl_bounded = 0;
      const Real c_bounded     = 0;
      const Real r_bounded     = 0;
      const Real c_free        = -1e+4;
      const Real r_free        = -1e+5;

      for (i = bse.nCols() - 1; i >= 0; i--)
      {
         Real n = nne * (bse.colVector(i).size() - 1);
         Real x = ax  * obj[i];
         Real u = bx  * up [i];
         Real l = bx  * low[i];

         if (up[i] < infinity)
         {
            if (fabs(low[i] - up[i]) < base.epsilon())
               colWeight[i] = c_fixed + n + fabs(x);
            else if (low[i] > -infinity)
            {
               if (x > 0)
               {
                  colWeight[i] = c_dbl_bounded + x - u + n;
                  colUp[i]     = true;
               }
               else
               {
                  colWeight[i] = c_dbl_bounded - x + l + n;
                  colUp[i]     = false;
               }
            }
            else
            {
               colWeight[i] = c_bounded - u + x + n;
               colUp[i]     = true;
            }
         }
         else
         {
            if (low[i] > -infinity)
            {
               colWeight[i] = c_bounded - x + l + n;
               colUp[i]     = false;
            }
            else
               colWeight[i] = c_free + n - fabs(x);
         }
      }

      for (i = bse.nRows() - 1; i >= 0; i--)
      {
         const Real len1 = 1; // (bse.rowVector(i).length() + bse.epsilon());
         Real n    = 0;  // nne * (bse.rowVector(i).size() - 1);
         Real u    = bx * len1 * rhs[i];
         Real l    = bx * len1 * lhs[i];
         Real x    = ax * len1 * (obj * bse.rowVector(i));

         if (rhs[i] < infinity)
         {
            if (fabs(lhs[i] - rhs[i]) < base.epsilon())
               rowWeight[i] = r_fixed + n + fabs(x);
            else if (lhs[i] > -infinity)
            {
               if (x > 0)
               {
                  rowWeight[i] = r_dbl_bounded + x - u + n;
                  rowRight[i]  = true;
               }
               else
               {
                  rowWeight[i] = r_dbl_bounded - x + l + n;
                  rowRight[i]  = false;
               }
            }
            else
            {
               rowWeight[i] = r_bounded - u + n + x;
               rowRight[i]  = true;
            }
         }
         else
         {
            if (lhs[i] > -infinity)
            {
               rowWeight[i] = r_bounded + l + n - x;
               rowRight[i]  = false;
            }
            else
            {
               rowWeight[i] = r_free + n - fabs(x);
            }
         }
      }
   }
   DEBUG({
      for(i = 0; i < bse.nCols(); i++)
         std::cerr << "C i= " << i 
                   << " up= " << colUp[i]
                   << " w= " << colWeight[i]
                   << std::endl;
      for(i = 0; i < bse.nRows(); i++)
         std::cerr << "R i= " << i 
                   << " rr= " << rowRight[i]
                   << " w= " << rowWeight[i]
                   << std::endl;
   });
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
