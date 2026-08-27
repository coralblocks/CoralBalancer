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

/**
 * Static Rendezvous hashing implementation used to choose one owner from the active nodes.
 */
final class RendezvousHashing {

    private static final long FNV64_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME  = 0x100000001b3L;

    private RendezvousHashing() {
    	
    }
    
    private static void validateKey(Object key) {

        if (key == null) {
            throw new IllegalArgumentException("The key argument cannot be null!");
        }
    }

    static CharSequence ownerForHash(long keyHash, CharSequence[] activeNodes, long[] activeNodeHashes,
            int activeNodeCount) {

        if (activeNodeCount == 0) {
            throw new IllegalArgumentException("The activeNodes argument cannot be empty!");
        }

        CharSequence bestNode = activeNodes[0];
        long bestScore = score(keyHash, activeNodeHashes[0]);

        for (int i = 1; i < activeNodeCount; i++) {
            CharSequence nodeAccount = activeNodes[i];
            long score = score(keyHash, activeNodeHashes[i]);

            if (score > bestScore || (score == bestScore && compare(nodeAccount, bestNode) < 0)) {
                bestScore = score;
                bestNode = nodeAccount;
            }
        }

        return bestNode;
    }

    static long hashNode(CharSequence nodeAccount) {
        return hash64(nodeAccount);
    }

    static long hashKey(CharSequence key) {
        validateKey(key);
        return hash64(key);
    }

    static long hashKey(byte[] key) {
        validateKey(key);
        return hash64(key);
    }

    static long hashKey(char[] key) {
        validateKey(key);
        return hash64(key);
    }

    static long hashKey(ByteBuffer key) {
        validateKey(key);
        return hash64(key);
    }

    static long hashKey(boolean key) {
        return hash64(key);
    }

    static long hashKey(byte key) {
        return hash64(key);
    }

    static long hashKey(char key) {
        return hash64(key);
    }

    static long hashKey(short key) {
        return hash64(key);
    }

    static long hashKey(int key) {
        return hash64(key);
    }

    static long hashKey(long key) {
        return hash64(key);
    }

    static long hashKey(float key) {
        return hash64(key);
    }

    static long hashKey(double key) {
        return hash64(key);
    }

    private static long score(long keyHash, long nodeHash) {
        return mix64(keyHash ^ nodeHash);
    }

    private static long hash64(CharSequence value) {
    	
        long h = FNV64_OFFSET;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            h ^= c & 0xff;
            h *= FNV64_PRIME;

            h ^= (c >>> 8) & 0xff;
            h *= FNV64_PRIME;
        }

        return mix64(h);
    }

    private static long hash64(byte[] value) {

        long h = FNV64_OFFSET;

        for (int i = 0; i < value.length; i++) {
            h ^= value[i] & 0xff;
            h *= FNV64_PRIME;
        }

        return mix64(h);
    }

    private static long hash64(char[] value) {

        long h = FNV64_OFFSET;

        for (int i = 0; i < value.length; i++) {
            char c = value[i];

            h ^= c & 0xff;
            h *= FNV64_PRIME;

            h ^= (c >>> 8) & 0xff;
            h *= FNV64_PRIME;
        }

        return mix64(h);
    }

    private static long hash64(ByteBuffer value) {

        long h = FNV64_OFFSET;

        for (int i = value.position(); i < value.limit(); i++) {
            h ^= value.get(i) & 0xff;
            h *= FNV64_PRIME;
        }

        return mix64(h);
    }

    private static long hash64(boolean value) {
        return hash64(value ? 1L : 0L);
    }

    private static long hash64(byte value) {
        return hash64((long) value);
    }

    private static long hash64(char value) {
        return hash64((long) value);
    }

    private static long hash64(short value) {
        return hash64((long) value);
    }

    private static long hash64(int value) {
        return hash64((long) value);
    }

    private static long hash64(long value) {
        return mix64(value);
    }

    private static long hash64(float value) {
        return hash64(Float.floatToIntBits(value));
    }

    private static long hash64(double value) {
        return hash64(Double.doubleToLongBits(value));
    }

    private static int compare(CharSequence a, CharSequence b) {

        int minLen = Math.min(a.length(), b.length());

        for (int i = 0; i < minLen; i++) {
            int diff = a.charAt(i) - b.charAt(i);
            if (diff != 0) return diff;
        }

        return a.length() - b.length();
    }

    private static long mix64(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }
}
