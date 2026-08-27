# CoralBalancer

CoralBalancer is a deterministic, garbage-free and fast _key-to-node_ balancer for event-stream distributed systems. It uses [Rendezvous Hashing](https://en.wikipedia.org/wiki/Rendezvous_hashing) to deterministically balance any key (of any type) uniformly by choosing a node from a set of nodes. It is meant to be used in deterministic, single-topic, single-threaded, event-stream architectures where every node receives all messages. With CoralBalancer, each node builds the same balancer state, then uses the `isForMe(key)` method to decide whether it should handle a message or not. It supports pinning, allowing a specific key to be pinned to a specific node, bypassing hashing.

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

## Determinism

All balancers in a distributed system must maintain the same active node list and pin state. The maximum number of active
nodes and the maximum cached or pinned variable-key length are fixed by the library so one balancer cannot accept a
routing change that another rejects because of different constructor settings. Owner-cache capacity and buffer storage are also fixed by the library.

## Pinning

```java
Balancer balancer = new Balancer("NODE1");

balancer.addNode("NODE1");
balancer.addNode("NODE2");
balancer.addNode("NODE3");

balancer.pin("MSFT", "NODE1");
balancer.pin("AAPL", "NODE4");
```

Pinning is useful when a key must be handled by a specific node while the rest of the keys remain balanced by hashing.

It is important to note that the target node does not even need to be in the active node list to be pinned. This allows a
node to handle only explicitly pinned keys without ever receiving any keys through hashing. Adding or removing an active
node does not affect pins.

To remove a single pin, use `unpin`. It returns the node account that was previously pinned, or `null` if the key was not
pinned. The returned `CharSequence` is reused and remains valid only until the next call to any `unpin` overload:

```java
CharSequence previousNode = balancer.unpin("MSFT");
```

To remove all pins pointing to a specific node, use `removePinsForNode`. The active node list is not affected, and each key whose pin is removed falls back to hashing.
If the node is currently active, it remains eligible to receive keys through hashing. Otherwise, it receives no keys until it is pinned again or added to the active node list.

```java
int removedPins = balancer.removePinsForNode("NODE4");
```
