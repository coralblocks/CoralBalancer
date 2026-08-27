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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.coralblocks.coralbalancer;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class BalancerGuaranteesTest {

	@Test
	public void testRemovingNodeOnlyRemapsItsKeys() {

		String[] initialNodes = new String[] { "NODE1", "NODE2", "NODE3", "NODE4" };
		Balancer beforeRemoval = new Balancer("NODE1");
		Balancer afterRemoval = new Balancer("NODE1");

		for (int i = 0; i < initialNodes.length; i++) {
			beforeRemoval.addNode(initialNodes[i]);
			afterRemoval.addNode(initialNodes[i]);
		}
		Assert.assertTrue(afterRemoval.removeNode("NODE3"));

		int keysOwnedByRemovedNode = 0;
		int unaffectedKeys = 0;
		for (long key = 0; key < 20_000; key++) {
			CharSequence ownerBefore = beforeRemoval.ownerFor(key);
			CharSequence ownerAfter = afterRemoval.ownerFor(key);
			if ("NODE3".contentEquals(ownerBefore)) {
				keysOwnedByRemovedNode++;
				Assert.assertFalse("NODE3".contentEquals(ownerAfter));
			} else {
				unaffectedKeys++;
				Assert.assertTrue(contentEquals(ownerBefore, ownerAfter));
			}
		}

		Assert.assertTrue(keysOwnedByRemovedNode > 0);
		Assert.assertTrue(unaffectedKeys > 0);
	}

	@Test
	public void testCharSequenceKeysAreUniformlyDistributedAcrossNodes() {

		String[] nodeAccounts = new String[] { "NODE1", "NODE2", "NODE3", "NODE4" };
		Balancer b = new Balancer("NODE1");
		for (int i = 0; i < nodeAccounts.length; i++) {
			b.addNode(nodeAccounts[i]);
		}

		int sampleSize = 200_000;
		int expectedCount = sampleSize / nodeAccounts.length;
		int maxAllowedDeviation = expectedCount / 40;
		int[] counts = new int[nodeAccounts.length];
		StringBuilder key = new StringBuilder(32);

		for (int i = 0; i < sampleSize; i++) {
			key.setLength(0);
			key.append("SYMBOL").append(i);
			counts[indexOf(nodeAccounts, b.ownerFor(key))]++;
		}

		for (int i = 0; i < counts.length; i++) {
			int deviation = Math.abs(counts[i] - expectedCount);
			Assert.assertTrue(nodeAccounts[i] + " received " + counts[i] + " keys",
					deviation <= maxAllowedDeviation);
		}
	}

	@Test
	public void testBalancersAgreeUnderNodeChurn() {

		String[] nodeAccounts = new String[] { "NODE1", "NODE2", "NODE3", "NODE4" };
		Balancer[] balancers = new Balancer[nodeAccounts.length];
		for (int i = 0; i < nodeAccounts.length; i++) {
			balancers[i] = new Balancer(nodeAccounts[i]);
		}

		for (int i = 0; i < balancers.length; i++) {
			for (int j = 0; j < nodeAccounts.length; j++) {
				balancers[i].addNode(nodeAccounts[j]);
			}
		}
		assertClusterAgreement(balancers, 2_000);

		for (int i = 0; i < balancers.length; i++) {
			balancers[i].removeNode("NODE2");
			balancers[i].pin(7L, "NODE2");
		}
		assertClusterAgreement(balancers, 2_000);

		for (int i = 0; i < balancers.length; i++) {
			balancers[i].addNode("NODE2");
			balancers[i].removeNode("NODE4");
		}
		assertClusterAgreement(balancers, 2_000);

		for (int i = 0; i < balancers.length; i++) {
			balancers[i].unpin(7L);
			balancers[i].addNode("NODE4");
		}
		assertClusterAgreement(balancers, 2_000);
	}

	@Test
	public void testSteadyStateOperationsDoNotAllocate() {

		com.sun.management.ThreadMXBean threadBean = allocationThreadBean();

		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");
		b.addNode("NODE2");
		b.addNode("NODE3");
		b.addNode("NODE4");

		CharSequence charSequenceKey = new StringBuilder("SYMBOL");
		byte[] byteArrayKey = new byte[] { 1, 2, 3, 4 };
		char[] charArrayKey = new char[] { 'S', 'Y', 'M', 'B', 'O', 'L' };
		ByteBuffer byteBufferKey = ByteBuffer.wrap(byteArrayKey);
		StringBuilder uncachedCharSequenceKey = new StringBuilder(32);
		byte[] uncachedByteArrayKey = new byte[8];

		b.ownerFor(charSequenceKey);
		b.ownerFor(byteArrayKey);
		b.ownerFor(charArrayKey);
		b.ownerFor(byteBufferKey);
		for (long key = 0; key < 300; key++) {
			b.ownerFor(key);
			setTextKey(uncachedCharSequenceKey, key);
			b.ownerFor(uncachedCharSequenceKey);
			setBinaryKey(uncachedByteArrayKey, key);
			b.ownerFor(uncachedByteArrayKey);
		}
		b.pin("PIN", "NODE1");
		b.unpin("PIN");

		int checksum = 0;
		for (int i = 0; i < 20_000; i++) {
			checksum += exerciseSteadyStateOperations(
					b, charSequenceKey, byteArrayKey, charArrayKey, byteBufferKey,
					uncachedCharSequenceKey, uncachedByteArrayKey, 10_000L + i);
		}

		long threadId = Thread.currentThread().getId();
		long allocatedBefore = threadBean.getThreadAllocatedBytes(threadId);
		for (int i = 0; i < 100_000; i++) {
			checksum += exerciseSteadyStateOperations(
					b, charSequenceKey, byteArrayKey, charArrayKey, byteBufferKey,
					uncachedCharSequenceKey, uncachedByteArrayKey, 100_000L + i);
		}
		long allocatedBytes = threadBean.getThreadAllocatedBytes(threadId) - allocatedBefore;

		Assert.assertTrue(checksum > 0);
		Assert.assertEquals("Steady-state operations allocated bytes", 0L, allocatedBytes);
	}

	@Test
	public void testNodeChangesDuringRegularUseDoNotAllocate() {

		com.sun.management.ThreadMXBean threadBean = allocationThreadBean();
		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");
		b.addNode("NODE2");
		b.addNode("NODE3");
		b.addNode("NODE4");

		CharSequence charSequenceKey = new StringBuilder("SYMBOL");
		byte[] byteArrayKey = new byte[] { 1, 2, 3, 4 };
		char[] charArrayKey = new char[] { 'S', 'Y', 'M', 'B', 'O', 'L' };
		ByteBuffer byteBufferKey = ByteBuffer.wrap(byteArrayKey);
		StringBuilder uncachedCharSequenceKey = new StringBuilder(32);
		byte[] uncachedByteArrayKey = new byte[8];

		for (int i = 0; i < 20_000; i++) {
			exerciseNodeChange(
					b, charSequenceKey, byteArrayKey, charArrayKey, byteBufferKey,
					uncachedCharSequenceKey, uncachedByteArrayKey, 10_000L + i);
		}

		int iterations = 100_000;
		int checksum = 0;
		long threadId = Thread.currentThread().getId();
		long allocatedBefore = threadBean.getThreadAllocatedBytes(threadId);
		for (int i = 0; i < iterations; i++) {
			checksum += exerciseNodeChange(
					b, charSequenceKey, byteArrayKey, charArrayKey, byteBufferKey,
					uncachedCharSequenceKey, uncachedByteArrayKey, 100_000L + i);
		}
		long allocatedBytes = threadBean.getThreadAllocatedBytes(threadId) - allocatedBefore;

		Assert.assertEquals(iterations * 32, checksum);
		Assert.assertEquals("Node changes during regular use allocated bytes", 0L, allocatedBytes);
	}

	private static void assertClusterAgreement(Balancer[] balancers, int keyCount) {
		for (long key = 0; key < keyCount; key++) {
			CharSequence expectedOwner = balancers[0].ownerFor(key);
			int localOwnerCount = 0;
			for (int i = 0; i < balancers.length; i++) {
				Assert.assertTrue(contentEquals(expectedOwner, balancers[i].ownerFor(key)));
				if (balancers[i].isForMe(key)) localOwnerCount++;
			}
			Assert.assertEquals("Expected exactly one local owner for key " + key, 1, localOwnerCount);
		}
	}

	private static int exerciseSteadyStateOperations(Balancer b, CharSequence charSequenceKey,
			byte[] byteArrayKey, char[] charArrayKey, ByteBuffer byteBufferKey,
			StringBuilder uncachedCharSequenceKey, byte[] uncachedByteArrayKey, long uncachedLongKey) {
		int checksum = exerciseOwnerLookups(
				b, charSequenceKey, byteArrayKey, charArrayKey, byteBufferKey,
				uncachedCharSequenceKey, uncachedByteArrayKey, uncachedLongKey);
		b.pin("PIN", "NODE1");
		if (b.unpin("PIN") != null) checksum++;
		return checksum;
	}

	private static int exerciseNodeChange(Balancer b, CharSequence charSequenceKey,
			byte[] byteArrayKey, char[] charArrayKey, ByteBuffer byteBufferKey,
			StringBuilder uncachedCharSequenceKey, byte[] uncachedByteArrayKey, long uncachedLongKey) {
		int checksum = 0;
		if (b.removeNode("NODE4")) checksum++;
		checksum += exerciseOwnerLookups(
				b, charSequenceKey, byteArrayKey, charArrayKey, byteBufferKey,
				uncachedCharSequenceKey, uncachedByteArrayKey, uncachedLongKey);
		if (b.addNode("NODE4")) checksum++;
		checksum += exerciseOwnerLookups(
				b, charSequenceKey, byteArrayKey, charArrayKey, byteBufferKey,
				uncachedCharSequenceKey, uncachedByteArrayKey, uncachedLongKey);
		return checksum;
	}

	private static int exerciseOwnerLookups(Balancer b, CharSequence charSequenceKey,
			byte[] byteArrayKey, char[] charArrayKey, ByteBuffer byteBufferKey,
			StringBuilder uncachedCharSequenceKey, byte[] uncachedByteArrayKey, long uncachedLongKey) {
		int checksum = 0;
		if (b.ownerFor(charSequenceKey) != null) checksum++;
		if (b.ownerFor(byteArrayKey) != null) checksum++;
		if (b.ownerFor(charArrayKey) != null) checksum++;
		if (b.ownerFor(byteBufferKey) != null) checksum++;
		if (b.ownerFor(true) != null) checksum++;
		if (b.ownerFor((byte) 7) != null) checksum++;
		if (b.ownerFor('A') != null) checksum++;
		if (b.ownerFor((short) 123) != null) checksum++;
		if (b.ownerFor(456) != null) checksum++;
		if (b.ownerFor(123456789L) != null) checksum++;
		if (b.ownerFor(123.25f) != null) checksum++;
		if (b.ownerFor(456.75d) != null) checksum++;
		if (b.ownerFor(uncachedLongKey) != null) checksum++;
		setTextKey(uncachedCharSequenceKey, uncachedLongKey);
		if (b.ownerFor(uncachedCharSequenceKey) != null) checksum++;
		setBinaryKey(uncachedByteArrayKey, uncachedLongKey);
		if (b.ownerFor(uncachedByteArrayKey) != null) checksum++;
		return checksum;
	}

	private static com.sun.management.ThreadMXBean allocationThreadBean() {
		java.lang.management.ThreadMXBean standardThreadBean = ManagementFactory.getThreadMXBean();
		Assume.assumeTrue(standardThreadBean instanceof com.sun.management.ThreadMXBean);
		com.sun.management.ThreadMXBean threadBean = (com.sun.management.ThreadMXBean) standardThreadBean;
		Assume.assumeTrue(threadBean.isThreadAllocatedMemorySupported());
		threadBean.setThreadAllocatedMemoryEnabled(true);
		return threadBean;
	}

	private static void setTextKey(StringBuilder key, long value) {
		key.setLength(0);
		key.append("MISS").append(value);
	}

	private static void setBinaryKey(byte[] key, long value) {
		for (int i = 0; i < key.length; i++) {
			key[i] = (byte) (value >>> (i * 8));
		}
	}

	private static int indexOf(String[] nodeAccounts, CharSequence owner) {
		for (int i = 0; i < nodeAccounts.length; i++) {
			if (nodeAccounts[i].contentEquals(owner)) return i;
		}
		throw new IllegalStateException("Unknown owner: " + owner);
	}

	private static boolean contentEquals(CharSequence a, CharSequence b) {
		if (a == b) return true;
		if (a == null || b == null || a.length() != b.length()) return false;
		for (int i = 0; i < a.length(); i++) {
			if (a.charAt(i) != b.charAt(i)) return false;
		}
		return true;
	}
}
