/**************************************************************************
MCF.H of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Thu Feb 17 22:10:51 2005 by Andreas Loebel (boss.local.de)  */
/*  $Id: mcf.c,v 1.15 2005/02/17 21:43:12 bzfloebe Exp $  */



#include "mcf.h"

#define REPORT

extern long min_impl_duration;
network_t net;





#ifdef _PROTO_
long global_opt( void )
#else
long global_opt( )
#endif
{
    long new_arcs;
    long residual_nb_it;
    

    new_arcs = -1;
    residual_nb_it = net.n_trips <= MAX_NB_TRIPS_FOR_SMALL_NET ?
        MAX_NB_ITERATIONS_SMALL_NET : MAX_NB_ITERATIONS_LARGE_NET;

    while( new_arcs )
    {
#ifdef REPORT
        printf( "active arcs                : %ld\n", net.m );
#endif

        primal_net_simplex( &net );


#ifdef REPORT
        printf( "simplex iterations         : %ld\n", net.iterations );
        printf( "objective value            : %0.0f\n", flow_cost(&net) );
#endif


#if defined AT_HOME
        printf( "%ld residual iterations\n", residual_nb_it );
#endif

        if( !residual_nb_it )
            break;


        if( net.m_impl )
        {
          new_arcs = suspend_impl( &net, (cost_t)-1, 0 );

#ifdef REPORT
          if( new_arcs )
            printf( "erased arcs                : %ld\n", new_arcs );
#endif
        }


        new_arcs = price_out_impl( &net );

#ifdef REPORT
        if( new_arcs )
            printf( "new implicit arcs          : %ld\n", new_arcs );
#endif
        
        if( new_arcs < 0 )
        {
#ifdef REPORT
            printf( "not enough memory, exit(-1)\n" );
#endif

            exit(-1);
        }

#ifndef REPORT
        printf( "\n" );
#endif


        residual_nb_it--;
    }

    printf( "checksum                   : %ld\n", net.checksum );

    return 0;
}






#ifdef _PROTO_
int main( int argc, char *argv[] )
#else
int main( argc, argv )
    int argc;
    char *argv[];
#endif
{
    if( argc < 2 )
        return -1;


    printf( "\nMCF SPEC CPU2006 version 1.10\n" );
    printf( "Copyright (c) 1998-2000 Zuse Institut Berlin (ZIB)\n" );
    printf( "Copyright (c) 2000-2002 Andreas Loebel & ZIB\n" );
    printf( "Copyright (c) 2003-2005 Andreas Loebel\n" );
    printf( "\n" );



    memset( (void *)(&net), 0, (size_t)sizeof(network_t) );
    net.bigM = (long)BIGM;

    strcpy( net.inputfile, argv[1] );
    
    if( read_min( &net ) )
    {
        printf( "read error, exit\n" );
        getfree( &net );
        return -1;
    }


#ifdef REPORT
    printf( "nodes                      : %ld\n", net.n_trips );
#endif


    primal_start_artificial( &net );
    global_opt( );


#ifdef REPORT
    printf( "done\n" );
#endif

    

#if !(defined(__riscv) && !defined(__linux))
/* FIXME: File I/O not available */
    if( write_circulations( "mcf.out", &net ) )
    {
        getfree( &net );
        return -1;    
    }
#endif


    getfree( &net );
    return 0;
}
