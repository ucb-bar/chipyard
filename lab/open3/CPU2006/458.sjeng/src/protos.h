/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: protos.h                                        
    Purpose: function prototypes

*/

#ifndef PROTOS_H
#define PROTOS_H

int allocate_time (void);
xbool check_legal (move_s moves[], int m, int incheck);
void comp_to_coord (move_s move, char str[]);
void display_board (FILE *stream, int color);
int seval(void);
int std_eval (int alpha, int beta);
int suicide_eval (void);
int losers_eval (void);
int eval (int alpha, int beta);
void gen (move_s moves[]);
void ics_game_end (void);
xbool in_check (void);
xbool f_in_check (move_s moves[], int m);
int extended_in_check (void);
void init_game (void);
xbool is_attacked (int square, int color);
xbool nk_attacked (int square, int color);
xbool is_move (char str[]);
void make (move_s moves[], int i);
void order_moves (move_s moves[], int move_ordering[], int see_values[], int num_moves, int best);
int suicide_mid_eval(void);
void check_phase(void);
void perft (int depth);
void perft_debug (void);
void post_thinking (int score);
void post_fl_thinking (int score, move_s *failmove);
void post_fh_thinking (int score, move_s *failmove);
void post_fail_thinking(int score, move_s *failmove);
void print_move (move_s moves[], int m, FILE *stream);
void push_pawn (int target, xbool is_ep); 
void push_king_castle (int target, int castle_type);
void push_pawn_simple (int target);
void push_king (int target);
void push_knighT (int target);

void try_drop (int ptype);
		

void push_slidE (int target);
int qsearch (int alpha, int beta, int depth);
void rdelay (int time_in_s);
int rdifftime (rtime_t end, rtime_t start);
xbool remove_one (int *marker, int move_ordering[], int num_moves);
void reset_piece_square (void);
void check_piece_square (void);
void rinput (char str[], int n, FILE *stream);
rtime_t rtime (void);
int search (int alpha, int beta, int depth, int is_null);
move_s search_root (int alpha, int beta, int depth);
void start_up (void);
move_s think (void);
void toggle_bool (xbool *var);
void tree (int depth, int indent, FILE *output, char *disp_b);
void tree_debug (void);
void unmake (move_s moves[], int i);
xbool verify_coord (char input[], move_s *move);

xbool is_draw(void);

void ProcessHoldings(char line[]);
void addHolding(int what, int who);
void removeHolding(int what, int who);
void DropaddHolding(int what, int who);
void DropremoveHolding(int what, int who);

void printHolding(void);

int SwitchColor(int piece);
int SwitchPromoted(int piece);

int evalHolding(void);

void initialize_zobrist(void);
void initialize_hash(void);
void initialize_eval(void);

void checkECache(int *score, int *in_cache);
void storeECache(int score);

void StoreTT(int score, int alpha, int beta, int best , int threat, int depth);
void QStoreTT(int score, int alpha, int beta, int best);
int ProbeTT(int *score, int beta, int *best, int *threat, int *donull, int depth);
int QProbeTT(int *score, int *best);
void LearnStoreTT(int score, unsigned nhash, unsigned hhash, int tomove, int best, int depth);

void pinput (int n, FILE *stream);

int calc_attackers(int square, int color);

int interrupt(void);

void PutPiece(int color, char piece, char xfile, int xrank);
void reset_board(void);

void reset_ecache(void);

void HandlePartner(char *input);
void HandlePtell(char *input);
void BegForPartner(void);
void CheckBadFlow(xbool reset);

void run_epd_testsuite(void);
void run_autotest(char *testset);

void ResetHandValue(void);

void comp_to_san (move_s move, char str[]);
void stringize_pv (char str[]);
  
void clear_tt(void);
void clear_dp_tt(void);

move_s proofnumbercheck(move_s compmove);
void proofnumbersearch(void);
void proofnumberscan(void);

void alloc_hash(void);
void alloc_ecache(void);
void free_hash(void);
void free_ecache(void);
void read_rcfile(void);

void book_learning(int gameresult);
void seedMT(unsigned int seed);
unsigned int randomMT(void);

void setup_epd_line(char* inbuff);

int see(int color, int square, int from);

#endif

