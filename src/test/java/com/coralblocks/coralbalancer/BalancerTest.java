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

import java.lang.reflect.Method;
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
	public void testMaximumNumberOfNodesIsEnforced() {

		Balancer b = new Balancer("NODE1");

		for (int i = 0; i < Balancer.MAX_NUMBER_OF_NODES; i++) {
			Assert.assertTrue(b.addNode("NODE" + i));
		}

		try {
			b.addNode("OVERFLOW");
			Assert.fail("Expected addNode to reject an active node beyond the fixed maximum");
		} catch (IllegalStateException expected) {
			Assert.assertEquals(Balancer.MAX_NUMBER_OF_NODES, b.getNumberOfNodes());
			Assert.assertFalse(b.hasNode("OVERFLOW"));
		}
	}

	@Test
	public void testNullNodeAccountIsRejected() {
		Balancer b = new Balancer("NODE1");

		try {
			b.addNode(null);
			Assert.fail("Expected a null node account to be rejected");
		} catch (IllegalArgumentException expected) {
			Assert.assertEquals("The nodeAccount argument cannot be null!", expected.getMessage());
		}
	}

	@Test
	public void testRoutingConfigurationIsFixed() {
		Assert.assertEquals(1, Balancer.class.getConstructors().length);
		Assert.assertEquals(1, Balancer.class.getConstructors()[0].getParameterTypes().length);
		Assert.assertEquals(CharSequence.class, Balancer.class.getConstructors()[0].getParameterTypes()[0]);
		Assert.assertEquals(256, Balancer.MAX_NUMBER_OF_NODES);
		Assert.assertEquals(128, Balancer.MAX_CACHED_VARIABLE_KEY_LENGTH);
		for (Method method : Balancer.class.getDeclaredMethods()) {
			Assert.assertFalse("withCacheCapacity".equals(method.getName()));
		}
	}

	@Test
	public void testNullMyNodeAccountIsRejected() {
		try {
			new Balancer(null);
			Assert.fail("Expected a null local node account to be rejected");
		} catch (IllegalArgumentException expected) {
			Assert.assertEquals("The myNodeAccount argument cannot be null!", expected.getMessage());
		}
	}

	@Test
	public void testNullVariableKeysAreRejectedBeforeHashing() {
		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");

		assertNullKeyRejected(() -> b.ownerFor((CharSequence) null));
		assertNullKeyRejected(() -> b.ownerFor((byte[]) null));
		assertNullKeyRejected(() -> b.ownerFor((char[]) null));
		assertNullKeyRejected(() -> b.ownerFor((ByteBuffer) null));
	}

	@Test
	public void testIsForMe() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE2");

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		CharSequence charSequenceKey = new StringBuilder("KEY1");
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(charSequenceKey, activeNodes), b),
				b.isForMe(charSequenceKey));

		byte[] byteArrayKey = new byte[] { 1, 2, 3, 4 };
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(byteArrayKey, activeNodes), b),
				b.isForMe(byteArrayKey));

		char[] charArrayKey = new char[] { 'K', 'E', 'Y', '1' };
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(charArrayKey, activeNodes), b),
				b.isForMe(charArrayKey));

		ByteBuffer byteBufferKey = ByteBuffer.wrap(new byte[] { 9, 1, 2, 3, 4, 9 });
		byteBufferKey.position(1);
		byteBufferKey.limit(5);
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(byteBufferKey, activeNodes), b),
				b.isForMe(byteBufferKey));
		Assert.assertEquals(1, byteBufferKey.position());
		Assert.assertEquals(5, byteBufferKey.limit());

		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(true, activeNodes), b),
				b.isForMe(true));
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor((byte) 7, activeNodes), b),
				b.isForMe((byte) 7));
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor('A', activeNodes), b),
				b.isForMe('A'));
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor((short) 123, activeNodes), b),
				b.isForMe((short) 123));
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(456, activeNodes), b),
				b.isForMe(456));
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(123456789L, activeNodes), b),
				b.isForMe(123456789L));
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(123.25f, activeNodes), b),
				b.isForMe(123.25f));
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(456.75d, activeNodes), b),
				b.isForMe(456.75d));

		long keyForMe = keyFor(b.getMyNodeAccount(), activeNodes);
		long keyForOtherNode = keyFor("NODE1", activeNodes);

		Assert.assertTrue(b.isForMe(keyForMe));
		Assert.assertFalse(b.isForMe(keyForOtherNode));
	}

	@Test
	public void testOwnerForClearsAllCachesWhenNodesChange() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2");
		Balancer b = new Balancer("NODE1");

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		CharSequence charSequenceKey = charSequenceKeyFor("NODE1", activeNodes);
		byte[] byteArrayKey = byteArrayKeyFor("NODE1", activeNodes);
		long primitiveKey = keyFor("NODE1", activeNodes);

		Assert.assertEquals("NODE1", b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(primitiveKey).toString());

		Assert.assertTrue(b.removeNode("NODE1"));
		Assert.assertEquals("NODE2", b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals("NODE2", b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals("NODE2", b.ownerFor(primitiveKey).toString());
		Assert.assertFalse(b.isForMe(primitiveKey));
	}

	@Test
	public void testOwnerForKeepsNodeHashesAlignedAfterRemoval() {

		List<CharSequence> initialNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		List<CharSequence> remainingNodes = Arrays.asList("NODE1", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE1");

		for (int i = 0; i < initialNodes.size(); i++) {
			b.addNode(initialNodes.get(i));
		}

		Assert.assertTrue(b.removeNode("NODE2"));

		for (long key = 0; key < 100; key++) {
			Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(key, remainingNodes).toString(),
					b.ownerFor(key).toString());
		}
	}

	@Test
	public void testOwnerForDistinguishesCacheHashCollisions() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE1");
		CharSequence key1 = "Aa";
		CharSequence key2 = "BB";

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		Assert.assertEquals(key1.hashCode(), key2.hashCode());
		Assert.assertNotEquals(RendezvousHashingTestSupport.ownerFor(key1, activeNodes).toString(),
				RendezvousHashingTestSupport.ownerFor(key2, activeNodes).toString());
		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(key1, activeNodes).toString(), b.ownerFor(key1).toString());
		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(key2, activeNodes).toString(), b.ownerFor(key2).toString());
	}

	@Test
	public void testLargeKeysAreAccepted() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE2");
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

		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(charSequenceKey, activeNodes).toString(),
				b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(charSequenceKey, activeNodes).toString(),
				b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(byteArrayKey, activeNodes).toString(),
				b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(charArrayKey, activeNodes).toString(),
				b.ownerFor(charArrayKey).toString());
		Assert.assertEquals(RendezvousHashingTestSupport.ownerFor(byteBufferKey, activeNodes).toString(),
				b.ownerFor(byteBufferKey).toString());
		Assert.assertEquals(isOwnerForMe(RendezvousHashingTestSupport.ownerFor(charSequenceKey, activeNodes), b),
				b.isForMe(charSequenceKey));
	}

	@Test
	public void testEquivalentTextKeysShareOwnerAndPins() {

		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");
		b.addNode("NODE2");

		CharSequence charSequenceKey = "ABC";
		char[] charArrayKey = new char[] { 'A', 'B', 'C' };
		String expectedOwner = b.ownerFor(charSequenceKey).toString();

		Assert.assertEquals(expectedOwner, b.ownerFor(charArrayKey).toString());

		b.pin(charSequenceKey, "PIN_NODE1");
		Assert.assertEquals("PIN_NODE1", b.ownerFor(charArrayKey).toString());

		b.pin(charArrayKey, "PIN_NODE2");
		Assert.assertEquals("PIN_NODE2", b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals("PIN_NODE2", b.unpin(charSequenceKey).toString());
		Assert.assertNull(b.unpin(charArrayKey));
		Assert.assertEquals(expectedOwner, b.ownerFor(charArrayKey).toString());
	}

	@Test
	public void testEquivalentBinaryKeysSharePins() {

		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");
		b.addNode("NODE2");

		byte[] byteArrayKey = new byte[] { 1, 2, 3, 4 };
		ByteBuffer byteBufferKey = ByteBuffer.wrap(new byte[] { 9, 1, 2, 3, 4, 9 });
		byteBufferKey.position(1);
		byteBufferKey.limit(5);

		Assert.assertTrue(contentEquals(b.ownerFor(byteArrayKey), b.ownerFor(byteBufferKey)));

		b.pin(byteArrayKey, "PIN_NODE1");
		Assert.assertEquals("PIN_NODE1", b.ownerFor(byteBufferKey).toString());

		b.pin(byteBufferKey, "PIN_NODE2");
		Assert.assertEquals("PIN_NODE2", b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals("PIN_NODE2", b.unpin(byteArrayKey).toString());
		Assert.assertNull(b.unpin(byteBufferKey));
		Assert.assertEquals(1, byteBufferKey.position());
		Assert.assertEquals(5, byteBufferKey.limit());
	}

	@Test
	public void testEquivalentPrimitiveKeysShareOwner() {

		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");
		b.addNode("NODE2");

		CharSequence expectedOwner = b.ownerFor(1L);
		Assert.assertSame(expectedOwner, b.ownerFor(true));
		Assert.assertSame(expectedOwner, b.ownerFor((byte) 1));
		Assert.assertSame(expectedOwner, b.ownerFor((char) 1));
		Assert.assertSame(expectedOwner, b.ownerFor((short) 1));
		Assert.assertSame(expectedOwner, b.ownerFor(1));
		Assert.assertSame(expectedOwner, b.ownerFor(Float.intBitsToFloat(1)));
		Assert.assertSame(expectedOwner, b.ownerFor(Double.longBitsToDouble(1L)));
	}

	@Test
	public void testPrimitivePinsRemainTypeSpecific() {

		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");

		b.pin(true, "BOOLEAN_NODE");
		b.pin((byte) 1, "BYTE_NODE");
		b.pin((char) 1, "CHAR_NODE");
		b.pin((short) 1, "SHORT_NODE");
		b.pin(1, "INT_NODE");
		b.pin(1L, "LONG_NODE");
		b.pin(Float.intBitsToFloat(1), "FLOAT_NODE");
		b.pin(Double.longBitsToDouble(1L), "DOUBLE_NODE");

		Assert.assertEquals("BOOLEAN_NODE", b.ownerFor(true).toString());
		Assert.assertEquals("BYTE_NODE", b.ownerFor((byte) 1).toString());
		Assert.assertEquals("CHAR_NODE", b.ownerFor((char) 1).toString());
		Assert.assertEquals("SHORT_NODE", b.ownerFor((short) 1).toString());
		Assert.assertEquals("INT_NODE", b.ownerFor(1).toString());
		Assert.assertEquals("LONG_NODE", b.ownerFor(1L).toString());
		Assert.assertEquals("FLOAT_NODE", b.ownerFor(Float.intBitsToFloat(1)).toString());
		Assert.assertEquals("DOUBLE_NODE", b.ownerFor(Double.longBitsToDouble(1L)).toString());
	}

	@Test
	public void testPinBypassesOwnerCacheAndUnpinFallsBack() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2", "NODE3", "NODE4");
		Balancer b = new Balancer("NODE1");
		CharSequence key = "KEY1";

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		CharSequence rendezvousOwner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);
		String pinnedOwner = differentNode(rendezvousOwner, activeNodes);

		Assert.assertEquals(rendezvousOwner.toString(), b.ownerFor(key).toString());
		b.pin(key, pinnedOwner);
		Assert.assertEquals(pinnedOwner, b.ownerFor(key).toString());
		b.unpin(key);
		b.unpin(key);
		Assert.assertEquals(rendezvousOwner.toString(), b.ownerFor(key).toString());
	}

	@Test
	public void testUnpinReturnsPreviousNodeForAllKeyTypes() {

		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");
		b.addNode("NODE2");
		b.addNode("NODE3");
		b.addNode("NODE4");

		CharSequence charSequenceKey = new StringBuilder("PIN1");
		b.pin(charSequenceKey, "NODE2");
		Assert.assertEquals("NODE2", b.unpin(charSequenceKey).toString());

		byte[] byteArrayKey = new byte[] { 1, 2, 3 };
		b.pin(byteArrayKey, "NODE3");
		Assert.assertEquals("NODE3", b.unpin(byteArrayKey).toString());

		char[] charArrayKey = new char[] { 'P', 'I', 'N' };
		b.pin(charArrayKey, "NODE4");
		Assert.assertEquals("NODE4", b.unpin(charArrayKey).toString());

		ByteBuffer byteBufferKey = ByteBuffer.wrap(new byte[] { 9, 4, 5, 6, 9 });
		byteBufferKey.position(1);
		byteBufferKey.limit(4);
		b.pin(byteBufferKey, "NODE2");
		Assert.assertEquals("NODE2", b.unpin(byteBufferKey).toString());
		Assert.assertEquals(1, byteBufferKey.position());
		Assert.assertEquals(4, byteBufferKey.limit());

		b.pin(true, "NODE3");
		Assert.assertEquals("NODE3", b.unpin(true).toString());
		b.pin((byte) 7, "NODE4");
		Assert.assertEquals("NODE4", b.unpin((byte) 7).toString());
		b.pin('A', "NODE2");
		Assert.assertEquals("NODE2", b.unpin('A').toString());
		b.pin((short) 123, "NODE3");
		Assert.assertEquals("NODE3", b.unpin((short) 123).toString());
		b.pin(456, "NODE4");
		Assert.assertEquals("NODE4", b.unpin(456).toString());
		b.pin(123456789L, "NODE2");
		Assert.assertEquals("NODE2", b.unpin(123456789L).toString());
		b.pin(123.25f, "NODE3");
		Assert.assertEquals("NODE3", b.unpin(123.25f).toString());
		b.pin(456.75d, "NODE4");
		Assert.assertEquals("NODE4", b.unpin(456.75d).toString());
	}

	@Test
	public void testUnpinReusesReturnedNodeAccount() {

		Balancer b = new Balancer("NODE1");

		b.pin("KEY1", "NODE1");
		b.pin(true, "NODE2");

		CharSequence firstResult = b.unpin("KEY1");
		Assert.assertEquals("NODE1", firstResult.toString());

		b.pin("KEY2", "A_DIFFERENT_NODE");
		Assert.assertEquals("NODE1", firstResult.toString());

		CharSequence secondResult = b.unpin(true);
		Assert.assertSame(firstResult, secondResult);
		Assert.assertEquals("NODE2", firstResult.toString());

		Assert.assertNull(b.unpin("MISSING"));
		Assert.assertEquals(0, firstResult.length());
	}

	@Test
	public void testRemovePinsForNodeDropsOnlyMatchingPins() {

		Balancer b = new Balancer("NODE2");
		b.addNode("NODE1");
		b.addNode("NODE2");

		CharSequence charSequenceKey = "CHAR_SEQUENCE";
		byte[] byteArrayKey = new byte[] { 1, 2, 3 };
		char[] charArrayKey = new char[] { 'C', 'H', 'A', 'R' };
		ByteBuffer byteBufferKey = ByteBuffer.wrap(new byte[] { 4, 5, 6 });

		b.pin(charSequenceKey, "NODE1");
		b.pin(byteArrayKey, "NODE1");
		b.pin(charArrayKey, "NODE1");
		b.pin(byteBufferKey, "NODE1");
		b.pin(true, "NODE1");
		b.pin((byte) 7, "NODE1");
		b.pin('A', "NODE1");
		b.pin((short) 123, "NODE1");
		b.pin(456, "NODE1");
		b.pin(123456789L, "NODE1");
		b.pin(123.25f, "NODE1");
		b.pin(456.75d, "NODE1");
		b.pin("KEEP", "NODE2");

		Assert.assertEquals("NODE1", b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(charArrayKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(byteBufferKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(true).toString());
		Assert.assertEquals("NODE1", b.ownerFor((byte) 7).toString());
		Assert.assertEquals("NODE1", b.ownerFor('A').toString());
		Assert.assertEquals("NODE1", b.ownerFor((short) 123).toString());
		Assert.assertEquals("NODE1", b.ownerFor(456).toString());
		Assert.assertEquals("NODE1", b.ownerFor(123456789L).toString());
		Assert.assertEquals("NODE1", b.ownerFor(123.25f).toString());
		Assert.assertEquals("NODE1", b.ownerFor(456.75d).toString());

		Assert.assertTrue(b.removeNode("NODE1"));
		Assert.assertFalse(b.hasNode("NODE1"));
		Assert.assertEquals("NODE1", b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(charArrayKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(byteBufferKey).toString());
		Assert.assertEquals("NODE1", b.ownerFor(true).toString());
		Assert.assertEquals("NODE1", b.ownerFor((byte) 7).toString());
		Assert.assertEquals("NODE1", b.ownerFor('A').toString());
		Assert.assertEquals("NODE1", b.ownerFor((short) 123).toString());
		Assert.assertEquals("NODE1", b.ownerFor(456).toString());
		Assert.assertEquals("NODE1", b.ownerFor(123456789L).toString());
		Assert.assertEquals("NODE1", b.ownerFor(123.25f).toString());
		Assert.assertEquals("NODE1", b.ownerFor(456.75d).toString());
		Assert.assertEquals(12, b.removePinsForNode("NODE1"));

		Assert.assertEquals("NODE2", b.ownerFor(charSequenceKey).toString());
		Assert.assertEquals("NODE2", b.ownerFor(byteArrayKey).toString());
		Assert.assertEquals("NODE2", b.ownerFor(charArrayKey).toString());
		Assert.assertEquals("NODE2", b.ownerFor(byteBufferKey).toString());
		Assert.assertEquals("NODE2", b.ownerFor(true).toString());
		Assert.assertEquals("NODE2", b.ownerFor((byte) 7).toString());
		Assert.assertEquals("NODE2", b.ownerFor('A').toString());
		Assert.assertEquals("NODE2", b.ownerFor((short) 123).toString());
		Assert.assertEquals("NODE2", b.ownerFor(456).toString());
		Assert.assertEquals("NODE2", b.ownerFor(123456789L).toString());
		Assert.assertEquals("NODE2", b.ownerFor(123.25f).toString());
		Assert.assertEquals("NODE2", b.ownerFor(456.75d).toString());
		Assert.assertEquals("NODE2", b.ownerFor("KEEP").toString());
		Assert.assertEquals(0, b.removePinsForNode("NODE1"));

		Assert.assertTrue(b.addNode("NODE3"));
		Assert.assertEquals("NODE3",
				RendezvousHashingTestSupport.ownerFor("KEEP", Arrays.<CharSequence>asList("NODE2", "NODE3")).toString());
		Assert.assertEquals("NODE2", b.ownerFor("KEEP").toString());
	}

	@Test
	public void testPinSupportsAllKeyTypes() {

		Balancer b = new Balancer("NODE1");
		b.addNode("NODE1");
		b.addNode("NODE2");
		b.addNode("NODE3");
		b.addNode("NODE4");

		CharSequence charSequenceKey = new StringBuilder("PIN1");
		b.pin(charSequenceKey, "NODE2");
		Assert.assertEquals("NODE2", b.ownerFor(charSequenceKey).toString());

		byte[] byteArrayKey = new byte[] { 1, 2, 3 };
		b.pin(byteArrayKey, "NODE3");
		Assert.assertEquals("NODE3", b.ownerFor(byteArrayKey).toString());

		char[] charArrayKey = new char[] { 'P', 'I', 'N' };
		b.pin(charArrayKey, "NODE4");
		Assert.assertEquals("NODE4", b.ownerFor(charArrayKey).toString());

		ByteBuffer byteBufferKey = ByteBuffer.wrap(new byte[] { 9, 4, 5, 6, 9 });
		byteBufferKey.position(1);
		byteBufferKey.limit(4);
		b.pin(byteBufferKey, "NODE2");
		Assert.assertEquals("NODE2", b.ownerFor(byteBufferKey).toString());
		Assert.assertEquals(1, byteBufferKey.position());
		Assert.assertEquals(4, byteBufferKey.limit());

		b.pin(true, "NODE3");
		Assert.assertEquals("NODE3", b.ownerFor(true).toString());
		b.pin((byte) 7, "NODE4");
		Assert.assertEquals("NODE4", b.ownerFor((byte) 7).toString());
		b.pin('A', "NODE2");
		Assert.assertEquals("NODE2", b.ownerFor('A').toString());
		b.pin((short) 123, "NODE3");
		Assert.assertEquals("NODE3", b.ownerFor((short) 123).toString());
		b.pin(456, "NODE4");
		Assert.assertEquals("NODE4", b.ownerFor(456).toString());
		b.pin(123456789L, "NODE2");
		Assert.assertEquals("NODE2", b.ownerFor(123456789L).toString());
		b.pin(123.25f, "NODE3");
		Assert.assertEquals("NODE3", b.ownerFor(123.25f).toString());
		b.pin(456.75d, "NODE4");
		Assert.assertEquals("NODE4", b.ownerFor(456.75d).toString());
	}

	@Test
	public void testPinSupportsNodeOutsideActiveList() {

		List<CharSequence> activeNodes = Arrays.asList("NODE1", "NODE2");
		Balancer b = new Balancer("PIN_ONLY_NODE");

		for (int i = 0; i < activeNodes.size(); i++) {
			b.addNode(activeNodes.get(i));
		}

		Assert.assertFalse(b.hasNode("PIN_ONLY_NODE"));
		b.pin("GOOG", "PIN_ONLY_NODE");
		Assert.assertEquals("PIN_ONLY_NODE", b.ownerFor("GOOG").toString());
		Assert.assertTrue(b.isForMe("GOOG"));

		CharSequence hashedOwner = b.ownerFor("AAPL");
		Assert.assertTrue("NODE1".contentEquals(hashedOwner) || "NODE2".contentEquals(hashedOwner));
		Assert.assertFalse(b.isForMe("AAPL"));
	}

	@Test
	public void testPinRejectsOversizedVariableKeys() {

		Balancer b = new Balancer("NODE1");
		StringBuilder maximumLengthKey = new StringBuilder(Balancer.MAX_CACHED_VARIABLE_KEY_LENGTH);
		for (int i = 0; i < Balancer.MAX_CACHED_VARIABLE_KEY_LENGTH; i++) {
			maximumLengthKey.append('P');
		}
		b.pin(maximumLengthKey, "NODE2");
		Assert.assertEquals("NODE2", b.unpin(maximumLengthKey).toString());

		int oversizedLength = Balancer.MAX_CACHED_VARIABLE_KEY_LENGTH + 1;
		StringBuilder charSequenceKey = new StringBuilder(oversizedLength);
		byte[] byteArrayKey = new byte[oversizedLength];
		char[] charArrayKey = new char[oversizedLength];

		for (int i = 0; i < oversizedLength; i++) {
			charSequenceKey.append('P');
			byteArrayKey[i] = (byte) i;
			charArrayKey[i] = (char) i;
		}

		ByteBuffer byteBufferKey = ByteBuffer.wrap(byteArrayKey);

		assertOversizedPinRejected(() -> b.pin(charSequenceKey, "NODE2"));
		assertOversizedPinRejected(() -> b.pin(byteArrayKey, "NODE2"));
		assertOversizedPinRejected(() -> b.pin(charArrayKey, "NODE2"));
		assertOversizedPinRejected(() -> b.pin(byteBufferKey, "NODE2"));
		Assert.assertNull(b.unpin(charSequenceKey));
		Assert.assertNull(b.unpin(byteArrayKey));
		Assert.assertNull(b.unpin(charArrayKey));
		Assert.assertNull(b.unpin(byteBufferKey));
	}

	private static void assertOversizedPinRejected(Runnable pinAction) {
		try {
			pinAction.run();
			Assert.fail("Expected an oversized key to be rejected");
		} catch (IllegalArgumentException expected) {
			Assert.assertEquals("The key length cannot exceed 128: 129", expected.getMessage());
		}
	}

	private static void assertNullKeyRejected(Runnable ownerLookup) {
		try {
			ownerLookup.run();
			Assert.fail("Expected a null key to be rejected");
		} catch (IllegalArgumentException expected) {
			Assert.assertEquals("The key argument cannot be null!", expected.getMessage());
		}
	}

	private static boolean contentEquals(CharSequence a, CharSequence b) {
		if (a == b) return true;
		if (a == null || b == null || a.length() != b.length()) return false;
		for (int i = 0; i < a.length(); i++) {
			if (a.charAt(i) != b.charAt(i)) return false;
		}
		return true;
	}

	private static boolean isOwnerForMe(CharSequence owner, Balancer b) {
		return b.getMyNodeAccount().contentEquals(owner);
	}

	private static String differentNode(CharSequence owner, List<CharSequence> activeNodes) {
		for (int i = 0; i < activeNodes.size(); i++) {
			CharSequence node = activeNodes.get(i);
			if (!node.toString().contentEquals(owner)) return node.toString();
		}
		throw new IllegalStateException("Could not find a different node for owner: " + owner);
	}

	private static CharSequence charSequenceKeyFor(CharSequence nodeAccount, List<CharSequence> activeNodes) {
		for (int i = 0; i < 10_000; i++) {
			String key = "KEY" + i;
			CharSequence owner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);
			if (nodeAccount.toString().contentEquals(owner)) return key;
		}
		throw new IllegalStateException("Could not find a CharSequence key for node account: " + nodeAccount);
	}

	private static byte[] byteArrayKeyFor(CharSequence nodeAccount, List<CharSequence> activeNodes) {
		for (int i = 0; i < 10_000; i++) {
			byte[] key = new byte[] { (byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i };
			CharSequence owner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);
			if (nodeAccount.toString().contentEquals(owner)) return key;
		}
		throw new IllegalStateException("Could not find a byte[] key for node account: " + nodeAccount);
	}

	private static long keyFor(CharSequence nodeAccount, List<CharSequence> activeNodes) {
		for (long key = 0; key < 10_000; key++) {
			CharSequence owner = RendezvousHashingTestSupport.ownerFor(key, activeNodes);
			if (nodeAccount.toString().contentEquals(owner)) return key;
		}
		throw new IllegalStateException("Could not find key for node account: " + nodeAccount);
	}
}
