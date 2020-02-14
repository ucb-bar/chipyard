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
#pragma ident "@(#) $Id: updatevector.cpp,v 1.7 2002/01/31 08:19:31 bzfkocht Exp $"
#endif

#include "updatevector.h"
#include "message.h"

namespace soplex
{

UpdateVector& UpdateVector::operator=(const UpdateVector& rhs)
{
   if (this != &rhs)
   {
      theval   = rhs.theval;
      thedelta = rhs.thedelta;
      DVector::operator=(rhs);
   }
   return *this;
}

bool UpdateVector::isConsistent() const
{
   if (dim() != thedelta.dim())
      return MSGinconsistent("UpdateVector");

   return DVector::isConsistent() && thedelta.isConsistent();
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
