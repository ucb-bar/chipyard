/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: extvars.h
    Purpose: global data definitions

*/

extern char divider[50];

extern int board[144], moved[144], ep_square, white_to_move, wking_loc,
  bking_loc, white_castled, black_castled, result, ply, pv_length[PV_BUFF],
  squares[144], num_pieces, i_depth, comp_color, fifty, piece_count;

extern int nodes, raw_nodes, qnodes, killer_scores[PV_BUFF],
  killer_scores2[PV_BUFF], killer_scores3[PV_BUFF], moves_to_tc, min_per_game,
  sec_per_game, inc, time_left, opp_time, time_cushion, time_for_move, cur_score;

extern unsigned int history_h[144][144];

extern xbool captures, searching_pv, post, time_exit, time_failure;
extern int xb_mode, maxdepth;

extern move_s pv[PV_BUFF][PV_BUFF], dummy, killer1[PV_BUFF], killer2[PV_BUFF],
  killer3[PV_BUFF];

extern  move_x path_x[PV_BUFF];
extern  move_s path[PV_BUFF];
extern  int maxposdiff;

extern rtime_t start_time;

extern int holding[2][16];
extern int num_holding[2];

extern int white_hand_eval;
extern int black_hand_eval;
extern int hand_value[];


extern int drop_piece;

extern int pieces[62];
extern int is_promoted[62];

extern int num_makemoves;
extern int num_unmakemoves;
extern int num_playmoves;
extern int num_pieceups;
extern int num_piecedowns;
extern int max_moves;

/* piece types range form 0..16 */
extern unsigned int zobrist[14][144];
extern unsigned int hash;

extern unsigned int ECacheProbes;
extern unsigned int ECacheHits;

extern unsigned int TTProbes;
extern unsigned int TTHits;
extern unsigned int TTStores;

extern unsigned int hold_hash;

extern char book[4000][161];
extern int num_book_lines;
extern int book_ply;
extern int use_book;
extern char opening_history[STR_BUFF];
extern unsigned int bookpos[400], booktomove[400], bookidx;

extern int Material;
extern int material[14];
extern int zh_material[14];
extern int std_material[14];
extern int suicide_material[14];
extern int losers_material[14];

extern unsigned int NTries, NCuts, TExt;

extern char ponder_input[STR_BUFF];

extern xbool is_pondering;

extern unsigned int FH, FHF, PVS, FULL, PVSF;
extern unsigned int ext_check, ext_recap, ext_onerep;
extern unsigned int razor_drop, razor_material;

extern unsigned int total_moves;
extern unsigned int total_movegens;

extern const int Xrank[144], Xfile[144], Xdiagl[144], Xdiagr[144], sqcolor[144];
extern int distance[144][144];
extern int rookdistance[144][144];
extern const int upscale[64];

extern int Variant;
extern int Giveaway;
extern int forcedwin;

extern xbool is_analyzing;

extern char my_partner[STR_BUFF];
extern xbool have_partner;
extern xbool must_sit;
extern int must_go;
extern xbool go_fast;
extern xbool piecedead;
extern xbool partnerdead;
extern int tradefreely;

extern char true_i_depth;

extern int fixed_time;

extern int numb_moves;

extern int phase;

extern int bestmovenum;

extern int ugly_ep_hack;

extern int root_to_move;

extern int kingcap;

extern int pn_time;
extern move_s pn_move;
extern move_s pn_saver;
extern xbool kibitzed;
extern int rootlosers[PV_BUFF];
extern int alllosers;
extern int s_threat;

extern int cfg_booklearn;
extern int cfg_devscale;
extern int cfg_razordrop;
extern int cfg_cutdrop;
extern int cfg_futprune;
extern int cfg_onerep;
extern int cfg_recap;
extern int cfg_smarteval;
extern int cfg_attackeval;
extern float cfg_scalefac;
extern int cfg_ksafety[15][9];
extern int cfg_tropism[5][7];
extern int havercfile;
extern int TTSize;
extern int PBSize;
extern int ECacheSize;

extern int my_rating, opp_rating;
extern int userealholdings;
extern char realholdings[255];

extern int move_number;
extern unsigned int hash_history[600];

extern int moveleft;
extern int movetotal;
extern char searching_move[20];

extern char setcode[30];

