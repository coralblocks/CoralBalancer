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

import org.junit.Assert;
import org.junit.Test;


public class BalancerTest {
	
	@Test
	public void testAddRemove() {
		
		Balancer b = new Balancer(64);
		
		StringBuilder sb = new StringBuilder();
		sb.append("NODE1");
		
		Assert.assertEquals(false, b.hasNode(sb));
		Assert.assertEquals(false, b.hasNode("NODE1"));
		
		boolean addRes = b.addNode(sb);
		
		Assert.assertEquals(true, addRes);
		Assert.assertEquals(true, b.hasNode(sb));
		Assert.assertEquals(true, b.hasNode(new StringBuilder("NODE1")));
		Assert.assertEquals(false, b.hasNode(new StringBuilder("NODE2")));
		Assert.assertEquals(true, b.hasNode("NODE1"));
		Assert.assertEquals(false, b.hasNode("NODE2"));
		
		addRes = b.addNode(sb);
		Assert.assertEquals(false, addRes);
		
		addRes = b.addNode("NODE1");
		Assert.assertEquals(false, addRes);
		
		addRes = b.addNode("NODE2");
		Assert.assertEquals(true, addRes);
		
		Assert.assertEquals(true, b.hasNode(sb));
		Assert.assertEquals(true, b.hasNode(new StringBuilder("NODE1")));
		Assert.assertEquals(true, b.hasNode(new StringBuilder("NODE2")));
		Assert.assertEquals(true, b.hasNode("NODE1"));
		Assert.assertEquals(true, b.hasNode("NODE2"));
		
		boolean delRes = b.removeNode("NODE3");
		Assert.assertEquals(false, delRes);
		
		delRes = b.removeNode(new StringBuilder("NODE3"));
		Assert.assertEquals(false, delRes);
		
		delRes = b.removeNode("NODE1");
		
		Assert.assertEquals(true, delRes);
		Assert.assertEquals(false, b.hasNode(sb));
		Assert.assertEquals(false, b.hasNode(new StringBuilder("NODE1")));
		Assert.assertEquals(true, b.hasNode(new StringBuilder("NODE2")));
		Assert.assertEquals(false, b.hasNode("NODE1"));
		Assert.assertEquals(true, b.hasNode("NODE2"));
		
		sb.setLength(0);
		sb.append("NODE2");
		
		Assert.assertEquals(true, b.hasNode(sb));
		
		delRes = b.removeNode(sb);
		
		Assert.assertEquals(true, delRes);
		Assert.assertEquals(false, b.hasNode(sb));
		Assert.assertEquals(false, b.hasNode(new StringBuilder("NODE1")));
		Assert.assertEquals(false, b.hasNode(new StringBuilder("NODE2")));
		Assert.assertEquals(false, b.hasNode("NODE1"));
		Assert.assertEquals(false, b.hasNode("NODE2"));
	}
}