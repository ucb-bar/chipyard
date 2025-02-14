/*! \file
Copyright (c) 2003, The Regents of the University of California, through
Lawrence Berkeley National Laboratory (subject to receipt of any required
approvals from U.S. Dept. of Energy)

All rights reserved.

The source code is distributed under BSD license, see the file License.txt
at the top-level directory.
*/
#include "../ext/SuperLU_MT_3.1/SRC/slu_mt_ddefs.h"
#include "../ext/SuperLU_MT_3.1/SRC/slu_mt_machines.h"

int_t sp_ienv(int_t ispec) {
    /*
     * -- SuperLU MT routine (version 1.0) --
     * Univ. of California Berkeley, Xerox Palo Alto Research Center,
     * and Lawrence Berkeley National Lab.
     * August 15, 1997
     *
     *  Purpose
     *  =======
     *
     *  sp_ienv() is inquired to choose machine-dependent parameters for the
     *  local environment. See ISPEC for a description of the parameters.
     *
     *  This version provides a set of parameters which should give good,
     *  but not optimal, performance on many of the currently available
     *  computers.  Users are encouraged to modify this subroutine to set
     *  the tuning parameters for their particular machine using the option
     *  and problem size information in the arguments.
     *
     *  Arguments
     *  =========
     *
     *  ISPEC   (input) int
     *          Specifies the parameter to be returned as the value of SP_IENV.
     *          = 1: the panel size w; a panel consists of w consecutive
     *               columns of matrix A in the process of Gaussian elimination.
     *               The best value depends on machine's cache characters.
     *          = 2: the relaxation parameter relax; if the number of
     *               nodes (columns) in a subtree of the elimination tree is
     * less than relax, this subtree is considered as one supernode, regardless
     * of the their row structures. = 3: the maximum size for a
     * supernode; = 4: the minimum row dimension for 2-D blocking to be
     * used; = 5: the minimum column dimension for 2-D blocking to be
     * used; = 6: size of the array to store the values of the L
     * supernodes; a negative number represents the fills growth
     * factor, i.e., the product of its magnitude and the number
     * of nonzeros in the original A will be used to allocate
     * storage; a positive number represents the number of
     * nonzeros; = 7: size of the array to store the columns in U;
     *               a negative number represents the fills growth factor, i.e.,
     *               the product of its magnitude and the number of nonzeros in
     * the original A will be used to allocate storage; a positive number
     * represents the number of nonzeros; = 8: size of the array
     * to store the subscripts of the L supernodes; a negative number
     * represents the fills growth factor, i.e., the product of
     * its magnitude and the number of nonzeros in the original A
     * will be used to allocate storage; a positive number
     * represents the number of nonzeros;
     *
     * (SP_IENV) (output) int
     *          >= 0: the value of the parameter specified by ISPEC
     *          < 0:  if SP_IENV = -k, the k-th argument had an illegal value.
     *
     *  =====================================================================
     */
    int i;

    switch (ispec) {
#if (MACH == SGI)
        case 1:
            return (20);
        case 2:
            return (6);
        case 3:
            return (100);
        case 4:
            return (800);
        case 5:
            return (100);
#elif (MACH == ORIGIN)
        case 1:
            return (12);
        case 2:
            return (6);
        case 3:
            return (100);
        case 4:
            return (400);
        case 5:
            return (100);
#elif (MACH == DEC)
        case 1:
            return (16);
        case 2:
            return (6);
        case 3:
            return (50);
        case 4:
            return (100);
        case 5:
            return (40);
#elif (MACH == CRAY_PVP)
        case 1:
            return (1);
        case 2:
            return (6);
        case 3:
            return (64);
        case 4:
            return (400);
        case 5:
            return (200);
#elif (MACH == SUN)
        case 1:
            return (8);
        case 2:
            return (6);
        case 3:
            return (100);
        case 4:
            return (400);
        case 5:
            return (40);
#else
        case 1:
            return (8);
        case 2:
            return (1);
        case 3:
            return (200);
        case 4:
            return (200);
        case 5:
            return (40);
#endif
        case 6:
            return (-20);
        case 7:
            return (-1000000);
        case 8:
            return (-1000000);
    }

    /* Invalid value for ISPEC */
    i = 1;
    xerbla_("sp_ienv", &i);
    return 0;

} /* sp_ienv_ */
