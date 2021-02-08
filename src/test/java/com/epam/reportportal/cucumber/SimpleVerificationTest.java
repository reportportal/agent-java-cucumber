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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class SimpleVerificationTest {
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

	public static void verifyRequest(StartTestItemRQ rq, String type, boolean hasStats) {
		assertThat(rq.getType(), allOf(notNullValue(), equalTo(type)));
		assertThat(rq.getStartTime(), notNullValue());
		assertThat(rq.getName(), notNullValue());
		assertThat(rq.isHasStats(), equalTo(hasStats));
	}

	@Test
	public void verify_step_reporter_steps_integrity() {
		TestUtils.runTests(SimpleTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> suiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(suiteCaptor.capture());
		verifyRequest(suiteCaptor.getValue(), "STORY", true);

		ArgumentCaptor<StartTestItemRQ> testCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), testCaptor.capture());
		verifyRequest(testCaptor.getValue(), "SCENARIO", true);

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testId), stepCaptor.capture());

		List<StartTestItemRQ> steps = stepCaptor.getAllValues();
		Set<String> stepTypes = steps.stream().map(StartTestItemRQ::getType).collect(Collectors.toSet());
		assertThat(stepTypes, hasSize(3));
		assertThat(stepTypes, hasItems("BEFORE_TEST", "STEP", "AFTER_TEST"));

		Optional<StartTestItemRQ> beforeTest = steps.stream().filter(s -> "BEFORE_TEST".equals(s.getType())).findAny();
		//noinspection OptionalGetWithoutIsPresent
		verifyRequest(beforeTest.get(), "BEFORE_TEST", true);
		steps.stream().filter(s -> "STEP".equals(s.getType())).forEach(rq -> verifyRequest(rq, "STEP", true));
		Optional<StartTestItemRQ> afterTest = steps.stream().filter(s -> "AFTER_TEST".equals(s.getType())).findAny();
		//noinspection OptionalGetWithoutIsPresent
		verifyRequest(afterTest.get(), "AFTER_TEST", true);
	}

	@Test
	public void verify_scenario_reporter_steps_integrity() {
		TestUtils.mockNestedSteps(client, nestedSteps);
		TestUtils.runTests(SimpleTestScenarioReporter.class);

		ArgumentCaptor<StartTestItemRQ> mainSuiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(mainSuiteCaptor.capture());
		verifyRequest(mainSuiteCaptor.getValue(), "SUITE", true);

		ArgumentCaptor<StartTestItemRQ> suiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), suiteCaptor.capture());
		verifyRequest(suiteCaptor.getValue(), "STORY", true);

		ArgumentCaptor<StartTestItemRQ> testCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(testId), testCaptor.capture());
		verifyRequest(testCaptor.getValue(), "STEP", true);

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(stepIds.get(0)), stepCaptor.capture());

		List<StartTestItemRQ> steps = stepCaptor.getAllValues();
		verifyRequest(steps.get(0), "BEFORE_TEST", false);
		steps.subList(1, steps.size() - 1).forEach(rq -> verifyRequest(rq, "STEP", false));
		verifyRequest(steps.get(steps.size() - 1), "AFTER_TEST", false);
	}
}
