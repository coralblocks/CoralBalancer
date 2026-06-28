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

public class BalancerExample {

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

		Map<String, Integer> symbolsByNode = new LinkedHashMap<String, Integer>();
		for (String nodeAccount : nodeAccounts) {
			symbolsByNode.put(nodeAccount, 0);
		}

		for (String symbol : symbols) {
			
			int matches = 0;
			
			for (Balancer balancer : balancers) {
				if (balancer.isForMe(symbol)) {
					symbolsByNode.put(balancer.getMyNodeAccount(), symbolsByNode.get(balancer.getMyNodeAccount()) + 1);
					matches++;
				}
			}
			
			if (matches != 1) throw new IllegalStateException("Something went very wrong: " + matches);
		}

		System.out.println("Symbols: " + symbolCount);
		System.out.println("Nodes: " + nodeCount);
		System.out.println();
		System.out.println("Symbols handled by each node:\n");

		for (Map.Entry<String, Integer> entry : symbolsByNode.entrySet()) {
			long nodeSymbols = entry.getValue();
			double percentage = nodeSymbols * 100.0 / symbolCount;
			System.out.printf("%s handled %d symbols (%.2f%%)%n", entry.getKey(), nodeSymbols, percentage);
		}
	}
}
