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
#pragma ident "@(#) $Id: svset.cpp,v 1.19 2002/03/11 11:41:56 bzfkocht Exp $"
#endif

#include <assert.h>

#include "spxdefines.h"
#include "svset.h"
#include "message.h"

namespace soplex
{

void SVSet::ensureMem(int n)
{
   if (memSize() + n > memMax())
   {
      int newMax = int( memFactor * memMax() );
      if ( memSize() + n > newMax )
         newMax  = memSize() + n;
      memRemax( newMax );
   }
}

void SVSet::add(const SVector svec[], int n)
{
   assert(n >= 0);

   int i, len;
   for (i = len = 0; i < n; ++i)
      len += svec[i].size();

   ensurePSVec(n);
   ensureMem(len + n);

   for (i = 0; i < n; ++i)
      *create(svec[i].size()) = svec[i];
}

void SVSet::add(DataKey nkey[], const SVector svec[], int n)
{
   add(svec, n);
   for (int i = num() - 1; --n; --i)
      nkey[n] = key(i);
}

void SVSet::add(const SVSet& pset)
{
   int i, n, len;
   n = pset.num();
   for (i = len = 0; i < n; ++i)
      len += pset[i].size();

   ensurePSVec(n);
   ensureMem(len + n);

   for (i = 0; i < n; ++i)
      *create(pset[i].size()) = pset[i];
}

void SVSet::add(DataKey nkey[], const SVSet& pset)
{
   add(pset);

   int i = size();
   int n = pset.size();

   while(n > 0)
      nkey[--n] = key(--i);
}

SVector* SVSet::create(int idxmax)
{
   DLPSV* ps;

   if (list.last())
   {
      ps = list.last();
      removeLast(ps->max() - ps->size());
      ps->set_max( ps->size() );
   }

   if (idxmax < 0)
   {
      ensureMem(2);
      idxmax = memMax() - memSize() - 1;
   }
   else
   {
      idxmax = (idxmax >= 0) ? idxmax : 0;
      ensureMem(idxmax + 1);
   }

   ensurePSVec(1);

   // We must call ensureMem() before insert() below, since insert() doesn't
   // know about the pointers into the NZE memory and therefore doesn't update
   // them if a realloc is necessary.
   ensureMem( memSize() + idxmax + 1 );
   assert( memMax() >= memSize() + idxmax + 1 );

   ps = set.create();
   list.append(ps);
   insert(memSize(), idxmax + 1);
   ps->setMem(idxmax + 1, &last() - idxmax);
   return ps;
}

SVector* SVSet::create(DataKey& nkey, int idxmax)
{
   SVector* ps = create(idxmax);
   nkey = key(num() - 1);
   return ps;
}

void SVSet::xtend(SVector& svec, int newmax)
{
   if (svec.max() < newmax)
   {
      assert(has(&svec));
      DLPSV* ps = static_cast<DLPSV*>( & svec );

      if (ps == list.last())
      {
         int sz = ps->size();
         ensureMem (newmax - ps->max() + 1);
         insert(memSize(), newmax - ps->max());
         ps->setMem (newmax + 1, ps->mem());
         ps->set_size( sz );
      }

      else
      {
         ensureMem(newmax + 1);
         SVector newps(newmax + 1, &last() + 1);
         int sz = ps->size();
         insert(memSize(), newmax + 1);
         newps = svec;

         if (ps != list.first())
         {
            SVector* prev = ps->prev();
            int prevsz = prev->size();
            prev->setMem (prev->max()
                           + ps->max() + 2, prev->mem());
            prev->set_size(prevsz);
         }
         list.remove(ps);
         list.append(ps);

         ps->setMem(newmax + 1, newps.mem());
         ps->set_size(sz);
      }
   }
}

void SVSet::add2(SVector &svec, int idx, Real val)
{
   xtend(svec, svec.size() + 1);
   svec.add(idx, val);
}

void SVSet::add2(SVector &svec, int n, const int idx[], const Real val[])
{
   xtend(svec, svec.size() + n);
   svec.add(n, idx, val);
}

void SVSet::remove(DataKey removekey)
{
   DLPSV* ps = &set[removekey];

   if (list.last() == ps)
      removeLast(ps->max() + 1);

   else if (list.first() != ps)
   {
      SVector* prev = ps->prev();
      int sz = prev->size();
      prev->setMem (prev->max()
                     + ps->max() + 2, prev->mem());
      prev->set_size(sz);
   }

   list.remove(ps);
   set.remove(removekey);
}

void SVSet::remove(int perm[])
{
   int j = num();
   for (int i = 0; i < j; ++i)
   {
      if (perm[i] < 0)
      {
         DLPSV* ps = &set[i];

         if (list.last() == ps)
            removeLast(ps->max() + 1);

         else if (list.first() != ps)
         {
            SVector* prev = ps->prev();
            int sz = prev->size();
            prev->setMem (prev->max() + ps->max() + 2, prev->mem());
            prev->set_size(sz);
         }

         list.remove(ps);
      }
   }

   set.remove(perm);
}

void SVSet::remove(DataKey keys[], int n, int* perm)
{
   for (int i = num() - 1; i >= 0; --i)
      perm[i] = i;
   while (n--)
      perm[ number(*keys++) ] = -1;
   remove(perm);
}

void SVSet::remove(int nums[], int n, int* perm)
{
   for (int i = num() - 1; i >= 0; --i)
      perm[i] = i;
   while (n--)
      perm[ *nums++ ] = -1;
   remove(perm);
}


/*      \SubSection{Memory Management}
 */
void SVSet::reMax(int newmax)
{
   list.move(set.reMax(newmax));
}

void SVSet::memRemax(int newmax)
{
   ptrdiff_t delta = DataArray < SVector::Element > ::reMax(newmax);

   if (delta != 0)
   {
      for (DLPSV* ps = list.first(); ps; ps = list.next(ps))
      {
         SVector::Element * info = reinterpret_cast<SVector::Element*>(reinterpret_cast<char*>(ps->mem()) + delta);
         int sz = info->idx;
         int l_max = int( info->val );
         assert(l_max >= sz );
         ps->setMem (l_max + 1, info);
         ps->set_max (l_max);
         ps->set_size(sz);
      }
   }
}

void SVSet::memPack()
{
   int used;
   int j;
   DLPSV* ps;
   for (used = 0, ps = list.first(); ps; ps = list.next(ps))
   {
      const DLPSV * cps = ps;
      const int sz = cps->size();

      if (ps->mem() != &this->SVSetBase::operator[](used))
      {
         for (j = 0; j <= sz; ++j)
            this->SVSetBase::operator[](used + j) = ps->mem()[j];
         ps->setMem(sz + 1, &this->SVSetBase::operator[](used));
         ps->set_size(sz);

      }
      used += sz + 1;
   }
   SVSetBase::reSize(used);
}

bool SVSet::isConsistent() const
{
   DLPSV* ps;
   DLPSV* next;
   for (ps = list.first(); ps; ps = next)
   {
      if (!ps->isConsistent())
         return MSGinconsistent("SVSet");
      if (ps->mem() > &last())
         return MSGinconsistent("SVSet");
      next = list.next(ps);
      if (next && ps->mem() + ps->max() + 1 != next->mem()) {
         return MSGinconsistent("SVSet");
      }
   }
   return DataArray < SVector::Element > ::isConsistent() 
      && set.isConsistent() && list.isConsistent();
}

SVSet& SVSet::operator=(const SVSet& rhs)
{
   if (this != &rhs)
   {
      clear();

      if (rhs.size() > 0)
      {
         DataArray < SVector::Element > ::operator=(rhs);
         set = rhs.set;

         DLPSV* ps;
         DLPSV* newps;

         void* delta0 = &(*(static_cast<SVSetBase*>(this)))[0];
         void* delta1 = &(*(static_cast<SVSetBase*>(
            const_cast<SVSet*>(&rhs))))[0];
         ptrdiff_t delta = reinterpret_cast<char*>(
            delta0) - reinterpret_cast<char*>(delta1);

         for (ps = rhs.list.first(); ps; ps = rhs.list.next(ps))
         {
            newps = & set[ rhs.number(ps) ];
            list.append(newps);
            newps->setMem(ps->max() + 1, 
               reinterpret_cast<SVector::Element*>(
                  reinterpret_cast<char*>(ps->mem()) + delta));
            newps->set_size( ps->size() );
         }
      }
   }
   assert(isConsistent());

   return *this;
}

SVSet::SVSet(const SVSet& old)
   : DataArray < SVector::Element > ()
   , factor (old.factor)
   , memFactor (DataArray < SVector::Element > ::memFactor)
{
   *this = old;
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
