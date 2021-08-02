package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.feature.ManualStepReporterSteps;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

public class ManualStepReporterTest {
	@CucumberOptions(features = "src/test/resources/features/ManualStepReporter.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class SimpleTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/ManualStepReporter.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class SimpleTestScenarioReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList());

	// Step reporter
	private final List<String> stepNestedStepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
			.limit(3)
			.collect(Collectors.toList());
	private final List<Pair<String, String>> stepNestedSteps = Stream.concat(
			Stream.of(Pair.of(stepIds.get(1), stepNestedStepIds.get(0))),
			stepNestedStepIds.stream().skip(1).map(s -> Pair.of(stepIds.get(2), s))
	).collect(Collectors.toList());

	// Scenario reporter
	private final List<String> scenarioNestedStepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
			.limit(4)
			.collect(Collectors.toList());
	private final List<Pair<String, String>> scenarioNestedSteps = scenarioNestedStepIds.stream()
			.map(s -> Pair.of(stepIds.get(0), s))
			.collect(Collectors.toList());
	private final List<String> scenarioSecondNestedStepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
			.limit(3)
			.collect(Collectors.toList());
	private final List<Pair<String, String>> scenarioSecondNestedSteps = Stream.concat(
			Stream.of(Pair.of(scenarioNestedStepIds.get(1), scenarioSecondNestedStepIds.get(0))),
			scenarioSecondNestedStepIds.stream().skip(1).map(s -> Pair.of(scenarioNestedStepIds.get(2), s))
	).collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	private static void verifyStepStart(StartTestItemRQ step, String stepName) {
		assertThat(step.getName(), equalTo(stepName));
		assertThat(step.isHasStats(), equalTo(Boolean.FALSE));
		assertThat(step.getType(), equalTo("STEP"));
	}

	private static void verifyLogEntry(SaveLogRQ firstStepLog, String stepId, String duringSecondNestedStepLog) {
		assertThat(firstStepLog.getItemUuid(), equalTo(stepId));
		assertThat(firstStepLog.getMessage(), containsString(duringSecondNestedStepLog));
		assertThat(firstStepLog.getFile(), nullValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_step_reporter_steps_integrity() {
		TestUtils.mockNestedSteps(client, stepNestedSteps);
		TestUtils.runTests(SimpleTestStepReporter.class);

		verify(client, times(4)).startTestItem(same(testId), any());
		ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(stepIds.get(1)), firstStepCaptor.capture());
		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, times(7)).log(logCaptor.capture());
		StartTestItemRQ firstStep = firstStepCaptor.getValue();
		List<SaveLogRQ> logs = filterLogs(logCaptor, l -> true);
		logs = logs.subList(1, logs.size() - 1);
		SaveLogRQ firstStepLog = logs.get(0);

		verifyStepStart(firstStep, ManualStepReporterSteps.FIRST_NAME);
		verifyLogEntry(firstStepLog, stepNestedStepIds.get(0), ManualStepReporterSteps.FIRST_NESTED_STEP_LOG);

		ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(2)), secondStepCaptor.capture());
		List<StartTestItemRQ> secondSteps = secondStepCaptor.getAllValues();
		List<SaveLogRQ> secondStepLogs = logs.subList(1, logs.size());

		StartTestItemRQ secondStep = secondSteps.get(0);
		verifyStepStart(secondStep, ManualStepReporterSteps.SECOND_NAME);
		verifyLogEntry(secondStepLogs.get(0), stepNestedStepIds.get(1), ManualStepReporterSteps.DURING_SECOND_NESTED_STEP_LOG);
		verifyLogEntry(secondStepLogs.get(1), stepNestedStepIds.get(1), ManualStepReporterSteps.SECOND_NESTED_STEP_LOG);

		StartTestItemRQ thirdStep = secondSteps.get(1);
		verifyStepStart(thirdStep, ManualStepReporterSteps.THIRD_NAME);

		SaveLogRQ pugLog = secondStepLogs.get(2);
		assertThat(pugLog.getItemUuid(), equalTo(stepNestedStepIds.get(2)));
		assertThat(pugLog.getMessage(), emptyString());
		assertThat(pugLog.getFile(), notNullValue());

		verifyLogEntry(secondStepLogs.get(3), stepNestedStepIds.get(2), ManualStepReporterSteps.THIRD_NESTED_STEP_LOG);

		ArgumentCaptor<String> finishIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<FinishTestItemRQ> finishRqCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(9)).finishTestItem(finishIdCaptor.capture(), finishRqCaptor.capture());
		List<String> finishIds = finishIdCaptor.getAllValues();
		List<FinishTestItemRQ> finishRqs = finishRqCaptor.getAllValues();
		List<FinishTestItemRQ> nestedStepFinishes = IntStream.range(0, finishIds.size())
				.filter(i -> stepNestedStepIds.contains(finishIds.get(i)))
				.mapToObj(finishRqs::get)
				.collect(Collectors.toList());

		assertThat(nestedStepFinishes.get(0).getStatus(), equalTo("PASSED"));
		assertThat(nestedStepFinishes.get(1).getStatus(), equalTo("PASSED"));
		assertThat(nestedStepFinishes.get(2).getStatus(), equalTo("FAILED"));

		List<FinishTestItemRQ> stepFinishes = IntStream.range(0, finishIds.size())
				.filter(i -> !stepNestedStepIds.contains(finishIds.get(i)))
				.mapToObj(finishRqs::get)
				.collect(Collectors.toList());

		assertThat(stepFinishes.get(0).getStatus(), equalTo("PASSED"));
		assertThat(stepFinishes.get(1).getStatus(), equalTo("PASSED"));
		assertThat(stepFinishes.get(2).getStatus(), equalTo("FAILED"));
		assertThat(stepFinishes.get(3).getStatus(), equalTo("PASSED"));
		assertThat(stepFinishes.get(4).getStatus(), equalTo("FAILED"));
		assertThat(stepFinishes.get(5).getStatus(), equalTo("FAILED"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_scenario_reporter_steps_integrity() {
		TestUtils.mockNestedSteps(client, scenarioNestedSteps);
		TestUtils.mockNestedSteps(client, scenarioSecondNestedSteps);
		TestUtils.runTests(SimpleTestScenarioReporter.class);

		verify(client, times(4)).startTestItem(same(stepIds.get(0)), any());
		ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(scenarioNestedStepIds.get(1)), firstStepCaptor.capture());
		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, times(7)).log(logCaptor.capture());
		StartTestItemRQ firstStep = firstStepCaptor.getValue();
		List<SaveLogRQ> logs = filterLogs(logCaptor, l -> true);

		SaveLogRQ firstStepLog = logs.get(1);
		verifyStepStart(firstStep, ManualStepReporterSteps.FIRST_NAME);
		verifyLogEntry(firstStepLog, scenarioSecondNestedStepIds.get(0), ManualStepReporterSteps.FIRST_NESTED_STEP_LOG);

		ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(scenarioNestedStepIds.get(2)), secondStepCaptor.capture());
		List<StartTestItemRQ> secondSteps = secondStepCaptor.getAllValues();
		List<SaveLogRQ> secondStepLogs = logs.subList(2, logs.size() - 1);

		StartTestItemRQ secondStep = secondSteps.get(0);
		verifyStepStart(secondStep, ManualStepReporterSteps.SECOND_NAME);
		verifyLogEntry(secondStepLogs.get(0), scenarioSecondNestedStepIds.get(1), ManualStepReporterSteps.DURING_SECOND_NESTED_STEP_LOG);
		verifyLogEntry(secondStepLogs.get(1), scenarioSecondNestedStepIds.get(1), ManualStepReporterSteps.SECOND_NESTED_STEP_LOG);

		StartTestItemRQ thirdStep = secondSteps.get(1);
		verifyStepStart(thirdStep, ManualStepReporterSteps.THIRD_NAME);

		SaveLogRQ pugLog = secondStepLogs.get(2);
		assertThat(pugLog.getItemUuid(), equalTo(scenarioSecondNestedStepIds.get(2)));
		assertThat(pugLog.getMessage(), emptyString());
		assertThat(pugLog.getFile(), notNullValue());

		verifyLogEntry(secondStepLogs.get(3), scenarioSecondNestedStepIds.get(2), ManualStepReporterSteps.THIRD_NESTED_STEP_LOG);

		ArgumentCaptor<String> finishIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<FinishTestItemRQ> finishRqCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(10)).finishTestItem(finishIdCaptor.capture(), finishRqCaptor.capture());
		List<String> finishIds = finishIdCaptor.getAllValues();
		List<FinishTestItemRQ> finishRqs = finishRqCaptor.getAllValues();
		List<FinishTestItemRQ> nestedStepFinishes = IntStream.range(0, finishIds.size())
				.filter(i -> scenarioSecondNestedStepIds.contains(finishIds.get(i)))
				.mapToObj(finishRqs::get)
				.collect(Collectors.toList());

		assertThat(nestedStepFinishes.get(0).getStatus(), equalTo("PASSED"));
		assertThat(nestedStepFinishes.get(1).getStatus(), equalTo("PASSED"));
		assertThat(nestedStepFinishes.get(2).getStatus(), equalTo("FAILED"));

		List<FinishTestItemRQ> stepFinishes = IntStream.range(0, finishIds.size())
				.filter(i -> !scenarioSecondNestedStepIds.contains(finishIds.get(i)))
				.mapToObj(finishRqs::get)
				.collect(Collectors.toList());

		assertThat(stepFinishes.get(0).getStatus(), equalTo("PASSED"));
		assertThat(stepFinishes.get(1).getStatus(), equalTo("PASSED"));
		assertThat(stepFinishes.get(2).getStatus(), equalTo("FAILED"));
		assertThat(stepFinishes.get(3).getStatus(), equalTo("PASSED"));
		assertThat(stepFinishes.get(4).getStatus(), equalTo("FAILED"));
		assertThat(stepFinishes.get(5).getStatus(), equalTo("FAILED"));
		assertThat(stepFinishes.get(6).getStatus(), equalTo("FAILED"));
	}
}
