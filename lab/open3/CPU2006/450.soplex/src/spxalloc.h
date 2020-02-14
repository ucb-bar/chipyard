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
#pragma ident "@(#) $Id: spxalloc.h,v 1.9 2002/03/11 17:43:56 bzfkocht Exp $"
#endif

/**@file  spxalloc.h
 * @brief Memory allocation routines.
 */
#ifndef _SPXALLOC_H_
#define _SPXALLOC_H_

#include <iostream>
#include <stdlib.h>
#include <assert.h>

#include "spxdefines.h"

namespace soplex
{
/**@name    Memory allocation routines
 * @ingroup Elementary
 * Here we have cover functions for malloc/realloc/free, to make sure
 * that we allays succeed. Otherwise abort() is called.
 *
 * We use templates to get the types right, otherwise casts would have 
 * been neccessary.
 */
//@{
/**@brief Allocate memory.
 * @param p some pointer
 * @param n the number of elements #p will point to.
 */
template <class T>
inline void spx_alloc(T& p, int n)
{
   assert(p == 0);
   assert(n >= 0);

   if (n == 0)
      n = 1;
   
   p = reinterpret_cast<T>(malloc(sizeof(*p) * n));

   if (0 == p)
   {
      std::cerr << "malloc: Out of memory" << std::endl;
      abort();
   }
}

/**@brief Change amount of allocated memory.
 * @param p some pointer
 * @param n the number of elements p should point to.
 */
template <class T>
inline void spx_realloc(T& p, int n)
{
   assert(p != 0);
   assert(n >= 0);

   p = reinterpret_cast<T>(realloc(p, sizeof(*p) * n));

   if (0 == p)
   {
      std::cerr << "realloc: Out of memory" << std::endl;
      abort();
   }
}

/// Release memory
template <class T>
inline void spx_free(T& p)
{
   assert(p != 0);

   free(p);
   
   p = 0;
}

//@}
} // namespace soplex
#endif // _SPXALLOC_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
