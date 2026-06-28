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
package com.coralblocks.coralbalancer.examples;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.coralblocks.coralbalancer.Balancer;

public class PinningExample {

	private static final int DEFAULT_SYMBOL_COUNT = 5000;
	private static final int DEFAULT_NODE_COUNT = 3;

	public static void main(String[] args) {

		int symbolCount = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_SYMBOL_COUNT;
		int nodeCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_NODE_COUNT;

		List<String> symbols = new ArrayList<String>(symbolCount);
		for (int i = 1; i <= symbolCount; i++) {
			symbols.add("A" + i);
		}

		List<String> nodeAccounts = new ArrayList<String>(nodeCount);
		for (int i = 1; i <= nodeCount; i++) {
			nodeAccounts.add("NODE" + i);
		}

		List<Balancer> balancers = new ArrayList<Balancer>(nodeCount);
		for (String nodeAccount : nodeAccounts) {
			Balancer balancer = new Balancer(nodeAccount);
			for (String na : nodeAccounts) balancer.addNode(na);
			balancers.add(balancer);
		}

		Map<String, Integer> symbolsByNodeBeforePinning = new LinkedHashMap<String, Integer>();
		for (String nodeAccount : nodeAccounts) {
			symbolsByNodeBeforePinning.put(nodeAccount, 0);
		}

		Map<String, String> ownerBeforePinning = new LinkedHashMap<String, String>();
		
		for (String symbol : symbols) {

			int matches = 0;
			for (Balancer balancer : balancers) {
				if (balancer.isForMe(symbol)) {
					String nodeAccount = balancer.getMyNodeAccount();
					ownerBeforePinning.put(symbol, nodeAccount);
					symbolsByNodeBeforePinning.put(nodeAccount, symbolsByNodeBeforePinning.get(nodeAccount) + 1);
					matches++;
				}
			}

			if (matches != 1) throw new IllegalStateException("Something went very wrong: " + matches);
		}

		String firstPinnedSymbol = symbols.get(0);
		String secondPinnedSymbol = symbols.get(1);
		Map<String, String> owndersAfterPinning = new LinkedHashMap<String, String>();

		for (String symbol : new String[] { firstPinnedSymbol, secondPinnedSymbol }) {
			String currentOwner = ownerBeforePinning.get(symbol);
			int currentOwnerIndex = nodeAccounts.indexOf(currentOwner);
			String pinnedOwner = nodeAccounts.get((currentOwnerIndex + 1) % nodeAccounts.size());

			owndersAfterPinning.put(symbol, pinnedOwner);

			for (Balancer balancer : balancers) {
				if (!balancer.pin(symbol, pinnedOwner)) {
					throw new IllegalStateException("Could not pin symbol: " + symbol);
				}
			}
		}

		Map<String, Integer> symbolsByNodeAfterPinning = new LinkedHashMap<String, Integer>();
		for (String nodeAccount : nodeAccounts) {
			symbolsByNodeAfterPinning.put(nodeAccount, 0);
		}

		Map<String, String> ownerAfterPinning = new LinkedHashMap<String, String>();
		for (String symbol : symbols) {

			int matches = 0;

			for (Balancer balancer : balancers) {
				if (balancer.isForMe(symbol)) {
					String nodeAccount = balancer.getMyNodeAccount();
					ownerAfterPinning.put(symbol, nodeAccount);
					symbolsByNodeAfterPinning.put(nodeAccount, symbolsByNodeAfterPinning.get(nodeAccount) + 1);
					matches++;
				}
			}

			if (matches != 1) throw new IllegalStateException("Something went very wrong: " + matches);
		}

		System.out.println("Symbols: " + symbolCount);
		System.out.println("Nodes: " + nodeCount);
		System.out.println();
		System.out.println("Before pinning:");

		for (Map.Entry<String, Integer> entry : symbolsByNodeBeforePinning.entrySet()) {
			long nodeSymbols = entry.getValue();
			double percentage = nodeSymbols * 100.0 / symbolCount;
			System.out.printf("%s handled %d symbols (%.2f%%)%n", entry.getKey(), nodeSymbols, percentage);
		}

		for (Map.Entry<String, String> entry : owndersAfterPinning.entrySet()) {
			String symbol = entry.getKey();
			System.out.printf("%s was handled by %s%n", symbol, ownerBeforePinning.get(symbol));
		}

		System.out.println();
		System.out.println("After pinning:");

		for (Map.Entry<String, Integer> entry : symbolsByNodeAfterPinning.entrySet()) {
			long nodeSymbols = entry.getValue();
			double percentage = nodeSymbols * 100.0 / symbolCount;
			System.out.printf("%s handled %d symbols (%.2f%%)%n", entry.getKey(), nodeSymbols, percentage);
		}

		for (Map.Entry<String, String> entry : owndersAfterPinning.entrySet()) {
			String symbol = entry.getKey();
			String pinnedOwner = entry.getValue();
			String newOwner = ownerAfterPinning.get(symbol);

			if (!pinnedOwner.equals(newOwner)) {
				throw new IllegalStateException(symbol + " should be pinned to " + pinnedOwner + " but went to "
						+ newOwner);
			}

			System.out.printf("%s was handled by %s%n", symbol, newOwner);
		}
	}
}
