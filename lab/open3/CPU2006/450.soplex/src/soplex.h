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
#pragma ident "@(#) $Id: soplex.h,v 1.47 2002/04/09 07:07:48 bzfkocht Exp $"
#endif

/**@file  soplex.h
 * @brief Sequential Objectoriented simPlex
 */
#ifndef _SOPLEX_H_
#define _SOPLEX_H_

#include <assert.h>

#include "spxdefines.h"
#include "timer.h"
#include "spxlp.h"
#include "spxbasis.h"
#include "array.h"
#include "random.h"
#include "unitvector.h"
#include "updatevector.h"

namespace soplex
{
class SPxPricer;
class SPxRatioTester;
class SPxStarter;
class SPxScaler;
class SPxSimplifier;

/**@brief   Sequential objectoriented simPlex.
   @ingroup Algo

   #SoPlex is an LP solver class using the revised Simplex algorithm. It
   provids two basis representations, namely a column basis and a row basis
   (see #Representation). For both representations, a primal and
   dual algorithm is available (see \Ref{Type}).
 
   In addition, #SoPlex can be custumized with various respects:
   - pricing algorithms using #SPxPricer
   - ratio test using class #SPxRatioTester
   - computation of a start basis using class #SPxStarter
   - preprocessing of the LP using class #SPxSimplifier
   - termination criteria by overriding 
 
   #SoPlex is derived from #SPxLP that is used to store the LP to be solved.
   Hence, the LPs solved with #SoPlex have the general format
 
   \f[
   \begin{array}{rl}
       \hbox{max}   & \mbox{maxObj}^T x                 \\
       \hbox{s.t.}  & \mbox{lhs} \le Ax \le \mbox{rhs}  \\
                    & \mbox{low} \le x  \le \mbox{up}
   \end{array}
   \f]
 
   Also, #SPxLP provide all manipulation methods for the LP. They allow
   #SoPlex to be used within cutting plane algorithms. (@see Programming)

   @todo We need a quiet or verbose variable to select the amount of
         information messages we give.
*/
class SoPlex : public SPxLP, protected SPxBasis
{
   friend class SPxFastRT;

public:
   /**@name Data Types */
   //@{
   /// LP basis representation.
   /** Solving LPs with the Simplex algorithm requires the definition of a
    *  \em basis. A basis can be defined as a set of column vectors or a
    *  set of row vectors building a nonsingular matrix. We will refer to
    *  the first case as the \em columnwise representation and the latter
    *  case will be called the \em rowwise representation.
    *
    *  Type #Representation determines the representation of #SoPlex, i.e.
    *  a columnwise (#COLUMN == 1) or rowwise (#ROW == -1) one.
    */
   enum Representation  
   {
      ROW    = -1,  ///< rowwise representation.
      COLUMN =  1   ///< columnwise representation.
   };

   /// Algorithmic type.
   /** #SoPlex uses the reviesed Simplex algorithm to solve LPs.
    *  Mathematically, one distinguishes the \em primal from the
    *  \em dual algorihm. Algorithmically, these relate to the two
    *  types #ENTER or #LEAVE. How they relate, depends on the chosen
    *  basis representation. This is desribed by the following table:
    *
    *  <TABLE>
    *  <TR><TD>&nbsp;</TD><TD>ENTER </TD><TD>LEAVE </TD></TR>
    *  <TR><TD>ROW   </TD><TD>DUAL  </TD><TD>PRIMAL</TD></TR>
    *  <TR><TD>COLUMN</TD><TD>PRIMAL</TD><TD>DUAL  </TD></TR>
    *  </TABLE>
    */
   enum Type
   {
      /// Entering Simplex.
      /** The Simplex loop for the entering Simplex can be sketched
       *  as follows:
       *  - \em Pricing : Select a variable to #ENTER the basis.
       *  - \em Ratio-Test : Select variable to #LEAVE the
       *    basis such that the basis remains feasible.
       *  - Perform the basis update.
       */
      ENTER = -1,
      /// Leaving Simplex.
      /** The Simplex loop for the leaving Simplex can be sketched
       *  as follows:
       *  - \em Pricing: Select a variable to #LEAVE the basis.
       *  - \em Ratio-Test: Select variable to #ENTER the
       *    basis such that the basis remains priced.
       *  - Perform the basis update.
       */
      LEAVE = 1
   };

   /// Pricing type.
   /** In case of the #ENTER%ing Simplex algorithm, for performance
    *  reasons it may be advisable not to compute and maintain up to
    *  date vectors #pVec() and #test() and instead compute only some
    *  of its elements explicitely. This is constroled by the #Pricing type.
    */
   enum Pricing
   {
      /// Full pricing.
      /** If #FULL pricing in selected for the #ENTER%ing Simplex,
       *  vectors #pVec() and #test() are kept up to date by
       *  #SoPlex. An #SPxPricer only needs to select an #Id such
       *  that the #test() or #coTest() value is < 0.
       */
      FULL,
      /// Partial pricing.
      /** When #PARTIAL pricing in selected for the #ENTER%ing
       *  Simplex, vectors #pVec() and #test() are not set up and
       *  updated by #SoPlex. However, vectors #coPvec() and
       *  #coTest() are still kept up to date by #SoPlex.
       *  An #SPxPricer object needs to compute the values for
       *  #pVec() and #test() itself in order to select an
       *  appropriate pivot with #test() < 0. Methods #computePvec(i)
       *  and #computeTest(i) will assist the used to do so. Note,
       *  that it may be feasable for a pricer to return an #Id with
       *  #test() > 0; such will be rejected by #SoPlex.
       */
      PARTIAL  
   };

   enum VarStatus
   {
      ON_UPPER,      ///< variable set to its upper bound.
      ON_LOWER,      ///< variable set to its lower bound.
      FIXED,         ///< variable fixed to identical bounds.
      ZERO,          ///< free variable fixed to zero.
      BASIC          ///< variable is basic.
   };

   /**@todo In spxchange and at loadbasis, change the status to
            if (m_status > 0) m_status = REGULAR;
     */
   enum Status
   {
      ERROR       = -9,  ///< an error occured.
      NOT_INIT    = -8,  ///< not initialised error
      ABORT_TIME  = -7,  ///< #solve() aborted due to time limit.
      ABORT_ITER  = -6,  ///< #solve() aborted due to iteration limit.
      ABORT_VALUE = -5,  ///< #solve() aborted due to objective limit.
      SINGULAR    = -4,  ///< Basis is singular, numerical troubles?
      NO_PROBLEM  = -3,  ///< No Problem has been loaded.
      REGULAR     = -2,  ///< LP has a usable Basis (maybe LP is changed).
      RUNNING     = -1,  ///< algorithm is running
      UNKNOWN     =  0,  ///< nothing known on loaded problem.
      OPTIMAL     =  1,  ///< LP has been solved to optimality.
      UNBOUNDED   =  2,  ///< LP has been proven to be primal unbounded.
      INFEASIBLE  =  3   ///< LP has been proven to be primal infeasible.
   };
   //@}

private:
   Type           theType;     ///< entering or leaving algortihm.
   Pricing        thePricing;  ///< full or partial pricing.
   Representation therep;      ///< row or column representation.
   Timer          theTime;
   int            maxIters;    ///< maximum allowed iterations.
   Real           maxTime;     ///< maximum allowed time.
   Real           maxValue;    ///< maximum allowed objective value.
   Status         m_status;    ///< status of algorithm.

   Real           thedelta;    ///< maximum allowed bound violation
   Real           theShift;    ///< shift of r/lhs or objective.
   Real           lastShift;   ///< for forcing feasibility.
   int            m_maxCycle;  ///< maximum steps before cycling is detected.
   int            m_numCycle;  ///< actual number of degenerate steps so far.
   bool           initialized; ///< true, if all vectors are setup.

   Vector*        solveVector2;      ///< when 2 systems are to solve at a time
   SSVector*      solveVector2rhs;   ///< when 2 systems are to solve at a time
   Vector*        coSolveVector2;    ///< when 2 systems are to solve at a time
   SSVector*      coSolveVector2rhs; ///< when 2 systems are to solve at a time

   Real           cacheProductFactor;

protected:
   Array < UnitVector > unitVecs; ///< array of unit vectors
   const SVSet*   thevectors;
   const SVSet*   thecovectors;

   int            nNZEs;          ///< number of nonzero elements
   int            coVecDim;

   DVector        primRhs;     ///< rhs vector for computing the primal vector
   UpdateVector   primVec;     ///< primal vector
   DVector        dualRhs;     ///< rhs vector for computing the dual vector
   UpdateVector   dualVec;     ///< dual vector
   UpdateVector   addVec;      ///< additional vector

   DVector        theURbound;  ///< Upper Row    Feasibility bound
   DVector        theLRbound;  ///< Lower Row    Feasibility bound
   DVector        theUCbound;  ///< Upper Column Feasibility bound
   DVector        theLCbound;  ///< Lower Column Feasibility bound

   /** In entering Simplex algorithm, the ratio test must ensure that all 
    *  \em basic variables remain within their feasibility bounds. To give fast
    *  acces to them, the bounds of basic variables are copied into the
    *  following two vectors.
    */
   DVector        theUBbound;  ///< Upper Basic Feasibility bound
   DVector        theLBbound;  ///< Lower Basic Feasibility bound

   DVector*       theFrhs;
   UpdateVector*  theFvec;

   DVector*       theCoPrhs;
   UpdateVector*  theCoPvec;
   UpdateVector*  thePvec;

   /** set on #theCoPvec or #thePvec */
   UpdateVector*  theRPvec;    ///< row pricing vector
   /** set on #thePvec or #theCoPvec */
   UpdateVector*  theCPvec;    /// column pricing vector

   // The following vectors serve for the virtualization of shift bounds
   //@todo In prinziple this schould be refernces.
   DVector*       theUbound;      ///< Upper bound for vars
   DVector*       theLbound;      ///< Lower bound for vars
   DVector*       theCoUbound;    ///< Upper bound for covars
   DVector*       theCoLbound;    ///< Lower bound for covars

   // The following vectors serve for the virtualization of testing vectors
   DVector        theCoTest;
   DVector        theTest;

   int             leaveCount;    ///< number of LEAVE iterations
   int             enterCount;    ///< number of ENTER iterations

   SPxPricer*      thepricer;
   SPxRatioTester* theratiotester;
   SPxStarter*     thestarter;
   SPxScaler*      thescaler;
   SPxSimplifier*  thesimplifier;

public:
   /// Return the version of SoPlex as number like 123 for 1.2.3
   int version() const
   {
      return SOPLEX_VERSION;
   }
   /// return the current basis representation.
   Representation rep() const
   {
      return therep;
   }

   /// return current #Type.
   Type type() const
   {
      return theType;
   }

   /// return current #Pricing.
   Pricing pricing() const
   {
      return thePricing;
   }

   /**@name Setup
    *  Before solving an LP with an instance of #SoPlex, 
    *  the following steps must be performed:
    *
    *  -# Load the LP by copying an external LP or reading it from an
    *     input stream.
    *  -# Setup the pricer to use by loading an #SPxPricer object
    *     (only neccessary, if not done in a previous call).
    *  -# Setup the ratio test method to use by loading an #SPxRatioTester 
    *     object (only neccessary, if not done in a previous call).
    *  -# Setup the linear system solver to use by loading an
    *     #SLinSolver object (only neccessary, if not done in a previous call).
    *  -# Optionally setup an LP simplifier by loading an SPxSimplifier object.
    *  -# Optionally setup an start basis generation method by loading an
    *     SPxStarter object.
    *  -# Optionally setup a start basis by loading a SPxBasis::Desc object.
    *  -# Optionally switch to another basis Representation by calling
    *     method #setRep().
    *  -# Optionally switch to another algorithm #Type by calling
    *     method #setType().
    *
    *  Now the solver is ready for execution. If the loaded LP is to be solved
    *  again from scratch, this can be done with method #reLoad(). Finally,
    *  #clear() removes the LP from the solver.
    */
   //@{
   /// read LP from input stream.
   virtual bool read(std::istream& in, NameSet* rowNames = 0,
      NameSet* colNames = 0, DIdxSet* intVars = 0);

   /// copy LP.
   virtual void loadLP(const SPxLP& LP);
   /// setup linear solver to use.
   virtual void setSolver(SLinSolver* slu);
   /// setup pricer to use.
   virtual void setPricer(SPxPricer*);
   /// setup ratio-tester to use.
   virtual void setTester(SPxRatioTester*);
   /// setup starting basis generator to use.
   virtual void setStarter(SPxStarter*);
   /// setup scaler to use.
   virtual void setScaler(SPxScaler*);
   /// setup simplifier to use.
   virtual void setSimplifier(SPxSimplifier*);
   /// set a start basis.
   virtual void loadBasis(const SPxBasis::Desc&);

   /// set #ROW or #COLUMN representation.
   void setRep (Representation p_rep);
   /// set #LEAVE or #ENTER algorithm.
   void setType(Type tp);
   /// set #FULL or #PARTIAL pricing.
   void setPricing(Pricing pr);

   /// reload LP.
   virtual void reLoad();

   /// load LP from \p filename in MPS or LPF format.
   virtual bool readFile(const char* filename, NameSet* rowNames = 0,
                         NameSet* colNames = 0, DIdxSet* intVars = 0);

   /// dump loaded LP to \p filename in LPF format.
   virtual void dumpFile(const char* filename) const;

   /// clear all data in solver.
   virtual void clear();

   /// load basis from \p filename in MPS format.
   virtual bool readBasisFile(const char* filename, 
                              const NameSet& rowNames,
                              const NameSet& colNames);

   //@}

   /**@name Solving LPs */
   //@{
   /// solve loaded LP.
   /** Solves the loaded LP by processing the Simplex iteration until
    *  the termination criteria is fullfilled (see #terminate()). 
    *  The #SPxStatus of the solver will indicate the reason for termination.
    */
   virtual Status solve();

   /// #Status of solution process.
   Status status() const;

   /// current objective value.
   /**@return Objective value of the current solution vector 
    *         (see #getPrimal()).
    */
   virtual Real value() const;

   /// get solution vector for primal variables.
   /** This method returns the #Status of the basis.
    *  If it is #REGULAR or better,
    *  the primal solution vector of the current basis will be copied
    *  to the argument \p vector. Hence, \p vector must be of dimension
    *  #nCols().
    */
   virtual Status getPrimal(Vector& vector) const;

   /// get unscaled solution vector for primal variables.
   /** The same as #getPrimal(), but with unscaled values.
    */
   virtual Status getPrimalUnscaled(Vector& vector) const;

   /// get vector of slack variables.
   /** This method returns the #Status of the basis.
    *  If it is #REGULAR or better,
    *  the slack variables of the current basis will be copied
    *  to the argument \p vector. Hence, \p vector must be of dimension
    *  #nRows().
    *
    *  @warning Because #SoPlex supports range constraints as its
    *     default, slack variables are defined in a nonstandard way:
    *     Let \i x be the current solution vector and \i A the constraint
    *     matrix. Then the vector of slack variables is defined as
    *     \f$s = Ax\f$.
    */
   virtual Status getSlacks (Vector& vector) const;

   /// get current solution vector for dual variables.
   /** This method returns the #Status of the basis.
    *  If it is #REGULAR or better,
    *  the vector of dual variables of the current basis will be copied
    *  to the argument \p vector. Hence, \p vector must be of dimension
    *  #nRows().
    *
    *  @warning Even though mathematically, each range constraint would
    *     account for two dual variables (one for each inequaility), only
    *     #nRows() dual variables are setup via the following
    *     construction: Given a range constraint, there are three possible
    *     situations:
    *     - None of its inequalities is tight: The dual variables
    *       for both are 0. However, when shifting (see below)
    *       occurs, it may be set to a value other than 0, which
    *       models a perturbed objective vector.
    *     - Both of its inequalities are tight: In this case the
    *       range constraint models an equality and we adopt the
    *       standard definition.
    *     - One of its inequalities is tight while the other is not:
    *       In this case only the dual variable for the tight
    *       constraint is given with the standard definition, while
    *       the other constraint is implicitely set to 0.
    */
   virtual Status getDual (Vector& vector) const;

   /// get vector of reduced costs.
   /** This method returns the \Ref{Status} of the basis.
    *  If it is #REGULAR or better,
    *  the vector of reduced costs of the current basis will be copied
    *  to the argument \p vector. Hence, \p vector must be of dimension
    *  #nCols().
    *
    *  Let \i d denote the vector of dual variables, as defined above,
    *  and \i A the LPs constraint matrix. Then the reduced cost vector
    *  \i r is defined as \f$r^T = c^T - d^TA\f$.
    */
   virtual Status getRdCost (Vector& vector) const;

   /// Termination criterion.
   /** This method is called in each Simplex iteration to determine, if
    *  the algorithm is to terminate. In this case a nonzero value is
    *  returned.
    *
    *  This method is declared virtual to allow for implementation of
    *  other stopping criteria or using it as callback method within the
    *  Simplex loop, by overriding the method in a derived class.
    *  However, all implementations must terminate with the
    *  statement \c return #SoPlex::terminate(), if no own termination
    *  criteria is encountered.
    *
    *  Note, that the Simplex loop stopped even when #terminate()
    *  returns 0, if the LP has been solved to optimality (i.e. no
    *  further pricing succeeds and no shift is present).
    */
   virtual bool terminate ();
   //@}

   /**@name Control Parameters */
   //@{

   /// values \f$|x| < \epsilon\f$ are considered to be 0.
   /** if you want another value for epsilon, use #Param::setEpsilon().
    */
   Real epsilon() const
   {
      return primVec.delta().epsilon;
   }

   /// allowed bound violation for optimal Solution.
   /** When all vectors do not violate their bounds by more than \f$\delta\f$,
    *  the basis is considered optimal.
    */
   Real delta() const
   {
      return thedelta;
   }
   /// set parameter \p delta.
   void setDelta(Real d);

   /** #SoPlex consideres a Simplex step as degenerate, if the
    *  steplength does not exceed #epsilon. Cycling occurs, if only
    *  degenerate steps are taken. To prevent this situation, #SoPlex
    *  perturbs the problem such that nondegenerate steps are ensured.
    *
    *  maxCycle() controls, how agressive such perturbation is
    *  performed, since no more than #maxCycle() degenerate steps are
    *  accepted before perturbing the LP. The current number of consequtive
    *  degenerate steps is counted in variable numCycle().
    */
   /// maximum number of degenerate simplex steps before we detect cycling.
   int maxCycle() const 
   {
      return m_maxCycle;
   }
   /// actual number of degenerate simplex steps encountered so far.
   int numCycle() const 
   {
      return m_numCycle;
   }
   //@}

   /**@name LP Modification
    *  These methods are the overridden counterparts of the base class
    *  #SPxLP. See there for a more detailed documentation of these
    *  methods.
    */
   //@{
private:
   ///
   void localAddRows(int start);
   ///
   void localAddCols(int start);

protected:
   ///
   virtual void addedRows(int n);
   ///
   virtual void addedCols(int n);

   ///
   virtual void doRemoveRow(int i);
   ///
   virtual void doRemoveRows(int perm[]);
   ///
   virtual void doRemoveCol(int i);
   ///
   virtual void doRemoveCols(int perm[]);

public:
   ///
   virtual void changeObj(const Vector& newObj);
   ///
   virtual void changeObj(int i, Real newVal);
   ///
   virtual void changeObj(SPxColId p_id, Real p_newVal)
   {
      changeObj(number(p_id), p_newVal);
   }
   ///
   virtual void changeLower(const Vector& newLower);
   ///
   virtual void changeLower(int i, Real newLower);
   ///
   virtual void changeLower(SPxColId p_id, Real p_newLower)
   {
      changeLower(number(p_id), p_newLower);
   }
   ///
   virtual void changeUpper(const Vector& newUpper);
   ///
   virtual void changeUpper(int i, Real newUpper);
   ///
   virtual void changeUpper(SPxColId p_id, Real p_newUpper)
   {
      changeUpper(number(p_id), p_newUpper);
   }
   ///
   virtual void changeBounds(const Vector& newLower, const Vector& newUpper);
   ///
   virtual void changeBounds(int i, Real newLower, Real newUpper);
   ///
   virtual void changeBounds(
      SPxColId p_id, Real p_newLower, Real p_newUpper)
   {
      changeBounds(number(p_id), p_newLower, p_newUpper);
   }
   ///
   virtual void changeLhs(const Vector& newLhs);
   ///
   virtual void changeLhs(int i, Real newLhs);
   ///
   virtual void changeLhs(SPxRowId p_id, Real p_newLhs)
   {
      changeLhs(number(p_id), p_newLhs);
   }
   ///
   virtual void changeRhs(const Vector& newRhs);
   ///
   virtual void changeRhs(int i, Real newRhs);
   ///
   virtual void changeRhs(SPxRowId p_id, Real p_newRhs)
   {
      changeRhs(number(p_id), p_newRhs);
   }
   ///
   virtual void changeRange(const Vector& newLhs, const Vector& newRhs);
   ///
   virtual void changeRange(int i, Real newLhs, Real newRhs);
   ///
   virtual void changeRange(
      SPxRowId p_id, Real p_newLhs, Real p_newRhs)
   {
      changeRange(number(p_id), p_newLhs, p_newRhs);
   }
   ///
   virtual void changeRow(int i, const LPRow& newRow);
   ///
   virtual void changeRow(SPxRowId p_id, const LPRow& p_newRow)
   {
      changeRow(number(p_id), p_newRow);
   }
   ///
   virtual void changeCol(int i, const LPCol& newCol);
   ///
   virtual void changeCol(SPxColId p_id, const LPCol& p_newCol)
   {
      changeCol(number(p_id), p_newCol);
   }
   ///
   virtual void changeElement(int i, int j, Real val);
   ///
   virtual void changeElement(
      SPxRowId rid, SPxColId cid, Real val)
   {
      changeElement(number(rid), number(cid), val);
   }
   ///
   virtual void changeSense(SPxSense sns);
   //@}

   /// dimension of basis matrix.
   int dim() const
   {
      return thecovectors->num();
   }
   /// codimension.
   int coDim() const
   {
      return thevectors->num();
   }

   /**@name Variables and Covariables
    *  Class #SPxLP introduces #Id%s to identify row or column data of
    *  an LP. #SoPlex uses this concept to access data with respect to the
    *  chosen representation.
    */
   //@{
   /// id of \p i 'th vector.
   /** The \p i 'th #id is the \p i 'th #SPxRowId for a rowwise and the
    *  \p i 'th #SPxColId for a columnwise basis represenation. Hence,
    *  0 <= i < #coDim().
    */
   SPxId id(int i) const
   {
      if (rep() == ROW)
      {
         SPxRowId rid = SPxLP::rId(i);
         return SPxId(rid);
      }
      else
      {
         SPxColId cid = SPxLP::cId(i);
         return SPxId(cid);
      }
   }

   /// id of \p i 'th covector.
   /** The \p i 'th #coId() is the \p i 'th #SPxColId for a rowwise and the
    *  \p i 'th #SPxRowId for a columnwise basis represenation. Hence,
    *  0 <= i < #dim().
    */
   SPxId coId(int i) const
   {
      if (rep() == ROW)
      {
         SPxColId cid = SPxLP::cId(i);
         return SPxId(cid);
      }
      else
      {
         SPxRowId rid = SPxLP::rId(i);
         return SPxId(rid);
      }
   }

   /// Is \p p_id an SPxId ?
   /** This method returns wheather or not \p p_id identifies a vector
    *  with respect to the chosen representation.
    */
   int isId(SPxId p_id) const
   {
      return p_id.info * therep > 0;
   }

   /// Is \p p_id a CoId.
   /** This method returns wheather or not \p p_id identifies a coVector
    *  with respect to the chosen representation.
    */
   int isCoId(SPxId p_id) const
   {
      return p_id.info * therep < 0;
   }
   //@}

   /**@name Vectors and Covectors */
   //@{
   /// \p i 'th vector.
   /**@return a reference to the \p i 'th, 0 <= i < #coDim(), vector of
    *         the loaded LP (with respect to the chosen representation).
    */
   const SVector& vector(int i) const
   {
      return (*thevectors)[i];
   }

   ///
   const SVector& vector(const SPxRowId& rid) const
   {
      assert(rid.isValid());
      return (rep() == ROW)
             ? (*thevectors)[number(rid)]
          : static_cast<const SVector&>(unitVecs[number(rid)]);
   }
   ///
   const SVector& vector(const SPxColId& cid) const
   {
      assert(cid.isValid());
      return (rep() == COLUMN)
             ? (*thevectors)[number(cid)]
          : static_cast<const SVector&>(unitVecs[number(cid)]);
   }

   /// vector associated to \p p_id.
   /**@return Returns a reference to the vector of the loaded LP corresponding
    *  to \p id (with respect to the chosen representation). If \p p_id is
    *  an id, a vector of the constraint matrix is returned, otherwise
    *  the corresponding unit vector (of the slack variable or bound
    *  inequality) is returned.
    *  @todo The implementation does not exactly look like it will do
    *        what is promised in the describtion.
    */
   const SVector& vector(const SPxId& p_id) const
   {
      assert(p_id.isValid());

      return p_id.isSPxRowId()
         ? vector(SPxRowId(p_id))
         : vector(SPxColId(p_id));
   }

   /// \p i 'th covector of LP.
   /**@return a reference to the \p i 'th, 0 <= i < #dim(), covector of
    *  the loaded LP (with respect to the chosen representation).
    */
   const SVector& coVector(int i) const
   {
      return (*thecovectors)[i];
   }
   ///
   const SVector& coVector(const SPxRowId& rid) const
   {
      assert(rid.isValid());
      return (rep() == COLUMN)
             ? (*thecovectors)[number(rid)]
          : static_cast<const SVector&>(unitVecs[number(rid)]);
   }
   ///
   const SVector& coVector(const SPxColId& cid) const
   {
      assert(cid.isValid());
      return (rep() == ROW)
             ? (*thecovectors)[number(cid)]
          : static_cast<const SVector&>(unitVecs[number(cid)]);
   }
   /// coVector associated to \p p_id.
   /**@return a reference to the covector of the loaded LP
    *  corresponding to \p p_id (with respect to the chosen
    *  representation). If \p p_id is a coid, a covector of the constraint
    *  matrix is returned, otherwise the corresponding unit vector is
    *  returned.
    */
   const SVector& coVector(const SPxId& p_id) const
   {
      assert(p_id.isValid());
      return p_id.isSPxRowId()
             ? coVector(SPxRowId(p_id))
          : coVector(SPxColId(p_id));
   }
   /// return \p i 'th unit vector.
   const SVector& unitVector(int i) const
   {
      return unitVecs[i];
   }
   //@}

   /**@name Variable status
    *  The Simplex basis assigns a #SPxBasis::Desc::Status to each
    *  variable and covariable. Depending on the representation, the status
    *  indicates that the corresponding vector is in the basis matrix or not.
    */
   //@{
   /// #Status of \p i 'th variable.
   SPxBasis::Desc::Status varStatus(int i) const
   {
      return desc().status(i);
   }

   /// #Status of \p i 'th covariable.
   SPxBasis::Desc::Status covarStatus(int i) const
   {
      return desc().coStatus(i);
   }

   /// does \p stat describe a basic index ?
   int isBasic(SPxBasis::Desc::Status stat) const
   {
      return (stat * rep() > 0);
   }

   /// is the \p p_id 'th vector basic ?
   int isBasic(SPxId p_id) const
   {
      assert(p_id.isValid());
      return p_id.isSPxRowId()
         ? isBasic(SPxRowId(p_id))
         : isBasic(SPxColId(p_id));
   }

   /// is the \p rid 'th vector basic ?
   int isBasic(SPxRowId rid) const
   {
      return isBasic(desc().rowStatus(number(rid)));
   }

   /// is the \p cid 'th vector basic ?
   int isBasic(SPxColId cid) const
   {
      return isBasic(desc().colStatus(number(cid)));
   }

   /// is the \p i 'th row vector basic ?
   int isRowBasic(int i) const
   {
      return isBasic(desc().rowStatus(i));
   }

   /// is the \p i 'th column vector basic ?
   int isColBasic(int i) const
   {
      return isBasic(desc().colStatus(i));
   }

   /// is the \p i 'th vector basic ?
   int isBasic(int i) const
   {
      return isBasic(desc().status(i));
   }

   /// is the \p i 'th covector basic ?
   int isCoBasic(int i) const
   {
      return isBasic(desc().coStatus(i));
   }
   //@}

   /// feasibility vector.
   /** This method return the \em feasibility vector. If it satisfies its
    *  bound, the basis is called feasible (independently of the chosen
    *  representation). The feasibility vector has dimension #dim().
    *
    *  For the entering Simplex, #fVec is kept within its bounds. In
    *  contrast to this, the pricing of the leaving Simplex selects an
    *  element of #fVec, that violates its bounds.
    */
   UpdateVector& fVec() const
   {
      return *theFvec;
   }
   /// right-hand side vector for #fVec.
   /** The feasibility vector is computed by solving a linear system with the
    *  basis matrix. The right-hand side vector of this system is refferd to as
    *  \em feasibility \em, right-hand \em side \em vector #fRhs().
    *
    *  For a row basis, #fRhs() is the objective vector (ignoring shifts).
    *  For a column basis, it is the sum of all nonbasic vectors scaled by
    *  the factor of their bound.
    */
   const Vector& fRhs() const
   {
      return *theFrhs;
   }
   ///
   const Vector& ubBound() const
   {
      return theUBbound;
   }
   /// upper bound for #fVec.
   /** This method returns the upper bound for the feasibility vector.
    *  It may only be called for the #ENTER%ing Simplex.
    *  
    *  For the #ENTER%ing Simplex algorithms, the feasibility vector is
    *  maintained to fullfill its bounds. As #fVec itself, also its
    *  bounds depend on the chosen representation. Furhter, they may
    *  need to be shifted (see below).
    */
   Vector& ubBound()
   {
      return theUBbound;
   }
   ///
   const Vector& lbBound() const
   {
      return theLBbound;
   }
   /// lower bound for #fVec.
   /** This method returns the lower bound for the feasibility vector.
    *  It may only be called for the #ENTER%ing Simplex.
    *
    *  For the #ENTER%ing Simplex algorithms, the feasibility vector is
    *  maintained to fullfill its bounds. As #fVec itself, also its
    *  bound depend on the chosen representation. Further, they may
    *  need to be shifted (see below).
    */
   Vector& lbBound()
   {
      return theLBbound;
   }

   /// Violations of #fVec.
   /** For the leaving Simplex algorithm, pricing involves selecting a
    *  variable from #fVec that violates its bounds that is to leave
    *  the basis. When a #SPxPricer is called to select such a
    *  leaving variable, #fTest() contains the vector of violations:
    *  For #fTest()[i] < 0, the \c i 'th basic variable violates one of
    *  its bounds by the given value. Otherwise no bound is violated.
    */
   const Vector& fTest() const
   {
      assert(type() == LEAVE);
      return theCoTest;
   }

   /// copricing vector.
   /** The copricing vector #coPvec along with the pricing vector
    *  #pVec are used for pricing in the #ENTER%ing Simplex algorithm,
    *  i.e. one variable is selected, that violates its bounds. In
    *  contrast to this, the #LEAVE%ing Simplex algorithm keeps both
    *  vectors within their bounds.
    */
   UpdateVector& coPvec() const
   {
      return *theCoPvec;
   }

   /// Right-hand side vector for #coPvec.
   /** Vector #coPvec is computed by solving a linear system with the
    *  basis matrix and #coPrhs as the right-hand side vector. For
    *  column basis representation, #coPrhs is build up of the
    *  objective vector elements of all basic variables. For a row
    *  basis, it consists of the thight bounds of all basic
    *  constraints.
    */
   const Vector& coPrhs() const
   {
      return *theCoPrhs;
   }

   ///
   const Vector& ucBound() const
   {
      assert(theType == LEAVE);
      return *theCoUbound;
   }
   /// upper bound for #coPvec.
   /** This method returns the upper bound for #coPvec. It may only be
    *  called for the leaving Simplex algorithm.
    *
    *  For the leaving Simplex algorithms #coPvec is maintained to
    *  fullfill its bounds. As #coPvec itself, also its bound depend
    *  on the chosen representation. Further, they may need to be
    *  shifted (see below).
    */
   Vector& ucBound()
   {
      assert(theType == LEAVE);
      return *theCoUbound;
   }

   ///
   const Vector& lcBound() const
   {
      assert(theType == LEAVE);
      return *theCoLbound;
   }
   /// lower bound for #coPvec.
   /** This method returns the lower bound for #coPvec. It may only be
    *  called for the leaving Simplex algorithm.
    *
    *  For the leaving Simplex algorithms #coPvec is maintained to
    *  fullfill its bounds. As #coPvec itself, also its bound depend
    *  on the chosen representation. Further, they may need to be
    *  shifted (see below).
    */
   Vector& lcBound()
   {
      assert(theType == LEAVE);
      return *theCoLbound;
   }

   /// violations of #coPvec.
   /** In entering Simplex pricing selects checks vectors #coPvec()
    *  and #pVec()# for violation of its bounds. #coTest() contains
    *  the violations for #coPvec() which are indicated by a negative
    *  value. I.e. if #coTest(i) < 0, the \p i 'th element of #coPvec()
    *  is violated by #-coTest(i).
    */
   const Vector& coTest() const
   {
      assert(type() == ENTER);
      return theCoTest;
   }
   /// pricing vector.
   /** The pricing vector #pVec is the product of #coPvec with the
    *  constraint matrix. As #coPvec, also #pVec is maintained within
    *  its bound for the leaving Simplex algorithm, while the bounds
    *  are tested for the entering Simplex. #pVec is of dimension
    *  #dim(). Vector #pVec() is only up to date for #LEAVE%ing
    *  Simplex or #FULL pricing in #ENTER%ing Simplex.
    */
   UpdateVector& pVec() const
   {
      return *thePvec;
   }
   ///
   const Vector& upBound() const
   {
      assert(theType == LEAVE);
      return *theUbound;
   }
   /// upper bound for #pVec.
   /** This method returns the upper bound for #pVec. It may only be
    *  called for the leaving Simplex algorithm.
    *
    *  For the leaving Simplex algorithms #pVec is maintained to
    *  fullfill its bounds. As #pVec itself, also its bound depend
    *  on the chosen representation. Further, they may need to be
    *  shifted (see below).
    */
   Vector& upBound()
   {
      assert(theType == LEAVE);
      return *theUbound;
   }

   ///
   const Vector& lpBound() const
   {
      assert(theType == LEAVE);
      return *theLbound;
   }
   /// lower bound for #pVec.
   /** This method returns the lower bound for #pVec. It may only be
    *  called for the leaving Simplex algorithm.
    *
    *  For the leaving Simplex algorithms #pVec is maintained to
    *  fullfill its bounds. As #pVec itself, also its bound depend
    *  on the chosen representation. Further, they may need to be
    *  shifted (see below).
    */
   Vector& lpBound()
   {
      assert(theType == LEAVE);
      return *theLbound;
   }

   /// Violations of #pVec.
   /** In entering Simplex pricing selects checks vectors #coPvec()
    *  and #pVec() for violation of its bounds. Vector #test()
    *  contains the violations for #pVec(), i.e.~if #test(i) < 0,
    *  the i'th element of #pVec() is violated by #test(i).
    *  Vector #test() is only up to date for #FULL pricing.
    */
   const Vector& test() const
   {
      assert(type() == ENTER);
      return theTest;
   }

   /// compute and return #pVec(i).
   Real computePvec(int i);
   /// compute entire #pVec().
   void computePvec();
   /// compute and return #test(i) in #ENTER%ing Simplex.
   Real computeTest(int i);
   /// compute test vector in #ENTER%ing Simplex.
   void computeTest();

   /**@name Shifting
    *  The task of the ratio test (implemented in #SPxRatioTester classes)
    *  is to select a variable for the basis update, such that the basis
    *  remains priced (i.e. both, the pricing and copricing vectors satisfy
    *  their bounds) or feasible (i.e. the feasibility vector satisfies its
    *  bounds). However, this can lead to numerically instable basis matrices
    *  or -- after accumulation of various errors -- even to a singular basis
    *  matrix.
    *
    *  The key to overcome this problem is to allow the basis to become "a
    *  bit" infeasible or unpriced, in order provide a better choice for the
    *  ratio test to select a stable variable. This is equivalent to enlarging
    *  the bounds by a small amount. This is referred to as \em shifting.
    *
    *  These methods serve for shifting feasibility bounds, either in order
    *  to maintain numerical stability or initially for computation of
    *  phase 1. The sum of all shifts applied to any bound is stored in
    *  #theShift.
    *
    *  The following methods are used to shift individual bounds. They are
    *  mainly intended for stable implenentations of #SPxRatioTester.
    */
   //@{
   /// Perform initial shifting to optain an feasible or pricable basis.
   void shiftFvec();
   /// Perform initial shifting to optain an feasible or pricable basis.
   void shiftPvec();

   /// shift \p i 'th #ubBound to \p to.
   void shiftUBbound(int i, Real to)
   {
      assert(theType == ENTER);
      theShift += to - theUBbound[i];
      theUBbound[i] = to;
   }
   /// shift \p i 'th #lbBound to \p to.
   void shiftLBbound(int i, Real to)
   {
      assert(theType == ENTER);
      theShift += theLBbound[i] - to;
      theLBbound[i] = to;
   }
   /// shift \p i 'th #upBound to \p to.
   void shiftUPbound(int i, Real to)
   {
      assert(theType == LEAVE);
      theShift += to - (*theUbound)[i];
      (*theUbound)[i] = to;
   }
   /// shift \p i 'th #lpBound# to \p to.
   void shiftLPbound(int i, Real to)
   {
      assert(theType == LEAVE);
      theShift += (*theLbound)[i] - to;
      (*theLbound)[i] = to;
   }
   /// shift \p i 'th #ucBound# to \p to.
   void shiftUCbound(int i, Real to)
   {
      assert(theType == LEAVE);
      theShift += to - (*theCoUbound)[i];
      (*theCoUbound)[i] = to;
   }
   /// shift \p i 'th #lcBound# to \p to.
   void shiftLCbound(int i, Real to)
   {
      assert(theType == LEAVE);
      theShift += (*theCoLbound)[i] - to;
      (*theCoLbound)[i] = to;
   }
   ///
   void testBounds() const;

   /// total current shift amount.
   virtual Real shift() const
   {
      return theShift;
   }
   /// remove shift as much as possible.
   virtual void unShift(void);

   /// get violation of constraints.
   virtual void qualConstraintViolation(
      double& maxviol, double& sumviol) const;

   /// get unscaled violation of constraints
   virtual void qualConstraintViolationUnscaled(
      double& maxviol, double& sumviol) const;

   /// get violations of bounds.
   virtual void qualBoundViolation(
      double& maxviol, double& sumviol) const;

   /// get unscaled violations of bounds.
   virtual void qualBoundViolationUnscaled(
      double& maxviol, double& sumviol) const;

   /// get the residuum |Ax-b|.
   virtual void qualSlackViolation(double& maxviol, double& sumviol) const;

   /// get violation of optimality criterion.
   virtual void qualRdCostViolation(double& maxviol, double& sumviol) const;

private:
   ///
   void perturbMin(
      const UpdateVector& vec, Vector& low, Vector& up, Real eps,
      int start = 0, int incr = 1);
   ///
   void perturbMax(
      const UpdateVector& vec, Vector& low, Vector& up, Real eps,
      int start = 0, int incr = 1);
   ///
   Real perturbMin(const UpdateVector& uvec,
      Vector& low, Vector& up, Real eps, Real delta,
      const SPxBasis::Desc::Status* stat, int start, int incr);
   ///
   Real perturbMax(const UpdateVector& uvec,
      Vector& low, Vector& up, Real eps, Real delta,
      const SPxBasis::Desc::Status* stat, int start, int incr);
   //@}

   /**@name The Simplex Loop
    *  We now present a set of methods that may be usefull when implementing
    *  own #SPxPricer or #SPxRatioTester classes. Here is, how
    *  #SoPlex will call methods from its loaded #SPxPricer and
    *  #SPxRatioTester.
    *  
    *  For the entering Simplex:
    *    -# #SPxPricer::selectEnter()
    *    -# #SPxRatioTester::selectLeave()
    *    -# #SPxPricer::entered4()
    *  
    *  For the leaving Simplex:
    *    -# #SPxPricer::selectLeave()
    *    -# #SPxRatioTester::selectEnter()
    *    -# #SPxPricer::left4()
    */
   //@{
public:
   /// Setup vectors to be solved within Simplex loop.
   /** Load vector \p y to be #solve%d with the basis matrix during the
    *  #LEAVE Simplex. The system will be solved after #SoPlex%'s call
    *  to #SPxRatioTester.  The system will be solved along with
    *  another system. Solving two linear system at a time has
    *  performance advantages over solving the two linear systems
    *  seperately.
    */
   void setup4solve(Vector* p_y, SSVector* p_rhs)
   {
      assert(type() == LEAVE);
      solveVector2    = p_y;
      solveVector2rhs = p_rhs;
   }
   /// Setup vectors to be cosolved within Simplex loop.
   /** Load vector \p y to be #coSolve%#d with the basis matrix during
    *  the #ENTER Simplex. The system will be solved after #SoPlex%'s
    *  call to #SPxRatioTester.  The system will be solved along
    *  with another system. Solving two linear system at a time has
    *  performance advantages over solving the two linear systems
    *  seperately.
    */
   void setup4coSolve(Vector* p_y, SSVector* p_rhs)
   {
      assert(type() == ENTER);
      coSolveVector2    = p_y;
      coSolveVector2rhs = p_rhs;
   }
   /// maximal infeasibility of basis
   /** This method is called for prooving optimality. Since it is
    *  possible, that some stable implementation of class
    *  #SPxRatioTester yielded a slightly infeasible (or unpriced)
    *  basis, this must be checked before terminating with an optimal
    *  solution.
    */
   virtual Real maxInfeas() const;

   /// Return current basis.
   /**@note The basis can be used to solve linear systems or use
    *  any other of its (const) methods.  It is, however, encuraged
    *  to use methods #setup4solve() and #setup4coSolve() for solving
    *  systems, since this is likely to have perfomance advantages.
    */
   const SPxBasis& basis() const
   {
      return *this;
   }
   ///
   SPxBasis& basis()
   {
      return *this;
   }
   /// return loaded #SPxPricer.
   const SPxPricer* pricer() const
   {
      return thepricer;
   }
   /// return loaded #SLinSolver.
   const SLinSolver* slinSolver() const
   {
      return SPxBasis::factor;
   }
   /// return loaded #SPxRatioTester.
   const SPxRatioTester* ratiotester() const
   {
      return theratiotester;
   }
   /// return loaded #SPxStarter.
   const SPxStarter* starter() const
   {
      return thestarter;
   }
   /// return loaded #SPxSimplifier.
   const SPxSimplifier* simplifier() const
   {
      return thesimplifier;
   }

protected:
   /// Factorize basis matrix.
   virtual void factorize();

private:
   int leave(int i);
   int enter(SPxId& id);

   /// test coVector #i# with status #stat#.
   Real coTest(int, SPxBasis::Desc::Status) const;
   /// compute coTest vector.
   void computeCoTest();
   /// recompute coTest vector.
   void updateCoTest();

   /// test vector #i# with status #stat#.
   Real test(int i, SPxBasis::Desc::Status stat) const;
   /// recompute test vector.
   void updateTest();

   /// compute basis feasibility test vector.
   void computeFtest();
   /// update basis feasibility test vector.
   void updateFtest();
   //@}

   /**@name Parallelization
    *  In this section we present the methods, that are provided in order to
    *  allow a parallel version to be implemented as a derived class, thereby
    *  inheriting most of the code of #SoPlex#.
    *
    *  @par Initialization
    *  These methods are used to setup all the vectors used in the Simplex
    *  loop, that where described in the previous sectios.
    */
   //@{
protected:
   /// has the internal data been initialized?
   /** As long as an instance of #SoPlex is not #initialized, no member
    *  contains setup data. Initialization is performed via method
    *  #init().  Afterwards all data structures are kept up to date (even
    *  for all manipulation methods), until #unInit() is called. However,
    *  some manipulation methods call #unInit()# themselfs.
    */
   bool isInitialized() const
   {
      return initialized;
   }
public:
   /// intialize data structures.
   /** If #SoPlex is not #isInitialized(), method solve calls
    *  #init() to setup all vectors and internal data structures.
    *  Most of the other methods within this section are called by
    *  #init().
    *
    *  Derived classes should add the initialization of additional
    *  data structures by overriding this method. Don't forget,
    *  however to call #SoPlex::init().
    */
   virtual void init();

protected:
   /// unintialize data structures.
   virtual void unInit()
   {
      initialized = false;
   }

   /// reset dimensions of vectors according to loaded LP.
   virtual void reDim();
   /// compute feasibility vector from scratch.
   void computeFrhs();
   ///
   virtual void computeFrhsXtra();
   ///
   virtual void computeFrhs1(const Vector&, const Vector&);
   ///
   void computeFrhs2(const Vector&, const Vector&);
   /// compute #theCoPrhs for entering Simplex.
   virtual void computeEnterCoPrhs();
   ///
   void computeEnterCoPrhs4Row(int i, int n);
   ///
   void computeEnterCoPrhs4Col(int i, int n);
   /// compute #theCoPrhs for leaving Simplex.
   virtual void computeLeaveCoPrhs();
   ///
   void computeLeaveCoPrhs4Row(int i, int n);
   ///
   void computeLeaveCoPrhs4Col(int i, int n);

   /// Compute part of objective value.
   /** This method is called from #value() in order to compute the part of
    *  the objective value resulting form nonbasic variables for #COLUMN
    *  #Representation.
    */
   Real nonbasicValue() const;

   /// Get pointer to the \p id 'th vector
   virtual const SVector* enterVector(const SPxId& p_id)
   {
      assert(p_id.isValid());
      return p_id.isSPxRowId() 
         ? &vector(SPxRowId(p_id)) : &vector(SPxColId(p_id));
   }
   ///
   virtual void getLeaveVals(int i,
      SPxBasis::Desc::Status& leaveStat, SPxId& leaveId,
      Real& leaveMax, Real& leavebound, int& leaveNum);
   ///
   virtual void getLeaveVals2(Real leaveMax, SPxId enterId,
      Real& enterBound, Real& newUBbound,
      Real& newLBbound, Real& newCoPrhs);
   ///
   virtual void getEnterVals(SPxId id, Real& enterTest,
      Real& enterUB, Real& enterLB, Real& enterVal, Real& enterMax,
      Real& enterPric, SPxBasis::Desc::Status& enterStat, Real& enterRO);
   ///
   virtual void getEnterVals2(int leaveIdx, 
      Real enterMax, Real& leaveBound);
   ///
   virtual void ungetEnterVal(SPxId enterId, SPxBasis::Desc::Status enterStat,
      Real leaveVal, const SVector& vec);
   ///
   virtual void rejectEnter(SPxId enterId,
      Real enterTest, SPxBasis::Desc::Status enterStat);
   ///
   virtual void rejectLeave(int leaveNum, SPxId leaveId,
      SPxBasis::Desc::Status leaveStat, const SVector* newVec = 0);
   ///
   virtual void setupPupdate(void);
   ///
   virtual void doPupdate(void);
   ///
   virtual void clearUpdateVecs(void);
   ///
   virtual void perturbMinEnter(void);
   /// perturb basis bounds.
   virtual void perturbMaxEnter(void);
   ///
   virtual void perturbMinLeave(void);
   /// perturb nonbasic bounds.
   virtual void perturbMaxLeave(void);

   //@}
   /*  The following methods serve for initializing the bounds for dual or
    *  primal Simplex algorithm of entering or leaving type.
    */
   //@{
   void clearDualBounds(SPxBasis::Desc::Status, Real&, Real&);

   void setDualColBounds();
   void setDualRowBounds();
   void setPrimalBounds();

   void setEnterBound4Col(int, int);
   void setEnterBound4Row(int, int);
   virtual void setEnterBounds();

   void setLeaveBound4Row(int i, int n);
   void setLeaveBound4Col(int i, int n);
   virtual void setLeaveBounds();
   //@}

public:
   /// set time limit.
   virtual void setTerminationTime(Real time = infinity);

   /// return time limit.
   virtual Real terminationTime() const;

   /// set iteration limit.
   virtual void setTerminationIter(int iteration = -1);

   /// return iteration limit.
   virtual int terminationIter() const;

   /// set objective limit.
   virtual void setTerminationValue(Real value = infinity);

   /// return objective limit.
   virtual Real terminationValue() const;
   
   /// get objective value of current solution.
   virtual Real objValue() const
   {
      return value();
   }

   /// get all results of last solve.
   Status getResult(Real* value = 0, Vector* primal = 0,
      Vector* slacks = 0, Vector* dual = 0, Vector* reduCost = 0) const;

protected:
   /**@todo put the following basis methods near the variable status methods!*/
   /// converts basis status to VarStatus
   VarStatus basisStatusToVarStatus( SPxBasis::Desc::Status stat ) const;

   /// converts VarStatus to basis status for rows
   SPxBasis::Desc::Status varStatusToBasisStatusRow( int row, VarStatus stat )
      const;

   /// converts VarStatus to basis status for columns
   SPxBasis::Desc::Status varStatusToBasisStatusCol( int col, VarStatus stat )
      const;

public:
   /// gets basis status for a single row
   VarStatus getBasisRowStatus( int row ) const;

   /// gets basis status for a single column
   VarStatus getBasisColStatus( int col ) const;

   /// get current basis, and return solver status.
   Status getBasis(VarStatus rows[], VarStatus cols[]) const;

   /// set #LPSolver's basis.
   void setBasis(const VarStatus rows[], const VarStatus cols[]);

   /// get number of iterations of current solution.
   int iterations() const
   {
      return basis().iteration();
   }

   /// time spent in last call to method #solve().
   Real time() const
   {
      return theTime.userTime();
   }
   /// return const lp's rows if available.
   const LPRowSet& rows() const
   {
      return *lprowset();
   }

   /// return const lp's cols if available.
   const LPColSet& cols() const
   {
      return *lpcolset();
   }

   /// copy lower bound vector to #low#.
   void getLower(Vector& lw) const
   {
      lw = SPxLP::lower();
   }
   /// copy upper bound vector to #up#.
   void getUpper(Vector& upp) const
   {
      upp = SPxLP::upper();
   }

   /// copy lhs value vector to #lhs#.
   void getLhs(Vector& p_lhs) const
   {
      p_lhs = SPxLP::lhs();
   }

   /// copy rhs value vector to #rhs#.
   void getRhs(Vector& p_rhs) const
   {
      p_rhs = SPxLP::rhs();
   }

   /// optimization sense.
   SPxSense sense() const
   {
      return spxSense();
   }

   /// number of rows of loaded LP.
   int nofRows() const
   {
      return nRows();
   }
   /// number of columns of loaded LP.
   int nofCols() const
   {
      return nCols();
   }
   /// number of nonzeros of loaded LP.
   int nofNZEs() const;

   /// #RowId# of \p i 'th inequality.
   SPxRowId rowId(int i) const
   {
      return rId(i);
   }
   /// #ColId# of \p i 'th column.
   SPxColId colId(int i) const
   {
      return cId(i);
   }

   /// default constructor.
   SoPlex(Type type = LEAVE, Representation rep = ROW,
      SPxPricer* pric = 0, SPxRatioTester* rt = 0,
      SPxStarter* start = 0, SPxScaler* scaler = 0, 
      SPxSimplifier* simple = 0);

   /// check consistency.
   bool isConsistent() const;

private:
   /// assignment operator is not implemented.
   SoPlex& operator=(const SoPlex& base);

   /// copy constructor is not implemented.
   SoPlex(const SoPlex& base);

   void testVecs();
};

} // namespace soplex
#endif // _SOPLEX_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
