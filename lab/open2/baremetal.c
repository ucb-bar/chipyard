/*
 * If needed, write bare-metal code here for testing cache-related and
 * other functions that can be quickly simulated without the overhead
 * of pk initialization.
 *
 * Programs execute in machine mode (M-mode) with physical addressing.
 * A limited subset of libc (newlib) is supported.
 *
 * NOTE: The Spectre attack *cannot* be fully implemented here as the
 * victim syscall and data are not available without pk.
 */

int main(void)
{
    return 0;
}
