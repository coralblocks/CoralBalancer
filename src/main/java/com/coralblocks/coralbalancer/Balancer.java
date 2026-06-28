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

import java.util.ArrayList;
import java.util.List;

import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectBuilder;
import com.coralblocks.coralpool.ObjectPool;

public class Balancer {
	
	private final List<CharSequence> nodes;
	private final ObjectPool<StringBuilder> sbPool;
	private final String myNodeAccount;
	
	public Balancer(CharSequence myNodeAccount, int maxNumberOfNodes, int maxNodeAccountLength) {
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
			return true;
		}
		return false;
	}
	
	public boolean removeNode(CharSequence nodeAccount) {
		int index = indexOf(nodeAccount);
		if (index >= 0) {
			sbPool.release((StringBuilder) nodes.remove(index));
			return true;
		}
		return false;
	}
	
	public boolean hasNode(CharSequence nodeAccount) {
		return contains(nodeAccount);
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
}
