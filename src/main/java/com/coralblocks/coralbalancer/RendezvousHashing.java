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
import java.util.List;

final class RendezvousHashing {

    private static final long FNV64_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME  = 0x100000001b3L;

    private RendezvousHashing() {
    	
    }
    
    private static void validateArguments(Object key, List<CharSequence> activeNodes) {
    	
    	if (key == null) {
    		throw new IllegalArgumentException("The key argument cannot be null!");
    	}

        validateActiveNodes(activeNodes);
    }

    private static void validateActiveNodes(List<CharSequence> activeNodes) {

    	if (activeNodes == null) {
    		throw new IllegalArgumentException("They activeNodes argument cannot be null!");
    	}
    	
    	if (activeNodes.isEmpty()) {
    		throw new IllegalArgumentException("The activeNodes argument cannot be empty!");
    	}
    }

    public static CharSequence ownerFor(CharSequence key, List<CharSequence> activeNodes) {

        validateArguments(key, activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(byte[] key, List<CharSequence> activeNodes) {

        validateArguments(key, activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(char[] key, List<CharSequence> activeNodes) {

        validateArguments(key, activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(ByteBuffer key, List<CharSequence> activeNodes) {

    	validateArguments(key, activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(boolean key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(byte key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(char key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(short key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(int key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(long key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(float key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }

    public static CharSequence ownerFor(double key, List<CharSequence> activeNodes) {

        validateActiveNodes(activeNodes);

        long keyHash = hash64(key);

        return hrwHashing(keyHash, activeNodes);
    }
    
    private static CharSequence hrwHashing(long keyHash, List<CharSequence> activeNodes) {
    	
        CharSequence bestNode = activeNodes.get(0);
        long bestScore = score(keyHash, bestNode);

        for (int i = 1; i < activeNodes.size(); i++) {

            CharSequence nodeAccount = activeNodes.get(i);
            long score = score(keyHash, nodeAccount);

            if (score > bestScore || (score == bestScore && compare(nodeAccount, bestNode) < 0)) {
                bestScore = score;
                bestNode = nodeAccount;
            }
        }

        return bestNode;
    }

    private static long score(long keyHash, CharSequence nodeAccount) {
        long nodeHash = hash64(nodeAccount);
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
