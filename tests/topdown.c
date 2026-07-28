#include <stdint.h>
#include <stdio.h>
#include <riscv-pk/encoding.h>

/*
    This experiment does first level Top-down analysis for BOOM.
*/

#define REPEAT 10UL

#define GCD_N 256UL
#define MAT_N 16UL
#define PTR_N 256UL
#define PTR_ITERS 4096UL

/*
 * bits [7:0]      = event set ID
 * bits [XLEN-1:8] = event mask inside that set
 *
 * selector = event_set_id | (1 << (8 + event_index))
 */
#define EVENT_SELECTOR(event_set_id, event_index) \
    (((uint64_t)(event_set_id)) | (1ULL << (8 + (event_index))))

/*
 * set 3:
 *   event 0 = TOPDOWN.SLOTS
 *   event 1 = TOPDOWN.RETIRING.SLOTS
 *   event 2 = TOPDOWN.FRONTEND_BOUND.SLOTS
 *   event 3 = TOPDOWN.BACKEND_BOUND.SLOTS
 *   event 4 = TOPDOWN.BAD_SPECULATION.SLOTS
 */
#define EVENT_SET_TOPDOWN 3
#define EVENT_TOPDOWN_SLOTS 0
#define EVENT_TOPDOWN_RETIRING_SLOTS 1
#define EVENT_TOPDOWN_FRONTEND_BOUND_SLOTS 2
#define EVENT_TOPDOWN_BACKEND_BOUND_SLOTS 3
#define EVENT_TOPDOWN_BAD_SPECULATION_SLOTS 4

#define HPM_COUNTER_SLOTS_INDEX 3
#define HPM_COUNTER_RETIRING_INDEX 4
#define HPM_COUNTER_FRONTEND_BOUND_INDEX 5
#define HPM_COUNTER_BACKEND_BOUND_INDEX 6
#define HPM_COUNTER_BAD_SPECULATION_INDEX 7

#define HPM_COUNTER_MASK                          \
    ((1ULL << HPM_COUNTER_SLOTS_INDEX) |          \
     (1ULL << HPM_COUNTER_RETIRING_INDEX) |       \
     (1ULL << HPM_COUNTER_FRONTEND_BOUND_INDEX) | \
     (1ULL << HPM_COUNTER_BACKEND_BOUND_INDEX) |  \
     (1ULL << HPM_COUNTER_BAD_SPECULATION_INDEX))
typedef struct
{
    uint64_t cycle;
    uint64_t instret;
    uint64_t slots;
    uint64_t retiring_slots;
    uint64_t frontend_bound_slots;
    uint64_t backend_bound_slots;
    uint64_t bad_speculation_slots;
} measurement_t;

static volatile uint64_t sink = 0;

static inline void program_counters(void)
{
    uint64_t slots_sel =
        EVENT_SELECTOR(EVENT_SET_TOPDOWN, EVENT_TOPDOWN_SLOTS);
    uint64_t retiring_sel =
        EVENT_SELECTOR(EVENT_SET_TOPDOWN, EVENT_TOPDOWN_RETIRING_SLOTS);
    uint64_t frontend_bound_sel =
        EVENT_SELECTOR(EVENT_SET_TOPDOWN, EVENT_TOPDOWN_FRONTEND_BOUND_SLOTS);
    uint64_t backend_bound_sel =
        EVENT_SELECTOR(EVENT_SET_TOPDOWN, EVENT_TOPDOWN_BACKEND_BOUND_SLOTS);
    uint64_t bad_speculation_sel =
        EVENT_SELECTOR(EVENT_SET_TOPDOWN, EVENT_TOPDOWN_BAD_SPECULATION_SLOTS);

    uint64_t old_mcountinhibit = read_csr(mcountinhibit);

    write_csr(mcountinhibit, old_mcountinhibit | HPM_COUNTER_MASK);

    write_csr(mhpmevent3, slots_sel);
    write_csr(mhpmevent4, retiring_sel);
    write_csr(mhpmevent5, frontend_bound_sel);
    write_csr(mhpmevent6, backend_bound_sel);
    write_csr(mhpmevent7, bad_speculation_sel);

    write_csr(mhpmcounter3, 0);
    write_csr(mhpmcounter4, 0);
    write_csr(mhpmcounter5, 0);
    write_csr(mhpmcounter6, 0);
    write_csr(mhpmcounter7, 0);

    write_csr(mcountinhibit, old_mcountinhibit);
}

static inline measurement_t start_measurement(const char *name)
{
    measurement_t m;

    uint64_t old_mcountinhibit = read_csr(mcountinhibit);
    write_csr(mcountinhibit, old_mcountinhibit & ~HPM_COUNTER_MASK);

    m.cycle = read_csr(cycle);
    m.instret = read_csr(instret);
    m.slots = read_csr(mhpmcounter3);
    m.retiring_slots = read_csr(mhpmcounter4);
    m.frontend_bound_slots = read_csr(mhpmcounter5);
    m.backend_bound_slots = read_csr(mhpmcounter6);
    m.bad_speculation_slots = read_csr(mhpmcounter7);

    return m;
}

static inline void end_measurement(const char *name, measurement_t start)
{
    measurement_t end;

    end.bad_speculation_slots = read_csr(mhpmcounter7);
    end.backend_bound_slots = read_csr(mhpmcounter6);
    end.frontend_bound_slots = read_csr(mhpmcounter5);
    end.retiring_slots = read_csr(mhpmcounter4);
    end.slots = read_csr(mhpmcounter3);
    end.instret = read_csr(instret);
    end.cycle = read_csr(cycle);

    uint64_t d_cycle = end.cycle - start.cycle;
    uint64_t d_instret = end.instret - start.instret;
    uint64_t d_slots = end.slots - start.slots;
    uint64_t d_retiring_slots = end.retiring_slots - start.retiring_slots;
    uint64_t d_frontend_bound_slots =
        end.frontend_bound_slots - start.frontend_bound_slots;
    uint64_t d_backend_bound_slots =
        end.backend_bound_slots - start.backend_bound_slots;
    uint64_t d_bad_speculation_slots =
        end.bad_speculation_slots - start.bad_speculation_slots;

    printf("--- %s diff ---\n", name);
    printf("cycles:                   %lu\n", d_cycle);
    printf("instructions:             %lu\n", d_instret);
    printf("slots:                    %lu\n", d_slots);
    printf("retiring slots:           %lu\n", d_retiring_slots);
    printf("frontend bound slots:     %lu\n", d_frontend_bound_slots);
    printf("backend bound slots:      %lu\n", d_backend_bound_slots);
    printf("bad speculation slots:    %lu\n", d_bad_speculation_slots);

    if (d_instret != 0)
    {
        printf("CPI x1000:                 %lu\n", (1000UL * d_cycle) / d_instret);
    }

    if (d_cycle != 0)
    {
        printf("slots/cycle x1000:         %lu\n", (1000UL * d_slots) / d_cycle);
    }

    if (d_slots != 0)
    {
        printf("retiring/slots x1000:      %lu\n", (1000UL * d_retiring_slots) / d_slots);
        printf("frontend bound/slots x1000:%lu\n", (1000UL * d_frontend_bound_slots) / d_slots);
        printf("backend bound/slots x1000: %lu\n", (1000UL * d_backend_bound_slots) / d_slots);
        printf("bad speculation/slots x1000:%lu\n", (1000UL * d_bad_speculation_slots) / d_slots);
    }
}

static uint64_t gcd_u64(uint64_t a, uint64_t b)
{
    while (b != 0)
    {
        uint64_t t = b;
        b = a % b;
        a = t;
    }
    return a;
}

static void benchmark_gcd(void)
{
    uint64_t acc = 0;

    for (uint64_t r = 0; r < REPEAT; r++)
    {
        for (uint64_t i = 1; i <= GCD_N; i++)
        {
            uint64_t a = 1234567UL + i * 97UL + r;
            uint64_t b = 7654321UL + i * 31UL + r;
            acc += gcd_u64(a, b);
        }
    }

    sink ^= acc;
}

static int64_t A[MAT_N][MAT_N];
static int64_t B[MAT_N][MAT_N];
static int64_t C[MAT_N][MAT_N];

static void init_matrices(void)
{
    for (uint64_t i = 0; i < MAT_N; i++)
    {
        for (uint64_t j = 0; j < MAT_N; j++)
        {
            A[i][j] = (int64_t)(i + j + 1);
            B[i][j] = (int64_t)(i * 3 + j * 5 + 1);
            C[i][j] = 0;
        }
    }
}

static void benchmark_matrix_multiply(void)
{
    int64_t acc = 0;

    for (uint64_t r = 0; r < REPEAT; r++)
    {
        for (uint64_t i = 0; i < MAT_N; i++)
        {
            for (uint64_t j = 0; j < MAT_N; j++)
            {
                int64_t sum = 0;
                for (uint64_t k = 0; k < MAT_N; k++)
                {
                    sum += A[i][k] * B[k][j];
                }
                C[i][j] += sum;
                acc += sum;
            }
        }
    }

    sink ^= (uint64_t)acc;
}

static uint64_t next_idx[PTR_N];

static void init_pointer_chase(void)
{
    for (uint64_t i = 0; i < PTR_N; i++)
    {
        next_idx[i] = (i * 73UL + 19UL) % PTR_N;
    }
}

static void benchmark_pointer_chase(void)
{
    uint64_t idx = 0;
    uint64_t acc = 0;

    for (uint64_t r = 0; r < REPEAT; r++)
    {
        for (uint64_t i = 0; i < PTR_ITERS; i++)
        {
            idx = next_idx[idx];
            acc += idx;
        }
    }

    sink ^= acc;
}

int main(void)
{
    program_counters();

    init_matrices();
    init_pointer_chase();

    measurement_t m;

    m = start_measurement("gcd");
    benchmark_gcd();
    end_measurement("gcd", m);

    m = start_measurement("matrix_multiply");
    benchmark_matrix_multiply();
    end_measurement("matrix_multiply", m);

    m = start_measurement("pointer_chase");
    benchmark_pointer_chase();
    end_measurement("pointer_chase", m);

    printf("\nfinal sink: %lu\n", sink);

    return 0;
}