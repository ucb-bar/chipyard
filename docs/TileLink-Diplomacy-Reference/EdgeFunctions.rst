TileLink Edge Object Methods
============================

The edge object associated with a TileLink node has several helpful methods
for constructing TileLink messages and retrieving data from them.


Get
---

Constructor for a TLBundleA encoding a ``Get`` message, which requests data
from memory. The D channel response to this message will be an
``AccessAckData``, which may have multiple beats.

**Arguments:**

 - ``fromSource: UInt`` - Source ID for this transaction
 - ``toAddress: UInt`` - The address to read from
 - ``lgSize: UInt`` - Base two logarithm of the number of bytes to be read

Put
---

Constructor for a TLBundleA encoding a ``PutFull`` or ``PutPartial`` message,
which write data to memory. It will be a ``PutPartial`` if the ``mask`` is
specified and a ``PutFull`` if it is omitted. The put may require multiple
beats. If that is the case, only ``data`` and ``mask`` should change for each
beat. All other fields must be the same for all beats in the transaction,
including the address. The manager will respond to this message with a single
``AccessAck``.

**Arguments:**

 - ``fromSource: UInt`` - Source ID for this transaction.
 - ``toAddress: UInt`` - The address to write to.
 - ``lgSize: UInt`` - Base two logarithm of the number of bytes to be written.
 - ``data: UInt`` - The data to write on this beat.
 - ``mask: UInt`` - (optional) The write mask for this beat.

Arithmetic
----------

Constructor for a TLBundleA encoding an ``Arithmetic`` message, which is an
atomic operation. The possible values for the ``atomic`` field are defined
in the ``TLAtomics`` object. It can be ``MIN``, ``MAX``, ``MINU``, ``MAXU``, or
``ADD``, which correspond to atomic minimum, maximum, unsigned minimum, unsigned
maximum, or addition operations, respectively. The previous value at the
memory location will be returned in the response, which will be in the form
of an ``AccessAckData``.

**Arguments:**

 - ``fromSource: UInt`` - Source ID for this transaction.
 - ``toAddress: UInt`` - The address to perform an arithmetic operation on.
 - ``lgSize: UInt`` - Base two logarithm of the number of bytes to operate on.
 - ``data: UInt`` - Right-hand operand of the arithmetic operation
 - ``atomic: UInt`` - Arithmetic operation type (from ``TLAtomics``)

Logical
-------

Constructor for a TLBundleA encoding a ``Logical`` message, an atomic operation.
The possible values for the ``atomic`` field are ``XOR``, ``OR``, ``AND``, and
``SWAP``, which correspond to atomic bitwise exclusive or, bitwise inclusive or,
bitwise and, and swap operations, respectively. The previous value at the
memory location will be returned in an ``AccessAckData`` response.

**Arguments:**

 - ``fromSource: UInt`` - Source ID for this transaction.
 - ``toAddress: UInt`` - The address to perform a logical operation on.
 - ``lgSize: UInt`` - Base two logarithm of the number of bytes to operate on.
 - ``data: UInt`` - Right-hand operand of the logical operation
 - ``atomic: UInt`` - Logical operation type (from ``TLAtomics``)

Hint
----

Constructor for a TLBundleA encoding a ``Hint`` message, which is used to
send prefetch hints to caches. The ``param`` argument determines what kind
of hint it is. The possible values come from the ``TLHints`` object and are
``PREFETCH_READ`` and ``PREFETCH_WRITE``. The first one tells caches to
acquire data in a shared state. The second one tells cache to acquire data
in an exclusive state. If the cache this message reaches is a last-level cache,
there won't be any difference. If the manager this message reaches is not a
cache, it will simply be ignored. In any case, a ``HintAck`` message will be
sent in response.

**Arguments:**

 - ``fromSource: UInt`` - Source ID for this transaction.
 - ``toAddress: UInt`` - The address to prefetch
 - ``lgSize: UInt`` - Base two logarithm of the number of bytes to prefetch
 - ``param: UInt`` - Hint type (from TLHints)

AccessAck
---------

Constructor for a TLBundleD encoding an ``AccessAck`` or ``AccessAckData``
message. If the optional ``data`` field is supplied, it will be an
``AccessAckData``. Otherwise, it will be an ``AccessAck``.

**Arguments**

 - ``a: TLBundleA`` - The A channel message to acknowledge
 - ``data: UInt`` - (optional) The data to send back

HintAck
-------

Constructor for a TLBundleD encoding a ``HintAck`` message.

**Arguments**

 - ``a: TLBundleA`` - The A channel message to acknowledge

first/last/count
----------------

These methods take a decoupled channel (either the A channel or D channel)
and determines whether the current beat is the first of the transaction,
whether the current beat is the last in the transaction, or the count
(starting from 0) of the current beat in the transaction.

**Arguments:**

 - ``x: DecoupledIO[TLChannel]`` - The decoupled channel to snoop on.

numBeats
---------

This method takes in a TileLink bundle and gives the number of beats expected
for the transaction.

**Arguments:**

 - ``x: TLChannel`` - The TileLink bundle to get the number of beats from

numBeats1
---------

Similar to ``numBeats`` except it gives the number of beats minus one. If this
is what you need, you should use this instead of doing ``numBeats - 1.U``, as
this is more efficient.

**Arguments:**

 - ``x: TLChannel`` - The TileLink bundle to get the number of beats from

hasData
--------

Determines whether the TileLink message contains data or not. This is true
if the message is a PutFull, PutPartial, Arithmetic, Logical, or AccessAckData.

**Arguments:**

 - ``x: TLChannel`` - The TileLink bundle to check
