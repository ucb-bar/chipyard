/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: epd.c                                             
    Purpose: run EPD test suite

*/

#include "sjeng.h"
#include "protos.h"
#include "extvars.h"

void setup_epd_line(char* inbuff)
{
  int i = 0;
  int rankp = 0;   /* a8 */
  int rankoffset = 0;
  int fileoffset = 0;
  int j;

  /* 0 : FEN data */
  /* 1 : Active color */
  /* 2 : Castling status */
  /* 3 : EP info */
  /* 4 : 50 move */
  /* 5 : movenumber */
  /* 6 : EPD data */
  int stage = 0;

  static int rankoffsets[] = {110, 98, 86, 74, 62, 50, 38, 26};
 
  /* conversion from algebraic to sjeng internal for ep squares */
  int converterf = (int) 'a';
  int converterr = (int) '1';
  int ep_file, ep_rank, norm_file, norm_rank;
  
  memset(board, frame, sizeof(board));
  
  white_castled = no_castle;
  black_castled = no_castle;

  book_ply = 50;

  rankoffset = rankoffsets[0];

  while (inbuff[i] == ' ') {i++;};

  while((inbuff[i] != '\n') && (inbuff[i] != '\0'))
    {
      if(stage == 0 && isdigit(inbuff[i]))
	{
	  for (j = 0; j < atoi(&inbuff[i]); j++)
	    board[rankoffset + j + fileoffset] = npiece;
	  
	  fileoffset += atoi(&inbuff[i]);
	}
      else if (stage == 0 && inbuff[i] == '/')
	{
	  rankp++;
	  rankoffset = rankoffsets[rankp];	
	  fileoffset = 0;
	}
      else if (stage == 0 && isalpha(inbuff[i]))
	{
	  switch (inbuff[i])
	    {
	    case 'p' : board[rankoffset + fileoffset] = bpawn; break;
	    case 'P' : board[rankoffset + fileoffset] = wpawn; break;	
	    case 'n' : board[rankoffset + fileoffset] = bknight; break;
	    case 'N' : board[rankoffset + fileoffset] = wknight; break;	
	    case 'b' : board[rankoffset + fileoffset] = bbishop; break;
	    case 'B' : board[rankoffset + fileoffset] = wbishop; break;	
	    case 'r' : board[rankoffset + fileoffset] = brook; break;
	    case 'R' : board[rankoffset + fileoffset] = wrook; break;	
	    case 'q' : board[rankoffset + fileoffset] = bqueen; break;
	    case 'Q' : board[rankoffset + fileoffset] = wqueen; break;	
	    case 'k' : 
	      bking_loc = rankoffset + fileoffset;
	      board[bking_loc] = bking; 
	      break;
	    case 'K' :
	      wking_loc = rankoffset + fileoffset;
	      board[wking_loc] = wking; 
	      break;	
	    }
	  fileoffset++;
	}
      else if (inbuff[i] == ' ')
	{
	  stage++;

	  if (stage == 1)
	    {
	      /* skip spaces */
	      while (inbuff[i] == ' ') i++;
	      
	      if (inbuff[i] == 'w') 
		white_to_move = 1;
	      else
		white_to_move = 0;
	    }
	  else if (stage == 2)
	    {
	      /* assume no castling at all */
	      moved[26] = moved[33] = moved[30] = 1;
	      moved[110] = moved[114] = moved[117] = 1;

	      while(inbuff[i] == ' ') i++;
	     
	      while (inbuff[i] != ' ')
		{
		  switch (inbuff[i])
		    {
		    case '-' :
		      break;
		    case 'K' :
		      moved[30] = moved[33] = 0;
		      break;
		    case 'Q' :
		      moved[30] = moved[26] = 0;
		      break;
		    case 'k' :
		      moved[114] = moved[117] = 0;
		      break;
		    case 'q' :
		      moved[114] = moved[110] = 0;
		      break;
		    }
		  i++;
		}
	      i--; /* go back to space so we move to next stage */
	      
	    }
	  else if (stage == 3)
	    {
	      /* skip spaces */
	      while (inbuff[i] == ' ') i++;
	      
	      if (inbuff[i] == '-')
		{
		  ep_square = 0;
		}
	      else
		{
		  ep_file = inbuff[i++];
		  ep_rank = inbuff[i++];
		  
		  norm_file = ep_file - converterf;
		  norm_rank = ep_rank - converterr;
		  
		  ep_square = ((norm_rank * 12) + 26) + (norm_file);		  
		}
	    }
	  else if (stage == 4)
	    {
	      /* ignore this for now */
	    }
	  else if (stage == 5)
	    {
	      /* ignore this for now */
	    }
	  else if (stage == 6)
	    {
	      /* ignore this for now */
	    }	  
	};
      
      i++;
    }

  reset_piece_square();
  initialize_hash();

}

int check_solution(char *inbuff, move_s cmove)
{
  char san[STR_BUFF];
  
  comp_to_san(cmove, san);
 
/*  printf("Sjeng's move: %s, EPD line: %s\n", san, strstr(inbuff,"bm"));
*/

  if (strstr(inbuff, "bm") != NULL)
    {
      if (strstr(inbuff, san) != NULL)
	return TRUE;
      else
	return FALSE;
    }
  else if (strstr(inbuff, "am") != NULL)
    {
      if (strstr(inbuff, san) != NULL)
	return FALSE;
      else
	return TRUE;
    }
  else
    printf("No best-move or avoid-move found!");

  return FALSE;
}

void run_epd_testsuite(void)
{
  FILE *testsuite;
  char readbuff[2000];
  char testname[FILENAME_MAX];
  char tempbuff[2000];
  int nps;
  int thinktime;
	move_s comp_move;
  int tested, found;	
  
  tested = 0;
  found = 0;
  
  printf("\nName of EPD testsuite: ");
  rinput(testname, STR_BUFF, stdin);
  printf("\nTime per move (s): ");
  rinput(readbuff, STR_BUFF, stdin);
  thinktime = atol(readbuff);
  printf("\n");

  thinktime *= 100;

  testsuite = fopen(testname, "r");

  while (fgets(readbuff, 2000, testsuite) != NULL)
    {
      tested++;

      setup_epd_line(readbuff);

      root_to_move = ToMove;
      
      clear_tt();
      initialize_hash();
      
      display_board(stdout, 1); 
 
      forcedwin = FALSE;    
       
     if (interrupt()) rinput(tempbuff, STR_BUFF, stdin);
      
      fixed_time = thinktime;
      
       comp_move = think();
      

      printf ("\nNodes: %i (%0.2f%% qnodes)\n", nodes,
	      (float) ((float) qnodes / (float) nodes * 100.0));
    
      printf("ECacheProbes : %u   ECacheHits : %u   HitRate : %f%%\n", 
	     ECacheProbes, ECacheHits, 
	     ((float)ECacheHits/((float)ECacheProbes+1)) * 100);
      
      printf("TTStores : %u TTProbes : %u   TTHits : %u   HitRate : %f%%\n", 
	     TTStores, TTProbes, TTHits, 
	     ((float)TTHits/((float)TTProbes+1)) * 100);
      
      printf("NTries : %u  NCuts : %u  CutRate : %f%%  TExt: %u\n", 
	     NTries, NCuts, (((float)NCuts*100)/((float)NTries+1)), TExt);
      
      printf("Check extensions: %u  Razor drops : %u  Razor Material : %u\n", ext_check, razor_drop, razor_material);
      
      printf("Move ordering : %f%%\n", (((float)FHF*100)/(float)FH+1));
      
      printf("Material score: %d  Eval : %d  MaxPosDiff: %d\n", Material, eval(-INF,INF), maxposdiff);
      printf("\n");
     
      if (!forcedwin)
      {
      if(check_solution(readbuff, comp_move))
	{
	  found++;
	  printf("Solution found.\n");
	}
      else
	{
	  printf("Solution not found.\n");
	}
      }
      else
      {
	found++;
      }
      
      printf("Solved: %d/%d\n", found, tested);
      
    };
 
  printf("\n");
}

void run_autotest(char *testset)
{
	FILE *testsuite;
	char readbuff[STR_BUFF];
        int searchdepth;
	rtime_t start, end;
	
	move_s comp_move;

	testsuite = fopen(testset, "r");

	if (testsuite == NULL) exit(EXIT_FAILURE);

	start = rtime();
	
	while (fgets(readbuff, STR_BUFF, testsuite) != NULL)
 	{
		setup_epd_line(readbuff);
                root_to_move = ToMove;
					        
		clear_tt();
		initialize_hash();
		           
		printf("\n");
		display_board(stdout, 1);

		printf("EPD: %s\n", readbuff);

		if (fgets(readbuff, STR_BUFF, testsuite) == NULL) exit(EXIT_FAILURE);
		searchdepth = atoi(readbuff);
		
		printf("Searching to %d ply\n", searchdepth);
		maxdepth = searchdepth;

		fixed_time = INF;
		comp_move = think();
	}
		
	end = rtime();
/*        printf("Total elapsed: %i.%02i seconds\n", rdifftime(end, start)/100,
			                           rdifftime(end, start)%100);
*/
	fclose(testsuite);
	exit(EXIT_SUCCESS);
}

