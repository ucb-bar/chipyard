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
#pragma ident "@(#) $Id: clufactor.h,v 1.13 2002/03/03 13:50:31 bzfkocht Exp $"
#endif

#ifndef _CLUFACTOR_H_
#define _CLUFACTOR_H_

#include "spxdefines.h"
#include "slinsolver.h"
#include "svector.h"

#define WITH_L_ROWS 1

namespace soplex
{

/**todo I think that CLUFactor should be a descendent of SLinSolver.
        This has to be muddled out with SLUFactor also.
        Probably CLUFactor should be inlined in SLUFactor.
 */
class CLUFactor
{
public:
   /** Double linked ring structure for garbage collection of column or
    *  row file in working matrix.
    */
   struct Dring
   {  
      Dring* next; 
      Dring* prev;
      int    idx;
   };

   /// Pivot Ring
   class Pring
   {
   public:
      Pring* next;
      Pring* prev;
      int   idx;            ///< index of pivot row
      int   pos;            ///< position of pivot column in row 
      int   mkwtz;          ///< markowitz number of pivot 

      Pring() : next(0), prev(0) 
      {}      

   private:
      Pring(const Pring&);
      Pring& operator= (const Pring&);
   };
protected:
   /// Temporary data structures.
   class Temp 
   {
   public: 
      int*    s_mark;
      Real*   s_max;        ///< maximum absolute value per row (or -1) 
      int*    s_cact;       ///< lengths of columns of active submatrix 
      int     stage;           
      Pring   pivots;       ///< ring of selected pivot rows 
      Pring*  pivot_col;    ///< column index handlers for Real linked list 
      Pring*  pivot_colNZ;  ///< lists for columns to number of nonzeros      
      Pring*  pivot_row;    ///< row index handlers for Real linked list 
      Pring*  pivot_rowNZ;  ///< lists for rows to number of nonzeros

      Temp();
      ~Temp();
      void init(int p_dim);
      void clear();

   private:
      Temp( const Temp& );
      Temp& operator= ( const Temp& );
   };

   /// Data structures for saving the row and column permutations.
   struct Perm
   {
      int* orig;          ///< orig[p] original index from p 
      int* perm;          ///< perm[i] permuted index from i 
   };

   /// Data structures for saving the working matrix and U factor.
   struct U
   {
      struct Row
      {
         Dring list;         /* Double linked ringlist of vector
                                indices in the order they appear
                                in the row file */
         Dring *elem;        /* Array of ring elements.            */
         int size;           /* size of arrays val and idx         */
         int used;           /* used entries of arrays idx and val */
         Real *val;          /* hold nonzero values                */
         int *idx;           /* hold nonzero indices               */
         int *start;         /* starting positions in val and idx  */
         int *len;           /* used nonzeros per row vectors      */
         int *max;           /* maximum available nonzeros per row:
                                start[i] + max[i] == start[elem[i].next->idx] 
                                len[i] <= max[i]. */
      } row;
      struct Col
      {
         Dring list;         /* Double linked ringlist of vector
                                indices in the order they appear
                                in the column file */
         Dring *elem;        /* Array of ring elements.            */
         int size;           /* size of array idx                  */
         int used;           /* used entries of array idx          */
         int *idx;           /* hold nonzero indices               */
         Real *val;          /* hold nonzero values: this is only initialized
                                in the end of the factorization with DEFAULT
                                updates. */
         int *start;         /* starting positions in val and idx  */
         int *len;           /* used nonzeros per column vector    */
         int *max;           /* maximum available nonzeros per colunn:
                                start[i] + max[i] == start[elem[i].next->idx] 
                                len[i] <= max[i]. */
      } col;
      int lastColSing;       /* stage of last eliminated column singleton */
      int lastRowSing;       /* stage of last eliminated row singleton */
   };


   /// Data structures for saving the working matrix and U factor.
   struct L
   {
      int size;           /* size of arrays val and idx        */
      Real *val;          /* values of L vectors               */
      int *idx;           /* indices of L vectors              */
      int startSize;      /* size of array start               */
      int firstUpdate;    /* number of first update L vector   */
      int firstUnused;    /* number of first unused L vector   */
      int *start;         /* starting positions in val and idx */
      int *row;           /* column indices of L vectors       */
      int updateType;     /* type of updates to be used.       */

      /* The following arrays have length |firstUpdate|, since they keep
       * rows of the L-vectors occuring during the factorization (without
       * updates), only:
       */
      Real *rval;         /* values of rows of L               */
      int *ridx;          /* indices of rows of L              */
      int *rbeg;          /* start of rows in rval and ridx    */
      int *rorig;         /* original row permutation          */
      int *rperm;         /* original row permutation          */
   };

   SLinSolver::Status stat;   ///< Status indicator.

   int     thedim;            ///< dimension of factorized matrix   
   int     nzCnt;             ///< number of nonzeros in U      
   Real    initMaxabs;        ///< maximum abs number in initail Matrix 
   Real    maxabs;            ///< maximum abs number in L and U        

   Real    rowMemMult;        ///< factor of minimum Memory * #of nonzeros 
   Real    colMemMult;        ///< factor of minimum Memory * #of nonzeros 
   Real    lMemMult;          ///< factor of minimum Memory * #of nonzeros 

   Perm    row;               ///< row permutation matrices 
   Perm    col;               ///< column permutation matrices 

   L       l;                 ///< L matrix 
   Real*   diag;              ///< Array of pivot elements          
   U       u;                 ///< U matrix 

   Real*   work;              ///< Working array: must always be left as 0! 
   Real*   work2;             ///< Working array: must always be left as 0! 

   int     factorCount;

private:
   Temp    temp;              ///< Temporary storage

   // From solve.cpp
   ///
   void solveUright(Real* wrk, Real* vec);
   ///
   int  solveUrightEps(Real* vec, int* nonz, Real eps, Real* rhs);
   ///
   void solveUright2(Real* work1, Real* vec1, Real* work2, Real* vec2);
   ///
   int  solveUright2eps(Real* work1, Real* vec1, Real* work2, 
      Real* vec2, int* nonz, Real eps);
   ///
   void solveLright2(Real* vec1, Real* vec2);
   ///
   void solveUpdateRight(Real* vec);
   ///
   void solveUpdateRight2(Real* vec1, Real* vec2);
   ///
   void solveUleft(Real* work, Real* vec);
   ///
   void solveUleft2(
      Real* work1, Real* vec1, Real* work2, Real* vec2);
   ///
   int solveLleft2forest(Real* vec1, int* /* nonz */,
      Real* vec2, Real /* eps */);
   ///
   void solveLleft2(Real* vec1, int* /* nonz */,
      Real* vec2, Real /* eps */);
   ///
   int solveLleftForest(Real* vec, int* /* nonz */, Real /* eps */);
   ///
   void solveLleft(Real* vec);
   ///
   int solveLleftEps(Real* vec, int* nonz, Real eps);
   ///
   void solveUpdateLeft(Real* vec);
   ///
   void solveUpdateLeft2(Real* vec1, Real* vec2);

   // From vsolve.cpp 
   ///
   int vSolveLright(Real* vec, int* ridx, int rn, Real eps);
   void vSolveLright2(Real* vec, int* ridx, int* rnptr, Real eps,
                      Real* vec2, int* ridx2, int* rn2ptr, Real eps2);
   ///
   int vSolveUright(Real* vec, int* vidx,
                    Real* rhs, int* ridx, int rn, Real eps);
   ///
   void vSolveUrightNoNZ(Real* vec,
                         Real* rhs, int* ridx, int rn, Real eps);
   ///
   int vSolveUright2(Real* vec, int* vidx, Real* rhs, 
                     int* ridx, int rn, Real eps,
                     Real* vec2, Real* rhs2, int* ridx2, int rn2, Real eps2);
   ///
   int vSolveUpdateRight(Real* vec, int* ridx, int n, Real eps);
   ///
   void vSolveUpdateRightNoNZ(Real* vec, Real /*eps*/);
   ///
   int solveUleft(Real eps, Real* vec, int* vecidx,
                  Real* rhs, int* rhsidx, int rhsn);
   ///
   void solveUleftNoNZ(Real eps, Real* vec,
                       Real* rhs, int* rhsidx, int rhsn);
   ///
   int solveLleftForest(Real eps, Real* vec, int* nonz, int n);
   ///
   void solveLleftForestNoNZ(Real* vec);
   ///
   int solveLleft(Real eps, Real* vec, int* nonz, int rn);
   ///
   void solveLleftNoNZ(Real* vec);
   ///
   int solveUpdateLeft(Real eps, Real* vec, int* nonz, int n);
   
   // from forest.cpp
   ///
   void forestPackColumns();
   ///
   void forestMinColMem(int size);
   ///
   void forestReMaxCol(int col, int len);
   
   // from factor.cpp
   ///
   void initPerm();
   ///
   void initFactorMatrix(SVector** vec, const Real eps );
   ///
   void minLMem(int size);
   ///
   void setPivot(const int p_stage, const int p_col, 
                 const int p_row, const Real val);
   ///
   void colSingletons();
   ///
   void rowSingletons();

   ///
   void initFactorRings();
   ///
   void freeFactorRings();
      
   ///
   int setupColVals();
   ///
   void setupRowVals();

   ///
   void eliminateRowSingletons();
   ///
   void eliminateColSingletons();
   ///
   void selectPivots(Real threshold);
   ///
   int updateRow(int r, int lv, int prow, int pcol, Real pval, Real eps);

   ///
   void eliminatePivot(int prow, int pos, Real eps);
   ///
   void eliminateNucleus(const Real eps, const Real threshold);
   ///
   void minRowMem(int size);
   ///
   void minColMem(int size);
   ///
   void remaxCol(int p_col, int len);
   ///
   void packRows();
   ///
   void packColumns();
   ///
   void remaxRow(int p_row, int len);
   ///
   int makeLvec(int p_len, int p_row);

   /// no copy construtor.
   CLUFactor(const CLUFactor&);
   /// no assignment operator.
   CLUFactor& operator=(const CLUFactor&);

protected:
   /// default construtor. 
   /** Since there is no sense in constructing a CLUFactor object
    *  per se, this is protected.
    */
   CLUFactor() {}

public:
   // From solve.cpp 
   ///
   void solveLright(Real* vec);
   ///
   int  solveRight4update(Real* vec, int* nonz, Real eps, Real* rhs,
                          Real* forest, int* forestNum, int* forestIdx);
   ///
   void solveRight(Real* vec, Real* rhs);
   ///
   int  solveRight2update(Real* vec1, Real* vec2, Real* rhs1,
                          Real* rhs2, int* nonz, Real eps, Real* forest,
                          int* forestNum, int* forestIdx);
   ///
   void solveRight2(Real* vec1, Real* vec2, Real* rhs1, Real* rhs2);
   ///
   void solveLeft(Real* vec, Real* rhs);
   ///
   int solveLeftEps(Real* vec, Real* rhs, int* nonz, Real eps);
   ///
   int solveLeft2(Real* vec1, int* nonz, Real* vec2, 
      Real eps, Real* rhs1, Real* rhs2);

   // From vsolve.cpp: Very sparse solution methods.
   ///
   int vSolveRight4update(Real eps, Real* vec, int* idx,     /* result       */
                          Real* rhs, int* ridx, int rn,      /* rhs & Forest */
                          Real* forest, int* forestNum, int* forestIdx);
   ///
   int vSolveRight4update2(Real eps,
                           Real* vec, int* idx,              /* result1 */
                           Real* rhs, int* ridx, int rn,     /* rhs1    */
                           Real* vec2, Real eps2,            /* result2 */
                           Real* rhs2, int* ridx2, int rn2,  /* rhs2    */
                           Real* forest, int* forestNum, int* forestIdx);
   ///
   void vSolveRightNoNZ(Real* vec2, Real eps2,              /* result2 */
                        Real* rhs2, int* ridx2, int rn2);   /* rhs2    */
   ///
   int vSolveLeft(Real eps,
                  Real* vec, int* idx,                      /* result */
                  Real* rhs, int* ridx, int rn);            /* rhs    */
   ///
   void vSolveLeftNoNZ(Real eps,
                       Real* vec,                           /* result */
                       Real* rhs, int* ridx, int rn);       /* rhs    */
   ///
   int vSolveLeft2(Real eps,
                   Real* vec, int* idx,                     /* result */
                   Real* rhs, int* ridx, int rn,            /* rhs    */
                   Real* vec2,                              /* result2 */
                   Real* rhs2, int* ridx2, int rn2);        /* rhs2    */

   // from forest.cpp
   void forestUpdate(int col, Real* work, int num, int *nonz);

   // from update.cpp
   void update(int p_col, Real* p_work, const int* p_idx, int num);
   void updateNoClear(int p_col, const Real* p_work,
                      const int* p_idx, int num);


   // from factor.cpp
   ///
   void factor(SVector** vec,     ///< Array of column vector pointers  
               Real threshold,    ///< pivoting threshold                
               Real eps);         ///< epsilon for zero detection        

   ///
   void dump() const;
   ///
   bool isConsistent() const;
};

} // namespace soplex
#endif // _CLUFACTOR_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
