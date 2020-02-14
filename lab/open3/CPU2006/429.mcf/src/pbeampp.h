/**************************************************************************
PBEAMPP.H of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Sun Nov 21 16:22:09 2004 by Andreas Loebel (boss.local.de)  */
/*  $Id: pbeampp.h,v 1.10 2005/02/17 19:42:21 bzfloebe Exp $  */



#ifndef _PBEAMPP_H
#define _PBEAMPP_H


#include "defines.h"


extern arc_t *primal_bea_mpp _PROTO_(( long, arc_t*, arc_t*, cost_t* ));


#endif
