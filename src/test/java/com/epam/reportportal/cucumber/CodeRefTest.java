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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

public class CodeRefTest {

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

	@CucumberOptions(features = "src/test/resources/features/TwoScenarioInOne.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class TwoFeaturesStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("test_")).limit(2).collect(Collectors.toList());
	private final List<Pair<String, ? extends Collection<String>>> tests = testIds.stream()
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

	private static final String FEATURE_CODE_REFERENCES = "src/test/resources/features/belly.feature:0";

	private static final String SCENARIO_CODE_REFERENCES = "src/test/resources/features/belly.feature:4";

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

		assertThat(feature.getCodeRef(),
				allOf(notNullValue(), equalTo(FEATURE_CODE_REFERENCES))
		);
		assertThat(scenario.getCodeRef(),
				allOf(notNullValue(), equalTo(SCENARIO_CODE_REFERENCES))
		);
	}

	private static final List<String> STEP_CODE_REFERENCE = Arrays.asList(
			"com.epam.reportportal.cucumber.integration.feature.BellyStepdefs.I_have_cukes_in_my_belly",
			"com.epam.reportportal.cucumber.integration.feature.BellyStepdefs.I_wait",
			"com.epam.reportportal.cucumber.integration.feature.BellyStepdefs.my_belly_should_growl"
	);

	@Test
	public void verify_code_reference_step_reporter() {
		TestUtils.runTests(BellyStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();

		StartTestItemRQ scenario = items.get(0);
		List<StartTestItemRQ> steps = items.subList(2, items.size());

		assertThat(scenario.getCodeRef(),
				allOf(notNullValue(), equalTo(SCENARIO_CODE_REFERENCES))
		);

		IntStream.range(0, STEP_CODE_REFERENCE.size())
				.forEach(i -> assertThat(steps.get(i).getCodeRef(), allOf(notNullValue(), equalTo(STEP_CODE_REFERENCE.get(i)))));
	}

	private static final List<String> TWO_FEATURES_CODE_REFERENCES = Arrays.asList("src/test/resources/features/TwoScenarioInOne.feature:3",
			"src/test/resources/features/TwoScenarioInOne.feature:7");

	private static final List<String> TWO_STEPS_CODE_REFERENCE = Arrays.asList(
			"com.epam.reportportal.cucumber.integration.feature.EmptySteps.i_have_empty_step",
			"com.epam.reportportal.cucumber.integration.feature.EmptySteps.i_have_another_empty_step"
	);

	@Test
	public void verify_code_reference_two_features_step_reporter() {
		TestUtils.runTests(TwoFeaturesStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(suiteId), captor.capture());
		verify(client, times(4)).startTestItem(same(testIds.get(0)), captor.capture());
		verify(client, times(4)).startTestItem(same(testIds.get(1)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<StartTestItemRQ> suites = items.subList(0, 2);
		List<StartTestItemRQ> steps = items.subList(2, items.size());

		IntStream.range(1, TWO_FEATURES_CODE_REFERENCES.size()).forEach(i -> assertThat(suites.get(i).getCodeRef(), allOf(notNullValue(),
				equalTo(TWO_FEATURES_CODE_REFERENCES.get(i))
		)));

		IntStream.range(1, TWO_STEPS_CODE_REFERENCE.size() - 1)
				.forEach(i -> assertThat(steps.get(i).getCodeRef(), allOf(notNullValue(), equalTo(TWO_STEPS_CODE_REFERENCE.get(i)))));
	}
}
