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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;

public class NestedStepsStepReporterTest {

	@CucumberOptions(features = "src/test/resources/features/NestedStepsFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class NestedStepsStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList());
	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("nested_step_"))
			.limit(3)
			.collect(Collectors.toList());
	private final String nestedNestedStepId = CommonUtils.namedId("double_nested_step_");
	private final List<Pair<String, String>> firstLevelNestedStepIds = Stream.concat(Stream.of(Pair.of(stepIds.get(1),
			nestedStepIds.get(0)
	)), nestedStepIds.stream().skip(1).map(i -> Pair.of(stepIds.get(2), i)))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestUtils.mockNestedSteps(client, firstLevelNestedStepIds);
		TestUtils.mockNestedSteps(client, Pair.of(nestedStepIds.get(0), nestedNestedStepId));
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	public static final List<String> FIRST_LEVEL_NAMES = Arrays.asList("A step inside step",
			"A step with parameters",
			"A step with attributes"
	);

	@Test
	public void test_step_reporter_nested_steps() {
		TestUtils.runTests(NestedStepsStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(4)).startTestItem(same(testId), any());

		ArgumentCaptor<StartTestItemRQ> firstLevelCaptor1 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(stepIds.get(1)), firstLevelCaptor1.capture());

		StartTestItemRQ firstLevelRq1 = firstLevelCaptor1.getValue();
		assertThat(firstLevelRq1.getName(), equalTo(FIRST_LEVEL_NAMES.get(0)));
		assertThat(firstLevelRq1.isHasStats(), equalTo(Boolean.FALSE));

		ArgumentCaptor<StartTestItemRQ> firstLevelCaptor2 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(2)), firstLevelCaptor2.capture());

		List<StartTestItemRQ> firstLevelRqs2 = firstLevelCaptor2.getAllValues();
		IntStream.range(1, FIRST_LEVEL_NAMES.size()).forEach(i -> {
			assertThat(firstLevelRqs2.get(i - 1).getName(), equalTo(FIRST_LEVEL_NAMES.get(i)));
			assertThat(firstLevelRqs2.get(i - 1).isHasStats(), equalTo(Boolean.FALSE));
		});

		StartTestItemRQ stepWithAttributes = firstLevelRqs2.get(1);
		Set<ItemAttributesRQ> attributes = stepWithAttributes.getAttributes();
		assertThat(attributes, allOf(notNullValue(), hasSize(2)));
		List<Pair<String, String>> kvAttributes = attributes.stream()
				.map(a -> Pair.of(a.getKey(), a.getValue()))
				.collect(Collectors.toList());
		List<Pair<String, String>> keyAndValueList = kvAttributes.stream().filter(kv -> kv.getKey() != null).collect(Collectors.toList());
		assertThat(keyAndValueList, hasSize(1));
		assertThat(keyAndValueList.get(0).getKey(), equalTo("key"));
		assertThat(keyAndValueList.get(0).getValue(), equalTo("value"));

		List<Pair<String, String>> tagList = kvAttributes.stream().filter(kv -> kv.getKey() == null).collect(Collectors.toList());
		assertThat(tagList, hasSize(1));
		assertThat(tagList.get(0).getValue(), equalTo("tag"));

		ArgumentCaptor<StartTestItemRQ> secondLevelCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(nestedStepIds.get(0)), secondLevelCaptor.capture());

		StartTestItemRQ secondLevelRq = secondLevelCaptor.getValue();
		assertThat(secondLevelRq.getName(), equalTo("A step inside nested step"));
		assertThat(secondLevelRq.isHasStats(), equalTo(Boolean.FALSE));
	}
}
