/**************************************************************************
PFLOWUP.H of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Sun Nov 21 16:22:28 2004 by Andreas Loebel (boss.local.de)  */
/*  $Id: pflowup.h,v 1.10 2005/02/17 19:42:21 bzfloebe Exp $  */



#ifndef _PFLOWUP_H
#define _PFLOWUP_H


#include "defines.h"


extern void primal_update_flow _PROTO_(( node_t *, node_t *, node_t * ));


#endif
