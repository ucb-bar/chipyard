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

**Returns:**

A ``(Bool, TLBundleA)`` tuple. The first item in the pair is a boolean
indicating whether or not the operation is legal for this edge. The second
is the A channel bundle.

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

**Returns:**

A ``(Bool, TLBundleA)`` tuple. The first item in the pair is a boolean
indicating whether or not the operation is legal for this edge. The second
is the A channel bundle.

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

**Returns:**

A ``(Bool, TLBundleA)`` tuple. The first item in the pair is a boolean
indicating whether or not the operation is legal for this edge. The second
is the A channel bundle.

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

**Returns:**

A ``(Bool, TLBundleA)`` tuple. The first item in the pair is a boolean
indicating whether or not the operation is legal for this edge. The second
is the A channel bundle.

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

**Returns:**

A ``(Bool, TLBundleA)`` tuple. The first item in the pair is a boolean
indicating whether or not the operation is legal for this edge. The second
is the A channel bundle.

AccessAck
---------

Constructor for a TLBundleD encoding an ``AccessAck`` or ``AccessAckData``
message. If the optional ``data`` field is supplied, it will be an
``AccessAckData``. Otherwise, it will be an ``AccessAck``.

**Arguments**

 - ``a: TLBundleA`` - The A channel message to acknowledge
 - ``data: UInt`` - (optional) The data to send back

**Returns:**

The ``TLBundleD`` for the D channel message.

HintAck
-------

Constructor for a TLBundleD encoding a ``HintAck`` message.

**Arguments**

 - ``a: TLBundleA`` - The A channel message to acknowledge

**Returns:**

The ``TLBundleD`` for the D channel message.

first
-----

This method take a decoupled channel (either the A channel or D channel)
and determines whether the current beat is the first beat in the transaction.

**Arguments:**

 - ``x: DecoupledIO[TLChannel]`` - The decoupled channel to snoop on.

**Returns:**

A ``Boolean`` which is true if the current beat is the first, or false otherwise.

last
----

This method take a decoupled channel (either the A channel or D channel)
and determines whether the current beat is the last in the transaction.

**Arguments:**

 - ``x: DecoupledIO[TLChannel]`` - The decoupled channel to snoop on.

**Returns:**

A ``Boolean`` which is true if the current beat is the last, or false otherwise.

done
----

Equivalent to ``x.fire() && last(x)``.

**Arguments:**

 - ``x: DecoupledIO[TLChannel]`` - The decoupled channel to snoop on.

**Returns:**

A ``Boolean`` which is true if the current beat is the last and a beat is
sent on this cycle. False otherwise.

count
-----

This method take a decoupled channel (either the A channel or D channel) and
determines the count (starting from 0) of the current beat in the transaction.

**Arguments:**

 - ``x: DecoupledIO[TLChannel]`` - The decoupled channel to snoop on.

**Returns:**

A ``UInt`` indicating the count of the current beat.

numBeats
---------

This method takes in a TileLink bundle and gives the number of beats expected
for the transaction.

**Arguments:**

 - ``x: TLChannel`` - The TileLink bundle to get the number of beats from

**Returns:**

A ``UInt`` that is the number of beats in the current transaction.

numBeats1
---------

Similar to ``numBeats`` except it gives the number of beats minus one. If this
is what you need, you should use this instead of doing ``numBeats - 1.U``, as
this is more efficient.

**Arguments:**

 - ``x: TLChannel`` - The TileLink bundle to get the number of beats from

**Returns:**

A ``UInt`` that is the number of beats in the current transaction minus one.

hasData
--------

Determines whether the TileLink message contains data or not. This is true
if the message is a PutFull, PutPartial, Arithmetic, Logical, or AccessAckData.

**Arguments:**

 - ``x: TLChannel`` - The TileLink bundle to check

**Returns:**

A ``Boolean`` that is true if the current message has data and false otherwise.
