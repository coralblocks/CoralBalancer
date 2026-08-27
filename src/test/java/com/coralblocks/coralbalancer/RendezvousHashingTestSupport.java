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

import java.nio.ByteBuffer;
import java.util.List;

final class RendezvousHashingTestSupport {

	private RendezvousHashingTestSupport() {

	}

	static CharSequence ownerFor(CharSequence key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(byte[] key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(char[] key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(ByteBuffer key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(boolean key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(byte key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(char key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(short key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(int key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(long key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(float key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	static CharSequence ownerFor(double key, List<CharSequence> activeNodes) {
		return ownerForHash(RendezvousHashing.hashKey(key), activeNodes);
	}

	private static CharSequence ownerForHash(long keyHash, List<CharSequence> activeNodes) {
		CharSequence[] nodeArray = activeNodes.toArray(new CharSequence[activeNodes.size()]);
		long[] nodeHashes = new long[nodeArray.length];

		for (int i = 0; i < nodeArray.length; i++) {
			nodeHashes[i] = RendezvousHashing.hashNode(nodeArray[i]);
		}

		return RendezvousHashing.ownerForHash(keyHash, nodeArray, nodeHashes, nodeArray.length);
	}
}
