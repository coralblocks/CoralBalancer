/*
 * Copyright 2015-2026 (c) CoralBlocks LLC - https://www.coralblocks.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package com.coralblocks.coralbalancer;

import java.nio.ByteBuffer;
import java.util.Iterator;

import com.coralblocks.coralds.map.ByteBufferMap;
import com.coralblocks.coralds.map.ByteMap;
import com.coralblocks.coralds.map.CharSequenceMap;
import com.coralblocks.coralds.map.IntMap;
import com.coralblocks.coralds.map.LongMap;
import com.coralblocks.coralpool.LinkedObjectPool;
import com.coralblocks.coralpool.ObjectBuilder;
import com.coralblocks.coralpool.ObjectPool;

/**
 * Balances keys across node accounts using Rendezvous hashing.
 *
 * <p>A {@code Balancer} represents one local node. In a deterministic message
 * stream, all nodes should build the same active node list and then call
 * {@code isForMe(key)} to decide whether the local node should handle the key or not.</p>
 *
 * <p>The local node account identifies the node used by {@code isForMe}; it is not
 * automatically added to the active node list. This allows a balancer to represent
 * a pin-only node that does not receive keys through hashing.</p>
 *
 * <p>Owner caching is intended for bounded key spaces. Each owner cache retains at most
 * 256 entries. Once full, new keys are balanced without being cached.</p>
 *
 * <p>Primitive owner lookups share a cache, but primitive pins remain key-type-specific.</p>
 *
 * <p>Equivalent {@code CharSequence} and {@code char[]} keys share caches and pins, as do
 * equivalent {@code byte[]} and {@code ByteBuffer} keys.</p>
 *
 * <p>All balancers in a distributed system must maintain the same active-node and pin state.</p>
 */
public class Balancer {

	/** The fixed maximum number of active nodes. */
	public static final int MAX_NUMBER_OF_NODES = 256;

	/** The fixed maximum length of variable keys stored in owner caches or pin maps. */
	public static final short MAX_CACHED_VARIABLE_KEY_LENGTH = 128;

	private static final int NODE_ACCOUNT_INITIAL_CAPACITY = 64;
	private static final int OWNER_CACHE_CAPACITY = 256;
	private static final int PIN_MAP_INITIAL_CAPACITY = OWNER_CACHE_CAPACITY;
	private static final boolean USE_DIRECT_BYTE_BUFFERS = false;
	private static final float OWNER_CACHE_LOAD_FACTOR = 1.0f;

	private final CharSequence[] nodes;
	private final long[] nodeHashes;
	private final ObjectPool<StringBuilder> sbPool;
	private final String myNodeAccount;
	private int nodeCount;
	
	private final CharArrayView charArrayView;
	private final StringBuilder unpinnedNodeAccount;
	
	private CharSequenceMap<CharSequence> charSequenceOwnerCache = null;
	private ByteBufferMap<CharSequence> byteSequenceOwnerCache = null;
	private LongMap<CharSequence> primitiveOwnerCache = null;

	private CharSequenceMap<CharSequence> charSequenceOwnerPins = null;
	private ByteBufferMap<CharSequence> byteSequenceOwnerPins = null;
	private ByteMap<CharSequence> booleanOwnerPins = null;
	private ByteMap<CharSequence> byteOwnerPins = null;
	private IntMap<CharSequence> charOwnerPins = null;
	private IntMap<CharSequence> shortOwnerPins = null;
	private IntMap<CharSequence> intOwnerPins = null;
	private LongMap<CharSequence> longOwnerPins = null;
	private IntMap<CharSequence> floatOwnerPins = null;
	private LongMap<CharSequence> doubleOwnerPins = null;
	
	/**
	 * Creates a balancer for the given local node account.
	 *
	 * @param myNodeAccount the local account used by {@code isForMe}; not automatically added to the active node list
	 * @throws IllegalArgumentException if {@code myNodeAccount} is {@code null}
	 */
	public Balancer(CharSequence myNodeAccount) {
		if (myNodeAccount == null) {
			throw new IllegalArgumentException("The myNodeAccount argument cannot be null!");
		}
		this.nodes = new CharSequence[MAX_NUMBER_OF_NODES];
		this.nodeHashes = new long[MAX_NUMBER_OF_NODES];
		ObjectBuilder<StringBuilder> builder = new ObjectBuilder<StringBuilder>() {
			@Override
			public StringBuilder newInstance() {
				return new StringBuilder(NODE_ACCOUNT_INITIAL_CAPACITY);
			}
		};

		int preloadCount = Math.max(MAX_NUMBER_OF_NODES / 2, 1);

		this.sbPool = new LinkedObjectPool<StringBuilder>(MAX_NUMBER_OF_NODES, preloadCount, builder);

		this.myNodeAccount = myNodeAccount.toString();

		this.charArrayView = new CharArrayView();
		this.unpinnedNodeAccount = new StringBuilder(NODE_ACCOUNT_INITIAL_CAPACITY);
	}

	/**
	 * Returns the account of the local node represented by this balancer.
	 *
	 * @return the local node account
	 */
	public String getMyNodeAccount() {
		return myNodeAccount;
	}

	/**
	 * Returns the number of active nodes in this balancer.
	 *
	 * @return the number of active nodes
	 */
	public int getNumberOfNodes() {
		return nodeCount;
	}

	/**
	 * Adds a node account to the active node list.
	 *
	 * <p>Owner caches are cleared when a node is added.</p>
	 *
	 * @param nodeAccount the node account to add
	 * @return {@code true} if the node was added; {@code false} if it was already present
	 * @throws IllegalStateException if the maximum number of active nodes has been reached
	 */
	public boolean addNode(CharSequence nodeAccount) {
		if (!contains(nodeAccount)) {
			if (nodeCount == nodes.length) {
				throw new IllegalStateException("Maximum number of active nodes reached: " + nodes.length);
			}
			CharSequence storedNodeAccount = getFromPool(nodeAccount);
			nodes[nodeCount] = storedNodeAccount;
			nodeHashes[nodeCount] = RendezvousHashing.hashNode(storedNodeAccount);
			nodeCount++;
			clearCaches();
			return true;
		}
		return false;
	}

	/**
	 * Removes a node account from the active node list.
	 *
	 * <p>Owner caches are cleared when a node is removed. Pins are not affected.</p>
	 *
	 * @param nodeAccount the node account to remove
	 * @return {@code true} if the node was removed; {@code false} if it was not present
	 */
	public boolean removeNode(CharSequence nodeAccount) {
		int index = indexOf(nodeAccount);
		if (index >= 0) {
			CharSequence removedNodeAccount = nodes[index];
			int moved = nodeCount - index - 1;
			if (moved > 0) {
				System.arraycopy(nodes, index + 1, nodes, index, moved);
				System.arraycopy(nodeHashes, index + 1, nodeHashes, index, moved);
			}
			nodeCount--;
			nodes[nodeCount] = null;
			nodeHashes[nodeCount] = 0L;
			sbPool.release((StringBuilder) removedNodeAccount);
			clearCaches();
			return true;
		}
		return false;
	}

	/**
	 * Checks whether a node account is in the active node list.
	 *
	 * @param nodeAccount the node account to find
	 * @return {@code true} if the node is active; {@code false} otherwise
	 */
	public boolean hasNode(CharSequence nodeAccount) {
		return contains(nodeAccount);
	}

	/**
	 * Pins a {@link CharSequence} key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if either argument is {@code null}, or if the key is longer than
	 *                                  {@link #MAX_CACHED_VARIABLE_KEY_LENGTH}
	 */
	public void pin(CharSequence key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		ensurePinnableVariableKeyLength(key.length());
		CharSequence oldNodeAccount = getCharSequenceOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a byte array key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if either argument is {@code null}, or if the key is longer than
	 *                                  {@link #MAX_CACHED_VARIABLE_KEY_LENGTH}
	 */
	public void pin(byte[] key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		ensurePinnableVariableKeyLength(key.length);
		CharSequence oldNodeAccount = getByteSequenceOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a char array key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if either argument is {@code null}, or if the key is longer than
	 *                                  {@link #MAX_CACHED_VARIABLE_KEY_LENGTH}
	 */
	public void pin(char[] key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		ensurePinnableVariableKeyLength(key.length);
		charArrayView.wrap(key);
		CharSequence oldNodeAccount = getCharSequenceOwnerPins().put(
				charArrayView, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a {@link ByteBuffer} key to a node account.
	 *
	 * @param key the key to pin, using bytes from position to limit
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if either argument is {@code null}, or if the key is longer than
	 *                                  {@link #MAX_CACHED_VARIABLE_KEY_LENGTH}
	 */
	public void pin(ByteBuffer key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		ensurePinnableVariableKeyLength(key.remaining());
		CharSequence oldNodeAccount = getByteSequenceOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a boolean key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(boolean key, CharSequence nodeAccount) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		CharSequence oldNodeAccount = getBooleanOwnerPins().put(cacheKey, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a byte key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(byte key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getByteOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a char key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(char key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getCharOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a short key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(short key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getShortOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins an int key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(int key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getIntOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a long key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(long key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getLongOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a float key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(float key, CharSequence nodeAccount) {
		int cacheKey = Float.floatToIntBits(key);
		CharSequence oldNodeAccount = getFloatOwnerPins().put(cacheKey, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Pins a double key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @throws IllegalArgumentException if {@code nodeAccount} is {@code null}
	 */
	public void pin(double key, CharSequence nodeAccount) {
		long cacheKey = Double.doubleToLongBits(key);
		CharSequence oldNodeAccount = getDoubleOwnerPins().put(cacheKey, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a {@link CharSequence} key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(CharSequence key) {
		ensureKeyNotNull(key);
		if (key.length() > MAX_CACHED_VARIABLE_KEY_LENGTH) return copyAndReleaseUnpinnedNodeAccount(null);
		CharSequence oldNodeAccount = charSequenceOwnerPins == null ? null : charSequenceOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a byte array key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(byte[] key) {
		ensureKeyNotNull(key);
		if (key.length > MAX_CACHED_VARIABLE_KEY_LENGTH) return copyAndReleaseUnpinnedNodeAccount(null);
		CharSequence oldNodeAccount = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a char array key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(char[] key) {
		ensureKeyNotNull(key);
		if (key.length > MAX_CACHED_VARIABLE_KEY_LENGTH) return copyAndReleaseUnpinnedNodeAccount(null);
		if (charSequenceOwnerPins == null) return copyAndReleaseUnpinnedNodeAccount(null);
		charArrayView.wrap(key);
		CharSequence oldNodeAccount = charSequenceOwnerPins.remove(charArrayView);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a {@link ByteBuffer} key if present.
	 *
	 * @param key the key to unpin, using bytes from position to limit
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(ByteBuffer key) {
		ensureKeyNotNull(key);
		if (key.remaining() > MAX_CACHED_VARIABLE_KEY_LENGTH) return copyAndReleaseUnpinnedNodeAccount(null);
		CharSequence oldNodeAccount = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a boolean key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(boolean key) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		CharSequence oldNodeAccount = booleanOwnerPins == null ? null : booleanOwnerPins.remove(cacheKey);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a byte key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(byte key) {
		CharSequence oldNodeAccount = byteOwnerPins == null ? null : byteOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a char key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(char key) {
		CharSequence oldNodeAccount = charOwnerPins == null ? null : charOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a short key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(short key) {
		CharSequence oldNodeAccount = shortOwnerPins == null ? null : shortOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for an int key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(int key) {
		CharSequence oldNodeAccount = intOwnerPins == null ? null : intOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a long key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(long key) {
		CharSequence oldNodeAccount = longOwnerPins == null ? null : longOwnerPins.remove(key);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a float key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(float key) {
		int cacheKey = Float.floatToIntBits(key);
		CharSequence oldNodeAccount = floatOwnerPins == null ? null : floatOwnerPins.remove(cacheKey);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes a pin for a double key if present.
	 *
	 * @param key the key to unpin
	 * @return the previous node account, valid until the next call to any {@code unpin} overload;
	 *         {@code null} if the key was not pinned
	 */
	public CharSequence unpin(double key) {
		long cacheKey = Double.doubleToLongBits(key);
		CharSequence oldNodeAccount = doubleOwnerPins == null ? null : doubleOwnerPins.remove(cacheKey);
		return copyAndReleaseUnpinnedNodeAccount(oldNodeAccount);
	}

	/**
	 * Removes all pins pointing at a node account.
	 *
	 * <p>The active node list is not affected. Keys whose pins are removed fall back to hashing.</p>
	 *
	 * @param nodeAccount the node account whose pins should be removed
	 * @return the number of pins removed
	 */
	public int removePinsForNode(CharSequence nodeAccount) {
		ensureNodeAccountNotNull(nodeAccount);
		int removed = 0;
		removed += removePinsForNode(charSequenceOwnerPins, nodeAccount);
		removed += removePinsForNode(byteSequenceOwnerPins, nodeAccount);
		removed += removePinsForNode(booleanOwnerPins, nodeAccount);
		removed += removePinsForNode(byteOwnerPins, nodeAccount);
		removed += removePinsForNode(charOwnerPins, nodeAccount);
		removed += removePinsForNode(shortOwnerPins, nodeAccount);
		removed += removePinsForNode(intOwnerPins, nodeAccount);
		removed += removePinsForNode(longOwnerPins, nodeAccount);
		removed += removePinsForNode(floatOwnerPins, nodeAccount);
		removed += removePinsForNode(doubleOwnerPins, nodeAccount);
		return removed;
	}

	/**
	 * Returns the owner node for a {@link CharSequence} key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(CharSequence key) {
		ensureKeyNotNull(key);
		if (key.length() > MAX_CACHED_VARIABLE_KEY_LENGTH) return ownerForHash(RendezvousHashing.hashKey(key));

		CharSequence owner = charSequenceOwnerPins == null ? null : charSequenceOwnerPins.get(key);
		if (owner != null) return owner;

		CharSequenceMap<CharSequence> cache = getCharSequenceOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = ownerForHash(RendezvousHashing.hashKey(key));
		if (cache.size() < OWNER_CACHE_CAPACITY) cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a byte array key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(byte[] key) {
		ensureKeyNotNull(key);
		if (key.length > MAX_CACHED_VARIABLE_KEY_LENGTH) return ownerForHash(RendezvousHashing.hashKey(key));

		CharSequence owner = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.get(key);
		if (owner != null) return owner;

		ByteBufferMap<CharSequence> cache = getByteSequenceOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = ownerForHash(RendezvousHashing.hashKey(key));
		if (cache.size() < OWNER_CACHE_CAPACITY) cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a char array key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(char[] key) {
		ensureKeyNotNull(key);
		if (key.length > MAX_CACHED_VARIABLE_KEY_LENGTH) return ownerForHash(RendezvousHashing.hashKey(key));

		charArrayView.wrap(key);

		CharSequence owner = charSequenceOwnerPins == null ? null : charSequenceOwnerPins.get(charArrayView);
		if (owner != null) return owner;

		CharSequenceMap<CharSequence> cache = getCharSequenceOwnerCache();
		owner = cache.get(charArrayView);
		if (owner != null) return owner;

		owner = ownerForHash(RendezvousHashing.hashKey(key));
		if (cache.size() < OWNER_CACHE_CAPACITY) cache.put(charArrayView, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a {@link ByteBuffer} key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance, using bytes from position to limit
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(ByteBuffer key) {
		ensureKeyNotNull(key);
		if (key.remaining() > MAX_CACHED_VARIABLE_KEY_LENGTH) return ownerForHash(RendezvousHashing.hashKey(key));

		CharSequence owner = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.get(key);
		if (owner != null) return owner;

		ByteBufferMap<CharSequence> cache = getByteSequenceOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = ownerForHash(RendezvousHashing.hashKey(key));
		if (cache.size() < OWNER_CACHE_CAPACITY) cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a boolean key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(boolean key) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		CharSequence owner = booleanOwnerPins == null ? null : booleanOwnerPins.get(cacheKey);
		if (owner != null) return owner;
		return ownerForPrimitive(cacheKey);
	}

	/**
	 * Returns the owner node for a byte key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(byte key) {
		CharSequence owner = byteOwnerPins == null ? null : byteOwnerPins.get(key);
		if (owner != null) return owner;
		return ownerForPrimitive(key);
	}

	/**
	 * Returns the owner node for a char key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(char key) {
		CharSequence owner = charOwnerPins == null ? null : charOwnerPins.get(key);
		if (owner != null) return owner;
		return ownerForPrimitive(key);
	}

	/**
	 * Returns the owner node for a short key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(short key) {
		CharSequence owner = shortOwnerPins == null ? null : shortOwnerPins.get(key);
		if (owner != null) return owner;
		return ownerForPrimitive(key);
	}

	/**
	 * Returns the owner node for an int key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(int key) {
		CharSequence owner = intOwnerPins == null ? null : intOwnerPins.get(key);
		if (owner != null) return owner;
		return ownerForPrimitive(key);
	}

	/**
	 * Returns the owner node for a long key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(long key) {
		CharSequence owner = longOwnerPins == null ? null : longOwnerPins.get(key);
		if (owner != null) return owner;
		return ownerForPrimitive(key);
	}

	/**
	 * Returns the owner node for a float key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(float key) {
		int cacheKey = Float.floatToIntBits(key);
		CharSequence owner = floatOwnerPins == null ? null : floatOwnerPins.get(cacheKey);
		if (owner != null) return owner;
		return ownerForPrimitive(cacheKey);
	}

	/**
	 * Returns the owner node for a double key.
	 *
	 * <p>The returned {@code CharSequence} is owned by this balancer and must not be modified or retained
	 * across changes to active nodes or pins.</p>
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(double key) {
		long cacheKey = Double.doubleToLongBits(key);
		CharSequence owner = doubleOwnerPins == null ? null : doubleOwnerPins.get(cacheKey);
		if (owner != null) return owner;
		return ownerForPrimitive(cacheKey);
	}

	/**
	 * Checks whether this balancer's local node owns a {@link CharSequence} key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(CharSequence key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a byte array key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(byte[] key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a char array key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(char[] key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a {@link ByteBuffer} key.
	 *
	 * @param key the key to check, using bytes from position to limit
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(ByteBuffer key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a boolean key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(boolean key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a byte key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(byte key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a char key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(char key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a short key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(short key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns an int key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(int key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a long key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(long key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a float key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(float key) {
		return isOwnerForMe(ownerFor(key));
	}

	/**
	 * Checks whether this balancer's local node owns a double key.
	 *
	 * @param key the key to check
	 * @return {@code true} if the local node owns the key; {@code false} otherwise
	 */
	public boolean isForMe(double key) {
		return isOwnerForMe(ownerFor(key));
	}

	private CharSequence getFromPool(CharSequence cs) {
		StringBuilder sb = sbPool.get();
		sb.setLength(0);
		final int len = cs.length();
		for(int i = 0; i < len; i++) {
			sb.append(cs.charAt(i));
		}
		return sb;
	}

	private int indexOf(CharSequence nodeAccount) {
		for(int i = nodeCount - 1; i >= 0; i--) {
			CharSequence cs = nodes[i];
			if (contentEquals(cs, nodeAccount)) return i;
		}
		return -1;
	}

	private boolean contains(CharSequence nodeAccount) {
		return indexOf(nodeAccount) >= 0;
	}

	private boolean isOwnerForMe(CharSequence owner) {
		return contentEquals(owner, myNodeAccount);
	}

	private CharSequence ownerForHash(long keyHash) {
		return RendezvousHashing.ownerForHash(keyHash, nodes, nodeHashes, nodeCount);
	}

	private CharSequence ownerForPrimitive(long key) {
		LongMap<CharSequence> cache = getPrimitiveOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = ownerForHash(RendezvousHashing.hashKey(key));
		if (cache.size() < OWNER_CACHE_CAPACITY) cache.put(key, owner);
		return owner;
	}

	private CharSequence copyAndReleaseUnpinnedNodeAccount(CharSequence oldNodeAccount) {
		unpinnedNodeAccount.setLength(0);
		if (oldNodeAccount == null) return null;
		unpinnedNodeAccount.append(oldNodeAccount);
		sbPool.release((StringBuilder) oldNodeAccount);
		return unpinnedNodeAccount;
	}

	private int removePinsForNode(Iterable<CharSequence> pins, CharSequence nodeAccount) {
		if (pins == null) return 0;
		int removed = 0;
		Iterator<CharSequence> iter = pins.iterator();
		while(iter.hasNext()) {
			CharSequence pinnedNodeAccount = iter.next();
			if (contentEquals(pinnedNodeAccount, nodeAccount)) {
				iter.remove();
				sbPool.release((StringBuilder) pinnedNodeAccount);
				removed++;
			}
		}
		return removed;
	}

	private void clearCaches() {
		if (charSequenceOwnerCache != null) charSequenceOwnerCache.clear();
		if (byteSequenceOwnerCache != null) byteSequenceOwnerCache.clear();
		if (primitiveOwnerCache != null) primitiveOwnerCache.clear();
	}

	private CharSequenceMap<CharSequence> getCharSequenceOwnerCache() {
		if (charSequenceOwnerCache == null) {
			charSequenceOwnerCache = new CharSequenceMap<CharSequence>(
					OWNER_CACHE_CAPACITY, MAX_CACHED_VARIABLE_KEY_LENGTH, OWNER_CACHE_LOAD_FACTOR);
		}
		return charSequenceOwnerCache;
	}

	private ByteBufferMap<CharSequence> getByteSequenceOwnerCache() {
		if (byteSequenceOwnerCache == null) {
			byteSequenceOwnerCache = new ByteBufferMap<CharSequence>(
					OWNER_CACHE_CAPACITY, MAX_CACHED_VARIABLE_KEY_LENGTH,
					OWNER_CACHE_LOAD_FACTOR, USE_DIRECT_BYTE_BUFFERS);
		}
		return byteSequenceOwnerCache;
	}

	private LongMap<CharSequence> getPrimitiveOwnerCache() {
		if (primitiveOwnerCache == null) {
			primitiveOwnerCache = new LongMap<CharSequence>(OWNER_CACHE_CAPACITY, OWNER_CACHE_LOAD_FACTOR);
		}
		return primitiveOwnerCache;
	}

	private CharSequenceMap<CharSequence> getCharSequenceOwnerPins() {
		if (charSequenceOwnerPins == null) {
			charSequenceOwnerPins = new CharSequenceMap<CharSequence>(
					PIN_MAP_INITIAL_CAPACITY, MAX_CACHED_VARIABLE_KEY_LENGTH);
		}
		return charSequenceOwnerPins;
	}

	private ByteBufferMap<CharSequence> getByteSequenceOwnerPins() {
		if (byteSequenceOwnerPins == null) {
			byteSequenceOwnerPins = new ByteBufferMap<CharSequence>(
					PIN_MAP_INITIAL_CAPACITY, MAX_CACHED_VARIABLE_KEY_LENGTH, USE_DIRECT_BYTE_BUFFERS);
		}
		return byteSequenceOwnerPins;
	}

	private ByteMap<CharSequence> getBooleanOwnerPins() {
		if (booleanOwnerPins == null) {
			booleanOwnerPins = new ByteMap<CharSequence>();
		}
		return booleanOwnerPins;
	}

	private ByteMap<CharSequence> getByteOwnerPins() {
		if (byteOwnerPins == null) {
			byteOwnerPins = new ByteMap<CharSequence>();
		}
		return byteOwnerPins;
	}

	private IntMap<CharSequence> getCharOwnerPins() {
		if (charOwnerPins == null) {
			charOwnerPins = new IntMap<CharSequence>(PIN_MAP_INITIAL_CAPACITY);
		}
		return charOwnerPins;
	}

	private IntMap<CharSequence> getShortOwnerPins() {
		if (shortOwnerPins == null) {
			shortOwnerPins = new IntMap<CharSequence>(PIN_MAP_INITIAL_CAPACITY);
		}
		return shortOwnerPins;
	}

	private IntMap<CharSequence> getIntOwnerPins() {
		if (intOwnerPins == null) {
			intOwnerPins = new IntMap<CharSequence>(PIN_MAP_INITIAL_CAPACITY);
		}
		return intOwnerPins;
	}

	private LongMap<CharSequence> getLongOwnerPins() {
		if (longOwnerPins == null) {
			longOwnerPins = new LongMap<CharSequence>(PIN_MAP_INITIAL_CAPACITY);
		}
		return longOwnerPins;
	}

	private IntMap<CharSequence> getFloatOwnerPins() {
		if (floatOwnerPins == null) {
			floatOwnerPins = new IntMap<CharSequence>(PIN_MAP_INITIAL_CAPACITY);
		}
		return floatOwnerPins;
	}

	private LongMap<CharSequence> getDoubleOwnerPins() {
		if (doubleOwnerPins == null) {
			doubleOwnerPins = new LongMap<CharSequence>(PIN_MAP_INITIAL_CAPACITY);
		}
		return doubleOwnerPins;
	}

	private CharSequence getNodeAccountFromPool(CharSequence nodeAccount) {
		ensureNodeAccountNotNull(nodeAccount);
		return getFromPool(nodeAccount);
	}

	private static void ensurePinnableVariableKeyLength(int length) {
		if (length > MAX_CACHED_VARIABLE_KEY_LENGTH) {
			throw new IllegalArgumentException("The key length cannot exceed "
					+ MAX_CACHED_VARIABLE_KEY_LENGTH + ": " + length);
		}
	}

	private static void ensureNodeAccountNotNull(CharSequence nodeAccount) {
		if (nodeAccount == null) {
			throw new IllegalArgumentException("The nodeAccount argument cannot be null!");
		}
	}

	private static void ensureKeyNotNull(Object key) {
		if (key == null) {
			throw new IllegalArgumentException("The key argument cannot be null!");
		}
	}

	private static boolean contentEquals(CharSequence a, CharSequence b) {

	    if (a == b) return true;

	    if (a == null || b == null) return false;

	    int len = a.length();

	    if (len != b.length()) return false;

	    for (int i = 0; i < len; i++) {
	        if (a.charAt(i) != b.charAt(i)) return false;
	    }

		return true;
	}

	private static final class CharArrayView implements CharSequence {

		private char[] chars;

		void wrap(char[] chars) {
			this.chars = chars;
		}

		@Override
		public int length() {
			return chars.length;
		}

		@Override
		public char charAt(int index) {
			return chars[index];
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			throw new UnsupportedOperationException();
		}
	}
}
