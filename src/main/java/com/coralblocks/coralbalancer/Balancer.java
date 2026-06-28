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
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectBuilder;
import com.coralblocks.coralpool.ObjectPool;

public class Balancer {

	private static final int DEFAULT_MAX_NUMBER_OF_NODES = 256;
	private static final int DEFAULT_MAX_NODE_ACCOUNT_LENGTH = 64;
	private static final int DEFAULT_CACHE_INITIAL_CAPACITY = 1024;
	private static final short MAX_CACHED_VARIABLE_KEY_LENGTH = 64;

	private final List<CharSequence> nodes;
	private final ObjectPool<StringBuilder> sbPool;
	private final String myNodeAccount;
	
	private final int maxCachedVariableKeyLength;
	
	private final CharSequenceMap<CharSequence> charSequenceOwnerCache;
	private final CharSequenceMap<CharSequence> charArrayOwnerCache;
	private final ByteBufferMap<CharSequence> byteSequenceOwnerCache;
	private final ByteMap<CharSequence> booleanOwnerCache;
	private final ByteMap<CharSequence> byteOwnerCache;
	private final IntMap<CharSequence> charOwnerCache;
	private final IntMap<CharSequence> shortOwnerCache;
	private final IntMap<CharSequence> intOwnerCache;
	private final LongMap<CharSequence> longOwnerCache;
	private final IntMap<CharSequence> floatOwnerCache;
	private final LongMap<CharSequence> doubleOwnerCache;
	private final CharArrayView charArrayView;

	public Balancer(CharSequence myNodeAccount) {
		this(myNodeAccount, DEFAULT_MAX_NUMBER_OF_NODES, DEFAULT_MAX_NODE_ACCOUNT_LENGTH);
	}

	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength) {
		this(myNodeAccount, maxNumberOfNodes, maxNodeAccountLength, MAX_CACHED_VARIABLE_KEY_LENGTH);
	}

	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength, int maxCachedVariableKeyLength) {
		this.nodes = new ArrayList<CharSequence>(maxNumberOfNodes);
		ObjectBuilder<StringBuilder> builder = new ObjectBuilder<StringBuilder>() {
			@Override
			public StringBuilder newInstance() {
				return new StringBuilder(maxNodeAccountLength);
			}
		};

		int preloadCount = Math.max(maxNumberOfNodes / 2, 1);

		this.sbPool = new ArrayObjectPool<StringBuilder>(maxNumberOfNodes, preloadCount, builder);

		this.myNodeAccount = myNodeAccount.toString();
		this.maxCachedVariableKeyLength = maxCachedVariableKeyLength;
		this.charSequenceOwnerCache = new CharSequenceMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY, this.maxCachedVariableKeyLength);
		this.charArrayOwnerCache = new CharSequenceMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY, this.maxCachedVariableKeyLength);
		this.byteSequenceOwnerCache = new ByteBufferMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY, this.maxCachedVariableKeyLength);
		this.booleanOwnerCache = new ByteMap<CharSequence>();
		this.byteOwnerCache = new ByteMap<CharSequence>();
		this.charOwnerCache = new IntMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY);
		this.shortOwnerCache = new IntMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY);
		this.intOwnerCache = new IntMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY);
		this.longOwnerCache = new LongMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY);
		this.floatOwnerCache = new IntMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY);
		this.doubleOwnerCache = new LongMap<CharSequence>(DEFAULT_CACHE_INITIAL_CAPACITY);
		this.charArrayView = new CharArrayView();
	}

	public String getMyNodeAccount() {
		return myNodeAccount;
	}

	public int getNumberOfNodes() {
		return nodes.size();
	}

	public boolean addNode(CharSequence nodeAccount) {
		if (!contains(nodeAccount)) {
			nodes.add(getFromPool(nodeAccount));
			clearCaches();
			return true;
		}
		return false;
	}

	public boolean removeNode(CharSequence nodeAccount) {
		int index = indexOf(nodeAccount);
		if (index >= 0) {
			sbPool.release((StringBuilder) nodes.remove(index));
			clearCaches();
			return true;
		}
		return false;
	}

	public boolean hasNode(CharSequence nodeAccount) {
		return contains(nodeAccount);
	}

	public CharSequence ownerFor(CharSequence key) {
		ensureKeyNotNull(key);
		if (key.length() > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		CharSequence owner = charSequenceOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		charSequenceOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(byte[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		CharSequence owner = byteSequenceOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		byteSequenceOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(char[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		charArrayView.wrap(key);

		CharSequence owner = charArrayOwnerCache.get(charArrayView);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		charArrayOwnerCache.put(charArrayView, owner);
		return owner;
	}

	public CharSequence ownerFor(ByteBuffer key) {
		ensureKeyNotNull(key);
		if (key.remaining() > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		CharSequence owner = byteSequenceOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		byteSequenceOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(boolean key) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		CharSequence owner = booleanOwnerCache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		booleanOwnerCache.put(cacheKey, owner);
		return owner;
	}

	public CharSequence ownerFor(byte key) {
		CharSequence owner = byteOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		byteOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(char key) {
		CharSequence owner = charOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		charOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(short key) {
		CharSequence owner = shortOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		shortOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(int key) {
		CharSequence owner = intOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		intOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(long key) {
		CharSequence owner = longOwnerCache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		longOwnerCache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(float key) {
		int cacheKey = Float.floatToIntBits(key);
		CharSequence owner = floatOwnerCache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		floatOwnerCache.put(cacheKey, owner);
		return owner;
	}

	public CharSequence ownerFor(double key) {
		long cacheKey = Double.doubleToLongBits(key);
		CharSequence owner = doubleOwnerCache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		doubleOwnerCache.put(cacheKey, owner);
		return owner;
	}

	public boolean isForMe(CharSequence key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(byte[] key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(char[] key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(ByteBuffer key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(boolean key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(byte key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(char key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(short key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(int key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(long key) {
		return isOwnerForMe(ownerFor(key));
	}

	public boolean isForMe(float key) {
		return isOwnerForMe(ownerFor(key));
	}

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
		charSequenceOwnerCache.clear();
		charArrayOwnerCache.clear();
		byteSequenceOwnerCache.clear();
		booleanOwnerCache.clear();
		byteOwnerCache.clear();
		charOwnerCache.clear();
		shortOwnerCache.clear();
		intOwnerCache.clear();
		longOwnerCache.clear();
		floatOwnerCache.clear();
		doubleOwnerCache.clear();
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
