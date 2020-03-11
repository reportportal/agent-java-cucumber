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
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import gherkin.formatter.Argument;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import io.reactivex.annotations.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Function;
import rp.com.google.common.base.Strings;
import rp.com.google.common.collect.ImmutableMap;
import rp.com.google.common.collect.Lists;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
	private static final String TABLE_SEPARATOR = "|";
	private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String GET_LOCATION_METHOD_NAME = "getLocation";
	private static final String METHOD_OPENING_BRACKET = "(";
	private static final String METHOD_FIELD_NAME = "method";
	private static final String MATCHED_COLUMNS_FIELD_NAME = "matchedColumns";

	//@formatter:off
	private static final Map<String, String> STATUS_MAPPING = ImmutableMap.<String, String>builder()
			.put("passed", Statuses.PASSED)
			.put("skipped", Statuses.SKIPPED)
			.put("pending", Statuses.SKIPPED)
			//TODO replace with NOT_IMPLEMENTED in future
			.put("undefined", Statuses.SKIPPED).build();
	//@formatter:on

	private Utils() {

	}

	public static void finishTestItem(Launch rp, Maybe<String> itemId) {
		finishTestItem(rp, itemId, null);
	}

	public static void finishTestItem(Launch rp, Maybe<String> itemId, String status) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return;
		}

		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setStatus(status);
		rq.setEndTime(Calendar.getInstance().getTime());

		rp.finishTestItem(itemId, rq);

	}

	public static Maybe<String> startNonLeafNode(Launch rp, Maybe<String> rootItemId, String name, String description, List<Tag> tags,
			String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setDescription(description);
		rq.setName(name);
		rq.setAttributes(extractAttributes(tags));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);

		return rp.startTestItem(rootItemId, rq);
	}

	public static void sendLog(final String message, final String level, final File file) {
		ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
			@Override
			public SaveLogRQ apply(String item) {
				SaveLogRQ rq = new SaveLogRQ();
				rq.setMessage(message);
				rq.setItemUuid(item);
				rq.setLevel(level);
				rq.setLogTime(Calendar.getInstance().getTime());
				if (file != null) {
					rq.setFile(file);
				}
				return rq;
			}
		});
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	public static Set<ItemAttributesRQ> extractAttributes(List<Tag> tags) {
		Set<ItemAttributesRQ> result = new HashSet<ItemAttributesRQ>();
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
		String mapped = null;
		if (cukesStatus.equalsIgnoreCase("passed")) {
			mapped = "INFO";
		} else if (cukesStatus.equalsIgnoreCase("skipped")) {
			mapped = "WARN";
		} else {
			mapped = "ERROR";
		}
		return mapped;
	}

	/**
	 * Map Cucumber statuses to RP
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular status
	 */
	public static String mapStatus(String cukesStatus) {
		if (Strings.isNullOrEmpty(cukesStatus)) {
			return Statuses.FAILED;
		}
		String status = STATUS_MAPPING.get(cukesStatus.toLowerCase());
		return null == status ? Statuses.FAILED : status;
	}

	/**
	 * Generate statement representation
	 *
	 * @param stmt   - Cucumber statement
	 * @param prefix - substring to be prepended at the beginning (optional)
	 * @param infix  - substring to be inserted between keyword and name
	 * @param suffix - substring to be appended at the end (optional)
	 * @return transformed string
	 */
	public static String buildStatementName(BasicStatement stmt, String prefix, String infix, String suffix) {
		return (prefix == null ? "" : prefix) + stmt.getKeyword() + infix + stmt.getName() + (suffix == null ? "" : suffix);
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

	@Nullable
	public static TestCaseIdEntry getTestCaseId(Match match, String codeRef) {
		try {
			Method method = retrieveMethod(match);
			TestCaseId testCaseIdAnnotation = method.getAnnotation(TestCaseId.class);
			return testCaseIdAnnotation != null ?
					getTestCaseId(testCaseIdAnnotation, method, match.getArguments()) :
					getTestCaseId(codeRef, match.getArguments());
		} catch (NoSuchFieldException | IllegalAccessException e) {
			return getTestCaseId(codeRef, match.getArguments());
		}
	}

	static List<ParameterResource> getParameters(Match match) {
		List<ParameterResource> parameters = Lists.newArrayList();
		String text = match.getLocation();
		Matcher matcher = Pattern.compile("\\(.+\\)$").matcher(text);
		Optional<String> parameterType = Optional.empty();
		if (matcher.find()) {
			parameterType = Optional.of(text.substring(matcher.start() + 1, matcher.end() - 1));
		}
		parameterType.ifPresent(it -> parameters.addAll(match.getArguments().stream().map(arg -> {
			ParameterResource parameterResource = new ParameterResource();
			parameterResource.setKey(it);
			parameterResource.setValue(arg.getVal());
			return parameterResource;
		}).collect(Collectors.toList())));
		return parameters;
	}

	private static Method retrieveMethod(Match match) throws NoSuchFieldException, IllegalAccessException {
		Field stepDefinitionField = match.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(match);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	@Nullable
	private static TestCaseIdEntry getTestCaseId(TestCaseId testCaseId, Method method, List<Argument> arguments) {
		if (testCaseId.parametrized()) {
			List<String> values = new ArrayList<String>(arguments.size());
			for (Argument argument : arguments) {
				values.add(argument.getVal());
			}
			return TestCaseIdUtils.getParameterizedTestCaseId(method, values.toArray());
		} else {
			return new TestCaseIdEntry(testCaseId.value(), testCaseId.hashCode());
		}
	}

	private static TestCaseIdEntry getTestCaseId(String codeRef, List<Argument> arguments) {
		List<String> values = new ArrayList<String>(arguments.size());
		for (Argument argument : arguments) {
			values.add(argument.getVal());
		}
		return new TestCaseIdEntry(StringUtils.join(codeRef, values.toArray()),
				Arrays.deepHashCode(new Object[] { codeRef, values.toArray() })
		);
	}
}
