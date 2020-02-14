/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
/*                                                                           */
/*                  This file is part of the class library                   */
/*       SoPlex --- the Sequential object-oriented simPlex.                  */
/*                                                                           */
/*    Copyright (C) 1997-1999 Roland Wunderling                              */
/*                  1997-2002 Konrad-Zuse-Zentrum                            */
/*                            fuer Informationstechnik Berlin                */
/*                                                                           */
/*  SoPlex is distributed under the terms of the ZIB Academic Licence.       */
/*                                                                           */
/*  You should have received a copy of the ZIB Academic License              */
/*  along with SoPlex; see the file COPYING. If not email to soplex@zib.de.  */
/*                                                                           */
/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
#ifndef SPEC_CPU
#pragma ident "@(#) $Id: timer.h,v 1.9 2002/01/31 08:19:30 bzfkocht Exp $"
#endif

/**@file  timer.h
 * @brief Timer class.
 */

#ifndef _TIMER_H_
#define _TIMER_H_

namespace soplex
{
/**@name    Timer
   @ingroup Elementary

    In C or C++ programs, the usual way to measure time intervalls,
    e.g.  running times of some complex computations, is to call one
    of the provided system functions like %clock(), %time(), %times(),
    %gettimeofday(), %getrusage() etc.  By these functions one can
    gather information about the process' user and system time and the
    system clock (real time).
 
    Unfortunately, these functions are rather clumsy.  The programmer
    determines computation times by querying a (virtual) clock value
    at the beginning and another one at the end of some computation
    and converting the difference of these values into seconds.  Some
    functions impose some restrictions, for instance, the values of
    the ANSI C function %clock() are of high resolution but will wrap
    around after about 36 minutes (cpu time).  Most timing functions
    take some data structure as argument that has to be allocated
    before the call and from which the user has to pick up the
    information of interest after the call.  Problems can arise, when
    porting programs to other operating systems that do not support
    standards like POSIX etc.
 
    In order to simplify measuring computation times and to hide the
    system-dependencies involved, a concept of \em timers accounting the
    process' user, system and real time is implemented.  C and C++ interfaces
    are provided as a set of functions operating on timers and a timer class
    respectively.
 
    The idea is to provide a type Timer for objects that act like a stopwatch.
    Operations on such an objects include: start accounting time, stop
    accounting, read the actual time account and reset the objects time account
    to zero.
 
    After initialization, accounting for user, system and real time can be
    started by calling a function start(). Accounting is suspended by calling
    a function stop() and can be resumed at any time by calling start()
    again.
 
    The user, system or real time actually accounted by a timer can be accessed
    at any time by the methods shown in this code section:
 
    \verbatim
       Real utime, stime, rtime;
         
       utime = timer.userTime();
       stime = timer.systemTime();
       rtime = timer.realTime();
         
       timer.getTimes(utime, stime rtime);
    \endverbatim
 
    For convenience, the actually accounted user time is returned by stop()
    too.  Function reset() re-initializes a timer clearing all time
    accounts.
 
    Function resolution() returns the smallest (non-zero) time intervall which
    is resolved by the underlying system function: res = 1/Timer_resolution().


    The process' user and system times are accessed by calling
    function %times(), which is declared in \c <sys/times.h>.  If OS
    supports POSIX compatibility through providing \c <sys/unistd.h>,
    set \c -DHAVE_UNISTD_H when compiling \c timer.c.  Ignore compiler
    warnings about missing prototypes of functions.
*/
class Timer
{
private:
   static const long ticks_per_sec;  ///< ticks per secound, should be constant

   enum  
   {
      RESET,                   ///< reset
      STOPPED,                 ///< stopped
      RUNNING                  ///< running
   } status;                   ///< timer status

   long         uAccount;      ///< user time
   long         sAccount;      ///< system time
   long         rAccount;      ///< real time
   mutable long uTicks;        ///< user ticks 
   mutable long sTicks;        ///< system ticks
   mutable long rTicks;        ///< real ticks 

   /// convert ticks to secounds.
   Real ticks2sec(long ticks) const
   {
      return (Real(ticks) * 1000.0 / ticks_per_sec) / 1000.0;
   }

   /// get actual user, system and real ticks from the system.
   void updateTicks() const;
 
public:
   /// default constructor
   Timer() : status(RESET), uAccount(0), sAccount(0), rAccount(0)
   {
      assert(ticks_per_sec > 0);
   }
   /// initialize timer, set timing accounts to zero.
   void reset()
   {
      status   = RESET;
      uAccount = rAccount = sAccount = 0;
   }

   /// start timer, resume accounting user, system and real time.
   void start();

   /// stop timer, return accounted user time.
   Real stop();

   /// get accounted user, system or real time.
   void getTimes(
      Real* userTime, Real* systemTime, Real* realTime) const;

   /// return accounted user time.
   Real userTime() const;

   /// return accounted system time.
   Real systemTime() const;

   /// return accounted real time.
   Real realTime() const;

   /// return resolution of timer as 1/seconds.
   long resolution() const { return ticks_per_sec; }
};
} // namespace soplex
#endif // _TIMER_H_

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------





