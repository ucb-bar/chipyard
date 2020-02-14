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
#pragma ident "@(#) $Id: svset.h,v 1.17 2002/03/03 13:50:36 bzfkocht Exp $"
#endif

/**@file  svset.h
 * @brief Set of sparse vectors.
 */

#ifndef _SVSET_H_
#define _SVSET_H_

#include <assert.h>

#include "spxdefines.h"
#include "svector.h"
#include "dataset.h"
#include "dataarray.h"
#include "idlist.h"
#include "datakey.h"

namespace soplex
{
typedef DataArray < SVector::Element > SVSetBase;

/**@brief   sparse vector %set.
   @ingroup Algebra

   Class SVSet provides a %set of sparse vectors SVector. All SVector%s
   in a SVSet share one big memory block for their nonzeros. This
   memory is reffered to as the \em nonzero \em memory. The SVector%s
   themselfs are saved in another memory block refered to as the 
   \em vector \em memory. Both memory blocks will grow automatically if
   required, when adding more SVector%s to the %set or enlarging
   SVector%s within the %set. For controlling memory consumption,
   methods are provided to inquire and reset the size of the memory
   blocks used for vectors and nonzeros.
 
   SVector%s in an SVSet are numbered from 0 thru num()-1. They can be
   accessed using the index operator[](). When removing SVector%s of a
   SVSet the remaining ones will be renumbered. However, all SVector
   with a smaller number than the lowest number of the removed
   SVector%s will remain unchanged.
 
   For providing a uniform access to SVector%s in a %set even if others
   are removed or added, SVSet assigns a #DataKey to each SVector in the
   %set. Such a #DataKey remains unchanged as long as the corresponding
   SVector is in the SVSet, no matter what other SVector%s are added
   to or removed from the SVSet. Methods are provided for getting the
   #DataKey to a SVector or its number and vice versa.  Further, each add()
   method for enlarging an SVSet is provided with two signatures. One
   of them returns the #DataKey%s assigned to the SVector%s added to the
   SVSet.
*/
class SVSet : protected SVSetBase
{
private:

   /**@name Memory management implementation 
      The management of the
      SVectors is implemented by by a DataSet<DLPSV>, the keys used
      externally are DataKey%s.

      The management of nonzeros is done by a Real linked list
      IdList<DLPSV>, where the SVector%s are kept in the order their
      indices occurr in the DataArray. The SVector%s are kept without
      holes: If one is removed or moved to the end, the SVector
      preceeding it obtains all the nonzeros that previously belonged
      to the (re-)moved one.  However, the nonzeros in use are
      uneffected by this.  
   */
   //@{
   /**@brief SVector with prev/next pointers
      @todo Check whether SVSet::DLPSV can be implemented as IdElement<SVector>
   */
   class DLPSV : public SVector
   {
   private:
      DLPSV *thenext; ///< previous SVector
      DLPSV *theprev; ///< next SVector

   public:
      /// next SVector
      DLPSV*& next()
      {
         return thenext;
      }
      /// next SVector
      DLPSV*const& next() const
      {
         return thenext;
      }
      /// previous SVector
      DLPSV*const& prev() const
      {
         return theprev;
      }
      /// previous SVector
      DLPSV*& prev()
      {
         return theprev;
      }
      /// access to SVector
      SVector& svector()
      {
         return *this; 
      }
      /// default constructor.
      DLPSV() : SVector()
      {}      
      /// copy constructor.
      DLPSV(const DLPSV& copy) : SVector(copy)
      {}
   };
   DataSet < DLPSV > set;  ///< %set of SVectors
   IdList < DLPSV > list;  ///< doubly linked list for non-zero management
   
   /// provides enough vector memory for \p n more SVector%s.
   void ensurePSVec(int n)
   {
      if (num() + n > max())
      {
         assert(factor > 1);
         reMax(int(factor*max()) + 8 + n);
      }
   }

   ///  provides enough nonzero memory for \p n more Elements%s.
   void ensureMem(int n);
   //@}

public:
   /**@name Control Parameters 
      @todo Should factor amd memFactor really be public variables in svset?
   */
   //@{
   /// Sparse vector memory enlargment factor.
   /** If the SVSet runs out of vector memory, it is enlareged by
       \p factor.
    */
   Real factor;

   /// Nonzero element memory enlargment factor.
   /** If the SVSet runs out of nonzero memory it is enlareged by a
       memFactor.
       @todo Should memFactor really be a Real& instead of a plain Real in svset?
    */
   Real& memFactor;
   //@}
   

   /**@name Extension */
   //@{
   /// Add \p svec to the %set.
   /**  This includes copying its nonzeros to the sets nonzero memory and
    *  creating an additional SVector entry in vector memory. If
    *  neccessary, the memory blocks are enlarged appropriately.
    */
   void add(const SVector& svec)
   {
      ensurePSVec(1);
      SVector* new_svec = create(svec.size());
      *new_svec = svec;
   }

   /// Add \p svec to SVSet.
   /**  Adds SVector \p svec to the %set. This includes copying its nonzeros
    *  to the sets nonzero memory and creating an additional SVector
    *  entry in vector memory. If neccessary, the memory blocks are
    *  enlarged appropriately. 
    *  @return \p nkey contains the DataKey, that
    *  the SVSet has assosicated to the new SVector.
    */
   void add(DataKey& nkey, const SVector& svec)
   {
      ensurePSVec(1);
      SVector* new_svec = create(nkey, svec.size());
      *new_svec = svec;
   }

   /// Add all \p n SVector%s in the array \p svec to the %set.
   /** @pre \p svec must be not larger than \p n
    */
   void add(const SVector svec[], int n);

   /// Add n SVector%s to SVSet.
   /**  Adds all \p n SVector%s in the array \p svec to the %set.  
    * @return \p nkey contains the DataKey%s, that the SVSet has assosicated to the
    *  new SVector%s. 
    * @pre \p nkey must be large enough to fit \p n
    *  DataKey%s.
    */
   void add(DataKey nkey[], const SVector svec[], int n);

   /// Add all SVector%s in \p pset to an SVSet.
   void add(const SVSet& pset);

   /// Add all SVector%s of \p pset to SVSet.
   /**  Adds all \p n SVector%s in the \p pset to an SVSet. 
    * @return \p nkey contains the DataKey%s, that the SVSet has assosicated to the
    *  new SVector%s. 
    * @pre \p nkey must be large enough to fit
    *  \p pset.num() DataKey%s.
    */
   void add(DataKey nkey[], const SVSet& pset);

   /// Creates new SVector in %set.
   /**  The new SVector will be ready to fit at least \p idxmax nonzeros.
    */
   SVector* create(int idxmax = -1);

   /// Creates new SVector in %set.
   /**  The new SVector will be ready to fit at least \p idxmax nonzeros.
    * @return \p nkey contains the DataKey associated to the new
    *  SVector.
    */
   SVector* create(DataKey& nkey, int idxmax = -1);

   /// Extend \p svec to fit \p newmax nonzeros.
   /** @pre \p svec must be an SVector of the SVSet.
    */
   void xtend(SVector& svec, int newmax);

   /// Add nonzero (\p idx, \p val) to \p svec of this SVSet.
   /**  Adds one nonzero (\p idx, \p val) to SVector \p svec in the SVSet. 
    *  If \p svec is not large enough to hold the additional nonzero, it will be
    *  automatically enlarged within the %set.
    * @pre \p svec must be an SVector of the SVSet. 
    */
   void add2(SVector &svec, int idx, Real val);

   /// Add \p n nonzeros to \p svec of this SVSet.
   /**  Adds \p n nonzeros to SVector \p svec in the SVSet. If \p svec is not large
    *  enough to hold the additional nonzeros, it will be automatically
    *  enlarged within the %set.
    * @pre \p svec must be an SVector of the SVSet. 
    */
   void add2(SVector &svec, int n, const int idx[], const Real val[]);
   //@}


   /**@name Shrinking */
   //@{
   /// removes the vector with key \p removekey from the %set
   /** @pre \p removekey must be a key from SVSet */
   void remove(DataKey removekey);

   /// removes the vector with number \p removenum from the %set
   /** @pre \p removenum must be a valid vector number from SVSet */
   void remove(int removenum)
   {
      remove(key(removenum));
   }

   /// remove one SVector from %set.
   /** @pre \p svec must be from SVSet */
   void remove(SVector *svec)
   {
      remove(key(svec));
   }

   /// remove multiple elements.
   /** Removes all SVector%s for the SVSet with an
       index \c i such that \p perm[i] < 0. Upon completion, \p perm[i] >= 0
       indicates the new index where the \c i 'th SVector has been moved to
       due to this removal. 
       @pre \p perm must point to an array of at
       least num() integers.
   */
   void remove(int perm[]);

   /// Remove \p n SVector%s from %set.
   /** 
    * @pre  \p keys must be at least of size \p n and valid keys
    */
   void remove(DataKey keys[], int n)
   {
      DataArray < int > perm(num());
      remove(keys, n, perm.get_ptr());
   }

   /// Remove \p n SVector%s from %set.
   /** 
    * @pre  \p nums must be at least of size \p n and valid vector numbers
    */
   void remove(int nums[], int n)
   {
      DataArray < int > perm(num());
      remove(nums, n, perm.get_ptr());
   }

   ///
   void remove(DataKey keys[], int n, int* perm);

   /// Remove \p n SVector%s from %set.
   /** 
    * @pre  \p perm must be at least of size num() 
    * @pre  \p nums must be at least of size \p n
    * @return \p perm is the permutations resulting from this removal: \p perm[i] < 0
    * indicates, that the element to index \c i has been removed. Otherwise, \p perm[i]
    *  is the new index of the element with index \c i before the removal.
    */
   void remove(int nums[], int n, int* perm);

   /// remove all SVector%s from %set.
   void clear()
   {
      DataArray < SVector::Element > ::clear();
      DataArray < SVector::Element > ::reMax(10000);
      set.clear();
      list.clear();
   }
   //@}


   /**@name Access */
   //@{
   /// get SVector by number, writeable
   SVector& operator[](int n)
   {
      return set[n];
   }

   ///get SVector by number
   const SVector& operator[](int n) const
   {
      return set[n];
   }

   ///get SVector by DataKey, writeable
   SVector& operator[](const DataKey& k)
   {
      return set[k];
   }

   ///get SVector by DataKey
   const SVector& operator[](const DataKey& k) const
   {
      return set[k];
   }
   //@}


   /**@name Inquiry */
   //@{
   /// current number of SVector%s.
   int num() const
   {
      return set.num();
   }

   /// current maximum number of SVector%s.
   int max() const
   {
      return set.max();
   }

   /// get DataKey of vector number
   DataKey key(int n) const
   {
      return set.key(n);
   }

   ///get DataKey of SVector
   DataKey key(const SVector* svec) const
   {
      return set.key(static_cast<const DLPSV*>(svec));
   }

   ///get vector number of DataKey
   int number(const DataKey& k) const
   {
      return set.number(k);
   }

   ///get vector number of SVector
   int number(const SVector* svec) const
   {
      return set.number(static_cast<const DLPSV*>(svec));
   }

   /// true iff SVSet contains a SVector for DataKey \p k
   int has(const DataKey& k) const
   {
      return set.has(k);
   }

   ///true iff SVSet contains a SVector for vector number n
   int has(int n) const
   {
      return set.has(n);
   }

   /// is an SVector in the %set.
   int has(const SVector* svec) const
   {
      return set.has(static_cast<const DLPSV*>(svec));
   }
   //@}

   /**@name Memory Management */
   //@{
   /// used nonzero memory.
   int memSize() const
   {
      return DataArray < SVector::Element > ::size();
   }

   /// length of nonzero memory.
   int memMax() const
   {
      return DataArray < SVector::Element > ::max();
   }

   /// reset length of nonzero memory.
   void memRemax(int newmax);

   /// garbage collection in nonzero memory.
   void memPack();
   //@}


   /**@name Miscellaneous */
   //@{
   /// reset maximum number of SVector%s.
   void reMax(int newmax = 0);

   /// consistency check.
   bool isConsistent() const;

   /// assignment operator.
   SVSet& operator=(const SVSet& rhs);

   /// copy constructor.
   SVSet(const SVSet& old);

   /// default constructor.
   SVSet(int pmax = -1,
         int pmemmax = -1,
         Real pfac = 1.1,
         Real pmemFac = 1.2)
      : DataArray < SVector::Element >
         (0, (pmemmax > 0) ? pmemmax : 8 * ((pmax > 0) ? pmax : 8), pmemFac)
         , set ((pmax > 0) ? pmax : 8)
         , factor (pfac)
         , memFactor (DataArray < SVector::Element > ::memFactor)
   { }
   //@}
};

} // namespace soplex
#endif // _SVSET_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
