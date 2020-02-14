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
#pragma ident "@(#) $Id: nameset.cpp,v 1.23 2002/04/03 11:22:04 bzfkocht Exp $"
#endif

#include <string.h>
#include "spxdefines.h"
#include "nameset.h"
#include "spxalloc.h"

namespace soplex
{
const char NameSet::Name::deflt = '\0';

void NameSet::add(const char* str)
{
   DataKey k;
   add(k, str);
}

void NameSet::add(DataKey& p_key, const char* str)
{
   const Name nstr (str);

   if (!hashtab.has(nstr))
   {
      if (size() + 1 > max())
      {
         assert(factor >= 1);
         reMax(int(factor*max() + 8));
      }

      if (memSize() + int(strlen(str)) >= memMax())
      {
         memPack();
         if (memSize() + int(strlen(str)) >= memMax())
         {
            assert(memFactor >= 1);
            memRemax(int(memFactor*memMax()) + 9 + int(strlen(str)));
            assert(memSize() + int(strlen(str)) < memMax());
         }
      }
      int   idx = memused;
      char* tmp = &(mem[idx]);
      memused  += int(strlen(str)) + 1;

      strcpy(tmp, str);
      *(set.create(p_key)) = idx;
      Name memname(tmp);
      hashtab.add(memname, p_key);
   }
}

void NameSet::add(const NameSet& p_set)
{
   for (int i = 0; i < p_set.num(); ++i)
   {
      Name iname(p_set[i]);
      if (!hashtab.has(iname))
         add(p_set[i]);
   }
}

void NameSet::add(DataKey p_key[], const NameSet& p_set)
{
   for (int i = 0; i < p_set.num(); ++i)
   {
      Name iname = Name(p_set[i]);
      if (!hashtab.has(iname))
         add(p_key[i], p_set[i]);
   }
}

void NameSet::remove(const char *str)
{
   const Name nam(str);
   if (hashtab.has(nam))
   {
      const DataKey* hkey = hashtab.get(nam);
      hashtab.remove(nam);
      set.remove(*hkey);
   }
}

void NameSet::remove(DataKey p_key)
{
   assert(has(p_key));

   hashtab.remove(Name(&mem[set[p_key]]));
   set.remove(p_key);
}

void NameSet::remove(DataKey keys[], int n)
{
   for (int i = 0; i < n; ++i)
      remove(keys[i]);
}

void NameSet::remove(int nums[], int n)
{
   for (int i = 0; i < n; ++i)
      remove(nums[i]);
}

void NameSet::remove(int dstat[])
{
   for(int i = 0; i < set.num(); i++)
   {
      if (dstat[i] < 0)
      {
         const Name nam = &mem[set[i]];
         hashtab.remove(nam);
      }
   }
   set.remove(dstat);

   assert(isConsistent());
}

void NameSet::clear()
{
   set.clear();
   hashtab.clear();
   memused = 0;
}

void NameSet::reMax(int newmax)
{
   hashtab.reMax(newmax);
   set.reMax(newmax);
}

void NameSet::memRemax(int newmax)
{
   memmax = (newmax < memSize()) ? memSize() : newmax;
   spx_realloc(mem, memmax);

   hashtab.clear ();

   for (int i = num() - 1; i >= 0; --i)
      hashtab.add(Name(&mem[set[key(i)]]), key(i));
}

void NameSet::memPack()
{
   char* newmem = 0;
   int   newlast = 0;
   int   i;

   hashtab.clear();

   spx_alloc(newmem, memSize());

   for(i = 0; i < num(); i++)
   {
      const char* t = &mem[set[i]];
      strcpy(&newmem[newlast], t);
      set[i] = newlast;
      newlast += strlen(t) + 1;      
   }
   memcpy(mem, newmem, newlast);
   memused = newlast;

   assert(memSize() <= memMax());

   spx_free(newmem);

   for (i = 0; i < num(); i++)
      hashtab.add(Name(&mem[set[key(i)]]), key(i));
}

/// returns the hash value of the name.
int NameSetNameHashFunction(const NameSet::Name* str)
{
   unsigned int res = 0;
   const char* sptr = str->name;

   while(*sptr != '\0')
   {
      res *= 65;
      res += *sptr++ - int('0');
      res %= 0x0fffffff;
   }
   return res;
}

#if 0
NameSet& NameSet::operator=(const NameSet& rhs)
{
   if (this != &rhs)
   {
      if (max() < rhs.size())
         reMax(rhs.size());
      if (memMax() < rhs.memSize())
         memRemax(rhs.memSize());

      set = rhs.set;

      hashtab.clear();
      for (int i = 0; i < set.num(); ++i)
      {
         Name iname(set[i].name);
         DataKey ikey = DataKey(set.key(i));
         hashtab.add(iname, ikey);
      }
      memPack();
   }
   return *this;
}

NameSet::NameSet(const NameSet& org)
   : set(org.set)
   , mem(0)
   , hashtab(org.hashtab)
   , factor(org.factor)
   , memFactor(org.memFactor)
{
   memused = 0;
   memmax = org.memSize();
   spx_alloc(mem, memmax);

   list.clear();
   hashtab.clear();
   for (int i = 0; i < set.num(); ++i)
   {
      list.append(&(set[i]));
      Name iname = set[i];
      DataKey k = DataKey(set.key(i));
      hashtab.add(iname, k);
   }
   memPack();
}
#endif

NameSet::NameSet(int p_max, int mmax, Real fac, Real memFac)
   : set(p_max)
   , mem(0)
   , hashtab(NameSetNameHashFunction, set.max(), 0, fac)
   , factor(fac)
   , memFactor(memFac)
{
   memused = 0;
   memmax = (mmax < 1) ? (8 * set.max() + 1) : mmax;
   spx_alloc(mem, memmax);
}

NameSet::~NameSet()
{
   spx_free(mem);
}

bool NameSet::isConsistent() const
{
   if (memused > memmax)
      return MSGinconsistent("NameSet");

   int i;

   for(i = 0; i < num(); i++)
   {
      const char* t = &mem[set[i]];

      if (!has(t))
         return MSGinconsistent("NameSet");

      if (strcmp(t, operator[](key(t))))
         return MSGinconsistent("NameSet");
   }
   return set.isConsistent() && hashtab.isConsistent();
}

std::ostream& operator<<(std::ostream& s, const NameSet& nset)
{
   for(int i = 0; i < nset.num(); i++)
   {
      s << i << " " 
        << nset.key(i).info << "."
        << nset.key(i).idx << "= "
        << nset[i] 
        << std::endl;
   }
   return s;
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
