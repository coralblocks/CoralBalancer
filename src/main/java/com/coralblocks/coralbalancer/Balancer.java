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
	
	public Balancer(CharSequence myNodeAccount) {
		this(myNodeAccount, DEFAULT_MAX_NUMBER_OF_NODES);
	}
	
	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes) {
		this(myNodeAccount, maxNumberOfNodes, DEFAULT_MAX_NODE_ACCOUNT_LENGTH);
	}

	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength) {
		this(myNodeAccount, maxNumberOfNodes, maxNodeAccountLength, MAX_CACHED_VARIABLE_KEY_LENGTH);
	}

	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength, short maxCachedVariableKeyLength) {
		this(myNodeAccount, maxNumberOfNodes, maxNodeAccountLength, maxCachedVariableKeyLength, DEFAULT_CACHE_INITIAL_CAPACITY);
	}
	
	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength, short maxCachedVariableKeyLength, int initialCacheCapacity) {
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
		
		this.initialCacheCapacity = initialCacheCapacity;
		this.maxCachedVariableKeyLength = maxCachedVariableKeyLength;
		
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

		CharSequenceMap<CharSequence> cache = getCharSequenceOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(byte[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		ByteBufferMap<CharSequence> cache = getByteSequenceOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(char[] key) {
		ensureKeyNotNull(key);
		if (key.length > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		charArrayView.wrap(key);

		CharSequenceMap<CharSequence> cache = getCharArrayOwnerCache();
		CharSequence owner = cache.get(charArrayView);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(charArrayView, owner);
		return owner;
	}

	public CharSequence ownerFor(ByteBuffer key) {
		ensureKeyNotNull(key);
		if (key.remaining() > maxCachedVariableKeyLength) return RendezvousHashing.ownerFor(key, nodes);

		ByteBufferMap<CharSequence> cache = getByteSequenceOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(boolean key) {
		byte cacheKey = key ? (byte) 1 : (byte) 0;
		ByteMap<CharSequence> cache = getBooleanOwnerCache();
		CharSequence owner = cache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(cacheKey, owner);
		return owner;
	}

	public CharSequence ownerFor(byte key) {
		ByteMap<CharSequence> cache = getByteOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(char key) {
		IntMap<CharSequence> cache = getCharOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(short key) {
		IntMap<CharSequence> cache = getShortOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(int key) {
		IntMap<CharSequence> cache = getIntOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(long key) {
		LongMap<CharSequence> cache = getLongOwnerCache();
		CharSequence owner = cache.get(key);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(key, owner);
		return owner;
	}

	public CharSequence ownerFor(float key) {
		int cacheKey = Float.floatToIntBits(key);
		IntMap<CharSequence> cache = getFloatOwnerCache();
		CharSequence owner = cache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(cacheKey, owner);
		return owner;
	}

	public CharSequence ownerFor(double key) {
		long cacheKey = Double.doubleToLongBits(key);
		LongMap<CharSequence> cache = getDoubleOwnerCache();
		CharSequence owner = cache.get(cacheKey);
		if (owner != null) return owner;

		owner = RendezvousHashing.ownerFor(key, nodes);
		cache.put(cacheKey, owner);
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
