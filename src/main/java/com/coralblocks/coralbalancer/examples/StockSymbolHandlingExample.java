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

import java.util.LinkedHashMap;
import java.util.Map;

import com.coralblocks.coralbalancer.Balancer;

public class StockSymbolHandlingExample {

	private static final int DEFAULT_SYMBOL_COUNT = 100;
	private static final int DEFAULT_NODE_COUNT = 3;

	public static void main(String[] args) {

		int symbolCount = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_SYMBOL_COUNT;
		int nodeCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_NODE_COUNT;
		
		Map<String, Balancer> balancers = createBalancers(nodeCount);
		Map<String, Long> symbolsByNode = createSymbolsByNode(nodeCount);

		for (int i = 1; i <= symbolCount; i++) {
			String symbol = "SYMBOL" + i;
			String nodeAccount = nodeThatShouldHandle(symbol, balancers);

			symbolsByNode.put(nodeAccount, symbolsByNode.get(nodeAccount) + 1);
		}

		System.out.println("Symbols: " + symbolCount);
		System.out.println("Nodes: " + nodeCount);
		System.out.println();
		System.out.println("Symbols handled by node:");

		for (Map.Entry<String, Long> entry : symbolsByNode.entrySet()) {
			long symbols = entry.getValue();
			double percentage = symbols * 100.0 / symbolCount;
			System.out.printf("%s handled %d symbols (%.2f%%)%n", entry.getKey(), symbols, percentage);
		}
	}

	private static Map<String, Balancer> createBalancers(int nodeCount) {
		
		Map<String, Balancer> balancers = new LinkedHashMap<String, Balancer>();

		for (int i = 1; i <= nodeCount; i++) {
			String nodeAccount = nodeAccount(i);
			balancers.put(nodeAccount, new Balancer(nodeAccount));
		}

		for (Balancer balancer : balancers.values()) {
			for (int i = 1; i <= nodeCount; i++) {
				balancer.addNode(nodeAccount(i));
			}
		}

		return balancers;
	}

	private static Map<String, Long> createSymbolsByNode(int nodeCount) {
		Map<String, Long> symbolsByNode = new LinkedHashMap<String, Long>();

		for (int i = 1; i <= nodeCount; i++) {
			symbolsByNode.put(nodeAccount(i), 0L);
		}

		return symbolsByNode;
	}

	private static String nodeAccount(int index) {
		return "NODE" + index;
	}

	private static String nodeThatShouldHandle(CharSequence symbol, Map<String, Balancer> balancers) {
		for (Map.Entry<String, Balancer> entry : balancers.entrySet()) {
			if (entry.getValue().isForMe(symbol)) return entry.getKey();
		}
		throw new IllegalStateException("No node should handle symbol: " + symbol);
	}
}
