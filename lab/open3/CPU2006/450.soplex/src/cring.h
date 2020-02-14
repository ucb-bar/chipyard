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
#pragma ident "@(#) $Id: cring.h,v 1.4 2002/02/04 15:34:08 bzfpfend Exp $"
#endif

#ifndef _CRING_H_
#define _CRING_H_

/***************************************************************
                    Double linked ring
 ***************************************************************/

#define initDR(ring)    ((ring).prev = (ring).next = &(ring))

#define init2DR(elem, ring)                                     \
{                                                               \
(elem).next = (ring).next;                                 \
(elem).next->prev = &(elem);                               \
(elem).prev = &(ring);                                     \
(ring).next = &(elem);                                     \
}

#define removeDR(ring)                                          \
{                                                               \
(ring).next->prev = (ring).prev;                           \
(ring).prev->next = (ring).next;                           \
}

#define mergeDR(ring1, ring2)                                   \
{                                                               \
Dring       *tmp;                                          \
tmp = (ring1).next;                                        \
(ring1).next = (ring2).next;                               \
(ring2).next = tmp;                                        \
(ring1).next->prev = &(ring1);                             \
(ring2).next->prev = &(ring2);                             \
}

#endif // _CRING_H_




//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
