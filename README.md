# CoralBalancer

CoralBalancer balances keys across node accounts using Rendezvous hashing, also known as highest-random-weight hashing.

It is meant for deterministic, single-topic sequence architectures where every node receives the same message stream. Each node builds the same balancer state, then uses `isForMe(key)` to decide whether it should handle a message.

## Example

```java
Balancer node1 = new Balancer("NODE1");
Balancer node2 = new Balancer("NODE2");
Balancer node3 = new Balancer("NODE3");

node1.addNode("NODE1");
node1.addNode("NODE2");
node1.addNode("NODE3");

node2.addNode("NODE1");
node2.addNode("NODE2");
node2.addNode("NODE3");

node3.addNode("NODE1");
node3.addNode("NODE2");
node3.addNode("NODE3");

String symbol = "AAPL";

if (node1.isForMe(symbol)) handle(symbol);
if (node2.isForMe(symbol)) handle(symbol);
if (node3.isForMe(symbol)) handle(symbol);
```

For a large number of symbols, ownership is uniformly distributed across the nodes. With three nodes, each node should handle roughly one third of the symbols.

## Features

- Deterministic ownership: the same key and node list always produce the same owner.
- Uniform distribution across active nodes.
- Garbage-free lookups after caches are warm.
- Per-key caching, cleared when nodes are added or removed.
- Key support for `CharSequence`, `byte[]`, `char[]`, `ByteBuffer`, primitives, `float`, and `double`.
- Pinning: force a specific key to a specific node and bypass Rendezvous hashing.

## Pinning

```java
Balancer balancer = new Balancer("NODE1");
balancer.addNode("NODE1");
balancer.addNode("NODE2");
balancer.addNode("NODE3");

balancer.pin("MSFT", "NODE1");

if (balancer.isForMe("MSFT")) handle("MSFT");
```

Pinning is useful when a symbol must be handled by a specific node while the rest of the symbols remain balanced by Rendezvous hashing.
