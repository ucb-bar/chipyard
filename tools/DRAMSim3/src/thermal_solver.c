/* thermal solver
 * based on superLU
 * zhiyuan yang
 */
#include <math.h>
#include <omp.h>
#include <stdbool.h>
#include <stdio.h>
#include <time.h>
#include "../ext/SuperLU_MT_3.1/SRC/slu_mt_ddefs.h"
#include "thermal_config.h"

//#define DEBUG
//#define DEBUGMIDX
//#define DEBUGMAT

double get_maxT(double *T, int Tsize);

double *initialize_Temperature(double W, double Lc, int numP, int dimX,
                               int dimZ, double Tamb) {
    int numLayer, l;
    double *T;

    numLayer = numP * 3;

    // define the temperature array
    if (!(T = doubleMalloc(dimX * dimZ * (numLayer + 1))))
        SUPERLU_ABORT("Malloc fails for K[].");
    for (l = 0; l < dimX * dimZ * (numLayer + 1); l++) T[l] = Tamb;

    return T;
}

double *calculate_Cap_array(double W, double Lc, int numP, int dimX, int dimZ,
                            int *CapSize) {
    double Wsink, Lsink, Hsink;
    int numLayer;
    double *C, *H, Csink;

    // initialize the parameters
    numLayer = numP * 3;
    Wsink = W;
    Lsink = Lc;
    Hsink = Hhs;
    Csink = Chs;

    // define the thermal capacitance and height array
    if (!(C = doubleMalloc(numLayer + 1)))
        SUPERLU_ABORT("Malloc fails for K[].");
    if (!(H = doubleMalloc(numLayer))) SUPERLU_ABORT("Malloc fails for H[].");
    for (int i = 0; i < numLayer; i++) {
        switch (i % 3) {
            case 0:
                C[i + 1] = Csi;
                H[i] = Hsi;
                break;
            case 1:
                C[i + 1] = Ccu;
                H[i] = Hcu;
                break;
            case 2:
                C[i + 1] = Cin;
                H[i] = Hin;
                break;
            default:
                printf("Error!");
        }
    }
    C[0] = Csink;

    ///////////// modify the cap vector with the physical parameters
    ///////////////
    double gridX, gridZ, gridXsink, gridZsink;
    gridX = W / dimX;
    gridZ = Lc / dimZ;
    gridXsink = Wsink / dimX;
    gridZsink = Lsink / dimZ;
    for (int i = 0; i < numLayer + 1; i++) {
        if (i == 0)
            C[i] = C[i] * Hsink * gridXsink * gridZsink;
        else
            C[i] = C[i] * H[i - 1] * gridX * gridZ;
    }

    *CapSize = numLayer + 1;
    return C;
}

double **calculate_Midx_array(double W, double Lc, int numP, int dimX, int dimZ,
                              int *MidxSize, double Tamb) {
    double Wsink, Lsink, Hsink, Ksink, rTSV, Ktsv;
    int numLayer;
    double *K, *H;
    int_t *layerP, *mapTSV;
    int ***TSV;      // number of TSVs in each grid
    int i, j, k, l;  // iterators

    // initialize the parameters
    numLayer = numP * 3;
    Wsink = W;
    Lsink = Lc;
    Hsink = Hhs;
    Ksink = Khs;

    rTSV = R_TSV;
    Ktsv = Kcu;

    // define the thermal conductance and height array
    if (!(K = doubleMalloc(numLayer))) SUPERLU_ABORT("Malloc fails for K[].");
    if (!(H = doubleMalloc(numLayer))) SUPERLU_ABORT("Malloc fails for H[].");
    for (i = 0; i < numLayer; i++) {
        switch (i % 3) {
            case 0:
                K[i] = Ksi;
                H[i] = Hsi;
                break;
            case 1:
                K[i] = Kcu;
                H[i] = Hcu;
                break;
            case 2:
                K[i] = Kin;
                H[i] = Hin;
                break;
            default:
                printf("Error!");
        }
    }

    // define the active layer array
    if (!(layerP = intMalloc(numP))) SUPERLU_ABORT("Malloc fails for numP[].");
    for (l = 0; l < numP; l++) layerP[l] = l * 3;

    // define the mapTSV array
    if (!(mapTSV = intMalloc(numLayer)))
        SUPERLU_ABORT("Malloc fails for mapTSV[].");

    for (i = 0; i < numLayer; i++) {
        if (i == 0 || i == numLayer - 1)
            mapTSV[i] = 0;
        else
            mapTSV[i] = ((i - 1) / 3) + 1;
    }
    mapTSV[numLayer - 2] = numP - 1;

    printf("================= STEADY TEMPERATURE SOLVER ===============\n\n");
    printf("Dimension of the Chip: %d x %d x %d (dimX x dimZ x numP)\n", dimX,
           dimZ, numP);
    printf(
        "Total Number of layers: %d (each tier contains an active layer, a "
        "wire layer and a "
        "dielectric layer\n",
        numLayer);
    printf("NOTE: ANOTHER HEAT SINK LAYER IS ATTACHED TO THE 1st LAYER\n");
    printf("Active layer(s) is(are) on the following layer(s): ");
    for (l = 0; l < numP; l++) printf("%lld, ", layerP[l]);
    printf("\n");
    printf("Distribution of TSVs arcoss layers: ");
    for (i = 0; i < numLayer; i++) printf("%lld, ", mapTSV[i]);
    printf("\n");
    printf("The ambient temperature is %.2f C\n", Tamb - T0);
    printf("------------------------------------------------------------\n\n");

    // define the TSV array (3D-int array!)
    if (!(TSV = (int ***)malloc(dimX * sizeof(int **))))
        printf("Malloc fails for TSV[].\n");
    for (i = 0; i < dimX; i++) {
        if (!(TSV[i] = (int **)malloc(dimZ * sizeof(int *))))
            printf("Malloc fails for TSV[%d][].\n", i);
        for (j = 0; j < dimZ; j++) {
            if (!(TSV[i][j] = (int *)malloc((numP - 1) * sizeof(int))))
                printf("Malloc fails for TSV[%d][%d][]. \n", i, j);
        }
    }
    // initialize TSV array
    for (i = 0; i < dimX; i++)
        for (j = 0; j < dimZ; j++)
            for (k = 0; k < numP - 1; k++) TSV[i][j][k] = 0;

    // generate the G matrix following
    double gridX, gridZ, gridXsink, gridZsink;
    double Rsinkx, Rsinky, Rsinkz, Ramb;
    double ***Rvert;  // vertical resistance
    double **Rhori;   // horizontal resistance
    int tier;

    gridX = W / dimX;
    gridZ = Lc / dimZ;
    gridXsink = Wsink / dimX;
    gridZsink = Lsink / dimZ;

    Rsinkx = gridXsink / Ksink / gridZsink / Hsink;  // x direction
    Rsinky = Hsink / Ksink / gridXsink / gridZsink;  // y direction
    Rsinkz = gridZsink / Ksink / gridXsink / Hsink;  // z direction
    Ramb = Rsinky / 2;

    // alloc Rvert mat
    if (!(Rvert = (double ***)malloc(dimX * sizeof(double **))))
        printf("Malloc fails for Rvert[].\n");
    for (i = 0; i < dimX; i++) {
        if (!(Rvert[i] = (double **)malloc(dimZ * sizeof(double *))))
            printf("Malloc fails for Rvert[%d][].\n", i);
        for (j = 0; j < dimZ; j++) {
            if (!(Rvert[i][j] =
                      (double *)malloc((numLayer + 1) * sizeof(double))))
                printf("Malloc fails for Rvert[%d][%d][]. \n", i, j);
        }
    }
    // initialize Rvert mat
    for (l = 0; l < numLayer; l++)  // one layer less than Rvert
    {
        tier = mapTSV[l];
        for (i = 0; i < dimX; i++) {
            for (j = 0; j < dimZ; j++) {
                if (l == 0) Rvert[i][j][l] = Rsinky;

                if (tier)
                    Rvert[i][j][l + 1] =
                        1 / (1 / (H[l] / K[l] /
                                  (gridX * gridZ -
                                   M_PI * rTSV * rTSV * TSV[i][j][tier - 1])) +
                             1 / (H[l] / Ktsv /
                                  (M_PI * rTSV * rTSV * TSV[i][j][tier - 1])));
                else
                    Rvert[i][j][l + 1] = H[l] / K[l] / (gridX * gridZ);
            }
        }
    }

    // alloc Rhori mat
    if (!(Rhori = (double **)malloc((numLayer + 1) * sizeof(double *))))
        printf("Malloc fails for Rhori[].\n");
    for (i = 0; i < numLayer + 1; i++) {
        if (!(Rhori[i] = (double *)malloc(2 * sizeof(double))))
            printf("Malloc fails for Rvert[%d][].\n", i);
    }
    // initialize Rhori mat
    for (i = 0; i < numLayer + 1; i++) {
        if (i == 0) {
            Rhori[i][0] = Rsinkx;
            Rhori[i][1] = Rsinkz;
        } else {
            Rhori[i][0] = gridX / K[i - 1] / gridZ / H[i - 1] * 10;
            Rhori[i][1] = gridZ / K[i - 1] / gridZ / H[i - 1] * 10;
        }
    }

    /* free the space of mapTSV and TSV */
    free(mapTSV);
    for (i = 0; i < dimX; i++) {
        for (j = 0; j < dimZ; j++) {
            free(TSV[i][j]);
        }
        free(TSV[i]);
    }
    free(TSV);

    /* now I calculate the number of non-zero entries and build Mid
     */
    int count = 0;  // count the number of non-zeros
    for (l = 0; l < numLayer + 1; l++) {
        for (i = 0; i < dimX; i++) {
            for (j = 0; j < dimZ; j++) {
                if (i - 1 >= 0) count++;
                if (i + 1 < dimX) count++;
                if (j - 1 >= 0) count++;
                if (j + 1 < dimZ) count++;
                if (l < numLayer) count++;
                if (l > 0) count++;
            }
        }
    }

    // printf("count = %d\n", count);
    // printf("Dimention is %d x %d\n", dimX*dimZ*(numLayer+1),
    // dimX*dimZ*(numLayer+1));

    count = count + dimX * dimZ * (numLayer + 1);
    // * since the previous calculation does not count the diagonal values
    // * here we update number of non-zero values by adding the number
    // * of the diagnal values

    double **Midx;
    // allocate space for Midx
    if (!(Midx = (double **)malloc((count) * sizeof(double *))))
        printf("Malloc fails for Midx[].\n");
    for (i = 0; i < count; i++)
        if (!(Midx[i] = (double *)malloc((3) * sizeof(double))))
            printf("Malloc fails for Midx[%d][].\n", i);

    // try to initialize Midx
    for (i = 0; i < count; i++)
        for (j = 0; j < 3; j++) Midx[i][j] = 0;

    // fill in the off-diagnal values
    int idx = 0,
        idx_re;  // idx_re records the idx of the first item of each row
    double row_t, col_t, val_t;  // for swap values
    for (l = 0; l < numLayer + 1; l++) {
        for (j = 0; j < dimZ; j++) {
            for (i = 0; i < dimX; i++) {
                idx_re = idx;
                if (l > 0) {
                    Midx[idx][0] = l * dimX * dimZ + j * dimX + i;
                    Midx[idx][1] = (l - 1) * dimX * dimZ + j * dimX + i;
                    Midx[idx][2] =
                        -1 / (Rvert[i][j][l] / 2 + Rvert[i][j][l - 1] / 2);
                    // printf("%d:%f\t%f\t%.5f\n", idx, Midx[idx][0],
                    // Midx[idx][1], Midx[idx][2]);
                    idx++;
                }
                if (j - 1 >= 0) {
                    Midx[idx][0] = l * dimX * dimZ + j * dimX + i;
                    Midx[idx][1] = l * dimX * dimZ + (j - 1) * dimX + i;
                    Midx[idx][2] = -1 / Rhori[l][1];
                    // printf("%d:%f\t%f\t%.5f\n", idx, Midx[idx][0],
                    // Midx[idx][1], Midx[idx][2]);
                    idx++;
                }
                if (i - 1 >= 0) {
                    Midx[idx][0] = l * dimX * dimZ + j * dimX + i;
                    Midx[idx][1] = l * dimX * dimZ + j * dimX + i - 1;
                    Midx[idx][2] = -1 / Rhori[l][0];
                    // printf("%d:%f\t%f\t%.5f\n", idx, Midx[idx][0],
                    // Midx[idx][1], Midx[idx][2]);
                    idx++;
                }
                if (i + 1 < dimX) {
                    Midx[idx][0] = l * dimX * dimZ + j * dimX + i;
                    Midx[idx][1] = l * dimX * dimZ + j * dimX + i + 1;
                    Midx[idx][2] = -1 / Rhori[l][0];
                    // printf("%d:%f\t%f\t%.5f\n", idx, Midx[idx][0],
                    // Midx[idx][1], Midx[idx][2]);
                    idx++;
                }

                if (j + 1 < dimZ) {
                    Midx[idx][0] = l * dimX * dimZ + j * dimX + i;
                    Midx[idx][1] = l * dimX * dimZ + (j + 1) * dimX + i;
                    Midx[idx][2] = -1 / Rhori[l][1];
                    // printf("%d:%f\t%f\t%.5f\n", idx, Midx[idx][0],
                    // Midx[idx][1], Midx[idx][2]);
                    idx++;
                }
                if (l < numLayer) {
                    Midx[idx][0] = l * dimX * dimZ + j * dimX + i;
                    Midx[idx][1] = (l + 1) * dimX * dimZ + j * dimX + i;
                    Midx[idx][2] =
                        -1 / (Rvert[i][j][l] / 2 + Rvert[i][j][l + 1] / 2);
                    // printf("%d:%f\t%f\t%.5f\n", idx, Midx[idx][0],
                    // Midx[idx][1], Midx[idx][2]);
                    idx++;
                }

                // calculate the diagnal values
                // printf("idx_re = %d; idx = %d\n", idx_re, idx);
                Midx[idx][0] = l * dimX * dimZ + j * dimX + i;
                Midx[idx][1] = l * dimX * dimZ + j * dimX + i;

                // printf("ATENTION: idx_re = %d, idx = %d\n", idx_re, idx);
                for (k = idx_re; k < idx; k++) {
                    // if (Midx[idx][0] == 5)
                    // printf("Midx[idx][2] = %.6f\n", Midx[idx][2]);
                    Midx[idx][2] -= Midx[k][2];
                }
                if (Midx[idx][0] < dimX * dimZ) {  // heat sink nodes
                    // if (Midx[idx][0] == 5)
                    // printf("Midx[idx][2] = %.6f\n", Midx[idx][2]);
                    Midx[idx][2] += 1 / Ramb;
                }
                // printf("%d:%f\t%f\t%.5f\t, %d\t%d\n", idx, Midx[idx][0],
                // Midx[idx][1], Midx[idx][2], idx_re, idx);
                idx++;
                // sort this row
                for (k = idx - 2; k >= idx_re; k--) {
                    if (Midx[k][1] > Midx[k + 1][1]) {
                        row_t = Midx[k][0];
                        col_t = Midx[k][1];
                        val_t = Midx[k][2];
                        Midx[k][0] = Midx[k + 1][0];
                        Midx[k][1] = Midx[k + 1][1];
                        Midx[k][2] = Midx[k + 1][2];
                        Midx[k + 1][0] = row_t;
                        Midx[k + 1][1] = col_t;
                        Midx[k + 1][2] = val_t;
                    } else
                        break;
                }

                if (idx > count) printf("Error: exceed the Midx dimension!\n");
            }
        }
    }

    /*  int iidx;
      for (iidx = 0; iidx < count; iidx ++)
        printf("%f\t%f\t%.5f\n", Midx[iidx][0], Midx[iidx][1], Midx[iidx][2]);
    */

    // printf("size of Midx is %d\n", sizeof Midx /sizeof Midx[0]);

    /* free the space of Rvert and Rhori */
    for (i = 0; i < dimX; i++) {
        for (j = 0; j < dimZ; j++) {
            free(Rvert[i][j]);
        }
        free(Rvert[i]);
    }
    free(Rvert);

    for (i = 0; i < numLayer + 1; i++) {
        free(Rhori[i]);
    }
    free(Rhori);
    SUPERLU_FREE(K);
    SUPERLU_FREE(H);
    SUPERLU_FREE(layerP);

    *MidxSize = count;
    return Midx;
}

double *steady_thermal_solver(double ***powerM, double W, double Lc, int numP,
                              int dimX, int dimZ, double **Midx, int count,
                              double Tamb) {
    int numLayer = numP * 3;
    int_t *layerP;
    // define the active layer array
    if (!(layerP = intMalloc(numP))) SUPERLU_ABORT("Malloc fails for numP[].");
    for (int l = 0; l < numP; l++) layerP[l] = l * 3;

    double Wsink = W;
    double Lsink = Lc;
    double Hsink = Hhs;
    double Ksink = Khs;
    double gridXsink = Wsink / dimX;
    double gridZsink = Lsink / dimZ;
    double Rsinky = Hsink / Ksink / gridXsink / gridZsink;  // y direction
    double Ramb = Rsinky / 2;

    // convert the values to the SuperMatrix format
    SuperMatrix A, L, U, B;
    double *a;
    int_t *asub, *xa;
    int_t *perm_r; /* row permutations from partial pivoting */
    int_t *perm_c; /* column permutation vector */
    SCPformat *Lstore;
    NCPformat *Ustore;
    int_t nrhs, info, m, n, nnz, b;
    int_t nprocs; /* maximum number of processors to use. */
    int_t panel_size, relax, maxsup;
    int_t permc_spec;
    trans_t trans;
    double *rhs;
    superlu_memusage_t superlu_memusage;

    nrhs = 1;
    trans = NOTRANS;
    nprocs = omp_get_max_threads();
    b = 1;
    panel_size = sp_ienv(1);
    relax = sp_ienv(2);
    maxsup = sp_ienv(3);

    /* Initialize matrix A. */
    m = n = dimX * dimZ * (numLayer + 1);
    nnz = count;
    if (!(a = doubleMalloc(nnz)))
        SUPERLU_ABORT("Malloc fails for a[].");  // I cannot free the space
    if (!(asub = intMalloc(nnz)))
        SUPERLU_ABORT("Malloc fails for asub[].");  // I cannot free the space
    if (!(xa = intMalloc(n + 1)))
        SUPERLU_ABORT("Malloc fails for xa[].");  // I cannot free the space

    /* assign values to the arrays: a, asub and xa */
    int row = -1;
    for (int i = 0; i < count; i++) {
        if (Midx[i][0] > row) {
            row = Midx[i][0];  // enter a new column
            xa[row] = i;       // index of the first item of each row
        }
        a[i] = Midx[i][2];
        asub[i] = (int)Midx[i][1];  // column index of each item
    }
    xa[row + 1] = count;

    printf("Using %lld Cores to calculate\n", nprocs);
    printf("Building the sparse matrix ...\n");
    printf("Dimension of the G matrix is %lld x %lld\n", m, n);
    printf("Number of non-zero entries is %lld\n", nnz);

    /* Create matrix A in the format expected by SuperLU. */
    dCreate_CompCol_Matrix(&A, m, n, nnz, a, asub, xa, SLU_NC, SLU_D, SLU_GE);
    // dPrint_CompCol_Matrix("A", &A);
    /* Create right-hand side matrix B. */
    if (!(rhs = doubleMalloc(m * nrhs)))
        SUPERLU_ABORT("Malloc fails for rhs[].");

    // assign values to B
    for (int i = 0; i < m; i++)  // initialize rhs to 0
        rhs[i] = 0;
    for (int i = 0; i < dimX * dimZ; i++) rhs[i] = Tamb / Ramb;
    for (int l = 0; l < numP; l++)
        for (int i = 0; i < dimX; i++)
            for (int j = 0; j < dimZ; j++) {
                rhs[dimX * dimZ * (layerP[l] + 1) + j * dimX + i] =
                    powerM[i][j][l];
                // rhs[dimX*dimZ*(layerP[l]+1) + i*dimZ + j] = powerM[i][j][l];
                // printf("%.6f\n", powerM[i][j][l]);
            }

    // free the space
    for (int i = 0; i < dimX; i++) {
        for (int j = 0; j < dimZ; j++) {
            free(powerM[i][j]);
        }
        free(powerM[i]);
    }
    free(powerM);

    dCreate_Dense_Matrix(&B, m, nrhs, rhs, m, SLU_DN, SLU_D, SLU_GE);

    // dPrint_Dense_Matrix("B", &B);

    if (!(perm_r = intMalloc(m))) SUPERLU_ABORT("Malloc fails for perm_r[].");
    if (!(perm_c = intMalloc(n))) SUPERLU_ABORT("Malloc fails for perm_c[].");

    /*
     * Get column permutation vector perm_c[], according to permc_spec:
     *   permc_spec = 0: natural ordering
     *   permc_spec = 1: minimum degree ordering on structure of A'*A
     *   permc_spec = 2: minimum degree ordering on structure of A'+A
     *   permc_spec = 3: approximate minimum degree for unsymmetric matrices
     */
    permc_spec = 1;
    get_perm_c(permc_spec, &A, perm_c);

    printf("Finish building the sparse matrix\n");
    printf("------------------------------------------------------------\n\n");

    /* Solve the linear system. */
    pdgssv(nprocs, &A, perm_c, perm_r, &L, &U, &B, &info);

    printf("Finish solving the linear equation\n");

    // dPrint_Dense_Matrix("B", &B);

    // extract the Temperature from B
    DNformat *Astore = (DNformat *)B.Store;
    // double *Tt; // vector stores the temperature for all grids

    double *Ttp, *Tt;
    if (!(Tt = (double *)malloc(dimX * dimZ * (numP * 3 + 1) * sizeof(double))))
        printf("Malloc fails for Tt\n");
    Ttp = (double *)Astore->nzval;
    printf("B.nrow is %lld\n", B.nrow);
    for (int i = 0; i < B.nrow; ++i) {
        Tt[i] = Ttp[i] - T0;
        // printf("Tt[%d] = %.2f\n", i, Tt[i]);
    }

    /*Tt = (double *) Astore->nzval;
    printf("B.nrow is %d\n", B.nrow);
    for (i = 0; i < B.nrow; ++i)
    {
        Tt[i] = Tt[i] - T0;
        //printf("%.2f\n", T[i]);
    }*/

    printf("Finish converting the temperature matrix\n");
    printf("Free the space...\n");

    if (info == 0) {
        // dinf_norm_error(nrhs, &B, xact); /* Inf. norm of the error */

        Lstore = (SCPformat *)L.Store;
        Ustore = (NCPformat *)U.Store;
        printf("#NZ in factor L = " IFMT "\n", Lstore->nnz);
        printf("#NZ in factor U = " IFMT "\n", Ustore->nnz);
        printf("#NZ in L+U = " IFMT "\n", Lstore->nnz + Ustore->nnz - L.ncol);

        superlu_dQuerySpace(nprocs, &L, &U, panel_size, &superlu_memusage);
        printf("L\\U MB %.3f\ttotal MB needed %.3f\texpansions " IFMT "\n",
               superlu_memusage.for_lu / 1024 / 1024,
               superlu_memusage.total_needed / 1024 / 1024,
               superlu_memusage.expansions);
    }

    /* De-allocate storage */
    // free the arrays defined by myself
    SUPERLU_FREE(layerP);
    SUPERLU_FREE(rhs);
    SUPERLU_FREE(perm_r);
    SUPERLU_FREE(perm_c);
    printf("finish SUPERLU_FREE\n");
    Destroy_CompCol_Matrix(&A);
    Destroy_SuperMatrix_Store(&B);
    Destroy_SuperNode_SCP(&L);
    Destroy_CompCol_NCP(&U);
    /* De-allocate other storage */
    // free(K); free(H); free(layerP); free(Tt);

    printf(
        "================= FINISH STEADY TEMPERATURE SOLVER "
        "===============\n\n");

    return Tt;
}

double *transient_thermal_solver(double ***powerM, double W, double Lc,
                                 int numP, int dimX, int dimZ, double **Midx,
                                 int MidxSize, double *Cap, int CapSize,
                                 double time, int iter, double *T_trans,
                                 double Tamb) {
    int numLayer = numP * 3;

    // define the active layer array
    int_t *layerP;
    if (!(layerP = intMalloc(numP))) SUPERLU_ABORT("Malloc fails for numP[].");
    for (int l = 0; l < numP; l++) layerP[l] = l * 3;

    double Wsink = W;
    double Lsink = Lc;
    double Hsink = Hhs;
    double Ksink = Khs;
    double gridXsink = Wsink / dimX;
    double gridZsink = Lsink / dimZ;
    double Rsinky = Hsink / Ksink / gridXsink / gridZsink;  // y direction
    double Ramb = Rsinky / 2;

    double *Tp = T_trans;
    double *T, *P;
    int T_size = dimX * dimZ * (numLayer + 1);
    if (!(T = doubleMalloc(T_size))) SUPERLU_ABORT("Malloc fails for rhs[].");
    if (!(P = doubleMalloc(T_size))) SUPERLU_ABORT("Malloc fails for rhs[].");

    // initialize T and P
    memset(T, 0, T_size * sizeof(*T));
    memset(P, 0, T_size * sizeof(*T));

    for (int i = 0; i < dimX * dimZ; i++) P[i] = Tamb / Ramb;
    for (int l = 0; l < numP; l++)
        for (int j = 0; j < dimZ; j++)
            for (int i = 0; i < dimX; i++) {
                P[dimX * dimZ * (layerP[l] + 1) + i * dimZ + j] =
                    powerM[i][j][l];
                // printf("%.6f\n", powerM[i][j][l]);
            }

    double dt = time / (double)iter;

    ////////////// iteratively update the temperature /////////////////

    // tried to optimize the following code
    // multi-threading won't work unless you figure out T[idx0] access patterns
    // also tried a variety of loop tiling technique, on our server block_size=8
    // or 4 yields best performance, so let it be...
    const int block_size = 8;
    for (int iit = 0; iit < iter; iit++) {
        // main calculation of the new T
        for (int j = 0; j < MidxSize; j += block_size) {
            int bound = j + block_size < MidxSize ? (j + block_size) : MidxSize;
            for (int b = j; b < bound; b++) {
                int idx0 = (int)(Midx[b][0] + 0.01);
                int idx1 = (int)(Midx[b][1] + 0.01);
                double tmp_c = Midx[b][2];
                int idxC = idx0 / (dimX * dimZ);

                if (idx0 == idx1) {
                    // if (1-Midx[j][2]*dt/Cap[idxC] < 0)
                    //    printf("NEGATIVE: idx0 = %d\n", idx0);
                    double tmp_a = 1 - tmp_c * dt / Cap[idxC];
                    double tmp_b = tmp_a * Tp[idx1] + P[idx0] * dt / Cap[idxC];
                    T[idx0] += tmp_b;
                } else {
                    double tmp_a = tmp_c * Tp[idx1] * dt / Cap[idxC];
                    T[idx0] -= tmp_a;
                }
            }
        }

        // give value for the next T
        double *Tt;  // for swap the storage between T and Tp
        Tt = Tp;
        Tp = T;
        T = Tt;  // exchange T, Tp
        memset(T, 0, dimX * dimZ * (numLayer + 1) * sizeof(*T));
    }

    // free the space
    for (int i = 0; i < dimX; i++) {
        for (int j = 0; j < dimZ; j++) {
            free(powerM[i][j]);
        }
        free(powerM[i]);
    }
    free(powerM);

    SUPERLU_FREE(layerP);
    SUPERLU_FREE(P);
    SUPERLU_FREE(T);

    return Tp;
}

double get_maxT(double *T, int Tsize) {
    double maxT = 0.0;
    int i;

    for (i = 0; i < Tsize; i++) {
        if (T[i] > maxT) maxT = T[i];
    }

    return maxT;
}
