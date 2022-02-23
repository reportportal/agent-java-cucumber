/*
 * Copyright 2019 EPAM Systems
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
package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ItemStatus;
import com.google.common.collect.ImmutableMap;
import gherkin.formatter.Argument;
import gherkin.formatter.model.Match;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

public class Utils {
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String METHOD_FIELD_NAME = "method";
	public static final String ONE_SPACE = "\u00A0";
	private static final String NEW_LINE = "\r\n";
	public static final String TABLE_INDENT = "\u00A0\u00A0\u00A0\u00A0";
	public static final String TABLE_COLUMN_SEPARATOR = "|";
	public static final String TABLE_ROW_SEPARATOR = "-";

	//@formatter:off
	public static final Map<String, ItemStatus> STATUS_MAPPING = ImmutableMap.<String, ItemStatus>builder()
			.put("passed", ItemStatus.PASSED)
			.put("failed", ItemStatus.FAILED)
			.put("skipped", ItemStatus.SKIPPED)
			.put("pending", ItemStatus.SKIPPED)
			.put("undefined", ItemStatus.SKIPPED).build();

	public static final Map<String, String> LOG_LEVEL_MAPPING = ImmutableMap.<String, String>builder()
			.put("passed", "INFO")
			.put("failed", "ERROR")
			.put("skipped", "WARN")
			.put("pending", "WARN")
			.put("undefined", "WARN").build();
	//@formatter:on

	private Utils() {
	}

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @return transformed string
	 */
	@Nonnull
	public static String buildName(@Nullable String prefix, @Nullable String infix, @Nullable String argument) {
		return (prefix == null ? "" : prefix) + infix + argument;
	}

	@Nullable
	public static Method retrieveMethod(@Nonnull Match match) throws NoSuchFieldException, IllegalAccessException {
		Field stepDefinitionField = match.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(match);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	public static final Function<List<Argument>, List<?>> ARGUMENTS_TRANSFORM = arguments -> ofNullable(arguments).map(args -> args.stream()
			.map(Argument::getVal)
			.collect(Collectors.toList())).orElse(null);

	/**
	 * Converts a table represented as List of Lists to a formatted table string
	 *
	 * @param table a table object
	 * @return string representation of the table
	 */
	@Nonnull
	public static String formatDataTable(@Nonnull final List<List<String>> table) {
		StringBuilder result = new StringBuilder();
		int tableLength = table.stream().mapToInt(List::size).max().orElse(-1);
		List<Iterator<String>> iterList = table.stream().map(List::iterator).collect(Collectors.toList());
		List<Integer> colSizes = IntStream.range(0, tableLength)
				.mapToObj(n -> iterList.stream().filter(Iterator::hasNext).map(Iterator::next).collect(Collectors.toList()))
				.map(col -> col.stream().mapToInt(String::length).max().orElse(0))
				.collect(Collectors.toList());

		boolean header = true;
		for (List<String> row : table) {
			result.append(TABLE_INDENT).append(TABLE_COLUMN_SEPARATOR);
			for (int i = 0; i < row.size(); i++) {
				String cell = row.get(i);
				int maxSize = colSizes.get(i) - cell.length() + 2;
				int lSpace = maxSize / 2;
				int rSpace = maxSize - lSpace;
				IntStream.range(0, lSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(cell);
				IntStream.range(0, rSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(TABLE_COLUMN_SEPARATOR);
			}
			if (header) {
				header = false;
				result.append(NEW_LINE);
				result.append(TABLE_INDENT).append(TABLE_COLUMN_SEPARATOR);
				for (int i = 0; i < row.size(); i++) {
					int maxSize = colSizes.get(i) + 2;
					IntStream.range(0, maxSize).forEach(j -> result.append(TABLE_ROW_SEPARATOR));
					result.append(TABLE_COLUMN_SEPARATOR);
				}
			}
			result.append(NEW_LINE);
		}
		return result.toString().trim();
	}
}
