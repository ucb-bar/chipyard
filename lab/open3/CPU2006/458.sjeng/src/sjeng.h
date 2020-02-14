/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: sjeng.h
    Purpose: global definitions

*/

#ifndef SJENG_H
#define SJENG_H

#ifndef INPROBECODE
typedef enum {FALSE, TRUE} xbool;
#endif

#include <ctype.h>
#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

// SPEC done at global level (define NDEBUG)
#include <assert.h>

#define DIE (*(int *)(NULL) = 0)

#define WHITE 0
#define BLACK 1

#define ToMove (white_to_move ? 0 : 1)
#define NotToMove (white_to_move ? 1 : 0)

#define Hash(x,y) (hash ^= zobrist[(x)][(y)])

#define Crazyhouse 0
#define Bughouse 1
#define Normal 2
#define Suicide 3
#define Losers 4

#define Opening      0
#define Middlegame   1
#define Endgame      2

#define mindepth 2

/* define names for piece constants: */
#define frame   0
#define wpawn   1
#define bpawn   2
#define wknight 3
#define bknight 4
#define wking   5
#define bking   6
#define wrook   7
#define brook   8
#define wqueen  9
#define bqueen  10
#define wbishop 11
#define bbishop 12
#define npiece  13

#define piecet(x) (((board[(x)])+1)>>1)
#define pieceside(x) ((board[(x)]+1)&1)

#define pawn    1
#define knight  2
#define king    3
#define rook    4
#define queen   5
#define bishop  6

/* result flags: */
#define no_result      0
#define stalemate      1
#define white_is_mated 2
#define black_is_mated 3
#define draw_by_fifty  4
#define draw_by_rep    5

#define rank(square) (Xrank[(square)])
#define file(square) (Xfile[(square)])
#define diagl(square) (Xdiagl[(square)])
#define diagr(square) (Xdiagr[(square)])


/* castle flags: */
#define no_castle  0
#define wck        1
#define wcq        2
#define bck        3
#define bcq        4

typedef struct {
  int from;
  int target;
  int captured;
  int promoted;
  int castled;
  int ep;
} move_s;

typedef struct {
  int cap_num;
  int was_promoted;
  int epsq;
  int fifty;
} move_x;


#define STR_BUFF 256
#define MOVE_BUFF 512
#define INF 1000000
#define PV_BUFF 300

#define AddMaterial(x) Material += material[(x)]
#define RemoveMaterial(x) Material -= material[(x)]

#define UPPER 1
#define LOWER 2
#define EXACT 3
#define HMISS 4
#define DUMMY 0

#define LOSS 0
#define WIN 1
#define DRAW 2

#ifndef max
#define max(x, y) ((x) > (y) ? (x) : (y))
#endif

#ifndef min
#define min(x, y) ((x) < (y) ? (x) : (y))
#endif

#define PACKAGE "Sjeng-SPEC"
#define VERSION "SPEC 1.0"

/*  Timing code. Define one or none of:
 *
 *  GETTICKCOUNT, FTIME, GETTIMEOFDAY, NOTIMING
 */

/* #define FTIME */
/* #define GETTICKCOUNT */
/* #define GETTIMEOFDAY */
#define NOTIMING

#include <time.h>

#ifdef GETTICKCOUNT
  typedef int rtime_t;
#else
#if defined(FTIME) || defined(GETTIMEOFDAY)
#include <sys/time.h>
#include <sys/timeb.h>
  typedef struct timeb rtime_t;
#else
  	typedef time_t rtime_t;
#endif
#endif

#endif

