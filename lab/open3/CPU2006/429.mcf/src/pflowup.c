/**************************************************************************
PFLOWUP.C of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Sun Nov 21 16:22:23 2004 by Andreas Loebel (boss.local.de)  */
/*  $Id: pflowup.c,v 1.10 2005/02/17 19:42:32 bzfloebe Exp $  */



#include "pflowup.h"




#ifdef _PROTO_
void primal_update_flow( 
                 node_t *iplus,
                 node_t *jplus,
                 node_t *w
                 )
#else
void primal_update_flow( iplus, jplus, w )
    node_t *iplus, *jplus;
    node_t *w; 
#endif
{
    for( ; iplus != w; iplus = iplus->pred )
    {
        if( iplus->orientation )
            iplus->flow = (flow_t)0;
        else
            iplus->flow = (flow_t)1;
    }

    for( ; jplus != w; jplus = jplus->pred )
    {
        if( jplus->orientation )
            jplus->flow = (flow_t)1;
        else
            jplus->flow = (flow_t)0;
    }
}
