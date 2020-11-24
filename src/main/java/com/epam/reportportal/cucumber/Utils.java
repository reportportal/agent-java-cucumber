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
import gherkin.formatter.Argument;
import gherkin.formatter.model.Match;
import rp.com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class Utils {
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String METHOD_FIELD_NAME = "method";

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

}
