# CoralBalancer

CoralBalancer is a deterministic, garbage-free and fast key-to-node balancing for Java. It uses [Rendezvous Hashing](https://en.wikipedia.org/wiki/Rendezvous_hashing) to balance any key (of any type) uniformly by deterministically choosing a node from a set of nodes. It is meant to be used by deterministic, single-topic, single-threaded, event-stream architectures where every node receives all messages. Each node builds the same balancer state, then uses `isForMe(key)` to decide whether it should handle a message or not.

## Example

```java
Balancer balancer = new Balancer("NODE1");

balancer.addNode("NODE1");
balancer.addNode("NODE2");
balancer.addNode("NODE3");

String symbol = "AAPL";

if (balancer.isForMe(symbol)) handle(message);
```

The keys are uniformly distributed across the nodes. With three nodes, each node should handle roughly one third of the keys.

## Features

- Fast
- Garbage-free
- Deterministic ownership: the same key and node list always produce the same owner.
- Uniform distribution across active nodes: four nodes should each receive roughly 25% of the keys.
- Per-key caching for speed: cleared when nodes are added or removed.
- Key support for `CharSequence`, `byte[]`, `char[]`, `ByteBuffer`, and all Java primitives.
- Pinning: force a specific key to a specific node and bypass the balancer (good for testing).

## Pinning

```java
Balancer balancer = new Balancer("NODE1");

balancer.addNode("NODE1");
balancer.addNode("NODE2");
balancer.addNode("NODE3");

balancer.pin("MSFT", "NODE1");

if (balancer.isForMe("MSFT")) handle(message); // only if you are NODE1
```

Pinning is useful when a symbol must be handled by a specific node while the rest of the symbols remain balanced by the balancer.
