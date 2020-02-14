/**************************************************************************
MCFLIMIT.H of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Thu Feb 17 22:24:36 2005 by Andreas Loebel (boss.local.de)  */
/*  $Id: mcflimit.h,v 1.12 2005/02/17 21:43:12 bzfloebe Exp $  */


#ifndef _MCF_LIMITS_H
#define _MCF_LIMITS_H


#define BIGM 1.0e7
#define STRECHT(x) ((long)(1.25 * (double)(x)))

#define MAX_NB_TRIPS_FOR_SMALL_NET 15000

#define MAX_NEW_ARCS_SMALL_NET 3000000
#define MAX_NEW_ARCS_LARGE_NET 28900000

#define MAX_NB_ITERATIONS_SMALL_NET  5
#define MAX_NB_ITERATIONS_LARGE_NET  5


/*
// Some operating systems and compiler, respectively, do not handle reallocs
// properly. Thus, this program requires a somehow static memory handling
// without reallocation of the main (and quite huge) arc array.
*/
#define SPEC_STATIC


#endif
