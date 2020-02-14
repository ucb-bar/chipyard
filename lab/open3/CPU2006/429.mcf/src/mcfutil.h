/**************************************************************************
MCFUTIL.H of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Sun Nov 21 16:21:49 2004 by Andreas Loebel (boss.local.de)  */
/*  $Id: mcfutil.h,v 1.10 2005/02/17 19:42:21 bzfloebe Exp $  */



#ifndef _MCFUTIL_H
#define _MCFUTIL_H


#include "defines.h"


extern void refresh_neighbour_lists _PROTO_(( network_t * ));
extern long refresh_potential _PROTO_(( network_t * ));
extern double flow_cost _PROTO_(( network_t * ));
extern double flow_org_cost _PROTO_(( network_t * ));
extern long primal_feasible _PROTO_(( network_t * ));
extern long dual_feasible _PROTO_(( network_t * ));
extern long getfree _PROTO_(( network_t * ));


#endif
