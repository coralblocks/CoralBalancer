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

/**
 * Example showing how symbol pinning changes ownership for selected symbols.
 */
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

		Map<String, Integer> symbolsHandledBeforePinning = new LinkedHashMap<String, Integer>();
		for (String nodeAccount : nodeAccounts) {
			symbolsHandledBeforePinning.put(nodeAccount, 0);
		}

		Map<String, String> firstPassOwnersBySymbol = new LinkedHashMap<String, String>();
		
		for (String symbol : symbols) {

			int matches = 0;
			
			for (Balancer balancer : balancers) {
				if (balancer.isForMe(symbol)) {
					String nodeAccount = balancer.getMyNodeAccount();
					firstPassOwnersBySymbol.put(symbol, nodeAccount);
					symbolsHandledBeforePinning.put(nodeAccount, symbolsHandledBeforePinning.get(nodeAccount) + 1);
					matches++;
				}
			}

			if (matches != 1) throw new IllegalStateException("Something went very wrong: " + matches);
		}

		String firstSymbol = symbols.get(0);
		String secondSymbol = symbols.get(1);
		String pinnedNodeAccount = nodeAccounts.get(0);

		for (Balancer balancer : balancers) {
			balancer.pin(firstSymbol, pinnedNodeAccount);
			balancer.pin(secondSymbol, pinnedNodeAccount);
		}

		Map<String, Integer> symbolsHandledAfterPinning = new LinkedHashMap<String, Integer>();
		for (String nodeAccount : nodeAccounts) {
			symbolsHandledAfterPinning.put(nodeAccount, 0);
		}

		Map<String, String> secondPassOwnersBySymbol = new LinkedHashMap<String, String>();
		
		for (String symbol : symbols) {

			int matches = 0;

			for (Balancer balancer : balancers) {
				if (balancer.isForMe(symbol)) {
					String nodeAccount = balancer.getMyNodeAccount();
					secondPassOwnersBySymbol.put(symbol, nodeAccount);
					symbolsHandledAfterPinning.put(nodeAccount, symbolsHandledAfterPinning.get(nodeAccount) + 1);
					matches++;
				}
			}

			if (matches != 1) throw new IllegalStateException("Something went very wrong: " + matches);
		}

		System.out.println("Symbols: " + symbolCount);
		System.out.println("Nodes: " + nodeCount);
		System.out.println();
		System.out.println("Before pinning:");

		for (Map.Entry<String, Integer> entry : symbolsHandledBeforePinning.entrySet()) {
			long nodeSymbols = entry.getValue();
			double percentage = nodeSymbols * 100.0 / symbolCount;
			System.out.printf("%s handled %d symbols (%.2f%%)%n", entry.getKey(), nodeSymbols, percentage);
		}

		System.out.printf("%s was handled by %s%n", firstSymbol, firstPassOwnersBySymbol.get(firstSymbol));
		System.out.printf("%s was handled by %s%n", secondSymbol, firstPassOwnersBySymbol.get(secondSymbol));

		System.out.println();
		System.out.println("After pinning:");

		for (Map.Entry<String, Integer> entry : symbolsHandledAfterPinning.entrySet()) {
			long nodeSymbols = entry.getValue();
			double percentage = nodeSymbols * 100.0 / symbolCount;
			System.out.printf("%s handled %d symbols (%.2f%%)%n", entry.getKey(), nodeSymbols, percentage);
		}

		if (!pinnedNodeAccount.equals(secondPassOwnersBySymbol.get(firstSymbol))) {
			throw new IllegalStateException(firstSymbol + " should be pinned to " + pinnedNodeAccount
					+ " but went to " + secondPassOwnersBySymbol.get(firstSymbol));
		}
		if (!pinnedNodeAccount.equals(secondPassOwnersBySymbol.get(secondSymbol))) {
			throw new IllegalStateException(secondSymbol + " should be pinned to " + pinnedNodeAccount
					+ " but went to " + secondPassOwnersBySymbol.get(secondSymbol));
		}

		System.out.printf("%s was handled by %s%n", firstSymbol, secondPassOwnersBySymbol.get(firstSymbol));
		System.out.printf("%s was handled by %s%n", secondSymbol, secondPassOwnersBySymbol.get(secondSymbol));
	}
}
