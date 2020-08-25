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
import static org.mockito.Mockito.*;

public class NestedStepsScenarioReporterTest {

	@CucumberOptions(features = "src/test/resources/features/NestedStepsFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class NestedStepsScenarioReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final String stepId = CommonUtils.namedId("step_");
	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("nested_step_"))
			.limit(4)
			.collect(Collectors.toList());
	private final List<String> nestedNestedStepIds = Stream.generate(() -> CommonUtils.namedId("double_nested_step_"))
			.limit(3)
			.collect(Collectors.toList());
	private final String nestedNestedNestedStepId = CommonUtils.namedId("triple_nested_step_");
	private final List<Pair<String, String>> firstLevelNestedStepIds = nestedStepIds.stream()
			.map(s -> Pair.of(stepId, s))
			.collect(Collectors.toList());

	private final List<Pair<String, String>> secondLevelNestedStepIds = Stream.concat(Stream.of(Pair.of(nestedStepIds.get(1),
			nestedNestedStepIds.get(0)
			)),
			nestedNestedStepIds.stream().skip(1).map(i -> Pair.of(nestedStepIds.get(2), i))
	).collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepId);
		TestUtils.mockNestedSteps(client, firstLevelNestedStepIds);
		TestUtils.mockNestedSteps(client, secondLevelNestedStepIds);
		TestUtils.mockNestedSteps(client, Pair.of(nestedNestedStepIds.get(0), nestedNestedNestedStepId));
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	public static final List<String> FIRST_LEVEL_NAMES = Arrays.asList("Before hooks",
			"Given I have a step",
			"When I have one more step",
			"After hooks"
	);

	public static final List<String> SECOND_LEVEL_NAMES = Arrays.asList("A step inside step",
			"A step with parameters",
			"A step with attributes"
	);

	@Test
	public void test_scenario_reporter_nested_steps() {
		TestUtils.runTests(NestedStepsScenarioReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(captor.capture());
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());
		verify(client, times(1)).startTestItem(same(testId), captor.capture());
		List<StartTestItemRQ> parentItems = captor.getAllValues();
		parentItems.forEach(i -> assertThat(i.isHasStats(), anyOf(equalTo(Boolean.TRUE))));

		ArgumentCaptor<StartTestItemRQ> firstLevelCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(stepId), firstLevelCaptor.capture());

		List<StartTestItemRQ> firstLevelRqs = firstLevelCaptor.getAllValues();
		IntStream.range(0, firstLevelRqs.size()).forEach(i -> {
			StartTestItemRQ rq = firstLevelRqs.get(i);
			assertThat(rq.isHasStats(), equalTo(Boolean.FALSE));
			assertThat(rq.getName(), equalTo(FIRST_LEVEL_NAMES.get(i)));
		});

		ArgumentCaptor<StartTestItemRQ> secondLevelCaptor1 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(nestedStepIds.get(1)), secondLevelCaptor1.capture());

		StartTestItemRQ secondLevelRq1 = secondLevelCaptor1.getValue();
		assertThat(secondLevelRq1.getName(), equalTo(SECOND_LEVEL_NAMES.get(0)));
		assertThat(secondLevelRq1.isHasStats(), equalTo(Boolean.FALSE));

		ArgumentCaptor<StartTestItemRQ> secondLevelCaptor2 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(nestedStepIds.get(2)), secondLevelCaptor2.capture());

		List<StartTestItemRQ> secondLevelRqs2 = secondLevelCaptor2.getAllValues();
		IntStream.range(1, SECOND_LEVEL_NAMES.size()).forEach(i -> {
			assertThat(secondLevelRqs2.get(i - 1).getName(), equalTo(SECOND_LEVEL_NAMES.get(i)));
			assertThat(secondLevelRqs2.get(i - 1).isHasStats(), equalTo(Boolean.FALSE));
		});

		StartTestItemRQ stepWithAttributes = secondLevelRqs2.get(1);
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

		ArgumentCaptor<StartTestItemRQ> thirdLevelCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(nestedNestedStepIds.get(0)), thirdLevelCaptor.capture());

		StartTestItemRQ thirdLevelRq = thirdLevelCaptor.getValue();
		assertThat(thirdLevelRq.getName(), equalTo("A step inside nested step"));
		assertThat(thirdLevelRq.isHasStats(), equalTo(Boolean.FALSE));
	}
}
