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
#pragma ident "@(#) $Id: dsvector.cpp,v 1.15 2002/03/03 13:50:31 bzfkocht Exp $"
#endif

#include <assert.h>
#include <iostream>

#include "spxdefines.h"
#include "dsvector.h"
#include "spxalloc.h"
#include "message.h"

namespace soplex
{
void DSVector::allocMem(int len)
{
   spx_alloc(theelem, len);
   setMem(len, theelem);
}

void DSVector::setMax(int newmax)
{
   int siz = size();
   int len = ((newmax < siz) ? siz : newmax) + 1;

   spx_realloc(theelem, len);
   setMem (len, theelem);
   set_size( siz );
}

DSVector& DSVector::operator=(const Vector& vec)
{
   clear();
   setMax(vec.dim());
   SVector::operator=(vec);
   return *this;
}

DSVector::DSVector(const SVector& old)
   : theelem( 0 )
{
   allocMem(old.size() + 1);
   SVector::operator= ( old );
}

DSVector::DSVector(const SSVector& old)
   : theelem( 0 )
{
   allocMem(old.size() + 1);
   SVector::operator= ( old );
}

DSVector::DSVector(const DSVector& old)
   : SVector()
   , theelem( 0 )
{
   allocMem(old.size() + 1);
   SVector::operator= ( old );
}

DSVector::DSVector(int n)
   : theelem( 0 )
{
   allocMem((n < 1) ? 2 : n + 1);
}

DSVector::DSVector(const Vector& vec)
   : theelem( 0 )
{
   allocMem((vec.dim() < 1) ? 2 : vec.dim() + 1);
   *this = vec;
}

DSVector::~DSVector()
{
   spx_free(theelem);
}

bool DSVector::isConsistent() const
{
   if ((theelem != 0) && (mem() != theelem))
      return MSGinconsistent("DSVector");

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


