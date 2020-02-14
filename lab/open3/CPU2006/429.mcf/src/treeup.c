/**************************************************************************
TREEUP.C of ZIB optimizer MCF, SPEC version

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
/*  LAST EDIT: Sun Nov 21 16:23:12 2004 by Andreas Loebel (boss.local.de)  */
/*  $Id: treeup.c,v 1.10 2005/02/17 19:42:32 bzfloebe Exp $  */



#include "treeup.h"




#ifdef _PROTO_
void update_tree( 
                 long cycle_ori,
                 long new_orientation,
                 flow_t delta,
                 flow_t new_flow,
                 node_t *iplus,
                 node_t *jplus,
                 node_t *iminus,
                 node_t *jminus,
                 node_t *w,
                 arc_t *bea,
                 cost_t sigma,
                 flow_t feas_tol
                )
#else
void update_tree( cycle_ori, new_orientation, delta, new_flow, 
                 iplus, jplus, iminus, jminus, w, bea, sigma, feas_tol )
     long cycle_ori;
     long new_orientation;
     flow_t delta; 
     flow_t new_flow;
     node_t *iplus, *jplus;
     node_t *iminus, *jminus;
     node_t *w;
     arc_t *bea;
     cost_t sigma; 
     flow_t feas_tol;
#endif
{
    arc_t    *basic_arc_temp;
    arc_t    *new_basic_arc;  
    node_t   *father;         
    node_t   *temp;           
    node_t   *new_pred;       
    long     orientation_temp;
    long     depth_temp;      
    long     depth_iminus;    
    long     new_depth;       
    flow_t   flow_temp;       


    /**/
    if( (bea->tail == jplus && sigma < 0) ||
        (bea->tail == iplus && sigma > 0) )
        sigma = ABS(sigma);
    else
        sigma = -(ABS(sigma));
    
    father = iminus;
    father->potential += sigma;
 RECURSION:
    temp = father->child;
    if( temp )
    {
    ITERATION:
        temp->potential += sigma;
        father = temp;
        goto RECURSION;
    }
 TEST:
    if( father == iminus )
        goto CONTINUE;
    temp = father->sibling;
    if( temp )
        goto ITERATION;
    father = father->pred;
    goto TEST;
    
 CONTINUE:
    /**/


    temp = iplus;
    father = temp->pred;
    new_depth = depth_iminus = iminus->depth;
    new_pred = jplus;
    new_basic_arc = bea;
    while( temp != jminus )
    {
        if( temp->sibling )
            temp->sibling->sibling_prev = temp->sibling_prev;
        if( temp->sibling_prev )
            temp->sibling_prev->sibling = temp->sibling;
        else father->child = temp->sibling;


        temp->pred = new_pred;
        temp->sibling = new_pred->child;
        if( temp->sibling )
            temp->sibling->sibling_prev = temp;
        new_pred->child = temp;
        temp->sibling_prev = 0;

        orientation_temp = !(temp->orientation); 
        if( orientation_temp == cycle_ori )
            flow_temp = temp->flow + delta;
        else
            flow_temp = temp->flow - delta;
        basic_arc_temp = temp->basic_arc;
        depth_temp = temp->depth;

        temp->orientation = new_orientation;
        temp->flow = new_flow;
        temp->basic_arc = new_basic_arc;
        temp->depth = new_depth;

        new_pred = temp;
        new_orientation = orientation_temp;
        new_flow = flow_temp;
        new_basic_arc = basic_arc_temp;
        new_depth = depth_iminus - depth_temp;      
        temp = father;
        father = temp->pred;
    } 

    if( delta > feas_tol )
    {
        for( temp = jminus; temp != w; temp = temp->pred )
        {
            temp->depth -= depth_iminus;
            if( temp->orientation != cycle_ori )
                temp->flow += delta;
            else
                temp->flow -= delta;
        }
        for( temp = jplus; temp != w; temp = temp->pred )
        {
            temp->depth += depth_iminus;
            if( temp->orientation == cycle_ori )
                temp->flow += delta;
            else
                temp->flow -= delta;
        }
    }
    else
    {
        for( temp = jminus; temp != w; temp = temp->pred )
            temp->depth -= depth_iminus;
        for( temp = jplus; temp != w; temp = temp->pred )
            temp->depth += depth_iminus;
    }

}


