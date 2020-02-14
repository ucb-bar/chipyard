/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto
    Originally based on code from Adrien M. Regimbald, used with permission
    Portions contributed by Vincent Diepeveen, used with permission

    File: sjeng.c
    Purpose: main program, xboard/user interface                  

*/

#include "sjeng.h"
#include "protos.h"
#include "extvars.h"

char divider[50] = "-------------------------------------------------";
move_s dummy = {0,0,0,0,0,0};

int board[144], moved[144], ep_square, white_to_move, comp_color, wking_loc,
  bking_loc, white_castled, black_castled, result, ply, pv_length[PV_BUFF],
  pieces[62], squares[144], num_pieces, i_depth, fifty, piece_count;

int nodes, raw_nodes, qnodes,  killer_scores[PV_BUFF],
  killer_scores2[PV_BUFF], killer_scores3[PV_BUFF], moves_to_tc, min_per_game,
  sec_per_game, inc, time_left, opp_time, time_cushion, time_for_move, cur_score;

unsigned int history_h[144][144];

unsigned int hash_history[600];
int move_number;

xbool captures, searching_pv, post, time_exit, time_failure;

int xb_mode, maxdepth;

int phase;
int root_to_move;

int my_rating, opp_rating;

char setcode[30];

move_s pv[PV_BUFF][PV_BUFF], killer1[PV_BUFF], killer2[PV_BUFF],
 killer3[PV_BUFF];

move_x path_x[PV_BUFF];
move_s path[PV_BUFF];
 
rtime_t start_time;

int is_promoted[62];

unsigned int NTries, NCuts, TExt;
unsigned int PVS, FULL, PVSF;
unsigned int ext_check;

xbool is_pondering, allow_pondering, is_analyzing;

unsigned int bookidx;

int Variant;
int Giveaway;

char my_partner[STR_BUFF];
xbool have_partner;
xbool must_sit;
xbool go_fast;

int fixed_time;

int book_ply;
int use_book;
char opening_history[STR_BUFF];

int main (int argc, char *argv[]) {

  char input[STR_BUFF], *p, output[STR_BUFF];
  char readbuff[STR_BUFF];
  move_s move, comp_move;
  int depth = 4;
  xbool force_mode, show_board;
  move_s game_history[600];
  move_x game_history_x[600];
  int is_edit_mode, edit_color;
  int pingnum;
  int braindeadinterface;
  int automode;
  rtime_t xstart_time;
  
  read_rcfile();
  initialize_zobrist();
 
  Variant = Normal;
  /*Variant = Crazyhouse;*/

  memcpy(material, std_material, sizeof(std_material));
  /*memcpy(material, zh_material, sizeof(zh_material));*/

  init_game ();

  initialize_hash();
  clear_tt();
  reset_ecache();
    
  ECacheProbes = 0;
  ECacheHits = 0;
  TTProbes = 0;
  TTStores = 0;
  TTHits = 0;
  bookidx = 0;
  total_moves = 0;
  ply = 0;
  braindeadinterface = 0;
  moves_to_tc = 40;
  min_per_game = 5;
  time_left = 30000;
  my_rating = opp_rating = 2000;
  maxdepth = 40;
  maxposdiff = 200;
  must_go = 1;
  tradefreely = 1;
  automode = 0;
 
  xb_mode = FALSE;
  force_mode = FALSE;
  comp_color = 0;
  edit_color = 0;
  show_board = TRUE;
  is_pondering = FALSE;
  allow_pondering = TRUE;
  is_analyzing = FALSE;
  is_edit_mode = FALSE;
  have_partner = FALSE;
  must_sit = FALSE;
  go_fast = FALSE;
  fixed_time = FALSE;
  phase = Opening;
  root_to_move = WHITE;
  kibitzed = FALSE;

  move_number = 0;
  memset(game_history, 0, sizeof(game_history));
  memset(game_history_x, 0, sizeof(game_history_x));

  hash_history[move_number] = hash;
  
  setbuf (stdout, NULL);
  setbuf (stdin, NULL);
  start_up ();

  
  if (argc == 2)
  {
  	printf("SPEC Workload\n");
        run_autotest(argv[1]);    
 }
  
  /* keep looping for input, and responding to it: */
  while (TRUE) {

    /* case where it's the computer's turn to move: */
    if (!is_edit_mode && (comp_color == white_to_move || automode) 
	&& !force_mode && !must_sit && !result) {

      /* whatever happens, never allow pondering in normal search */
      is_pondering = FALSE;
  
      comp_move = think ();

      ply = 0;

      /* must_sit can be changed by search */
      if (!must_sit || must_go != 0)
	{
	  /* check for a game end: */
	  if ((
	      ((Variant == Losers || Variant == Suicide)
	        && 
	      ((result != white_is_mated) && (result != black_is_mated)))
	      || 
	      ((Variant == Normal || Variant == Crazyhouse || Variant == Bughouse)
	      && ((comp_color == 1 && result != white_is_mated) 
	         ||
	         (comp_color == 0 && result != black_is_mated)
	        ))) 
	      && result != stalemate 
	      && result != draw_by_fifty 
	      && result != draw_by_rep) 
	  {
	    
	    comp_to_coord (comp_move, output);
	   
	    hash_history[move_number] = hash;
	    
	    game_history[move_number] = comp_move;
	    make (&comp_move, 0);
	   
	    /* saves state info */
	    game_history_x[move_number++] = path_x[0];
	    
	    userealholdings = 0;
	    must_go--;
	    
            /* check to see if we draw by rep/fifty after our move: */
	    if (is_draw ()) {
	    	result = draw_by_rep;
	    }
	    else if (fifty > 100) {
	        result = draw_by_fifty;
	    }
	    
	    root_to_move ^= 1;

	    reset_piece_square ();
	    
	    if (book_ply < 40) {
	      if (!book_ply) {
		strcpy(opening_history, output);
	      }
	      else {
		strcat(opening_history, output);
	      }
	    }
	    
	    book_ply++;
	    
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

	    printf("Move ordering : %f%%\n", (((float)FHF*100)/(float)(FH+1)));
	    
	    printf("Material score: %d   Eval : %d  MaxPosDiff: %d  White hand: %d  Black hand : %d\n", 
		Material, eval(-INF,INF), maxposdiff, white_hand_eval, black_hand_eval);
	    
	    printf("Hash : %X  HoldHash : %X\n", hash, hold_hash);

	    /* check to see if we mate our opponent with our current move: */
	    if (!result) {
	      if (xb_mode) {

		/* safety in place here */
		if (comp_move.from != dummy.from || comp_move.target != dummy.target)
		    printf ("move %s\n", output);

		if (Variant == Bughouse)
		  {
		    CheckBadFlow(FALSE);
		  }	
	      }
	      else {
		if (comp_move.from != dummy.from || comp_move.target != dummy.target)
		printf ("\n%s\n", output);
      	      }
	    }
	    else {
	      if (xb_mode) {
		if (comp_move.from != dummy.from || comp_move.target != dummy.target)
		    printf ("move %s\n", output);
	      }
	      else {
		if (comp_move.from != dummy.from || comp_move.target != dummy.target)
		printf ("\n%s\n", output);
	      }
	      if (result == white_is_mated) {
		printf ("0-1 {Black Mates}\n");
	      }
	      else if (result == black_is_mated) {
		printf ("1-0 {White Mates}\n");
	      }
	      else if (result == draw_by_fifty) {
	        printf ("1/2-1/2 {Fifty move rule}\n");
	      }
	      else if (result == draw_by_rep) {
	        printf ("1/2-1/2 {3 fold repetition}\n");
	      }
	      else {
		printf ("1/2-1/2 {Draw}\n");
	      }
	      automode = 0;
	    }
	  }
	  /* we have been mated or stalemated: */
	  else {
	    if (result == white_is_mated) {
	      printf ("0-1 {Black Mates}\n");
	    }
	    else if (result == black_is_mated) {
	      printf ("1-0 {White Mates}\n");
	    }
            else if (result == draw_by_fifty) {
	      printf ("1/2-1/2 {Fifty move rule}\n");
	    }
	    else if (result == draw_by_rep) {
	      printf ("1/2-1/2 {3 fold repetition}\n");
	    }
	    else {
	      printf ("1/2-1/2 {Draw}\n");
	    }
	    automode = 0;
	  }
	}
    }

    /* get our input: */
    if (!xb_mode) {
      if (show_board) {
	printf ("\n");
	display_board (stdout, 1-comp_color);
      }
      if (!automode)
      {
      	printf ("Sjeng: ");
      	rinput (input, STR_BUFF, stdin);
      }
    }
    else {
      /* start pondering */

      if ((must_sit || (allow_pondering && !is_edit_mode && !force_mode &&
	      move_number != 0) || is_analyzing) && !result && !automode)
	{
	  is_pondering = TRUE;
	  think();
	  is_pondering = FALSE;

	  ply = 0;
	}
      if (!automode)
      {
      	rinput (input, STR_BUFF, stdin);
      }
    }

    /* check to see if we have a move.  If it's legal, play it. */
    if (!is_edit_mode && is_move (&input[0])) {
      if (verify_coord (input, &move)) {
	
	game_history[move_number] = move;
	hash_history[move_number] = hash;
	
        make (&move, 0);
	game_history_x[move_number++] = path_x[0];
	
	reset_piece_square ();
	
	root_to_move ^= 1;
	
	if (book_ply < 40) {
	  if (!book_ply) {
	    strcpy(opening_history, input);
	  }
	  else {
	    strcat(opening_history, input);
	  }
        }
	
	book_ply++;
	
	if (show_board) {
	  printf ("\n");
	  display_board (stdout, 1-comp_color);
	}
      }
      else {
	printf ("Illegal move: %s\n", input);
	}
    }
    else {

      /* make everything lower case for convenience: */
      /* GCP: except for setboard, which is case sensitive */
      if (!strstr(input, "setboard"))
      	for (p = input; *p; p++) *p = tolower (*p);

      /* command parsing: */
      if (!strcmp (input, "quit")) {
	free_hash();
	free_ecache();	
	exit (EXIT_SUCCESS);
      }
      else if (!strcmp (input, "exit"))
	{
	  if (is_analyzing)
	    {
	      is_analyzing = FALSE;
	      is_pondering = FALSE;
	      time_for_move = 0;
	    }
	  else
	    {
	      free_hash();
	      free_ecache();	      
	      exit (EXIT_SUCCESS);
	    }
	}
      else if (!strcmp (input, "diagram") || !strcmp (input, "d")) {
	toggle_bool (&show_board);
      }
      else if (!strncmp (input, "perft", 5)) {
	sscanf (input+6, "%d", &depth);
	raw_nodes = 0;
	xstart_time = rtime();
	perft (depth);
	printf ("Raw nodes for depth %d: %i\n", depth, raw_nodes);
	printf("Time : %.2f\n", (float)rdifftime(rtime(), xstart_time)/100.);
      }
      else if (!strcmp (input, "new")) {

	if (xb_mode)
	  {
	    printf("tellics set 1 Sjeng " VERSION " (SPEC/%s)\n", setcode);
	  }

	if (!is_analyzing)
	{	
	  memcpy(material, std_material, sizeof(std_material));
	  Variant = Normal;

	 /* memcpy(material, zh_material, sizeof(zh_material));
	  Variant = Crazyhouse;*/
	    
	  init_game ();
	  initialize_hash();

	  if (!braindeadinterface)
	  {
	    clear_tt();
	    reset_ecache();
	  }
  
	  force_mode = FALSE;
	  must_sit = FALSE;
	  go_fast = FALSE;
	  piecedead = FALSE;
	  partnerdead = FALSE;
	  kibitzed = FALSE;
	  fixed_time = FALSE;

	  root_to_move = WHITE;
  
	  comp_color = 0;
	  move_number = 0;
	  hash_history[move_number] = 0;
	  bookidx = 0;
	  my_rating = opp_rating = 2000;
          must_go = 0;
	  tradefreely = 1;
	  automode = 0;
	  
	  CheckBadFlow(TRUE);
	  ResetHandValue();
	}
	else
	{
	  init_game ();
	  move_number = 0;
	}
	
      }
      else if (!strcmp (input, "xboard")) {
	xb_mode = TRUE;
	toggle_bool (&show_board);
	signal (SIGINT, SIG_IGN);
	printf ("\n");
	
	/* Reset f5 in case we left with partner */
	printf("tellics set f5 1=1\n");
	
	BegForPartner();
      }
      else if (!strcmp (input, "nodes")) {
	printf ("Number of nodes: %i (%0.2f%% qnodes)\n", nodes,
		(float) ((float) qnodes / (float) nodes * 100.0));
      }
      else if (!strcmp (input, "post")) {
	toggle_bool (&post);
	if (xb_mode)
	  post = TRUE;
      }
      else if (!strcmp (input, "nopost")) {
	post = FALSE;
      }
      else if (!strcmp (input, "random")) {
	continue;
      }
      else if (!strcmp (input, "hard")) {

	allow_pondering = TRUE;

	continue;
      }
      else if (!strcmp (input, "easy")) {

	allow_pondering = FALSE;

	continue;
      }
      else if (!strcmp (input, "?")) {
	continue;
      }
      else if (!strcmp (input, "white")) {
	white_to_move = 1;
	root_to_move = WHITE;
	comp_color = 0;
      }
      else if (!strcmp (input, "black")) {
	white_to_move = 0;
	root_to_move = BLACK;
	comp_color = 1;
      }
      else if (!strcmp (input, "force")) {
	force_mode = TRUE;
      }
      else if (!strcmp (input, "eval")) {
	check_phase();
	printf("Eval: %d\n", eval(-INF,INF));
      }
      else if (!strcmp (input, "go")) {
	comp_color = white_to_move;
	force_mode = FALSE;
      }
      else if (!strncmp (input, "time", 4)) {
	sscanf (input+5, "%i", &time_left);
      }
      else if (!strncmp (input, "otim", 4)) {
	sscanf (input+5, "%i", &opp_time);
      }
      else if (!strncmp (input, "level", 5)) {
         if (strstr(input+6, ":"))
	 {
	   /* time command with seconds */
	   sscanf (input+6, "%i %i:%i %i", &moves_to_tc, &min_per_game, 
		   &sec_per_game, &inc);
	   time_left = (min_per_game*6000) + (sec_per_game * 100);
	   opp_time = time_left;
	 }
	 else
	   {
	     /* extract the time controls: */
	     sscanf (input+6, "%i %i %i", &moves_to_tc, &min_per_game, &inc);
	     time_left = min_per_game*6000;
	     opp_time = time_left;
	   }
	 fixed_time = FALSE;
	 time_cushion = 0; 
      }
      else if (!strncmp (input, "rating", 6)) {
	sscanf (input+7, "%i %i", &my_rating, &opp_rating);
	if (my_rating == 0) my_rating = 2000;
	if (opp_rating == 0) opp_rating = 2000;
      }
      else if (!strncmp (input, "holding", 7)) {
	ProcessHoldings(input);     
      }
      else if (!strncmp (input, "variant", 7)) {
	if (strstr(input, "normal"))
	  {
	    Variant = Normal;
	    memcpy(material, std_material, sizeof(std_material));
	  }
	else if (strstr(input, "crazyhouse"))
	  {
	    Variant = Crazyhouse;
	    memcpy(material, zh_material, sizeof(zh_material));
	  }
	else if (strstr(input, "bughouse"))
	  {
	    Variant = Bughouse;
	    memcpy(material, zh_material, sizeof(zh_material));
	  }
	else if (strstr(input, "suicide"))
	  {
	    Variant = Suicide;
	    Giveaway = FALSE;
	    memcpy(material, suicide_material, sizeof(suicide_material));
	  }
	else if (strstr(input, "giveaway"))
	  {
	    Variant = Suicide;
	    Giveaway = TRUE;
	    memcpy(material, suicide_material, sizeof(suicide_material));
	  }
	else if (strstr(input, "losers"))
	  {
	    Variant = Losers;
	    memcpy(material, losers_material, sizeof(losers_material));
	  }
	
	initialize_hash();
	clear_tt();
	reset_ecache();

      }
      else if (!strncmp (input, "analyze", 7)) {
	is_analyzing = TRUE;
	is_pondering = TRUE;
	think();
	ply = 0;
      }
      else if (!strncmp (input, "undo", 4)) {
	    printf("Move number : %d\n", move_number);
	if (move_number > 0)
	  {
	    path_x[0] = game_history_x[--move_number];
	    unmake(&game_history[move_number], 0);
	    reset_piece_square();
	    root_to_move ^= 1;
	  }
      }
      else if (!strncmp (input, "remove", 5)) {
	if (move_number > 1)
	  {
	    path_x[0] = game_history_x[--move_number];
	    unmake(&game_history[move_number], 0);
	    reset_piece_square();

	    path_x[0] = game_history_x[--move_number];
	    unmake(&game_history[move_number], 0);
	    reset_piece_square();
	  }
      }
      else if (!strncmp (input, "edit", 4)) {
	is_edit_mode = TRUE;
	edit_color = WHITE;
      }
      else if (!strncmp (input, ".", 1) && is_edit_mode) {
	is_edit_mode = FALSE;
	if (wking_loc == 30) white_castled = no_castle;
	if (bking_loc == 114) black_castled = no_castle;
	book_ply = 50;
	ep_square = 0;
	move_number = 0;
	memset(opening_history, 0, sizeof(opening_history));
	clear_tt();
	initialize_hash();
	reset_piece_square();
      }
      else if (is_edit_mode && !strncmp (input, "c", 1)) {
	if (edit_color == WHITE) edit_color = BLACK; else edit_color = WHITE;
	}
      else if (is_edit_mode && !strncmp (input, "#", 1)) {
	reset_board();
	move_number = 0;
      }
      else if (is_edit_mode 
	       && isalpha(input[0]) 
	       && isalpha(input[1]) 
	       && isdigit(input[2])) {
	PutPiece(edit_color, input[0], input[1], input[2]);
      }
      else if (!strncmp (input, "partner", 7)) {
	HandlePartner(input+7);
	}
      else if (!strncmp (input, "$partner", 8)) {
	HandlePartner(input+8);
      }
      else if (!strncmp (input, "ptell", 5)) {
	HandlePtell(input);
      }
      else if (!strncmp (input, "test", 4)) {
	run_epd_testsuite();
      }
      else if (!strncmp (input, "st", 2)) {
	sscanf(input+3, "%d", &fixed_time);
	fixed_time = fixed_time * 100;
      }
      else if (!strncmp (input, "result", 6)) {
	}
      else if (!strncmp (input, "prove", 5)) {
	printf("\nMax time to search (s): ");
	start_time = rtime();
	rinput(readbuff, STR_BUFF, stdin);
	pn_time = atol(readbuff) * 100;
	printf("\n");
	proofnumbersearch();      
       }
      else if (!strncmp (input, "ping", 4)) {
	sscanf (input+5, "%d", &pingnum);
	printf("pong %d\n", pingnum);
      }
      else if (!strncmp (input, "fritz", 5)) {
	braindeadinterface = TRUE;
      }
      else if (!strncmp (input, "reset", 5)) {
	
	memcpy(material, std_material, sizeof(std_material));
	Variant = Normal;
	
	init_game ();
	initialize_hash();
	
	clear_tt();
	reset_ecache();       
	
	force_mode = FALSE;
	fixed_time = FALSE;
	
	root_to_move = WHITE;
	
	comp_color = 0;
	move_number = 0;
	bookidx = 0;
	my_rating = opp_rating = 2000;
      }
      else if (!strncmp (input, "setboard", 8)) {
	setup_epd_line(input+9);
      }            
      else if (!strncmp (input, ".", 1)) {
        /* periodic updating and were not searching */
	/* most likely due to proven mate */
	continue;
      }
      else if (!strncmp (input, "sd", 2)) {
	sscanf(input+3, "%d", &maxdepth);
	printf("New max depth set to: %d\n", maxdepth);
	continue;
      }
      else if (!strncmp (input, "auto", 4)) {
	automode = 1;
	continue;
      }
      else if (!strncmp (input, "protover", 8)) {
	printf("feature ping=0 setboard=1 playother=0 san=0 usermove=0 time=1\n");
	printf("feature draw=0 sigint=0 sigterm=0 reuse=1 analyze=0\n");
	printf("feature myname=\"Sjeng " VERSION "\"\n");
	printf("feature variants=\"normal,bughouse,crazyhouse,suicide,giveaway,losers\"\n");
	printf("feature colors=1 ics=0 name=0 pause=0 done=1\n");
	xb_mode = 2;
      }
      else if (!strncmp (input, "accepted", 8)) {
	/* do nothing as of yet */
      }
      else if (!strncmp (input, "rejected", 8)) {
	printf("Interface does not support a required feature...expect trouble.\n");
      }
      else if (!strcmp (input, "help")) {
	printf ("\n%s\n\n", divider);
	printf ("diagram/d:       toggle diagram display\n");
	printf ("exit/quit:       terminate Sjeng\n");
	printf ("go:              make Sjeng play the side to move\n");
	printf ("new:             start a new game\n");
	printf ("level <x>:       the xboard style command to set time\n");
	printf ("  <x> should be in the form: <a> <b> <c> where:\n");
	printf ("  a -> moves to TC (0 if using an ICS style TC)\n");
	printf ("  b -> minutes per game\n");
	printf ("  c -> increment in seconds\n");
	printf ("nodes:           outputs the number of nodes searched\n");
	printf ("perft <x>:       compute raw nodes to depth x\n");
	printf ("post:            toggles thinking output\n");
	printf ("xboard:          put Sjeng into xboard mode\n");
	printf ("test:            run an EPD testsuite\n");
	printf ("speed:           test movegen and evaluation speed\n");
	printf( "proof:           try to prove or disprove the current pos\n");
	printf( "sd <x>:          limit thinking to depth x\n");
	printf( "st <x>:          limit thinking to x centiseconds\n");
	printf( "setboard <FEN>:  set board to a specified FEN string\n");
	printf( "undo:            back up a half move\n");
	printf( "remove:          back up a full move\n");
	printf( "force:           disable computer moving\n");
	printf( "auto:            computer plays both sides\n");
	printf ("\n%s\n\n", divider);
	
        show_board = 0;
      }
      else if (!xb_mode) {
	printf ("Illegal move: %s\n", input);
      }

    }

  }

  return 0;

}
