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
import java.util.ArrayList;
import java.util.List;

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
 */
public class Balancer {

	private static final int DEFAULT_MAX_NUMBER_OF_NODES = 256;
	private static final int DEFAULT_MAX_NODE_ACCOUNT_LENGTH = 64;
	private static final int DEFAULT_CACHE_INITIAL_CAPACITY = 1024 * 8;
	private static final short MAX_CACHED_VARIABLE_KEY_LENGTH = 128;

	private final List<CharSequence> nodes;
	private final ObjectPool<StringBuilder> sbPool;
	private final String myNodeAccount;
	
	private final int initialCacheCapacity;
	private final short maxCachedVariableKeyLength;
	private final CharArrayView charArrayView;
	
	private CharSequenceMap<CharSequence> charSequenceOwnerCache = null;
	private CharSequenceMap<CharSequence> charArrayOwnerCache = null;
	private ByteBufferMap<CharSequence> byteSequenceOwnerCache = null;
	private ByteMap<CharSequence> booleanOwnerCache = null;
	private ByteMap<CharSequence> byteOwnerCache = null;
	private IntMap<CharSequence> charOwnerCache = null;
	private IntMap<CharSequence> shortOwnerCache = null;
	private IntMap<CharSequence> intOwnerCache = null;
	private LongMap<CharSequence> longOwnerCache = null;
	private IntMap<CharSequence> floatOwnerCache = null;
	private LongMap<CharSequence> doubleOwnerCache = null;

	private CharSequenceMap<CharSequence> charSequenceOwnerPins = null;
	private CharSequenceMap<CharSequence> charArrayOwnerPins = null;
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
	 * @param myNodeAccount the account of the local node
	 */
	public Balancer(CharSequence myNodeAccount) {
		this(myNodeAccount, DEFAULT_MAX_NUMBER_OF_NODES);
	}
	
	/**
	 * Creates a balancer for the given local node account.
	 *
	 * @param myNodeAccount the account of the local node
	 * @param maxNumberOfNodes the maximum number of active nodes
	 */
	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes) {
		this(myNodeAccount, maxNumberOfNodes, DEFAULT_MAX_NODE_ACCOUNT_LENGTH);
	}

	/**
	 * Creates a balancer for the given local node account.
	 *
	 * @param myNodeAccount the account of the local node
	 * @param maxNumberOfNodes the maximum number of active nodes
	 * @param maxNodeAccountLength the maximum expected node account length
	 */
	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength) {
		this(myNodeAccount, maxNumberOfNodes, maxNodeAccountLength, MAX_CACHED_VARIABLE_KEY_LENGTH);
	}

	/**
	 * Creates a balancer for the given local node account.
	 *
	 * @param myNodeAccount the account of the local node
	 * @param maxNumberOfNodes the maximum number of active nodes
	 * @param maxNodeAccountLength the maximum expected node account length
	 * @param maxCachedVariableKeyLength the maximum variable-length key size to cache or pin
	 */
	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength, short maxCachedVariableKeyLength) {
		this(myNodeAccount, maxNumberOfNodes, maxNodeAccountLength, maxCachedVariableKeyLength, DEFAULT_CACHE_INITIAL_CAPACITY);
	}
	
	/**
	 * Creates a balancer for the given local node account.
	 *
	 * @param myNodeAccount the account of the local node
	 * @param maxNumberOfNodes the maximum number of active nodes
	 * @param maxNodeAccountLength the maximum expected node account length
	 * @param maxCachedVariableKeyLength the maximum variable-length key size to cache or pin
	 * @param initialCacheCapacity the initial capacity for owner caches and pin maps
	 */
	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength, short maxCachedVariableKeyLength, int initialCacheCapacity) {
		this.nodes = new ArrayList<CharSequence>(maxNumberOfNodes);
		ObjectBuilder<StringBuilder> builder = new ObjectBuilder<StringBuilder>() {
			@Override
			public StringBuilder newInstance() {
				return new StringBuilder(maxNodeAccountLength);
			}
		};

		int preloadCount = Math.max(maxNumberOfNodes / 2, 1);

		this.sbPool = new LinkedObjectPool<StringBuilder>(maxNumberOfNodes, preloadCount, builder);

		this.myNodeAccount = myNodeAccount.toString();
		
		this.initialCacheCapacity = initialCacheCapacity;
		this.maxCachedVariableKeyLength = maxCachedVariableKeyLength;
		
		this.charArrayView = new CharArrayView();
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
		return nodes.size();
	}

	/**
	 * Adds a node account to the active node list.
	 *
	 * <p>Owner caches are cleared when a node is added.</p>
	 *
	 * @param nodeAccount the node account to add
	 * @return {@code true} if the node was added; {@code false} if it was already present
	 */
	public boolean addNode(CharSequence nodeAccount) {
		if (!contains(nodeAccount)) {
			nodes.add(getFromPool(nodeAccount));
			clearCaches();
			return true;
		}
		return false;
	}

	/**
	 * Removes a node account from the active node list.
	 *
	 * <p>Owner caches are cleared when a node is removed.</p>
	 *
	 * @param nodeAccount the node account to remove
	 * @return {@code true} if the node was removed; {@code false} if it was not present
	 */
	public boolean removeNode(CharSequence nodeAccount) {
		int index = indexOf(nodeAccount);
		if (index >= 0) {
			sbPool.release((StringBuilder) nodes.remove(index));
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
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(CharSequence key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		if (!canPinVariableKey(key.length())) return false;
		CharSequence oldNodeAccount = getCharSequenceOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a byte array key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(byte[] key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		if (!canPinVariableKey(key.length)) return false;
		CharSequence oldNodeAccount = getByteSequenceOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a char array key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(char[] key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		if (!canPinVariableKey(key.length)) return false;
		charArrayView.wrap(key);
		CharSequence oldNodeAccount = getCharArrayOwnerPins().put(charArrayView, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a {@link ByteBuffer} key to a node account.
	 *
	 * @param key the key to pin, using bytes from position to limit
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(ByteBuffer key, CharSequence nodeAccount) {
		ensureKeyNotNull(key);
		if (!canPinVariableKey(key.remaining())) return false;
		CharSequence oldNodeAccount = getByteSequenceOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a boolean key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(boolean key, CharSequence nodeAccount) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		CharSequence oldNodeAccount = getBooleanOwnerPins().put(cacheKey, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a byte key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(byte key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getByteOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a char key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(char key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getCharOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a short key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(short key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getShortOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins an int key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(int key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getIntOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a long key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(long key, CharSequence nodeAccount) {
		CharSequence oldNodeAccount = getLongOwnerPins().put(key, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a float key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(float key, CharSequence nodeAccount) {
		int cacheKey = Float.floatToIntBits(key);
		CharSequence oldNodeAccount = getFloatOwnerPins().put(cacheKey, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Pins a double key to a node account.
	 *
	 * @param key the key to pin
	 * @param nodeAccount the node account that should own the key
	 * @return {@code true} if the pin succeeds; {@code false} otherwise
	 */
	public boolean pin(double key, CharSequence nodeAccount) {
		long cacheKey = Double.doubleToLongBits(key);
		CharSequence oldNodeAccount = getDoubleOwnerPins().put(cacheKey, getNodeAccountFromPool(nodeAccount));
		if (oldNodeAccount != null) sbPool.release((StringBuilder) oldNodeAccount);
		return true;
	}

	/**
	 * Removes a pin for a {@link CharSequence} key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(CharSequence key) {
		ensureKeyNotNull(key);
		if (key.length() > maxCachedVariableKeyLength) return;
		CharSequence oldNodeAccount = charSequenceOwnerPins == null ? null : charSequenceOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a byte array key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(byte[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return;
		CharSequence oldNodeAccount = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a char array key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(char[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return;
		if (charArrayOwnerPins == null) return;
		charArrayView.wrap(key);
		CharSequence oldNodeAccount = charArrayOwnerPins.remove(charArrayView);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a {@link ByteBuffer} key if present.
	 *
	 * @param key the key to unpin, using bytes from position to limit
	 */
	public void unpin(ByteBuffer key) {
		ensureKeyNotNull(key);
		if (key.remaining() > maxCachedVariableKeyLength) return;
		CharSequence oldNodeAccount = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a boolean key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(boolean key) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		CharSequence oldNodeAccount = booleanOwnerPins == null ? null : booleanOwnerPins.remove(cacheKey);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a byte key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(byte key) {
		CharSequence oldNodeAccount = byteOwnerPins == null ? null : byteOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a char key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(char key) {
		CharSequence oldNodeAccount = charOwnerPins == null ? null : charOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a short key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(short key) {
		CharSequence oldNodeAccount = shortOwnerPins == null ? null : shortOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for an int key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(int key) {
		CharSequence oldNodeAccount = intOwnerPins == null ? null : intOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a long key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(long key) {
		CharSequence oldNodeAccount = longOwnerPins == null ? null : longOwnerPins.remove(key);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a float key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(float key) {
		int cacheKey = Float.floatToIntBits(key);
		CharSequence oldNodeAccount = floatOwnerPins == null ? null : floatOwnerPins.remove(cacheKey);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Removes a pin for a double key if present.
	 *
	 * @param key the key to unpin
	 */
	public void unpin(double key) {
		long cacheKey = Double.doubleToLongBits(key);
		CharSequence oldNodeAccount = doubleOwnerPins == null ? null : doubleOwnerPins.remove(cacheKey);
		if (oldNodeAccount == null) return;
		sbPool.release((StringBuilder) oldNodeAccount);
	}

	/**
	 * Returns the owner node for a {@link CharSequence} key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(CharSequence key) {
		ensureKeyNotNull(key);
		if (key.length() > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		CharSequence owner = charSequenceOwnerPins == null ? null : charSequenceOwnerPins.get(key);
		if (owner != null) return owner;

		CharSequenceMap<CharSequence> cache = getCharSequenceOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a byte array key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(byte[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		CharSequence owner = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.get(key);
		if (owner != null) return owner;

		ByteBufferMap<CharSequence> cache = getByteSequenceOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a char array key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(char[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		charArrayView.wrap(key);

		CharSequence owner = charArrayOwnerPins == null ? null : charArrayOwnerPins.get(charArrayView);
		if (owner != null) return owner;

		CharSequenceMap<CharSequence> cache = getCharArrayOwnerCache();
		owner = cache.get(charArrayView);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(charArrayView, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a {@link ByteBuffer} key.
	 *
	 * @param key the key to balance, using bytes from position to limit
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(ByteBuffer key) {
		ensureKeyNotNull(key);
		if (key.remaining() > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		CharSequence owner = byteSequenceOwnerPins == null ? null : byteSequenceOwnerPins.get(key);
		if (owner != null) return owner;

		ByteBufferMap<CharSequence> cache = getByteSequenceOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a boolean key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(boolean key) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		CharSequence owner = booleanOwnerPins == null ? null : booleanOwnerPins.get(cacheKey);
		if (owner != null) return owner;

		ByteMap<CharSequence> cache = getBooleanOwnerCache();
		owner = cache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(cacheKey, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a byte key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(byte key) {
		CharSequence owner = byteOwnerPins == null ? null : byteOwnerPins.get(key);
		if (owner != null) return owner;

		ByteMap<CharSequence> cache = getByteOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a char key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(char key) {
		CharSequence owner = charOwnerPins == null ? null : charOwnerPins.get(key);
		if (owner != null) return owner;

		IntMap<CharSequence> cache = getCharOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a short key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(short key) {
		CharSequence owner = shortOwnerPins == null ? null : shortOwnerPins.get(key);
		if (owner != null) return owner;

		IntMap<CharSequence> cache = getShortOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for an int key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(int key) {
		CharSequence owner = intOwnerPins == null ? null : intOwnerPins.get(key);
		if (owner != null) return owner;

		IntMap<CharSequence> cache = getIntOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a long key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(long key) {
		CharSequence owner = longOwnerPins == null ? null : longOwnerPins.get(key);
		if (owner != null) return owner;

		LongMap<CharSequence> cache = getLongOwnerCache();
		owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a float key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(float key) {
		int cacheKey = Float.floatToIntBits(key);
		CharSequence owner = floatOwnerPins == null ? null : floatOwnerPins.get(cacheKey);
		if (owner != null) return owner;

		IntMap<CharSequence> cache = getFloatOwnerCache();
		owner = cache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(cacheKey, owner);
		return owner;
	}

	/**
	 * Returns the owner node for a double key.
	 *
	 * @param key the key to balance
	 * @return the node account that owns the key
	 */
	public CharSequence ownerFor(double key) {
		long cacheKey = Double.doubleToLongBits(key);
		CharSequence owner = doubleOwnerPins == null ? null : doubleOwnerPins.get(cacheKey);
		if (owner != null) return owner;

		LongMap<CharSequence> cache = getDoubleOwnerCache();
		owner = cache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(cacheKey, owner);
		return owner;
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
		for(int i = nodes.size() - 1; i >= 0; i--) {
			CharSequence cs = nodes.get(i);
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

	private void clearCaches() {
		if (charSequenceOwnerCache != null) charSequenceOwnerCache.clear();
		if (charArrayOwnerCache != null) charArrayOwnerCache.clear();
		if (byteSequenceOwnerCache != null) byteSequenceOwnerCache.clear();
		if (booleanOwnerCache != null) booleanOwnerCache.clear();
		if (byteOwnerCache != null) byteOwnerCache.clear();
		if (charOwnerCache != null) charOwnerCache.clear();
		if (shortOwnerCache != null) shortOwnerCache.clear();
		if (intOwnerCache != null) intOwnerCache.clear();
		if (longOwnerCache != null) longOwnerCache.clear();
		if (floatOwnerCache != null) floatOwnerCache.clear();
		if (doubleOwnerCache != null) doubleOwnerCache.clear();
	}

	private CharSequenceMap<CharSequence> getCharSequenceOwnerCache() {
		if (charSequenceOwnerCache == null) {
			charSequenceOwnerCache = new CharSequenceMap<CharSequence>(initialCacheCapacity, maxCachedVariableKeyLength);
		}
		return charSequenceOwnerCache;
	}

	private CharSequenceMap<CharSequence> getCharArrayOwnerCache() {
		if (charArrayOwnerCache == null) {
			charArrayOwnerCache = new CharSequenceMap<CharSequence>(initialCacheCapacity, maxCachedVariableKeyLength);
		}
		return charArrayOwnerCache;
	}

	private ByteBufferMap<CharSequence> getByteSequenceOwnerCache() {
		if (byteSequenceOwnerCache == null) {
			byteSequenceOwnerCache = new ByteBufferMap<CharSequence>(initialCacheCapacity, maxCachedVariableKeyLength);
		}
		return byteSequenceOwnerCache;
	}

	private ByteMap<CharSequence> getBooleanOwnerCache() {
		if (booleanOwnerCache == null) {
			booleanOwnerCache = new ByteMap<CharSequence>();
		}
		return booleanOwnerCache;
	}

	private ByteMap<CharSequence> getByteOwnerCache() {
		if (byteOwnerCache == null) {
			byteOwnerCache = new ByteMap<CharSequence>();
		}
		return byteOwnerCache;
	}

	private IntMap<CharSequence> getCharOwnerCache() {
		if (charOwnerCache == null) {
			charOwnerCache = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return charOwnerCache;
	}

	private IntMap<CharSequence> getShortOwnerCache() {
		if (shortOwnerCache == null) {
			shortOwnerCache = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return shortOwnerCache;
	}

	private IntMap<CharSequence> getIntOwnerCache() {
		if (intOwnerCache == null) {
			intOwnerCache = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return intOwnerCache;
	}

	private LongMap<CharSequence> getLongOwnerCache() {
		if (longOwnerCache == null) {
			longOwnerCache = new LongMap<CharSequence>(initialCacheCapacity);
		}
		return longOwnerCache;
	}

	private IntMap<CharSequence> getFloatOwnerCache() {
		if (floatOwnerCache == null) {
			floatOwnerCache = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return floatOwnerCache;
	}

	private LongMap<CharSequence> getDoubleOwnerCache() {
		if (doubleOwnerCache == null) {
			doubleOwnerCache = new LongMap<CharSequence>(initialCacheCapacity);
		}
		return doubleOwnerCache;
	}

	private CharSequenceMap<CharSequence> getCharSequenceOwnerPins() {
		if (charSequenceOwnerPins == null) {
			charSequenceOwnerPins = new CharSequenceMap<CharSequence>(initialCacheCapacity, maxCachedVariableKeyLength);
		}
		return charSequenceOwnerPins;
	}

	private CharSequenceMap<CharSequence> getCharArrayOwnerPins() {
		if (charArrayOwnerPins == null) {
			charArrayOwnerPins = new CharSequenceMap<CharSequence>(initialCacheCapacity, maxCachedVariableKeyLength);
		}
		return charArrayOwnerPins;
	}

	private ByteBufferMap<CharSequence> getByteSequenceOwnerPins() {
		if (byteSequenceOwnerPins == null) {
			byteSequenceOwnerPins = new ByteBufferMap<CharSequence>(initialCacheCapacity, maxCachedVariableKeyLength);
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
			charOwnerPins = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return charOwnerPins;
	}

	private IntMap<CharSequence> getShortOwnerPins() {
		if (shortOwnerPins == null) {
			shortOwnerPins = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return shortOwnerPins;
	}

	private IntMap<CharSequence> getIntOwnerPins() {
		if (intOwnerPins == null) {
			intOwnerPins = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return intOwnerPins;
	}

	private LongMap<CharSequence> getLongOwnerPins() {
		if (longOwnerPins == null) {
			longOwnerPins = new LongMap<CharSequence>(initialCacheCapacity);
		}
		return longOwnerPins;
	}

	private IntMap<CharSequence> getFloatOwnerPins() {
		if (floatOwnerPins == null) {
			floatOwnerPins = new IntMap<CharSequence>(initialCacheCapacity);
		}
		return floatOwnerPins;
	}

	private LongMap<CharSequence> getDoubleOwnerPins() {
		if (doubleOwnerPins == null) {
			doubleOwnerPins = new LongMap<CharSequence>(initialCacheCapacity);
		}
		return doubleOwnerPins;
	}

	private CharSequence getNodeAccountFromPool(CharSequence nodeAccount) {
		if (nodeAccount == null) {
			throw new IllegalArgumentException("The nodeAccount argument cannot be null!");
		}
		return getFromPool(nodeAccount);
	}

	private boolean canPinVariableKey(int len) {
		return len <= maxCachedVariableKeyLength;
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
