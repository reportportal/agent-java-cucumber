package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
			.limit(2)
			.collect(Collectors.toList());
	private final String nestedNestedStepId = CommonUtils.namedId("double_nested_step_");
	private final List<Pair<String, String>> firstLevelNestedStepIds = nestedStepIds.stream()
			.map(s -> Pair.of(stepId, s))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepId);
		TestUtils.mockNestedSteps(client, firstLevelNestedStepIds);
		TestUtils.mockNestedSteps(client, Pair.of(nestedStepIds.get(0), nestedNestedStepId));
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	public void test_scenario_reporter_nested_steps() {
		TestUtils.runTests(NestedStepsScenarioReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(1)).startTestItem(same(testId), any());
		ArgumentCaptor<StartTestItemRQ> firstLevelCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(stepId), firstLevelCaptor.capture());

	}
}
