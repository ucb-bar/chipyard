/**************************************************************************
IMPLICIT.H of ZIB optimizer MCF, SPEC version

This software was developed at ZIB Berlin. Maintenance and revisions 
solely on responsibility of Andreas Loebel

Dr. Andreas Loebel
Ortlerweg 29b, 12207 Berlin

Konrad-Zuse-Zentrum fuer Informationstechnik Berlin (ZIB)
Scientific Computing - Optimization
Takustr. 7, 14195 Berlin-Dahlem

Copyright (c) 1998-2000 ZIB.           
Copyright (c) 2000-2002 ZIB & Loebel.  
Copyright (c) 2003-2005 Andreas Loebel.
**************************************************************************/
/*  LAST EDIT: Sun Nov 21 16:21:18 2004 by Andreas Loebel (boss.local.de)  */
/*  $Id: implicit.h,v 1.11 2005/02/17 19:42:21 bzfloebe Exp $  */


#ifndef _IMPLICIT_H
#define _IMPLICIT_H


#include "mcfutil.h"
#include "mcflimit.h"


extern long price_out_impl _PROTO_(( network_t * ));
extern long suspend_impl _PROTO_(( network_t *, cost_t, long ));


#endif
