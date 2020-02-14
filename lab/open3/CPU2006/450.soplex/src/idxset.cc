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
#pragma ident "@(#) $Id: idxset.cpp,v 1.9 2002/01/31 08:19:26 bzfkocht Exp $"
#endif

#include "idxset.h"
#include "message.h"

namespace soplex
{

int IdxSet::dim() const
{
   int ddim = -1;

   for (int i = 0; i < size(); i++)
      if (ddim < idx[i])
         ddim = idx[i];

   return ddim; 
}

int IdxSet::number(int i) const
{
   for(int n = 0; n < size(); n++)
      if (idx[n] == i)
         return n;

   return -1;
}

void IdxSet::add(int n, const int i[])
{
   assert(n >= 0 && size() + n <= max());
   for (int j = 0; j < n; j++)
      idx[size() + j] = i[j];
   add(n);
}

void IdxSet::remove(int n, int m)
{
   assert(n <= m && m < size() && n >= 0);
   ++m;

   int cpy = m - n;
   int newnum = num - cpy;
   cpy = (size() - m >= cpy) ? cpy : size() - m;

   do
   {
      --num;
      --cpy;
      idx[n + cpy] = idx[num];
   }
   while (cpy > 0);
   num = newnum;
}

IdxSet& IdxSet::operator=(const IdxSet& rhs)
{
   if (this != &rhs)
   {
      assert(max() >= rhs.size());

      for (num = 0; num < rhs.size(); num++)
         idx[num] = rhs.idx[num];
   }
   assert(size() == rhs.size());
   assert(size() <= max());

   return *this;
}

bool IdxSet::isConsistent() const
{
   int i, j;

   if (len > 0 && idx == 0)
      return MSGinconsistent("IdxSet");

   for (i = 0; i < size(); ++i)
   {
      if (index(i) < 0)
         return MSGinconsistent("IdxSet");

      for (j = 0; j < i; j++)
         if (index(i) == index(j))
            return MSGinconsistent("IdxSet");
   }
   return true;
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
