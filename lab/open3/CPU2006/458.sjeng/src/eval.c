/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: eval.c
    Purpose: evaluate crazyhouse/bughouse positions

*/

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"
#include "squares.h"

int Material;
int std_material[] = { 0, 100, -100, 310, -310, 4000, -4000, 500, -500, 900, -900, 325, -325, 0 };

int zh_material[] = { 0, 100, -100, 210, -210, 4000, -4000, 250, -250, 450, -450, 230, -230, 0 };

int suicide_material[] = { 0, 15, -15, 150, -150, 500, -500, 150, -150, 50, -50, 0, 0, 0 };

int losers_material[] = { 0, 80, -80, 320, -320, 1000, -1000, 350, -350, 400, -400, 270, -270, 0 };

int material[] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

const int Xfile[144] =
{
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};

const int Xrank[144] =
{
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,1,1,1,1,1,1,1,1,0,0,
  0,0,2,2,2,2,2,2,2,2,0,0,
  0,0,3,3,3,3,3,3,3,3,0,0,
  0,0,4,4,4,4,4,4,4,4,0,0,
  0,0,5,5,5,5,5,5,5,5,0,0,
  0,0,6,6,6,6,6,6,6,6,0,0,
  0,0,7,7,7,7,7,7,7,7,0,0,
  0,0,8,8,8,8,8,8,8,8,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};

const int Xdiagl[144] =
{
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0, 1, 2, 3, 4, 5, 6, 7, 8,0,0,
  0,0, 9, 1, 2, 3, 4, 5, 6, 7,0,0,
  0,0,10, 9, 1, 2, 3, 4, 5, 6,0,0,
  0,0,11,10, 9, 1, 2, 3, 4, 5,0,0,
  0,0,12,11,10, 9, 1, 2, 3, 4,0,0,
  0,0,13,12,11,10, 9, 1, 2, 3,0,0,
  0,0,14,13,12,11,10, 9, 1, 2,0,0,
  0,0,15,14,13,12,11,10, 9, 1,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};

const int Xdiagr[144] =
{
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,15,14,13,12,11,10,9,1,0,0,
  0,0,14,13,12,11,10,9,1,2,0,0,
  0,0,13,12,11,10,9,1,2,3,0,0,
  0,0,12,11,10,9,1,2,3,4,0,0,
  0,0,11,10,9,1,2,3,4,5,0,0,
  0,0,10,9,1,2,3,4,5,6,0,0,
  0,0,9,1,2,3,4,5,6,7,0,0,
  0,0,1,2,3,4,5,6,7,8,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};

const int sqcolor[144] =
{
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,1,0,1,0,1,0,1,0,0,0,
  0,0,0,1,0,1,0,1,0,1,0,0,
  0,0,1,0,1,0,1,0,1,0,0,0,
  0,0,0,1,0,1,0,1,0,1,0,0,
  0,0,1,0,1,0,1,0,1,0,0,0,
  0,0,0,1,0,1,0,1,0,1,0,0,
  0,0,1,0,1,0,1,0,1,0,0,0,
  0,0,0,1,0,1,0,1,0,1,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};


/* these tables will be used for positional bonuses: */

const int pcsqbishop[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-5,-5,-10,-5,-5,-10,-5,-5,0,0,
0,0,-5,10,5,10,10,5,10,-5,0,0,
0,0,-5,5,6,15,15,6,5,-5,0,0,
0,0,-5,3,15,10,10,15,3,-5,0,0,
0,0,-5,3,15,10,10,15,3,-5,0,0,
0,0,-5,5,6,15,15,6,5,-5,0,0,
0,0,-5,10,5,10,10,5,10,-5,0,0,
0,0,-5,-5,-10,-5,-5,-10,-5,-5,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

const int black_knight[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-20,-10,-10,-10,-10, -10,-10,-20,0,0,
0,0,-10, 15, 25, 25, 25,  25, 15,-10,0,0,
0,0,-10, 15, 25, 35, 35 , 35, 15,-10,0,0,
0,0,-10, 10, 25, 20, 25,  25, 10,-10,0,0,
0,0,-10, 0,  20, 20, 20,  20,  0,-10,0,0,
0,0,-10, 0,  15, 15, 15,  15,  0,-10,0,0,
0,0,-10, 0,   0,  3,  3,   0,  0,-10,0,0,
0,0,-20,-35,-10,-10,-10, -10,-35,-20,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

const int white_knight[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-20, -35,-10, -10, -10,-10, -35, -20,0,0,
0,0,-10,   0,  0,   3,   3,  0,   0, -10,0,0,
0,0,-10,   0, 15,  15,  15, 15,   0, -10,0,0,
0,0,-10,   0, 20,  20,  20, 20,   0, -10,0,0,
0,0,-10,  10, 25,  20,  25, 25,  10, -10,0,0,
0,0,-10,  15, 25,  35,  35, 35,  15, -10,0,0,
0,0,-10,  15, 25,  25,  25, 25,  15, -10,0,0,
0,0,-20, -10,-10, -10, -10,-10, -10, -20,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

const int white_pawn[144] =
{
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,25, 25, 35,  5,  5, 50, 45, 30,0,0,
  0,0, 0,  0,  0,  7,  7,  5,  5,  0,0,0,
  0,0, 0,  0,  0, 14, 14,  0,  0,  0,0,0,
  0,0, 0,  0, 10, 20, 20, 10,  5,  5,0,0,
  0,0,12, 18, 18, 27, 27, 18, 18, 18,0,0,
  0,0,25, 30, 30, 35, 35, 35, 30, 25,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};

const int black_pawn[144] =
{
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,30, 30, 30, 35, 35, 35, 30, 25,0,0,
  0,0,12, 18, 18, 27, 27, 18, 18, 18,0,0,
  0,0, 0,  0, 10, 20, 20, 10,  5,  5,0,0,
  0,0, 0,  0,  0, 14, 14,  0,  0,  0,0,0,
  0,0, 0,  0,  0,  7,  7,  5,  5,  0,0,0,
  0,0,25, 25, 35,  5,  5, 50, 45, 30,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};

/* to be used during opening and middlegame for white king positioning: */
const int white_king[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-100,   7,   4,  0,  10  , 4,   7,-100,0,0,
0,0,-250,-200,-150,-100,-100,-150,-200,-250,0,0,
0,0,-350,-300,-300,-250,-250,-300,-300,-350,0,0,
0,0,-400,-400,-400,-350,-350,-400,-400,-400,0,0,
0,0,-450,-450,-450,-450,-450,-450,-450,-450,0,0,
0,0,-500,-500,-500,-500,-500,-500,-500,-500,0,0,
0,0,-500,-500,-500,-500,-500,-500,-500,-500,0,0,
0,0,-500,-500,-500,-500,-500,-500,-500,-500,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

/* to be used during opening and middlegame for black king positioning: */
const int black_king[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-500,-500,-500,-500,-500,-500,-500,-500,0,0,
0,0,-500,-500,-500,-500,-500,-500,-500,-500,0,0,
0,0,-500,-500,-500,-500,-500,-500,-500,-500,0,0,
0,0,-450,-450,-450,-450,-450,-450,-450,-450,0,0,
0,0,-400,-400,-400,-350,-350,-400,-400,-400,0,0,
0,0,-350,-300,-300,-250,-250,-300,-300,-350,0,0,
0,0,-250,-200,-150,-100,-100,-150,-200,-250,0,0,
0,0,-100,   7,   4,   0,  10  , 4,   7,-100,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

const int black_queen[144] = {
0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
0, 0, 5, 5, 5,10,10, 5, 5, 5, 0, 0,
0, 0, 0, 0, 3, 3, 3, 3, 3, 0, 0, 0,
0, 0,-30,-30,-30,-30,-30,-30,-30,-30,0, 0,
0,0,-60,-40,-40,-60,-60,-40,-40,-60,0,0,
0,0,-40,-40,-40,-40,-40,-40,-40,-40,0,0,
0,0,-15,-15,-15,-10,-10,-15,-15,-15,0,0,
0,0,0,0,0,7,10,5,0,0,0,0,
0,0,0,0,0, 5,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};


const int white_queen[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,5,0,0,0,0,0,0,
0,0,0,0,0,7,10,5,0,0,0,0,
0,0,-15,-15,-15,-10,-10,-15,-15,-15,0,0,
0,0,-40,-40,-40,-40,-40,-40,-40,-40,0,0,
0,0,-60,-40,-40,-60,-60,-40,-40,-60,0,0,
0, 0,-30,-30,-30,-30,-30,-30,-30,-30,0, 0,
0, 0, 0, 0, 3, 3, 3,3,3, 0, 0, 0,
0, 0, 5, 5, 5,10,10,5,5,5, 0, 0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

const int black_rook[144] =
{
  0,0,  0,  0,  0,  0,  0,  0,  0,  0,0,0,
  0,0,  0,  0,  0,  0,  0,  0,  0,  0,0,0,
  0,0, 10, 15, 20, 25, 25, 20, 15, 10,0,0,
  0,0,  0, 10, 15, 20, 20, 15, 10,  0,0,0,
  0,0,-20,-20,-20,-20,-20,-20,-20,-20,0,0,
  0,0,-20,-20,-20,-30,-30,-20,-20,-20,0,0,
  0,0,-20,-20,-20,-20,-20,-20,-20,-20,0,0,
  0,0,-15,-15,-15,-10,-10,-15,-15,-15,0,0,
  0,0,  0,  0,  0,  7, 10,  0,  0,  0,0,0,
  0,0,  2,  2,  2,  2,  2,  2,  2,  2,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0
};

const int white_rook[144] =
{
    0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,  2,  2,  2,  2,  2,  2,  2,  2,0,0,
    0,0,  0,  0,  0,  7, 10,  0,  0,  0,0,0,
    0,0,-15,-15,-15,-10,-10,-15,-15,-15,0,0,
    0,0,-20,-20,-20,-20,-20,-20,-20,-20,0,0,
    0,0,-20,-20,-20,-30,-30,-20,-20,-20,0,0,
    0,0,-20,-20,-20,-20,-20,-20,-20,-20,0,0,
    0,0,  0, 10, 15, 20, 20, 15, 10,  0,0,0,
    0,0, 10, 15, 20, 25, 25, 20, 15, 10,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0
};

const int upscale[64] = {
  A1,B1,C1,D1,E1,F1,G1,H1,
  A2,B2,C2,D2,E2,F2,G2,H2,
  A3,B3,C3,D3,E3,F3,G3,H3,
  A4,B4,C4,D4,E4,F4,G4,H4,
  A5,B5,C5,D5,E5,F5,G5,H5,
  A6,B6,C6,D6,E6,F6,G6,H6,
  A7,B7,C7,D7,E7,F7,G7,H7,
  A8,B8,C8,D8,E8,F8,G8,H8
};

/* tropism values of 0 and 8 are bogus,
   and should never happen in the actual eval */

int pre_p_tropism[9] =
{ 9999, 40, 20, 10, 3, 1, 1, 0, 9999};

int pre_r_tropism[9] =
{ 9999, 50, 40, 15, 5, 1, 1, 0, 9999};

int pre_n_tropism[9] =
{ 9999, 50, 70, 35, 10, 2, 1, 0, 9999};

int pre_q_tropism[9] =
{ 9999, 100, 60, 20, 5, 2, 0, 0, 9999};

int pre_b_tropism[9] =
{ 9999, 50, 25, 15, 5, 2, 2, 2, 9999};

unsigned char p_tropism[144][144];
unsigned char q_tropism[144][144];
unsigned char n_tropism[144][144];
unsigned char r_tropism[144][144];
unsigned char b_tropism[144][144];

int ksafety_scaled[15][9] =
{
  {  -5,   5,  10,  15,  50,  80, 150, 150, 150 },   /* nothing */
  {  -5,  15,  20,  25,  70, 150, 200, 200, 200 },   /* 1 pawns */
  {  -5,  15,  30,  30, 100, 200, 300, 300, 300 },   /* 2 pawns */
  { -10,  20,  40,  40, 100, 200, 300, 300, 400 },   /* 1 minor piece */
  { -10,  30,  50,  80, 150, 300, 400, 400, 500 },   /* 1 minor piece + pawn */
  { -10,  35,  60, 100, 200, 250, 400, 400, 500 },   /* queen */
  { -10,  40,  70, 110, 210, 300, 500, 500, 600 },   /* queen + pawn */
  { -10,  45,  75, 125, 215, 300, 500, 600, 700 },   /* queen + 2 pawn */
  { -10,  60,  90, 130, 240, 350, 500, 600, 700 },   /* queen + piece */
  { -15,  60,  95, 145, 260, 350, 500, 600, 700 },   /* queen + piece + pawn */
  { -15,  60, 100, 150, 270, 350, 500, 600, 700 },   /* 2 queen */
  { -15,  60, 110, 160, 280, 400, 600, 700, 800 },
  { -20,  70, 115, 165, 290, 400, 600, 700, 800 },
  { -20,  80, 120, 170, 300, 450, 700, 800, 900 },
  { -20,  80, 125, 175, 310, 450, 700, 800, 900 }
};

void initialize_eval(void)
{
  int i, j, sd, sdi;

  for (i = 0; i < 64; i++)
      {
        for (j = 0; j < 64; j++)
        {
          sd  = abs((i&7) - (j&7));
          sdi = abs((i>>3) - (j>>3));
          rookdistance[upscale[i]][upscale[j]] = sd+sdi;
          distance[upscale[i]][upscale[j]] = (sd > sdi ? sd : sdi);
        }
      }

  for(i = 0; i < 144; i++)
    {
      for(j = 0; j < 144; j++)
	{
	  p_tropism[i][j] =
	    pre_p_tropism[max(abs(rank(i) - rank(j)), abs(file(i) - file(j)))];
	  b_tropism[i][j] =
	    pre_b_tropism[max(abs(rank(i) - rank(j)), abs(file(i) - file(j)))];
	  n_tropism[i][j] =
	    pre_n_tropism[max(abs(rank(i) - rank(j)), abs(file(i) - file(j)))];
	  r_tropism[i][j] =
	    pre_r_tropism[max(abs(rank(i) - rank(j)), abs(file(i) - file(j)))];
	  q_tropism[i][j] =
	    pre_q_tropism[max(abs(rank(i) - rank(j)), abs(file(i) - file(j)))];
	}
    }
}

int eval (int alpha, int beta) {

  /* return a score for the current middlegame position: */

  int i, a, j;
  int score = 0;
  int in_cache;
  int safety, badsquares;
  int norm_white_hand_eval, norm_black_hand_eval;
  int wdev_dscale, bdev_dscale;

  if (Variant == Normal)
    {
      return std_eval(alpha, beta);
    }
  else if (Variant == Suicide)
    {
      return suicide_eval();
    }
  else if (Variant == Losers)
    {
      return losers_eval();
    }

  in_cache = 0;

  checkECache(&score, &in_cache);

  if(in_cache)
    {
      if (white_to_move == 1) return score;
      return -score;
    }

  /* set up development scalefactor depending on material
   * in hand */
  if (cfg_devscale)
    {
      /* computer plays black -> no white downscaling */
      if (white_to_move != comp_color)
	{
	  if (white_hand_eval <= 200 && (Variant != Bughouse))
	    {
	      /* 2 pawns or less */
	      wdev_dscale = 2;
	    }
	  else if (white_hand_eval >= 700)
	    {
	      /* queen + minor, three minors or more */
	      wdev_dscale = 0;
	    }
	  else
	    {
	      wdev_dscale = 1;
	    }
	}
      else
	wdev_dscale = 0;

      if (white_to_move == comp_color)
	{
	  if ((-black_hand_eval) <= 200 && (Variant != Bughouse))
	    {
	      /* 2 pawns or less */
	      bdev_dscale = 2;
	}
	  else if ((-black_hand_eval) >= 700)
	    {
	      /* queen + pawn, two minors + pawn */
	      bdev_dscale = 0;
	    }
	  else
	    {
	      bdev_dscale = 1;
	    }
	}
      else
	bdev_dscale = 0;
    }
  else
    {
      wdev_dscale = bdev_dscale = 0;
    }

  /* loop through the board, adding material value, as well as positional
     bonuses for all pieces encountered: */
  for (a = 1, j = 1;(a <= piece_count); j++) {
    i = pieces[j];

    if (!i)
      continue;
    else
      a++;

    switch (board[i]) {
      case (wpawn):
	score += 100;
	score += white_pawn[i] >> wdev_dscale;
	score += p_tropism[i][bking_loc];
	break;

      case (bpawn):
	score -= 100;
	score -= black_pawn[i] >> bdev_dscale;
	score -= p_tropism[i][wking_loc];
	break;

      case (wrook):
	score += 250;
	score += white_rook[i] >> wdev_dscale;
	score += r_tropism[i][bking_loc];
	break;

      case (brook):
	score -= 250;
	score -= black_rook[i] >> bdev_dscale;
	score -= r_tropism[i][wking_loc];
	break;

      case (wbishop):
	score += 230;
	score += pcsqbishop[i] >> wdev_dscale;
	score += b_tropism[i][bking_loc];
	break;

      case (bbishop):
	score -= 230;
	score -= pcsqbishop[i] >> bdev_dscale;
	score -= b_tropism[i][wking_loc];
	break;

      case (wknight):
	score += 210;
	score += white_knight[i] >> wdev_dscale;
	score += n_tropism[i][bking_loc];
	break;

      case (bknight):
	score -= 210;
	score -= black_knight[i] >> bdev_dscale;
	score -= n_tropism[i][wking_loc];
	break;

      case (wqueen):
	score += 450;
	score += white_queen[i] >> wdev_dscale;
	score += q_tropism[i][bking_loc];
	break;

      case (bqueen):
	score -= 450;
	score -= black_queen[i] >> bdev_dscale;
	score -= q_tropism[i][wking_loc];
	break;

    }
  }

  /* we scale our kings position depending on how
     much material the _other_ side has in hand */

  score += white_king[wking_loc] >> bdev_dscale;
  score -= black_king[bking_loc] >> wdev_dscale;

  /* we do not give a bonus for castling, but it is important
     to keep our options open */

  if (!white_castled && moved[30])
  {
      score -= 30;
  }
  if (!black_castled && moved[114])
  {
      score += 30;
  }

  /* give penalties for blocking the e/d pawns: */
  if (!moved[41] && board[53] != npiece)
    score -= 15;
  if (!moved[42] && board[54] != npiece)
    score -= 15;
  if (!moved[101] && board[89] != npiece)
    score += 15;
  if (!moved[102] && board[90] != npiece)
    score += 15;


  if (cfg_smarteval)
  {
  /* Pawn cover for the King please... */
  /* White */

  if (wking_loc != E1 && wking_loc != D1)
  {
  	if (board[wking_loc+11] != wpawn) score -= 24;
  	if (board[wking_loc+12] != wpawn) score -= 35;
  	if (board[wking_loc+13] != wpawn) score -= 24;

	/* When castled, building a fortress wont hurt */
	if (white_castled)
	  {
	    if (board[bking_loc-25] == bpawn) score += 11;
	    if (board[bking_loc-24] == bpawn) score += 15;
	    if (board[bking_loc-23] == bpawn) score += 11;
	  }
  }
  /* Black */
  if (bking_loc != E8 && bking_loc != D8)
  {
  	if (board[bking_loc-13] != bpawn) score += 24;
  	if (board[bking_loc-12] != bpawn) score += 35;
  	if (board[bking_loc-11] != bpawn) score += 24;

	/* When castled, building a fortress wont hurt */
	if (black_castled)
	  {
	    if (board[bking_loc-25] == bpawn) score -= 11;
	    if (board[bking_loc-24] == bpawn) score -= 15;
	    if (board[bking_loc-23] == bpawn) score -= 11;
	  }
  }
  /* Develop stuff */
  if (moved[E2])
    {
      score += 30;
      if (moved[D2]) score += 25;
      if (moved[G1]) score += 20;
      if (moved[B1]) score += 15;
      if (moved[C1]) score += 10;
    }
  if (moved[E7])
    {
      score -= 30;
      if (moved[D7]) score -= 25;
      if (moved[G8]) score -= 20;
      if (moved[B8]) score -= 15;
      if (moved[C8]) score -= 10;

    }

  /* Bad holes in the kingside (g2/e2) or (g7/e7) allow attacks */

  if ((board[G2] != wpawn) && (board[F3] == bpawn || board[E4] == bpawn))
      score -= 30;
  if ((board[G7] != bpawn) && (board[F6] == wpawn || board[E5] == wpawn))
      score += 30;

#define Fis_attacked(x,y) (board[(x)] == frame ? 0 : is_attacked((x),(y)))
#define Gis_attacked(x,y) (board[(x)] == frame ? 0 : nk_attacked((x),(y)))

  /* An enemy pawn in front of the king can be deadly.*/
  /* especially if it is protected                    */

  if (board[wking_loc + 12] == bpawn || board[wking_loc + 12] == bbishop)
    {
      score -= 35;
      if (Fis_attacked(wking_loc + 12, 0))
	score -= 150 >> bdev_dscale;
    }
  if (board[bking_loc - 12] == wpawn || board[bking_loc - 12] == wbishop)
    {
      score += 35;
      if (Fis_attacked(bking_loc - 12,1))
	score += 150 >> wdev_dscale;
    }

  /* If e6 is attacked but there is no pawn there (just P-f7) */


  if (((board[F2] == wpawn) || (board[E3] == wpawn) || board[E3] == bpawn) && Fis_attacked(E3,0))
    {
      if (board[F2] == wpawn) score += 10;
      if (board[E3] == wpawn) score += 20;
      else if (board[E3] == bpawn) score -= 15;
    }
  if (((board[F7] == bpawn) || (board[E6] == bpawn) || board[E6] == wpawn) && Fis_attacked(E6,1))
    {
      if (board[F7] == bpawn) score -= 10;
      if (board[E6] == bpawn) score -= 20;
      else if (board[E6] == wpawn) score += 15;
    }

  /* Bonus if in check */

  if (Fis_attacked(bking_loc,1))
    score += 50 >> wdev_dscale;
  else if (Fis_attacked(wking_loc,0))
    score -= 50 >> bdev_dscale;

  /* Give big pentalty for knight or pawn at g2/g7 especially if supported */
  /* Also  protect  with  Rook  and  Bishop                                */

  if (board[G2] == bknight)
    {
      score -= 20;
      if (Fis_attacked(G2,0)) score -= 40;
      if (board[G1] == wrook) score += 10;
      if (board[F1] == wbishop) score += 10;
    }
  if (board[G7] == wknight)
    {
      score += 20;
      if (Fis_attacked(G7,1)) score += 40;
      if (board[G8] == brook) score -= 10;
      if (board[F8] == bbishop)score -= 10;
    }

  /* Bishop at h3/h6 often leads to crushing attacks */
  /* Especially when the king is trapped behind a N  */

  if ((board[H3] == bbishop) && (board[G2] != wpawn))
    {
      score -= 20;
      if (board[G2] == bknight)
	{
	  score -= 40;
	  if (board[F1] == wking || board[G1] == wking || board[H1] == wking)
	      score -= 80;
	}
    }
  if ((board[H6] == wbishop) && (board[G7] != bpawn))
    {
      score += 20;
      if (board[G7] == wknight)
	{
	  score += 40;
	  if (board[F8] == bking || board[G8] == bking || board[H8] == bking)
	    score += 80;
	}
    }
  }

  if (cfg_attackeval)
  {
    badsquares = 0;
    safety = 0;

    badsquares += Gis_attacked(wking_loc - 13, 0);
    badsquares += Gis_attacked(wking_loc - 12, 0);
    badsquares += Gis_attacked(wking_loc - 11, 0);
    badsquares += Gis_attacked(wking_loc -  1, 0);
    badsquares += Gis_attacked(wking_loc +  1, 0);
    badsquares += Gis_attacked(wking_loc + 11, 0);
    badsquares += Gis_attacked(wking_loc + 12, 0);
    badsquares += Gis_attacked(wking_loc + 13, 0);

    norm_black_hand_eval = ((-black_hand_eval) / 100);
    if (norm_black_hand_eval > 14) norm_black_hand_eval = 14;
    else if (norm_black_hand_eval < 0) norm_black_hand_eval = 0;

    safety -= ksafety_scaled[norm_black_hand_eval][badsquares];

    badsquares = 0;

    badsquares += Gis_attacked(bking_loc - 13, 1);
    badsquares += Gis_attacked(bking_loc - 12, 1);
    badsquares += Gis_attacked(bking_loc - 11, 1);
    badsquares += Gis_attacked(bking_loc -  1, 1);
    badsquares += Gis_attacked(bking_loc +  1, 1);
    badsquares += Gis_attacked(bking_loc + 11, 1);
    badsquares += Gis_attacked(bking_loc + 12, 1);
    badsquares += Gis_attacked(bking_loc + 13, 1);

    norm_white_hand_eval = (white_hand_eval / 100);
    if (norm_white_hand_eval > 14) norm_white_hand_eval = 14;
    else if (norm_white_hand_eval < 0) norm_white_hand_eval = 0;

    safety += ksafety_scaled[norm_white_hand_eval][badsquares];

    score += safety;
  }

  score += (white_hand_eval + black_hand_eval);

  storeECache(score);

  /* adjust for color: */
  if (white_to_move == 1) {
    return score;
  }
  else {
    return -score;
  }

}
