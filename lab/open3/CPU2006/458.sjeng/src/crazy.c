/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto
                                                          
    File: crazy.c
    Purpose: bughouse/crazyhouse specific functions                  

*/ 

#include <assert.h>
#include "sjeng.h"
#include "protos.h"
#include "extvars.h"

int holding[2][16];
int num_holding[2];

char realholdings[255];
int userealholdings;

int drop_piece;

int white_hand_eval;
int black_hand_eval;

unsigned int hold_hash;

#define HHash(x,y)  (hold_hash ^= zobrist[(x)][(y)])

/* input example : holding [BPPP] [QR] */
/* based on db's parser */
void ProcessHoldings(char str[])
{
  int c, i;

  i = 0;

  memset(holding, 0, sizeof(holding));
  hold_hash = 0xC0FFEE00;

  white_hand_eval = 0;
  black_hand_eval = 0;
  reset_ecache();
  
  num_holding[WHITE] = 0;
  num_holding[BLACK] = 0;

  for(c = WHITE; c <= BLACK; c++) 
    {
      while(str[i++] != '[')
	if(str[i] == 0) return;
      
      while(str[i] != ']') {
	switch(str[i++]) {
	case 'p':
	case 'P':
	  holding[c][c == WHITE ? wpawn : bpawn]++;
	  num_holding[c]++;
	  HHash((c == WHITE ? wpawn : bpawn),
		holding[c][(c == WHITE ? wpawn : bpawn)]); 
	  break;
	case 'q':
	case 'Q':
	  holding[c][c == WHITE ? wqueen : bqueen]++;
	  num_holding[c]++;
	  HHash((c == WHITE ? wqueen : bqueen),
		holding[c][(c == WHITE ? wqueen : bqueen)]); 
	  break;
	case 'r':
	case 'R':
	  holding[c][c == WHITE ? wrook : brook]++;
	  num_holding[c]++;
	  HHash((c == WHITE ? wrook : brook),
		holding[c][(c == WHITE ? wrook : brook)]); 
	  break;
	case 'b':
	case 'B':
	  holding[c][c == WHITE ? wbishop : bbishop]++;
	  num_holding[c]++;
	  HHash((c == WHITE ? wbishop : bbishop),
		holding[c][(c == WHITE ? wbishop : bbishop)]); 
	  break;
	case 'n':
	case 'N':
	  holding[c][c == WHITE ? wknight : bknight]++;
	  num_holding[c]++;
	  HHash((c == WHITE ? wknight : bknight),
		holding[c][(c == WHITE ? wknight : bknight)]); 
	  break;
	default:
	  return;
	}
      }
    }

  /* no fake pieces in crazyhouse! */
  if (Variant == Bughouse && !userealholdings)
    {
      strncpy(realholdings, str, 200);

      if (comp_color == 1)
	{
	  /* computer is white*/
	  if (holding[BLACK][bpawn] == 0)
	    {
	      holding[BLACK][bpawn]++;
	      num_holding[BLACK]++;
	      HHash(bpawn, holding[BLACK][bpawn]);
	    }
	  if (holding[BLACK][bbishop] == 0)
	    {
	      holding[BLACK][bbishop]++;
	      num_holding[BLACK]++;
	      HHash(bpawn, holding[BLACK][bbishop]);
	    }
	  if (holding[BLACK][bknight] == 0)
	  {
	    holding[BLACK][bknight]++;
	    num_holding[BLACK]++;
	    HHash(bknight, holding[BLACK][bknight]);
	  }
	  if (holding[BLACK][brook] == 0)
	  {
	    holding[BLACK][brook]++;
	    num_holding[BLACK]++;
	    HHash(bknight, holding[BLACK][brook]);
	  }
	  if (holding[BLACK][bqueen] == 0)
	  {
	    holding[BLACK][bqueen]++;
	    num_holding[BLACK]++;
	    HHash(bknight, holding[BLACK][bqueen]);
	  }
	}
      else
	{
	  /* computer is black*/
	  if (holding[WHITE][wqueen] == 0)
	  {
	    holding[WHITE][wqueen]++;
	    num_holding[WHITE]++;
	    HHash(wqueen, holding[WHITE][wqueen]);
	  }
	  if (holding[WHITE][wrook] == 0)
	  {
	    holding[WHITE][wrook]++;
	    num_holding[WHITE]++;
	    HHash(wqueen, holding[WHITE][wrook]);
	  }
	  if (holding[WHITE][wbishop] == 0)
	  {
	    holding[WHITE][wbishop]++;
	    num_holding[WHITE]++;
	    HHash(wqueen, holding[WHITE][wbishop]);
	  }
	  if (holding[WHITE][wknight] == 0)
	  {
	    holding[WHITE][wknight]++;
	    num_holding[WHITE]++;
	    HHash(wqueen, holding[WHITE][wknight]);
	  }
	  if (holding[WHITE][wpawn] == 0)
	  {
	    holding[WHITE][wpawn]++;
	    num_holding[WHITE]++;
	    HHash(wqueen, holding[WHITE][wpawn]);
	  }
	}
    }
}

int text_to_piece(char txt, int who)
{
  switch(txt)
    {
    case 'p':
    case 'P':
      return (who == WHITE ? wpawn : bpawn);
    case 'b':
    case 'B':
      return (who == WHITE ? wbishop : bbishop);
    case 'n':
    case 'N':
      return (who == WHITE ? wknight : bknight);
    case 'r':
    case 'R':
      return (who == WHITE ? wrook : brook);
    case 'q':
    case 'Q':
      return (who == WHITE ? wqueen : bqueen);
    };

  return npiece;
}

int SwitchColor(int piece)
{
  int t[] = { 0, bpawn, wpawn, bknight, wknight, 0, 0, brook, wrook, bqueen, wqueen, bbishop, wbishop };

  assert(piece > frame  && piece < npiece);

  return(t[piece]);
}

int SwitchPromoted(int piece)
{
  int t[] = { 0, bpawn, wpawn, bpawn, wpawn, 0, 0, bpawn, wpawn, bpawn, wpawn, bpawn, wpawn };

  assert(piece > frame && piece < npiece);

  return(t[piece]);
}

void addHolding(int what, int who)
{

  if (Variant == Crazyhouse)
    {

      holding[who][what]++;
      
      num_holding[who]++;
      
      HHash(what, holding[who][what]);

    };

  if (who == WHITE)
    white_hand_eval += hand_value[what];
  else
    black_hand_eval += hand_value[what];

  Material += material[what];

  return;
    
}

void removeHolding(int what, int who)
{

  if (Variant == Crazyhouse)
    {

      assert(holding[who][what] > 0);
      assert(holding[who][what] < 20);	
      
      HHash(what, holding[who][what]);
      
      holding[who][what]--;
      
      num_holding[who]--;
      
    }

  if (who == WHITE)
  	white_hand_eval -= hand_value[what];
  else
    	black_hand_eval -= hand_value[what];

  Material -= material[what];

  return;

}

void DropaddHolding(int what, int who)
{
  holding[who][what]++;
  
  num_holding[who]++;
  
  HHash(what, holding[who][what]);
  
  if (who == WHITE)
    white_hand_eval += hand_value[what];
  else
    black_hand_eval += hand_value[what];

  Material += material[what];

  return;
}

void DropremoveHolding(int what, int who)
{
  assert(holding[who][what] > 0);

  assert(holding[who][what] < 20);	

  HHash(what, holding[who][what]);
  
  holding[who][what]--;
  
  num_holding[who]--;

  if (who == WHITE)
      white_hand_eval -= hand_value[what];
  else
      black_hand_eval -= hand_value[what];

  Material -= material[what];

  return;
}

void printHolding(void)
{

  printf("WP: %d WR: %d WB: %d WN: %d WQ: %d\n",
	 holding[WHITE][wpawn], holding[WHITE][wrook], 
	 holding[WHITE][wbishop],
	 holding[WHITE][wknight], holding[WHITE][wqueen]);
  
  printf("BP: %d BR: %d BB: %d BN: %d BQ: %d\n",
	 holding[BLACK][bpawn], holding[BLACK][brook], 
	 holding[BLACK][bbishop],
	 holding[BLACK][bknight], holding[BLACK][bqueen]);

}
