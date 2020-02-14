/* $Id: lbm.h,v 1.1 2004/04/20 14:33:59 pohlt Exp $ */

/*############################################################################*/

#ifndef _LBM_H_
#define _LBM_H_

/*############################################################################*/

#include "config.h"

/*############################################################################*/

typedef enum {C = 0,
              N, S, E, W, T, B,
              NE, NW, SE, SW,
              NT, NB, ST, SB,
              ET, EB, WT, WB,
              FLAGS, N_CELL_ENTRIES} CELL_ENTRIES;
#define N_DISTR_FUNCS FLAGS

typedef enum {OBSTACLE    = 1 << 0,
              ACCEL       = 1 << 1,
              IN_OUT_FLOW = 1 << 2} CELL_FLAGS;

#include "lbm_1d_array.h"

/*############################################################################*/

void LBM_allocateGrid( double** ptr );
void LBM_freeGrid( double** ptr );
void LBM_initializeGrid( LBM_Grid grid );
void LBM_initializeSpecialCellsForLDC( LBM_Grid grid );
void LBM_loadObstacleFile( LBM_Grid grid, const char* filename );
void LBM_initializeSpecialCellsForChannel( LBM_Grid grid );
void LBM_swapGrids( LBM_GridPtr* grid1, LBM_GridPtr* grid2 );
void LBM_performStreamCollide( LBM_Grid srcGrid, LBM_Grid dstGrid );
void LBM_handleInOutFlow( LBM_Grid srcGrid );
void LBM_showGridStatistics( LBM_Grid Grid );
void LBM_storeVelocityField( LBM_Grid grid, const char* filename,
                           const BOOL binary );
void LBM_compareVelocityField( LBM_Grid grid, const char* filename,
                             const BOOL binary );

/*############################################################################*/

#endif /* _LBM_H_ */
