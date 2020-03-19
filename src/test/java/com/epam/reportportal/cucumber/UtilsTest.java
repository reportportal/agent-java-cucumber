package com.epam.reportportal.cucumber;

import com.epam.ta.reportportal.ws.model.ParameterResource;
import gherkin.formatter.Argument;
import gherkin.formatter.model.Match;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import rp.com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class UtilsTest {

	@Test
	public void retrieveParamsFromMatchWithoutArgumentsTest() {
		Match match = new Match(Collections.emptyList(), "com.epam.test.testMethod()");
		List<ParameterResource> parameters = Utils.getParameters(match);
		assertNotNull(parameters);
		assertTrue(parameters.isEmpty());
	}

	@Test
	public void retrieveParamsFromMatchWithoutArgumentFromParametrizedMethodTest() {
		Match match = new Match(Collections.emptyList(), "com.epam.test.testMethod(String)");
		List<ParameterResource> parameters = Utils.getParameters(match);
		assertNotNull(parameters);
		assertTrue(parameters.isEmpty());
	}

	@Test
	public void retrieveSingleParameter() {
		String parameterValue = "value";
		String parameterType = "String";
		Match match = new Match(Lists.newArrayList(new Argument(1, parameterValue)),
				String.format("com.epam.test.testMethod(%s)", parameterType)
		);
		List<ParameterResource> parameters = Utils.getParameters(match);
		assertNotNull(parameters);
		assertEquals(1, parameters.size());
		parameters.forEach(it -> {
			assertEquals(parameterType, it.getKey());
			assertEquals(parameterValue, it.getValue());
		});
	}

	@Test
	public void retrieveParameters() {
		List<String> parameterTypes = Lists.newArrayList("String", "int");
		List<String> parameterValues = Lists.newArrayList("val1", "val2");
		Match match = new Match(parameterValues.stream().map(it -> new Argument(RandomUtils.nextInt(), it)).collect(Collectors.toList()),
				String.format("com.epam.test.testMethod(%s)", String.join(",", parameterTypes))
		);
		List<ParameterResource> parameters = Utils.getParameters(match);
		assertNotNull(parameters);
		assertEquals(2, parameters.size());
		IntStream.range(0, parameters.size()).forEach(index -> {
			assertEquals(parameterTypes.get(index), parameters.get(index).getKey());
			assertEquals(parameterValues.get(index), parameters.get(index).getValue());
		});
	}
}