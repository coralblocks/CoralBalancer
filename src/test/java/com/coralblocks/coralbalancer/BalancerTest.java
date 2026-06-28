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


public class BalancerTest {

	@Test
	public void testAddRemove() {

		Balancer b = new Balancer("NODE1");

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

	@Test
	public void testIsForMe() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE2", 64, 6);

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		CharSequence charSequenceKey = new StringBuilder("KEY1");
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(charSequenceKey, activeNodes), b),
				b.isForMe(charSequenceKey));

		byte[] byteArrayKey = new byte[] { 1, 2, 3, 4 };
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(byteArrayKey, activeNodes), b),
				b.isForMe(byteArrayKey));

		char[] charArrayKey = new char[] { 'K', 'E', 'Y', '1' };
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(charArrayKey, activeNodes), b),
				b.isForMe(charArrayKey));

		ByteBuffer byteBufferKey = ByteBuffer.wrap(new byte[] { 9, 1, 2, 3, 4, 9 });
		byteBufferKey.position(1);
		byteBufferKey.limit(5);
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(byteBufferKey, activeNodes), b),
				b.isForMe(byteBufferKey));
		Assert.assertEquals(1, byteBufferKey.position());
		Assert.assertEquals(5, byteBufferKey.limit());

		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(true, activeNodes), b),
				b.isForMe(true));
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor((byte) 7, activeNodes), b),
				b.isForMe((byte) 7));
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor('A', activeNodes), b),
				b.isForMe('A'));
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor((short) 123, activeNodes), b),
				b.isForMe((short) 123));
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(456, activeNodes), b),
				b.isForMe(456));
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(123456789L, activeNodes), b),
				b.isForMe(123456789L));
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(123.25f, activeNodes), b),
				b.isForMe(123.25f));
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(456.75d, activeNodes), b),
				b.isForMe(456.75d));

		long keyForMe = keyFor(b.getMyNodeAccount(), activeNodes);
		long keyForOtherNode = keyFor("NODE1", activeNodes);

		Assert.assertTrue(b.isForMe(keyForMe));
		Assert.assertFalse(b.isForMe(keyForOtherNode));
	}

	@Test
	public void testOwnerForClearsCacheWhenNodesChange() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2");
		Balancer b = new Balancer("NODE1", 64, 6);

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		long keyForNode1 = keyFor("NODE1", activeNodes);

		Assert.assertEquals("NODE1", b.ownerFor(keyForNode1).toString());
		Assert.assertEquals("NODE1", b.ownerFor(keyForNode1).toString());

		Assert.assertTrue(b.removeNode("NODE1"));

		Assert.assertEquals("NODE2", b.ownerFor(keyForNode1).toString());
		Assert.assertFalse(b.isForMe(keyForNode1));
	}

	@Test
	public void testOwnerForDistinguishesCacheHashCollisions() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE1", 64, 6);
		CharSequence key1 = "Aa";
		CharSequence key2 = "BB";

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		Assert.assertEquals(key1.hashCode(), key2.hashCode());
		Assert.assertNotEquals(RendezvousHashing.ownerFor(key1, activeNodes).toString(),
				RendezvousHashing.ownerFor(key2, activeNodes).toString());
		Assert.assertEquals(RendezvousHashing.ownerFor(key1, activeNodes).toString(), b.ownerFor(key1).toString());
		Assert.assertEquals(RendezvousHashing.ownerFor(key2, activeNodes).toString(), b.ownerFor(key2).toString());
	}

	@Test
	public void testLargeKeysAreAccepted() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE2", 64, 6);
		StringBuilder charSequenceKey = new StringBuilder(40_000);
		byte[] byteArrayKey = new byte[40_000];
		char[] charArrayKey = new char[40_000];

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		for (int i = 0; i < 40_000; i++) {
			byteArrayKey[i] = (byte) i;
			charArrayKey[i] = (char) ('A' + (i % 26));
			charSequenceKey.append(charArrayKey[i]);
		}

		ByteBuffer byteBufferKey = ByteBuffer.wrap(byteArrayKey);

		Assert.assertEquals(RendezvousHashing.ownerFor(charSequenceKey, activeNodes).toString(),
				b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals(RendezvousHashing.ownerFor(charSequenceKey, activeNodes).toString(),
				b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals(RendezvousHashing.ownerFor(byteArrayKey, activeNodes).toString(),
				b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals(RendezvousHashing.ownerFor(charArrayKey, activeNodes).toString(),
				b.ownerFor(charArrayKey).toString());
		Assert.assertEquals(RendezvousHashing.ownerFor(byteBufferKey, activeNodes).toString(),
				b.ownerFor(byteBufferKey).toString());
		Assert.assertEquals(isOwnerForMe(RendezvousHashing.ownerFor(charSequenceKey, activeNodes), b),
				b.isForMe(charSequenceKey));
	}

	private static boolean isOwnerForMe(CharSequence owner, Balancer b) {
		return b.getMyNodeAccount().contentEquals(owner);
	}

	private static long keyFor(CharSequence nodeAccount, List<CharSequence> activeNodes) {
		for (long key = 0; key < 10_000; key++) {
			CharSequence owner = RendezvousHashing.ownerFor(key, activeNodes);
			if (nodeAccount.toString().contentEquals(owner)) return key;
		}
		throw new IllegalStateException("Could not find key for node account: " + nodeAccount);
	}
}
