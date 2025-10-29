Branch Prediction
=================

.. _front-end-bpu:
.. figure:: /figures/front-end.svg
    :alt: BOOM Front-end

    The BOOM Front-end

This chapter discusses how BOOM predicts branches and then resolves
these predictions.

BOOM uses two levels of branch prediction - a fast :term:`Next-Line Predictor (NLP)`
and a slower but more complex :term:`Backing Predictor (BPD)` [1]_. In this case,
the :term:`NLP<Next-Line Predictor (NLP)>` is a Branch Target Buffer and the :term:`BPD<Backing Predictor (BPD)>`
is a more complicated structure like a GShare predictor.

.. toctree::
    :maxdepth: 2
    :caption: Branch Prediction:

    nl-predictor
    backing-predictor

.. [1] Unfortunately, the terminology in the literature gets a bit
    muddled here in what to call different types and levels of branch
    predictor. Literature has references to different structures; "micro-BTB" versus "BTB", "NLP" versus "BHT",
    and "cache-line predictor" versus "overriding predictor". Although
    the Rocket core calls its own predictor the "BTB", BOOM
    refers to it as the :term:`Next-Line Predictor (NLP)` , to denote
    that it is a combinational predictor that provides single-cycle
    predictions for fetching "the next line", and the Rocket BTB
    encompasses far more complexity than just a "branch target buffer"
    structure. Likewise, the name :term:`Backing Predictor (BPD)` was chosen to avoid
    being overly descriptive of the internal design (is it a simple BHT?
    Is it tagged? Does it override the :term:`NLP<Next-Line Predictor (NLP)>` ?) while being accurate. If you have recommendations for
    better names, feel free to reach out!
