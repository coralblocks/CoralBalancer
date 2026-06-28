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
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class RendezvousHashingTest {
	
	@Test
	public void testOwnerForReturnsActiveNode() {
		
		CharSequence key = new StringBuilder("KEY1");
		CharSequence node1 = new StringBuilder("NODE1");
		CharSequence node2 = new StringBuilder("NODE2");
		List<CharSequence> activeNodes = Arrays.asList(node1, node2);
		
		CharSequence owner = RendezvousHashing.ownerFor(key, activeNodes);
		
		Assert.assertTrue(owner == node1 || owner == node2);
		Assert.assertSame(owner, RendezvousHashing.ownerFor(key, activeNodes));
	}
	
	@Test
	public void testOwnerForHashesNodeContent() {
		
		List<CharSequence> activeNodes = Arrays.asList(
				new StringBuilder("NODE1"),
				new StringBuilder("NODE2"),
				new StringBuilder("NODE3"));
		
		List<CharSequence> equivalentActiveNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		
		CharSequence owner = RendezvousHashing.ownerFor("KEY1", activeNodes);
		CharSequence equivalentOwner = RendezvousHashing.ownerFor("KEY1", equivalentActiveNodes);
		
		Assert.assertEquals(owner.toString(), equivalentOwner.toString());
	}

	@Test
	public void testOwnerForHashesKeyContent() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");

		CharSequence owner = RendezvousHashing.ownerFor(new StringBuilder("KEY1"), activeNodes);
		CharSequence equivalentOwner = RendezvousHashing.ownerFor("KEY1", activeNodes);

		Assert.assertSame(owner, equivalentOwner);
	}

	@Test
	public void testOwnerForByteArrayKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		byte[] key = new byte[] { 1, 2, 3, 4 };

		CharSequence owner = RendezvousHashing.ownerFor(key, activeNodes);

		Assert.assertTrue(activeNodes.contains(owner));
		Assert.assertSame(owner, RendezvousHashing.ownerFor(key, activeNodes));
	}

	@Test
	public void testOwnerForByteBufferKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		ByteBuffer key = ByteBuffer.wrap(new byte[] { 9, 1, 2, 3, 4, 9 });
		key.position(1);
		key.limit(5);

		CharSequence owner = RendezvousHashing.ownerFor(key, activeNodes);
		CharSequence equivalentOwner = RendezvousHashing.ownerFor(new byte[] { 1, 2, 3, 4 }, activeNodes);

		Assert.assertSame(owner, equivalentOwner);
		Assert.assertEquals(1, key.position());
		Assert.assertEquals(5, key.limit());
	}

	@Test
	public void testOwnerForLongKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		long key = 123456789L;

		CharSequence owner = RendezvousHashing.ownerFor(key, activeNodes);

		Assert.assertTrue(activeNodes.contains(owner));
		Assert.assertSame(owner, RendezvousHashing.ownerFor(key, activeNodes));
	}
}
