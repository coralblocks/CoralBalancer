# CoralBalancer

CoralBalancer is a deterministic, garbage-free, fast _key-to-node_ balancer for Java. It uses [Rendezvous Hashing](https://en.wikipedia.org/wiki/Rendezvous_hashing) to deterministically balance any key (of any type) uniformly by choosing a node from a set of nodes. It is meant to be used in deterministic, single-topic, single-threaded, event-stream architectures where every node receives all messages. With CoralBalancer, each node builds the same balancer state, then uses its `isForMe(key)` method to decide whether it should handle a message or not.

## Example

```java
Balancer balancer = new Balancer("NODE1");

balancer.addNode("NODE1");
balancer.addNode("NODE2");
balancer.addNode("NODE3");

String symbol = message.getSymbol();

if (balancer.isForMe(symbol)) handle(message); 
```

The keys are uniformly distributed across the nodes. For the balancer above containing three nodes, each node should handle roughly one third of the keys.

## Features

- Fast
- Garbage-free
- Deterministic ownership: the same key and node list always produce the same owner.
- Uniform distribution across active nodes: four nodes should each receive roughly 25% of the keys.
- Per-key caching for speed: cleared when nodes are added or removed.
- Key support for `CharSequence`, `byte[]`, `char[]`, `ByteBuffer`, and all Java primitives.
- Pinning: force a specific key to a specific node and bypass hashing (good for testing).

## Pinning

```java
Balancer balancer = new Balancer("NODE1");

balancer.addNode("NODE1");
balancer.addNode("NODE2");
balancer.addNode("NODE3");

balancer.pin("MSFT", "NODE1");
```

Pinning is useful when a key must be handled by a specific node while the rest of the keys remain balanced by hashing.
