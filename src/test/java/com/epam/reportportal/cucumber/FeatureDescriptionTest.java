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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

public class FeatureDescriptionTest {

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class BellyScenarioReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class BellyStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("test_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	private static final String FEATURE_CODE_REFERENCES = "src/test/resources/features/belly.feature";

	private static final String SCENARIO_CODE_REFERENCES = "src/test/resources/features/belly.feature";

	@Test
	public void verify_code_reference_scenario_reporter() {
		TestUtils.runTests(BellyScenarioReporter.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());
		verify(client, times(1)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();

		StartTestItemRQ feature = items.get(0);
		StartTestItemRQ scenario = items.get(1);

		assertThat(feature.getDescription(), allOf(notNullValue(), equalTo(FEATURE_CODE_REFERENCES)));
		assertThat(scenario.getDescription(), allOf(notNullValue(), equalTo(SCENARIO_CODE_REFERENCES)));
	}

	@Test
	public void verify_code_reference_step_reporter() {
		TestUtils.runTests(BellyStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(captor.capture());
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(0)), any());

		List<StartTestItemRQ> items = captor.getAllValues();
		StartTestItemRQ feature = items.get(0);
		StartTestItemRQ scenario = items.get(1);

		assertThat(feature.getDescription(), allOf(notNullValue(), equalTo(FEATURE_CODE_REFERENCES)));
		assertThat(scenario.getDescription(), allOf(notNullValue(), equalTo(SCENARIO_CODE_REFERENCES)));
	}
}
