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
#pragma ident "@(#) $Id: spxid.cpp,v 1.1 2002/03/21 16:06:18 bzfkocht Exp $"
#endif

#include "spxid.h"

namespace soplex
{
SPxColId::SPxColId(const DataKey& p_key) 
   : DataKey(p_key)
{
   info = SPxId::COL_ID;
}

SPxColId::SPxColId(const SPxId& p_key) 
   : DataKey(p_key)
{
   assert(!p_key.isSPxRowId());

   info = SPxId::COL_ID;
}

SPxRowId::SPxRowId(const DataKey& p_key) 
   : DataKey(p_key)
{
   info = SPxId::ROW_ID;
}

SPxRowId::SPxRowId(const SPxId& p_key) 
   : DataKey(p_key)
{
   assert(!p_key.isSPxColId());

   info = SPxId::ROW_ID;
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




