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

public class Balancer {
	
	private final List<String> nodes;
	
	public Balancer(int maxNumberOfNodes) {
		this.nodes = new ArrayList<String>(maxNumberOfNodes);
	}
	
	public int getNumberOfNodes() {
		return nodes.size();
	}
	
	public boolean addNode(CharSequence nodeAccount) {
		if (!contains(nodeAccount)) {
			nodes.add(nodeAccount.toString()); // if it is a String then toString() returns self/this
			return true;
		}
		return false;
	}
	
	public boolean removeNode(CharSequence nodeAccount) {
		int index = indexOf(nodeAccount);
		if (index >= 0) {
			nodes.remove(index);
			return true;
		}
		return false;
	}
	
	public boolean hasNode(CharSequence nodeAccount) {
		return contains(nodeAccount);
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

	    if (a == b) {
	        return true;
	    }

	    if (a == null || b == null) {
	        return false;
	    }

	    int len = a.length();

	    if (len != b.length()) {
	        return false;
	    }

	    for (int i = 0; i < len; i++) {
	        if (a.charAt(i) != b.charAt(i)) {
	            return false;
	        }
	    }

	    return true;
	}
}
