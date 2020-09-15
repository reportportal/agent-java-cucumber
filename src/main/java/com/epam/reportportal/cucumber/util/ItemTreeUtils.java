/*
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.cucumber.util;

import com.epam.reportportal.service.tree.TestItemTree;

import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * @author Vadzim Hushchanskou
 */
public class ItemTreeUtils {

	private ItemTreeUtils() {
		//static only
	}

	public static TestItemTree.ItemTreeKey createKey(String key) {
		return TestItemTree.ItemTreeKey.of(key);
	}

	public static TestItemTree.ItemTreeKey createKey(int lineNumber) {
		return TestItemTree.ItemTreeKey.of(String.valueOf(lineNumber));
	}

	public static Optional<TestItemTree.TestItemLeaf> retrieveLeaf(String featureUri, TestItemTree testItemTree) {
		return ofNullable(testItemTree.getTestItems().get(createKey(featureUri)));
	}

	public static Optional<TestItemTree.TestItemLeaf> retrieveLeaf(String featureUri, int lineNumber, TestItemTree testItemTree) {
		Optional<TestItemTree.TestItemLeaf> suiteLeaf = retrieveLeaf(featureUri, testItemTree);
		return suiteLeaf.map(leaf -> leaf.getChildItems().get(createKey(lineNumber)));
	}

	public static Optional<TestItemTree.TestItemLeaf> retrieveLeaf(String featureUri, int lineNumber, String text,
			TestItemTree testItemTree) {
		Optional<TestItemTree.TestItemLeaf> testClassLeaf = retrieveLeaf(featureUri, lineNumber, testItemTree);
		return testClassLeaf.map(leaf -> leaf.getChildItems().get(createKey(text)));
	}
}
