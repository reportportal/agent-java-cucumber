package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class AttributeReportingTest {
	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class SimpleTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class SimpleTestScenarioReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());
	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedSteps = nestedStepIds.stream()
			.map(s -> Pair.of(stepIds.get(0), s))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	private static void verifyAttributes(Collection<ItemAttributesRQ> attributes, Collection<Pair<String, String>> values) {
		assertThat(attributes, hasSize(values.size()));
		Set<Pair<String, String>> attributePairs = attributes.stream()
				.map(a -> Pair.of(a.getKey(), a.getValue()))
				.collect(Collectors.toSet());
		values.forEach(v -> assertThat(attributePairs, hasItem(v)));
	}

	private static void verifyAnnotationAttributes(List<StartTestItemRQ> testSteps) {
		Set<ItemAttributesRQ> stepAttributes = testSteps.get(0).getAttributes();
		verifyAttributes(stepAttributes, Collections.singleton(Pair.of("key", "value")));

		stepAttributes = testSteps.get(1).getAttributes();
		verifyAttributes(
				stepAttributes,
				new HashSet<>(Arrays.asList(Pair.of("key1", "value1"), Pair.of("key2", "value2"), Pair.of("k1", "v"), Pair.of("k2", "v")))
		);

		stepAttributes = testSteps.get(2).getAttributes();
		verifyAttributes(stepAttributes, new HashSet<>(Arrays.asList(Pair.of(null, "v1"), Pair.of(null, "v2"))));
	}

	@Test
	public void verify_step_reporter_attributes() {
		TestUtils.runTests(SimpleTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> suiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(suiteCaptor.capture());

		assertThat(suiteCaptor.getValue().getAttributes(), anyOf(emptyIterable(), nullValue()));

		ArgumentCaptor<StartTestItemRQ> testCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), testCaptor.capture());
		verifyAttributes(testCaptor.getValue().getAttributes(), Collections.singleton(Pair.of(null, "@ok")));

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testId), stepCaptor.capture());

		List<StartTestItemRQ> allSteps = stepCaptor.getAllValues();
		List<StartTestItemRQ> testSteps = allSteps.stream()
				.filter(s -> !(s.getName().startsWith("Before") || s.getName().startsWith("After"))).collect(Collectors.toList());
		verifyAnnotationAttributes(testSteps);
		ArrayList<StartTestItemRQ> beforeAfter = new ArrayList<>(allSteps);
		beforeAfter.removeAll(testSteps);
		beforeAfter.forEach(s->assertThat(s.getAttributes(), anyOf(emptyIterable(), nullValue())));
	}

	@Test
	public void verify_scenario_reporter_attributes() {
		TestUtils.mockNestedSteps(client, nestedSteps);
		TestUtils.runTests(SimpleTestScenarioReporter.class);

		ArgumentCaptor<StartTestItemRQ> mainSuiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(mainSuiteCaptor.capture());
		assertThat(mainSuiteCaptor.getValue().getAttributes(), anyOf(emptyIterable(), nullValue()));

		ArgumentCaptor<StartTestItemRQ> suiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), suiteCaptor.capture());
		assertThat(mainSuiteCaptor.getValue().getAttributes(), anyOf(emptyIterable(), nullValue()));

		ArgumentCaptor<StartTestItemRQ> testCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(testId), testCaptor.capture());
		verifyAttributes(testCaptor.getValue().getAttributes(), Collections.singleton(Pair.of(null, "@ok")));

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(stepIds.get(0)), stepCaptor.capture());

		List<StartTestItemRQ> allSteps = stepCaptor.getAllValues();
		List<StartTestItemRQ> testSteps = allSteps.stream()
				.filter(s -> !(s.getName().startsWith("Before") || s.getName().startsWith("After"))).collect(Collectors.toList());
		verifyAnnotationAttributes(testSteps);
		ArrayList<StartTestItemRQ> beforeAfter = new ArrayList<>(allSteps);
		beforeAfter.removeAll(testSteps);
		beforeAfter.forEach(s->assertThat(s.getAttributes(), anyOf(emptyIterable(), nullValue())));
	}
}
