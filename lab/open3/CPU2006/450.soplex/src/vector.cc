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
#pragma ident "@(#) $Id: vector.cpp,v 1.11 2002/04/06 13:05:03 bzfkocht Exp $"
#endif

#include <iostream>

#include "spxdefines.h"
#include "vector.h"
#include "ssvector.h"
#include "svector.h"
#include "message.h"

namespace soplex
{
Vector& Vector::operator=(const Vector& vec)
{
   if (this != &vec)
   {
      assert(dim() == vec.dim());
      memcpy(val, vec.val, dimen*sizeof(Real));
   }
   return *this;
}

Vector& Vector::operator=(const SVector& vec)
{
   clear();
   assign(vec);
   return *this;
}

Vector& Vector::assign(const SVector& psv)
{
   for (int i = psv.size(); i-- > 0;)
      val[psv.index(i)] = psv.value(i);
   return *this;
}

Vector& Vector::operator+=(const Vector& vec)
{
   assert(dim() == vec.dim());
   for (int i = 0; i < dim(); ++i)
      val[i] += vec[i];
   return *this;
}

Vector& Vector::operator+=(const SVector& vec)
{
   // for (int i = vec.size(); i > 0; --i)
   for( int i = 0; i < vec.size(); ++i )
   {
      assert(vec.index(i) >= 0);
      assert(vec.index(i) < dim());
      val[vec.index(i)] += vec.value(i);
   }
   return *this;
}

Vector& Vector::operator-=(const Vector& vec)
{
   assert(dim() == vec.dim());
   for (int i = 0; i < dim(); ++i)
      val[i] -= vec[i];
   return *this;
}

Vector& Vector::operator-=(const SVector& vec)
{
   // for (int i = vec.size(); i--;)
   for( int i = 0; i < vec.size(); ++i )
   {
      assert(vec.index(i) >= 0);
      assert(vec.index(i) < dim());
      val[vec.index(i)] -= vec.value(i);
   }
   return *this;
}

Vector& Vector::operator*=(Real x)
{
   for (int i = 0; i < dim(); ++i)
      val[i] *= x;
   return *this;
}

Real Vector::length() const
{
   return sqrt(length2());
}

Real Vector::length2() const
{
   return *this * *this;
}

Real Vector::maxAbs() const
{
   Real x = 0;
   int n = dim();
   Real* v = val;
   while (n--)
   {
      x = (*v > x) ? *v : ((-*v > x) ? -*v : x);
      v++;
   }
   return x;
}

std::ostream& operator<<(std::ostream& s, const Vector& vec)
{
   int i;
   s << '(';
   for (i = 0; i < vec.dim() - 1; ++i)
      s << vec[i] << ", ";
   s << vec[i] << ')';
   return s;
}

Real Vector::operator*(const SVector& v) const
{
   assert(dim() >= v.dim());
   int i;
   Real x = 0;
   for (i = v.size(); i-- > 0;)
      x += val[v.index(i)] * v.value(i);
   return x;
}

bool Vector::isConsistent() const
{
   if (dim() > 0 && val == 0)
      return MSGinconsistent("Vector");

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
