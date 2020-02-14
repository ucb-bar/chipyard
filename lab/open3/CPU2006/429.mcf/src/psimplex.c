/**************************************************************************
PSIMPLEX.C of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Sun Nov 21 16:22:44 2004 by Andreas Loebel (boss.local.de)  */
/*  $Id: psimplex.c,v 1.9 2005/02/17 19:44:56 bzfloebe Exp $  */



#undef DEBUG

#include "psimplex.h"



#ifdef _PROTO_
long primal_net_simplex( network_t *net )
#else
long primal_net_simplex(  net )
    network_t *net;
#endif
{
    flow_t        delta;
    flow_t        new_flow;
    long          opt = 0;
    long          xchange;
    long          new_orientation;
    node_t        *iplus;
    node_t        *jplus; 
    node_t        *iminus;
    node_t        *jminus;
    node_t        *w; 
    arc_t         *bea;
    arc_t         *bla;
    arc_t         *arcs          = net->arcs;
    arc_t         *stop_arcs     = net->stop_arcs;
    node_t        *temp;
    long          m = net->m;
    long          new_set;
    cost_t        red_cost_of_bea;
    long          *iterations = &(net->iterations);
    long          *bound_exchanges = &(net->bound_exchanges);
    long          *checksum = &(net->checksum);


    while( !opt )
    {       
        if( (bea = primal_bea_mpp( m, arcs, stop_arcs, &red_cost_of_bea )) )
        {
            (*iterations)++;

#ifdef DEBUG
            printf( "it %ld: bea = (%ld,%ld), red_cost = %ld\n", 
                    *iterations, bea->tail->number, bea->head->number,
                    red_cost_of_bea );
#endif

            if( red_cost_of_bea > ZERO ) 
            {
                iplus = bea->head;
                jplus = bea->tail;
            }
            else 
            {
                iplus = bea->tail;
                jplus = bea->head;
            }

            delta = (flow_t)1;
            iminus = primal_iminus( &delta, &xchange, iplus, 
                    jplus, &w );

            if( !iminus )
            {
                (*bound_exchanges)++;
                
                if( bea->ident == AT_UPPER)
                    bea->ident = AT_LOWER;
                else
                    bea->ident = AT_UPPER;

                if( delta )
                    primal_update_flow( iplus, jplus, w );
            }
            else 
            {
                if( xchange )
                {
                    temp = jplus;
                    jplus = iplus;
                    iplus = temp;
                }

                jminus = iminus->pred;

                bla = iminus->basic_arc;
                 
                if( xchange != iminus->orientation )
                    new_set = AT_LOWER;
                else
                    new_set = AT_UPPER;

                if( red_cost_of_bea > 0 )
                    new_flow = (flow_t)1 - delta;
                else
                    new_flow = delta;

                if( bea->tail == iplus )
                    new_orientation = UP;
                else
                    new_orientation = DOWN;

                update_tree( !xchange, new_orientation,
                            delta, new_flow, iplus, jplus, iminus, 
                            jminus, w, bea, red_cost_of_bea,
                            (flow_t)net->feas_tol );

                bea->ident = BASIC; 
                bla->ident = new_set;
               
                if( !((*iterations-1) % 200) )
                {
                    *checksum += refresh_potential( net );
#if defined AT_HOME
                    if( *checksum > 2000000000l )
                    {
                        printf( "%ld\n", *checksum );
                        fflush(stdout);
                    }
#endif
                }   
            }
        }
        else
            opt = 1;
    }


    *checksum += refresh_potential( net );
    primal_feasible( net );
    dual_feasible( net );
    
    return 0;
}



