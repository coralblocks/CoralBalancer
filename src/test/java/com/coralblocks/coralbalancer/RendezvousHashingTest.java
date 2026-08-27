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
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class RendezvousHashingTest {
	
	@Test
	public void testOwnerForReturnsActiveNode() {
		
		CharSequence key = new StringBuilder("KEY1");
		CharSequence node1 = new StringBuilder("NODE1");
		CharSequence node2 = new StringBuilder("NODE2");
		List<CharSequence> activeNodes = Arrays.asList(node1, node2);
		
		CharSequence owner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);
		
		Assert.assertTrue(owner == node1 || owner == node2);
		Assert.assertSame(owner, RendezvousHashingTestSupport.ownerFor(key, activeNodes));
	}
	
	@Test
	public void testOwnerForHashesNodeContent() {
		
		List<CharSequence> activeNodes = Arrays.asList(
				new StringBuilder("NODE1"),
				new StringBuilder("NODE2"),
				new StringBuilder("NODE3"));
		
		List<CharSequence> equivalentActiveNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		
		CharSequence owner = RendezvousHashingTestSupport.ownerFor("KEY1", activeNodes);
		CharSequence equivalentOwner = RendezvousHashingTestSupport.ownerFor("KEY1", equivalentActiveNodes);
		
		Assert.assertEquals(owner.toString(), equivalentOwner.toString());
	}

	@Test
	public void testOwnerForUsesPrecomputedNodeHashes() {

		CountingCharSequence node1 = new CountingCharSequence("NODE1");
		CountingCharSequence node2 = new CountingCharSequence("NODE2");
		CountingCharSequence node3 = new CountingCharSequence("NODE3");
		CharSequence[] activeNodes = new CharSequence[] { node1, node2, node3 };
		long[] activeNodeHashes = new long[activeNodes.length];

		for (int i = 0; i < activeNodes.length; i++) {
			activeNodeHashes[i] = RendezvousHashing.hashNode(activeNodes[i]);
		}

		node1.resetCharAtCalls();
		node2.resetCharAtCalls();
		node3.resetCharAtCalls();

		CharSequence owner = RendezvousHashing.ownerForHash(
				RendezvousHashing.hashKey("KEY1"), activeNodes, activeNodeHashes, activeNodes.length);

		Assert.assertEquals(0, node1.getCharAtCalls());
		Assert.assertEquals(0, node2.getCharAtCalls());
		Assert.assertEquals(0, node3.getCharAtCalls());
		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(
				"KEY1", Arrays.<CharSequence>asList("NODE1", "NODE2", "NODE3")).toString(),
				owner.toString());
	}

	@Test
	public void testOwnerForHashesKeyContent() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");

		CharSequence owner = RendezvousHashingTestSupport.ownerFor(new StringBuilder("KEY1"), activeNodes);
		CharSequence equivalentOwner = RendezvousHashingTestSupport.ownerFor("KEY1", activeNodes);

		Assert.assertSame(owner, equivalentOwner);
	}

	@Test
	public void testOwnerForCharArrayKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");

		CharSequence owner = RendezvousHashingTestSupport.ownerFor(new char[] { 'K', 'E', 'Y', '1' }, activeNodes);
		CharSequence equivalentOwner = RendezvousHashingTestSupport.ownerFor("KEY1", activeNodes);

		Assert.assertSame(owner, equivalentOwner);
	}

	@Test
	public void testOwnerForByteArrayKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		byte[] key = new byte[] { 1, 2, 3, 4 };

		CharSequence owner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);

		Assert.assertTrue(activeNodes.contains(owner));
		Assert.assertSame(owner, RendezvousHashingTestSupport.ownerFor(key, activeNodes));
	}

	@Test
	public void testOwnerForByteBufferKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		ByteBuffer key = ByteBuffer.wrap(new byte[] { 9, 1, 2, 3, 4, 9 });
		key.position(1);
		key.limit(5);

		CharSequence owner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);
		CharSequence equivalentOwner = RendezvousHashingTestSupport.ownerFor(new byte[] { 1, 2, 3, 4 }, activeNodes);

		Assert.assertSame(owner, equivalentOwner);
		Assert.assertEquals(1, key.position());
		Assert.assertEquals(5, key.limit());
	}

	@Test
	public void testOwnerForLongKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		long key = 123456789L;

		CharSequence owner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);

		Assert.assertTrue(activeNodes.contains(owner));
		Assert.assertSame(owner, RendezvousHashingTestSupport.ownerFor(key, activeNodes));
	}

	@Test
	public void testOwnerForBooleanKey() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");

		CharSequence trueOwner = RendezvousHashingTestSupport.ownerFor(true, activeNodes);
		CharSequence falseOwner = RendezvousHashingTestSupport.ownerFor(false, activeNodes);

		Assert.assertTrue(activeNodes.contains(trueOwner));
		Assert.assertTrue(activeNodes.contains(falseOwner));
		Assert.assertSame(trueOwner, RendezvousHashingTestSupport.ownerFor(1L, activeNodes));
		Assert.assertSame(falseOwner, RendezvousHashingTestSupport.ownerFor(0L, activeNodes));
	}

	@Test
	public void testOwnerForIntegralPrimitiveKeys() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");

		CharSequence byteOwner = RendezvousHashingTestSupport.ownerFor((byte) 7, activeNodes);
		CharSequence charOwner = RendezvousHashingTestSupport.ownerFor('A', activeNodes);
		CharSequence shortOwner = RendezvousHashingTestSupport.ownerFor((short) 123, activeNodes);
		CharSequence intOwner = RendezvousHashingTestSupport.ownerFor(456, activeNodes);

		Assert.assertSame(byteOwner, RendezvousHashingTestSupport.ownerFor(7L, activeNodes));
		Assert.assertSame(charOwner, RendezvousHashingTestSupport.ownerFor(65L, activeNodes));
		Assert.assertSame(shortOwner, RendezvousHashingTestSupport.ownerFor(123L, activeNodes));
		Assert.assertSame(intOwner, RendezvousHashingTestSupport.ownerFor(456L, activeNodes));
	}

	@Test
	public void testOwnerForFloatingPointPrimitiveKeys() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3");
		float floatKey = 123.25f;
		double doubleKey = 456.75d;

		CharSequence floatOwner = RendezvousHashingTestSupport.ownerFor(floatKey, activeNodes);
		CharSequence doubleOwner = RendezvousHashingTestSupport.ownerFor(doubleKey, activeNodes);

		Assert.assertTrue(activeNodes.contains(floatOwner));
		Assert.assertTrue(activeNodes.contains(doubleOwner));
		Assert.assertSame(floatOwner, RendezvousHashingTestSupport.ownerFor(floatKey, activeNodes));
		Assert.assertSame(doubleOwner, RendezvousHashingTestSupport.ownerFor(doubleKey, activeNodes));
	}

	@Test
	public void testRandomKeysAreUniformlyDistributedAcrossNodes() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		int[] counts = new int[activeNodes.size()];
		Random random = new Random(123456789L);
		int sampleSize = 200_000;
		int expectedCount = sampleSize / activeNodes.size();
		int maxAllowedDeviation = expectedCount / 40;

		for (int i = 0; i < sampleSize; i++) {
			CharSequence owner = RendezvousHashingTestSupport.ownerFor(random.nextLong(), activeNodes);
			counts[activeNodes.indexOf(owner)]++;
		}

		System.out.println("Rendezvous hashing distribution for " + sampleSize + " random keys:");

		for (int i = 0; i < counts.length; i++) {
			double percentage = 100.0d * counts[i] / sampleSize;
			double deviation = percentage - 25.0d;
			System.out.printf("%s: %d keys, %.3f%%, deviation from 25%%: %+.3f%%%n",
					activeNodes.get(i), counts[i], percentage, deviation);
		}

		for (int i = 0; i < counts.length; i++) {
			int deviation = Math.abs(counts[i] - expectedCount);
			Assert.assertTrue(activeNodes.get(i) + " received " + counts[i] + " keys",
					deviation <= maxAllowedDeviation);
		}
	}

	private static final class CountingCharSequence implements CharSequence {

		private final String value;
		private int charAtCalls;

		private CountingCharSequence(String value) {
			this.value = value;
		}

		private int getCharAtCalls() {
			return charAtCalls;
		}

		private void resetCharAtCalls() {
			charAtCalls = 0;
		}

		@Override
		public int length() {
			return value.length();
		}

		@Override
		public char charAt(int index) {
			charAtCalls++;
			return value.charAt(index);
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			return value.subSequence(start, end);
		}

		@Override
		public String toString() {
			return value;
		}
	}
}
