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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

public class TestCaseIdTest {

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RunBellyTestScenarioReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class RunBellyTestStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());

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

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	public void shouldSendCaseIdWhenParametrizedScenarioReporter() {
		TestUtils.runTests(RunBellyTestScenarioReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(testId), captor.capture());

		StartTestItemRQ rq = captor.getValue();
		assertThat(rq.getTestCaseId(), equalTo("src/test/resources/features/belly.feature:4"));
	}

	@Test
	public void shouldSendCaseIdWhenParametrizedStepReporter() {
		TestUtils.runTests(RunBellyTestStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testId), captor.capture());

		List<StartTestItemRQ> steps = captor.getAllValues().stream().skip(1).limit(3).collect(Collectors.toList());

		assertThat(steps.get(0).getTestCaseId(),
				equalTo("com.epam.reportportal.cucumber.integration.feature.BellyStepdefs.I_have_cukes_in_my_belly[42]")
		);
		assertThat(steps.get(1).getTestCaseId(), equalTo("com.epam.reportportal.cucumber.integration.feature.BellyStepdefs.I_wait[1]"));
		assertThat(steps.get(2).getTestCaseId(),
				equalTo("com.epam.reportportal.cucumber.integration.feature.BellyStepdefs.my_belly_should_growl[]")
		);
	}
}
