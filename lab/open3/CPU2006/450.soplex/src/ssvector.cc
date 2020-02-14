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
#pragma ident "@(#) $Id: ssvector.cpp,v 1.33 2005/01/12 12:00:03 bzfkocht Exp $"
#endif

#include <iostream>
#include <iomanip>
#include <assert.h>

#include "spxdefines.h"
#include "ssvector.h"
#include "svset.h"
#include "message.h"

/**@file ssvector.cpp
 * @todo There is a lot pointer arithmetic done here. It is not clear if
 *       this is an advantage at all. See all the function int() casts.
 * @todo Several operations like maxAbs could setup the vector while
 *       computing there result.
 */
namespace soplex
{
#define MARKER   1e-100

static const Real shortProductFactor = 0.5;

void SSVector::setMax(int newmax)
{
   assert(idx    != 0);
   assert(newmax != 0);
   assert(newmax >= IdxSet::size());

   // len = (newmax < IdxSet::max()) ? IdxSet::max() : newmax;
   len = newmax;

   spx_realloc(idx, len);
}

void SSVector::reDim (int newdim)
{
   for (int i = IdxSet::size() - 1; i >= 0; --i)
      if (index(i) >= newdim)
         remove(i);
   DVector::reDim(newdim);
   setMax(DVector::memSize() + 1);
   assert(isConsistent());
}

void SSVector::reMem(int newsize)
{
   DVector::reSize(newsize);
   assert(isConsistent());
   setMax(DVector::memSize() + 1);
}

void SSVector::clear ()
{
   if (isSetup())
   {
      for(int i = 0; i < num; ++i)
         val[idx[i]] = 0.0;
   }
   else
      Vector::clear();

   IdxSet::clear();
   setupStatus = true;
   assert(isConsistent());
}

void SSVector::setValue(int i, Real x)
{
   assert(i >= 0 && i < DVector::dim());

   if (isSetup())
   {
      int n = number(i);

      if (n < 0)
      {
         if (isNotZero(x, epsilon))
            IdxSet::add(1, &i);
      }
      else if (x == 0)
         clearNum(n);
   }
   val[i] = x;

   assert(isConsistent());
}

#ifdef USE_OLD // old version
void SSVector::setup()
{
   if (!isSetup())
   {
      IdxSet::clear();

      // #define      TWO_LOOPS
#ifdef  TWO_LOOPS
      int i = 0;
      int n = 0;
      int* id = idx;
      Real* v = val;
      const Real* end = val + dim();

      while (v < end)
      {
         id[n] = i++;
         n += (*v++ != 0);
      }

      Real x;
      int* ii = idx;
      int* last = idx + n;
      v = val;

      for (; id < last; ++id)
      {
         x = v[*id];
         if (isNotZero(x, epsilon))
            *ii++ = *id;
         else
            v[*id] = 0;
      }
      num = ii - idx;

#else

      if (dim() <= 1)
      {
         if (dim())
         {
            if (isNotZero(*val, epsilon))
               IdxSet::add(0);
            else
               *val = 0;
         }
      }
      else
      {
         int* ii = idx;
         Real* v = val;
         Real* end = v + dim() - 1;

         /* setze weissen Elefanten */
         Real last = *end;
         *end = MARKER;

         /* erstes element extra */
         if (isNotZero(*v, epsilon))
            *ii++ = 0;
         else
            *v = 0.0;

         for(;;)
         {
            while (*++v == 0.0)
               ;
            if (isNotZero(*v, epsilon))
            {
               *ii++ = int(v - val);
            }
            else
            {
               *v = 0.0;
               if (v == end)
                  break;
            }
         }
         /* fange weissen Elefanten wieder ein */
         if (isNotZero(last, epsilon))
         {
            *v = last;
            *ii++ = dim() - 1;
         }
         else
            *v = 0;

         num = int(ii - idx);
      }

#endif

      setupStatus = true;
      assert(isConsistent());
   }
}
#else // new version, not yet fully testet
void SSVector::setup()
{
   if (!isSetup())
   {
      IdxSet::clear();

      num = 0;

      for(int i = 0; i < dim(); ++i)
      {
         if (val[i] != 0.0)
         {
            if (isZero(val[i], epsilon))
               val[i] = 0.0;
            else
            {
               idx[num] = i;
               num++;
            }
         }
      }
      setupStatus = true;
      assert(isConsistent());
   }
}
#endif

SSVector& SSVector::operator+=(const Vector& vec)
{
   Vector::operator+=(vec);

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}

SSVector& SSVector::operator+=(const SVector& vec)
{
   Vector::operator+=(vec);

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}

#ifdef USE_OLD // old version
SSVector& SSVector::operator+=(const SSVector& vec)
{
   for (int i = vec.size() - 1; i >= 0; --i)
      val[vec.index(i)] += vec.value(i);

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}
#else
SSVector& SSVector::operator+=(const SSVector& vec)
{
   for (int i = 0; i < vec.size(); ++i)
      val[vec.index(i)] += vec.value(i);

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}
#endif

SSVector& SSVector::operator-=(const Vector& vec)
{
   Vector::operator-=(vec);

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}

SSVector& SSVector::operator-=(const SVector& vec)
{
   Vector::operator-=(vec);

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}

#ifdef USE_OLD // old version
SSVector& SSVector::operator-=(const SSVector& vec)
{
   if (vec.isSetup())
   {
      for (int i = vec.size() - 1; i >= 0; --i)
         val[vec.index(i)] -= vec.value(i);
   }
   else
   {
      Vector::operator-=(Vector(vec));
   }

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}
#else
SSVector& SSVector::operator-=(const SSVector& vec)
{
   if (vec.isSetup())
   {
      for (int i = 0; i < vec.size(); ++i)
         val[vec.index(i)] -= vec.value(i);
   }
   else
   {
      Vector::operator-=(Vector(vec));
   }

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}
#endif

#ifdef USE_OLD // old version
SSVector& SSVector::operator*=(Real x)
{
   assert(isSetup());

   for (int i = size() - 1; i >= 0; --i)
      val[index(i)] *= x;
   assert(isConsistent());
   return *this;
}
#else
SSVector& SSVector::operator*=(Real x)
{
   assert(isSetup());

   for (int i = 0; i < size(); ++i)
      val[index(i)] *= x;

   assert(isConsistent());

   return *this;
}
#endif

#ifdef USE_OLD // old
Real SSVector::maxAbs() const
{
   if (isSetup())
   {
      int* i = idx;
      int* end = idx + num;
      Real* v = val;
      Real absval = 0.0;

      for (; i < end; ++i)
      {
         Real x = v[*i];
         if (fabs(x) > absval)
            absval = fabs(x);
      }
      return absval;
   }
   else
      return Vector::maxAbs();
}
#else // new, not fully tested
Real SSVector::maxAbs() const
{
   if (isSetup())
   {
      Real maxabs = 0.0;

      for(int i = 0; i < num; ++i)
      {
         Real x = fabs(val[idx[i]]);

         if (x > maxabs)
            maxabs = x;
      }
      return maxabs;
   }
   else
      return Vector::maxAbs();
}
#endif // !0

Real SSVector::length2() const
{
   Real x = 0.0;

   if (isSetup())
   {
      for(int i = 0; i < num; ++i)
         x += val[idx[i]] * val[idx[i]];
   }
   else
      x = Vector::length2();

   return x;
}

Real SSVector::length() const
{
   return sqrt(length2());
}

#if 0 // buggy and not used
/* @todo check if really not used or if the Vector version is used instead.
 */
SSVector& SSVector::multAdd(Real xx, const SSVector& svec)
{
   if (svec.isSetup())
   {
      if (isSetup())
      {
         int i, j;
         Real x;

         for (i = svec.size() - 1; i >= 0; --i)
         {
            j = svec.index(i);
            if (val[j])
            {
               x = val[j] + xx * svec.value(i);
               if (isNotZero(x, epsilon))
                  val[j] = x;
               else
               {
                  val[j] = 0;
                  for (--i; i >= 0; --i)
                     val[svec.index(i)] += xx * svec.value(i);
                  unSetup();
                  break;
               }
            }
            else
            {
               x = xx * svec.value(i);
               if (isNotZero(x, epsilon))
               {
                  val[j] = x;
                  addIdx(j);
               }
            }
         }
      }
      else
         Vector::multAdd(xx, svec);
   }
   else
   {
      /**@todo this code does not work, because in is never something
       *       added to v. Also the idx will not be setup correctly
       *       Fortunately the whole function seems not to be called
       *       at all. 
       */
      assert(false);

      Real y;
      int* ii = idx;
      Real* v = val;
      Real* rv = static_cast<Real*>(svec.val);
      Real* last = rv + svec.dim() - 1;
      Real x = *last;

      *last = MARKER;
      for(;;)
      {
         while (!*rv)
         {
            ++rv;
            ++v;
         }
         y = *rv++ * xx;
         if (isNotZero(y, epsilon))
         {
            *ii++ = int(v - val);
            *v++ = y;
         }
         else if (rv == last)
            break;
         else
            v++;
      }
      *rv = x;

      x *= xx;
      if (isNotZero(x, epsilon))
      {
         *ii++ = int(v - val);
         *v = x;
      }
      num = int(ii - idx);

      setupStatus = true;
   }

   assert(isConsistent());
   return *this;
}
#endif // 0

/* @todo This function does not look good. MARKER is set but never really used.
 */
SSVector& SSVector::multAdd(Real xx, const SVector& svec)
{
   if (isSetup())
   {
      int i, j;
      Real x;
      Real* v = val;
      int adjust = 0;

      for (i = svec.size() - 1; i >= 0; --i)
      {
         j = svec.index(i);
         if (v[j])
         {
            x = v[j] + xx * svec.value(i);
            if (isNotZero(x, epsilon))
               v[j] = x;
            else
            {
               adjust = 1;
               v[j] = MARKER;
            }
         }
         else
         {
            x = xx * svec.value(i);
            if (isNotZero(x, epsilon))
            {
               v[j] = x;
               addIdx(j);
            }
         }
      }

      if (adjust)
      {
         int* iptr = idx;
         int* iiptr = idx;
         int* endptr = idx + num;
         for (; iptr < endptr; ++iptr)
         {
            x = v[*iptr];
            if (isNotZero(x, epsilon))
               *iiptr++ = *iptr;
            else
               v[*iptr] = 0;
         }
         num = int(iiptr - idx);
      }
   }
   else
      Vector::multAdd(xx, svec);

   assert(isConsistent());
   return *this;
}

SSVector& SSVector::multAdd(Real x, const Vector& vec)
{
   Vector::multAdd(x, vec);

   if (isSetup())
   {
      setupStatus = false;
      setup();
   }
   return *this;
}

#ifdef USE_OLD // old version
SSVector& SSVector::operator=(const SSVector& rhs)
{
   assert(rhs.isConsistent());

   if (this != &rhs)
   {
      clear();

      setMax(rhs.max());
      IdxSet::operator=(rhs);
      DVector::reDim(rhs.dim());

      if (rhs.isSetup())
      {
         for (int i = size() - 1; i >= 0; --i)
         {
            int j = index(i);
            val[j] = rhs.val[j];
         }
      }
      else
      {
         int* ii = idx;
         Real* v = val;
         Real* rv = static_cast<Real*>(rhs.val);
         Real* last = rv + rhs.dim() - 1;
         Real x = *last;
         
         *last = MARKER;
         for(;;)
         {
            while (!*rv)
            {
               ++rv;
               ++v;
            }
            if (isNotZero(*rv, epsilon))
            {
               *ii++ = int(v - val);
               *v++ = *rv++;
            }
            else if (rv == last)
               break;
            else
            {
               v++;
               rv++;
            }
         }
         *rv = x;
         
         if (isNotZero(x, epsilon))
         {
            *ii++ = int(v - val);
            *v++ = x;
         }
         num = int(ii - idx);
      }
      setupStatus = true;
   }
   assert(isConsistent());

   return *this;
}
#else // new version
SSVector& SSVector::operator=(const SSVector& rhs)
{
   assert(rhs.isConsistent());

   if (this != &rhs)
   {
      clear();
      epsilon = rhs.epsilon;
      setMax(rhs.max());
      DVector::reDim(rhs.dim());

      if (rhs.isSetup())
      {
         IdxSet::operator=(rhs);

         for(int i = 0; i < size(); ++i)
         {
            int j  = index(i);
            val[j] = rhs.val[j];
         }
      }
      else
      {
         num = 0;

         for(int i = 0; i < rhs.dim(); ++i)
         {
            if (isNotZero(rhs.val[i], epsilon))
            {
               val[i]       = rhs.val[i];
               idx[num]     = i;
               num++;
            }
         }
      }
      setupStatus = true;
   }
   assert(isConsistent());

   return *this;
}
#endif // 0

#ifdef USE_OLD // old version
void SSVector::setup_and_assign(SSVector& rhs)
{
   assert(rhs.isConsistent());

   clear();
   epsilon = rhs.epsilon;
   setMax(rhs.max());
   DVector::reDim(rhs.dim());

   if (rhs.isSetup())
   {
      int i, j;
      IdxSet::operator=(rhs);
      for (i = size() - 1; i >= 0; --i)
      {
         j = index(i);
         val[j] = rhs.val[j];
      }
   }
   else
   {
      int* ri = rhs.idx;
      int* ii = idx;
      Real* rv = rhs.val;
      Real* v = val;
      Real* last = rv + rhs.dim() - 1;
      Real x = *last;

      *last = MARKER;

      for(;;)
      {
         while (!*rv)
         {
            ++rv;
            ++v;
         }
         if (isNotZero(*rv, rhs.epsilon))
         {
            *ri++ = *ii++ = int(v - val);
            *v++ = *rv++;
         }
         else if (rv == last)
            break;
         else
         {
            v++;
            *rv++ = 0;
         }
      }

      if (isNotZero(x, rhs.epsilon))
      {
         *ri++ = *ii++ = int(v - val);
         *v++ = *rv = x;
      }
      else
         *rv = 0;
      num = rhs.num = int(ii - idx);
      rhs.setupStatus = true;
   }
   setupStatus = true;

   assert(isConsistent());
}
#else // new version
/* setup rhs and assign to this
 */
void SSVector::setup_and_assign(SSVector& rhs)
{
   clear();
   epsilon = rhs.epsilon;
   setMax(rhs.max());
   DVector::reDim(rhs.dim());

   if (rhs.isSetup())
   {
      IdxSet::operator=(rhs);
      
      for(int i = 0; i < size(); ++i)
      {
         int j  = index(i);
         val[j] = rhs.val[j];
      }
   }
   else
   {
      num = 0;

      for(int i = 0; i < rhs.dim(); ++i)
      {
         if (rhs.val[i] != 0.0)
         {
            if (isNotZero(rhs.val[i], epsilon))
            {
               rhs.idx[num] = i;
               idx[num]     = i;
               val[i]       = rhs.val[i];
               num++;
            }
            else
            {
               rhs.val[i] = 0.0;
            }
         }
      }
      rhs.num         = num;
      rhs.setupStatus = true;
   }
   setupStatus = true;

   assert(rhs.isConsistent());
   assert(isConsistent());
}
#endif // 0


SSVector& SSVector::operator=(const SVector& rhs)
{
   clear();
   return assign(rhs);
}

#ifdef USE_OLD // old version (buggy or optimization dependend)
SSVector& SSVector::assign(const SVector& rhs)
{
   assert(rhs.dim() <= Vector::dim());

   const SVector::Element* e = rhs.m_elem;
   int* p = idx;
   int i = rhs.size();

   while (i--)
   {
      val[*p = e->idx] = e->val;
      p += ((e++)->val != 0);
   }
   num = int(p - idx);
   setupStatus = true;

   assert(isConsistent());
   return *this;
}

#else // new version not yet fully testet, could be put into operator=()
SSVector& SSVector::assign(const SVector& rhs)
{
   assert(rhs.dim() <= Vector::dim());

   num = 0;

   for(int i = 0; i < rhs.size(); ++i)
   {
      int  k = rhs.index(i);
      Real v = rhs.value(i);

      if (isZero(v, epsilon))
         val[k] = 0.0;
      else
      {
         val[k]     = v;
         idx[num++] = k;
      }
   }
   setupStatus = true;

   assert(isConsistent());

   return *this;
}
#endif

SSVector& SSVector::assign2product1(const SVSet& A, const SSVector& x)
{
   assert(x.isSetup());

   const Real* vl = x.val;
   const int* xi = x.idx;

   int* ii = idx;
   SVector* svec = const_cast<SVector*>( & A[*xi] );
   const SVector::Element* e = &(svec->element(0));
   const SVector::Element* last = e + (num = svec->size());
   Real* v = val;
   Real y = vl[*xi];

   for (; e < last; ++e)
      v[ *ii++ = e->idx ] = y * e->val;

   assert(isConsistent());

   return *this;
}

SSVector& SSVector::assign2productShort(const SVSet& A, const SSVector& x)
{
   assert(x.isSetup());

   int i;
   int j;
   Real y;
   int* ii                      = idx;
   const Real* vl               = x.val;
   const int*  xi               = x.idx;
   SVector* svec                = const_cast<SVector*>( & A[*xi] );
   const SVector::Element* e    = &(svec->element(0));
   const SVector::Element* last = e + (num = svec->size());
   Real* v                      = val;
   Real xx                      = vl[*xi++];

   for (; e < last; ++e)
   {
      y      = xx * e->val;
      *ii    = e->idx;
      v[*ii] = y;
      ii    += (y != 0) ? 1 : 0;
   }

   for (i = x.size(); --i > 0;)
   {
      xx = vl[*xi];
      svec = const_cast<SVector*>( & A[*xi++] );
      e = &(svec->element(0));
      for (int k = svec->size(); --k >= 0;)
      {
         j   = e->idx;
         *ii = j;
         y   = v[j];
         ii  += (y == 0) ? 1 : 0;
         y   += xx * e->val;
         e++;
         v[j] = (y != 0) ? y : MARKER;
      }
   }

   int* is = idx;
   int* it = idx;
   for (; is < ii; ++is)
   {
      if (isNotZero(v[*is], epsilon))
         *it++ = *is;
      else
         v[*is] = 0;
   }
   num = int(it - idx);

   assert(isConsistent());

   return *this;
}

SSVector& SSVector::assign2productFull(const SVSet& A, const SSVector& x)
{
   assert(x.isSetup());

   int i;
   const Real* vl = x.val;
   const int* xi = x.idx;

   SVector* svec;
   const SVector::Element* elem;
   const SVector::Element* last;
   Real y;
   Real* v = val;

   for (i = x.size(); i-- > 0; ++xi)
   {
      svec = const_cast<SVector*>( & A[*xi] );
      elem = &(svec->element(0));
      last = elem + svec->size();
      y = vl[*xi];
      for (; elem < last; ++elem)
         v[elem->idx] += y * elem->val;
   }
   return *this;
}

SSVector& SSVector::assign2product4setup(const SVSet& A, const SSVector& x)
{
   assert(A.num() == x.dim());

   assert(x.isSetup());

   clear();

   if (x.size() == 1)
   {
      assign2product1(A, x);
      setupStatus = true;
   }

   else if (Real(x.size())*A.memSize() <= shortProductFactor*dim()*A.num()
             && isSetup())
   {
      assign2productShort(A, x);
      setupStatus = true;
   }

   else
   {
      assign2productFull(A, x);
      setupStatus = false;
   }
   assert(isConsistent());

   return *this;
}

SSVector& SSVector::assign2product(const SSVector& x, const SVSet& A)
{
   assert(A.num() == dim());

   Real y;

   clear();

   for (int i = dim(); i-- > 0;)
   {
      y = A[i] * x;

      if (isNotZero(y, epsilon))
      {
         val[i] = y;
         IdxSet::addIdx(i);
      }
   }
   assert(isConsistent());

   return *this;
}

SSVector& SSVector::assign2productAndSetup(const SVSet& A, SSVector& x)
{
   if (x.isSetup())
      return assign2product4setup(A, x);

   SVector* svec;
   const SVector::Element* elem;
   const SVector::Element* last;
   Real y;
   Real* v = val;
   int* xi = x.idx;
   Real* xv = x.val;
   Real* end = xv + x.dim() - 1;

   /* setze weissen Elefanten */
   Real lastval = *end;
   *end = MARKER;

   for(;;)
   {
      while (!*xv)
         ++xv;
      if (isNotZero(*xv, epsilon))
      {
         y = *xv;
         svec = const_cast<SVector*>( & A[ *xi++ = int(xv - x.val) ] );
         elem = &(svec->element(0));
         last = elem + svec->size();
         for (; elem < last; ++elem)
            v[elem->idx] += y * elem->val;
      }
      else
      {
         *xv = 0;
         if (xv == end)
            break;
      }
      xv++;
   }

   /* fange weissen Elefanten wieder ein */
   if (isNotZero(lastval, epsilon))
   {
      y = *xv = lastval;
      svec = const_cast<SVector*>( & A[ *xi++ = int(xv - x.val) ] );
      elem = &(svec->element(0));
      last = elem + svec->size();
      for (; elem < last; ++elem)
         v[elem->idx] += y * elem->val;
   }
   else
      *xv = 0;

   x.num = int(xi - x.idx);
   x.setupStatus = true;
   setupStatus = false;

   assert(isConsistent());

   return *this;
}

bool SSVector::isConsistent() const
{
   if (Vector::dim() > IdxSet::max())
      return MSGinconsistent("SSVector");

   if (Vector::dim() < IdxSet::dim())
      return MSGinconsistent("SSVector");

   if (isSetup())
   {
      for (int i = 0; i < Vector::dim(); ++i)
      {
         int j = number(i);

         if (j < 0 && fabs(val[i]) > 0.0) 
         {
            std::cerr << "i= " << i << " idx= " << j << " val= " << std::setprecision(16) << val[i] << std::endl;
            return MSGinconsistent("SSVector");
         }
      }
   }
   return DVector::isConsistent() && IdxSet::isConsistent();
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

