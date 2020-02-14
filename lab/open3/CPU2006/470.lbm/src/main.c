/* $Id: main.c,v 1.4 2004/04/21 04:23:43 pohlt Exp $ */

/*############################################################################*/

#include "main.h"
#include "lbm.h"
#include <stdio.h>
#include <stdlib.h>

#if defined(SPEC_CPU)
#   include <time.h>
#else
#   include <sys/times.h>
#   include <unistd.h>
#endif

#include <sys/stat.h>

/*############################################################################*/

static LBM_GridPtr srcGrid, dstGrid;

/*############################################################################*/

int main( int nArgs, char* arg[] ) {
	MAIN_Param param;
#if !defined(SPEC_CPU)
	MAIN_Time time;
#endif
	int t;

	MAIN_parseCommandLine( nArgs, arg, &param );
	MAIN_printInfo( &param );
	MAIN_initialize( &param );
#if !defined(SPEC_CPU)
	MAIN_startClock( &time );
#endif

	for( t = 1; t <= param.nTimeSteps; t++ ) {
		if( param.simType == CHANNEL ) {
			LBM_handleInOutFlow( *srcGrid );
		}

		LBM_performStreamCollide( *srcGrid, *dstGrid );
		LBM_swapGrids( &srcGrid, &dstGrid );

		if( (t & 63) == 0 ) {
			printf( "timestep: %i\n", t );
			LBM_showGridStatistics( *srcGrid );
		}
	}

#if !defined(SPEC_CPU)
	MAIN_stopClock( &time, &param );
#endif
	MAIN_finalize( &param );

	return 0;
}

/*############################################################################*/

void MAIN_parseCommandLine( int nArgs, char* arg[], MAIN_Param* param ) {
	struct stat fileStat;
	
	if( nArgs < 5 || nArgs > 6 ) {
		printf( "syntax: lbm <time steps> <result file> <0: nil, 1: cmp, 2: str> <0: ldc, 1: channel flow> [<obstacle file>]\n" );
		exit( 1 );
	}

	param->nTimeSteps     = atoi( arg[1] );
	param->resultFilename = arg[2];
	param->action         = (MAIN_Action) atoi( arg[3] );
	param->simType        = (MAIN_SimType) atoi( arg[4] );

	if( nArgs == 6 ) {
		param->obstacleFilename = arg[5];

#if !(defined(__riscv) && !defined(__linux))
/* FIXME: stat(2) not available */
		if( stat( param->obstacleFilename, &fileStat ) != 0 ) {
			printf( "MAIN_parseCommandLine: cannot stat obstacle file '%s'\n",
			         param->obstacleFilename );
			exit( 1 );
		}
		if( fileStat.st_size != SIZE_X*SIZE_Y*SIZE_Z+(SIZE_Y+1)*SIZE_Z ) {
			printf( "MAIN_parseCommandLine:\n"
			        "\tsize of file '%s' is %i bytes\n"
					    "\texpected size is %i bytes\n",
			        param->obstacleFilename, (int) fileStat.st_size,
			        SIZE_X*SIZE_Y*SIZE_Z+(SIZE_Y+1)*SIZE_Z );
			exit( 1 );
		}
#endif
	}
	else param->obstacleFilename = NULL;

	if( param->action == COMPARE &&
	    stat( param->resultFilename, &fileStat ) != 0 ) {
		printf( "MAIN_parseCommandLine: cannot stat result file '%s'\n",
		         param->resultFilename );
		exit( 1 );
	}
}

/*############################################################################*/

void MAIN_printInfo( const MAIN_Param* param ) {
	const char actionString[3][32] = {"nothing", "compare", "store"};
	const char simTypeString[3][32] = {"lid-driven cavity", "channel flow"};
	printf( "MAIN_printInfo:\n"
	        "\tgrid size      : %i x %i x %i = %.2f * 10^6 Cells\n"
	        "\tnTimeSteps     : %i\n"
	        "\tresult file    : %s\n"
	        "\taction         : %s\n"
	        "\tsimulation type: %s\n"
	        "\tobstacle file  : %s\n\n",
	        SIZE_X, SIZE_Y, SIZE_Z, 1e-6*SIZE_X*SIZE_Y*SIZE_Z,
	        param->nTimeSteps, param->resultFilename, 
	        actionString[param->action], simTypeString[param->simType],
	        (param->obstacleFilename == NULL) ? "<none>" :
	                                            param->obstacleFilename );
}

/*############################################################################*/

void MAIN_initialize( const MAIN_Param* param ) {
	LBM_allocateGrid( (double**) &srcGrid );
	LBM_allocateGrid( (double**) &dstGrid );

	LBM_initializeGrid( *srcGrid );
	LBM_initializeGrid( *dstGrid );

	if( param->obstacleFilename != NULL ) {
		LBM_loadObstacleFile( *srcGrid, param->obstacleFilename );
		LBM_loadObstacleFile( *dstGrid, param->obstacleFilename );
	}

	if( param->simType == CHANNEL ) {
		LBM_initializeSpecialCellsForChannel( *srcGrid );
		LBM_initializeSpecialCellsForChannel( *dstGrid );
	}
	else {
		LBM_initializeSpecialCellsForLDC( *srcGrid );
		LBM_initializeSpecialCellsForLDC( *dstGrid );
	}

	LBM_showGridStatistics( *srcGrid );
}

/*############################################################################*/

void MAIN_finalize( const MAIN_Param* param ) {
	LBM_showGridStatistics( *srcGrid );

	if( param->action == COMPARE )
		LBM_compareVelocityField( *srcGrid, param->resultFilename, TRUE );
	if( param->action == STORE )
	LBM_storeVelocityField( *srcGrid, param->resultFilename, TRUE );

	LBM_freeGrid( (double**) &srcGrid );
	LBM_freeGrid( (double**) &dstGrid );
}

#if !defined(SPEC_CPU)
/*############################################################################*/

void MAIN_startClock( MAIN_Time* time ) {
	time->timeScale = 1.0 / sysconf( _SC_CLK_TCK );
	time->tickStart = times( &(time->timeStart) );
}


/*############################################################################*/

void MAIN_stopClock( MAIN_Time* time, const MAIN_Param* param ) {
	time->tickStop = times( &(time->timeStop) );

	printf( "MAIN_stopClock:\n"
	        "\tusr: %7.2f sys: %7.2f tot: %7.2f wct: %7.2f MLUPS: %5.2f\n\n",
	        (time->timeStop.tms_utime - time->timeStart.tms_utime) * time->timeScale,
	        (time->timeStop.tms_stime - time->timeStart.tms_stime) * time->timeScale,
	        (time->timeStop.tms_utime - time->timeStart.tms_utime +
	         time->timeStop.tms_stime - time->timeStart.tms_stime) * time->timeScale,
	        (time->tickStop           - time->tickStart          ) * time->timeScale,
	        1.0e-6 * SIZE_X * SIZE_Y * SIZE_Z * param->nTimeSteps /
	        (time->tickStop           - time->tickStart          ) / time->timeScale );
}
#endif
