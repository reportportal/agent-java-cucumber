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

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.ParameterUtils;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import gherkin.formatter.Argument;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import io.reactivex.annotations.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Strings;
import rp.com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

public class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
	private static final String TABLE_SEPARATOR = "|";
	private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String GET_LOCATION_METHOD_NAME = "getLocation";
	private static final String METHOD_OPENING_BRACKET = "(";
	private static final String METHOD_FIELD_NAME = "method";

	//@formatter:off
	private static final Map<String, ItemStatus> STATUS_MAPPING = ImmutableMap.<String, ItemStatus>builder()
			.put("passed", ItemStatus.PASSED)
			.put("failed", ItemStatus.FAILED)
			.put("skipped", ItemStatus.SKIPPED)
			.put("pending", ItemStatus.SKIPPED)
			.put("undefined", ItemStatus.SKIPPED).build();
	//@formatter:on

	private Utils() {
	}

	public static void finishTestItem(Launch rp, Maybe<String> itemId) {
		finishTestItem(rp, itemId, null);
	}

	public static void finishTestItem(Launch rp, Maybe<String> itemId, ItemStatus status) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return;
		}

		FinishTestItemRQ rq = new FinishTestItemRQ();
		ofNullable(status).ifPresent(s -> rq.setStatus(s.name()));
		rq.setEndTime(Calendar.getInstance().getTime());

		rp.finishTestItem(itemId, rq);

	}

	public static Maybe<String> startNonLeafNode(Launch rp, Maybe<String> rootItemId, String name, String description, String codeRef,
			List<Tag> tags, String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setDescription(description);
		rq.setCodeRef(codeRef);
		rq.setName(name);
		rq.setAttributes(extractAttributes(tags));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);
		if ("STEP".equals(type)) {
			rq.setTestCaseId(ofNullable(Utils.getTestCaseId(codeRef, null)).map(TestCaseIdEntry::getId).orElse(null));
		}

		return rp.startTestItem(rootItemId, rq);
	}

	static void sendLog(final String message, final String level) {
		ReportPortal.emitLog(message, level, Calendar.getInstance().getTime());
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	public static Set<ItemAttributesRQ> extractAttributes(List<Tag> tags) {
		Set<ItemAttributesRQ> result = new HashSet<>();
		for (Tag tag : tags) {
			result.add(new ItemAttributesRQ(null, tag.getName()));
		}
		return result;
	}

	/**
	 * Map Cucumber statuses to RP log levels
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular log level
	 */
	public static String mapLevel(String cukesStatus) {
		if (cukesStatus.equalsIgnoreCase("passed")) {
			return "INFO";
		} else if (cukesStatus.equalsIgnoreCase("skipped")) {
			return "WARN";
		} else {
			return "ERROR";
		}
	}

	/**
	 * Map Cucumber statuses to RP
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular status
	 */
	public static ItemStatus mapStatus(String cukesStatus) {
		if (Strings.isNullOrEmpty(cukesStatus)) {
			return ItemStatus.FAILED;
		}
		ItemStatus status = STATUS_MAPPING.get(cukesStatus.toLowerCase());
		return null == status ? ItemStatus.FAILED : status;
	}

	/**
	 * Generate statement representation
	 *
	 * @param stmt   - Cucumber statement
	 * @param prefix - substring to be prepended at the beginning (optional)
	 * @param suffix - substring to be appended at the end (optional)
	 * @return transformed string
	 */
	public static String buildStatementName(BasicStatement stmt, String prefix, String suffix) {
		return (prefix == null ? "" : prefix) + stmt.getKeyword() + stmt.getName() + (suffix == null ? "" : suffix);
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	public static String buildMultilineArgument(Step step) {
		List<DataTableRow> table = step.getRows();
		DocString ds = step.getDocString();
		StringBuilder marg = new StringBuilder();
		StringBuilder markDownSeparator = new StringBuilder();
		if (table != null) {
			marg.append("\r\n\r\n");
			for (Row row : table) {
				marg.append(TABLE_SEPARATOR);
				for (String cell : row.getCells()) {
					marg.append(" ").append(cell).append(" ").append(TABLE_SEPARATOR);
				}
				marg.append("\r\n");
				if (markDownSeparator.length() == 0) {
					markDownSeparator.append(TABLE_SEPARATOR).append("-").append(TABLE_SEPARATOR);
					marg.append(markDownSeparator);
					marg.append("\r\n");
				}
			}
		}

		if (ds != null) {
			marg.append(DOCSTRING_DECORATOR).append(ds.getValue()).append(DOCSTRING_DECORATOR);
		}
		return marg.toString();
	}

	@Nullable
	public static Set<ItemAttributesRQ> getAttributes(Match match) {
		try {
			Method method = retrieveMethod(match);
			Attributes attributesAnnotation = method.getAnnotation(Attributes.class);
			if (attributesAnnotation != null) {
				return AttributeParser.retrieveAttributes(attributesAnnotation);
			}
		} catch (NoSuchFieldException | IllegalAccessException e) {
			return null;
		}
		return null;
	}

	@Nullable
	public static String getCodeRef(Match match) {

		try {
			Field stepDefinitionField = match.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
			stepDefinitionField.setAccessible(true);
			Object javaStepDefinition = stepDefinitionField.get(match);
			Method getLocationMethod = javaStepDefinition.getClass().getDeclaredMethod(GET_LOCATION_METHOD_NAME, boolean.class);
			getLocationMethod.setAccessible(true);
			String fullCodeRef = String.valueOf(getLocationMethod.invoke(javaStepDefinition, true));
			return fullCodeRef != null ? fullCodeRef.substring(0, fullCodeRef.indexOf(METHOD_OPENING_BRACKET)) : null;
		} catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			return null;
		}

	}

	@Nonnull
	public static String getCodeRef(@Nonnull String uri, int line) {
		return uri + ":" + line;
	}

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @param suffix   - substring to be appended at the end (optional)
	 * @return transformed string
	 */
	//TODO: pass Node as argument, not test event
	public static String buildNodeName(String prefix, String infix, String argument, String suffix) {
		return buildName(prefix, infix, argument, suffix);
	}

	private static String buildName(String prefix, String infix, String argument, String suffix) {
		return (prefix == null ? "" : prefix) + infix + argument + (suffix == null ? "" : suffix);
	}

	static List<ParameterResource> getParameters(String codeRef, Match match) {
		List<Pair<String, String>> params = ofNullable(match.getArguments()).map(a -> IntStream.range(0, a.size())
				.mapToObj(i -> Pair.of("arg" + i, a.get(i).getVal()))
				.collect(Collectors.toList())).orElse(null);
		return ParameterUtils.getParameters(codeRef, params);
	}

	private static Method retrieveMethod(Match match) throws NoSuchFieldException, IllegalAccessException {
		Field stepDefinitionField = match.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(match);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	private static final Function<List<Argument>, List<?>> ARGUMENTS_TRANSFORM = arguments -> ofNullable(arguments).map(args -> args.stream()
			.map(Argument::getVal)
			.collect(Collectors.toList())).orElse(null);

	@SuppressWarnings("unchecked")
	public static TestCaseIdEntry getTestCaseId(Match match, String codeRef) {
		try {
			Method method = retrieveMethod(match);
			return TestCaseIdUtils.getTestCaseId(
					method.getAnnotation(TestCaseId.class),
					method,
					codeRef,
					(List<Object>) ARGUMENTS_TRANSFORM.apply(match.getArguments())
			);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			return getTestCaseId(codeRef, match.getArguments());
		}
	}

	@SuppressWarnings("unchecked")
	private static TestCaseIdEntry getTestCaseId(String codeRef, List<Argument> arguments) {
		return TestCaseIdUtils.getTestCaseId(codeRef, (List<Object>) ARGUMENTS_TRANSFORM.apply(arguments));
	}

	@Nonnull
	public static String getDescription(@Nonnull String uri) {
		return uri;
	}

	public static StartTestItemRQ buildStartStepRequest(String stepPrefix, Step step, Match match, boolean hasStats) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(Utils.buildStatementName(step, stepPrefix, null));
		rq.setDescription(Utils.buildMultilineArgument(step));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("STEP");
		rq.setHasStats(hasStats);
		String codeRef = Utils.getCodeRef(match);
		rq.setParameters(Utils.getParameters(codeRef, match));
		rq.setCodeRef(codeRef);
		rq.setTestCaseId(ofNullable(Utils.getTestCaseId(match, codeRef)).map(TestCaseIdEntry::getId).orElse(null));
		rq.setAttributes(Utils.getAttributes(match));
		return rq;
	}
}
