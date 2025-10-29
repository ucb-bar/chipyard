Here's a short list of TODO ideas:

   * Add wait-bit for memory disambiguation. Prevent loads executing ahead of
      stores with unknown addresses, which can be a serious performance issue.
   
   * Make i-cache hit-under-miss. That code is actually in the Rocket repo, but
      is used by BOOM.

   * Add data prefetchers.

   * Allow for under-provisioned register file ports.
