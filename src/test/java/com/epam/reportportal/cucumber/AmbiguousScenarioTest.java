package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class AmbiguousScenarioTest {
	@CucumberOptions(features = "src/test/resources/features/AmbiguousTest.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class SimpleTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/AmbiguousTest.feature", glue = {
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

	@Test
	public void verify_step_reporter_ambiguous_item() {
		TestUtils.runTests(SimpleTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testId), stepCaptor.capture());

		List<StartTestItemRQ> rqs = stepCaptor.getAllValues();
		List<Integer> stepIdxs = IntStream.range(0, rqs.size())
				.filter(i -> "STEP".equals(rqs.get(i).getType()))
				.boxed()
				.collect(Collectors.toList());
		assertThat(stepIdxs, hasSize(1));

		ArgumentCaptor<String> finishIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<FinishTestItemRQ> finishRqCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(5)).finishTestItem(finishIdCaptor.capture(), finishRqCaptor.capture());

		List<String> finishIds = finishIdCaptor.getAllValues();
		List<Integer> finishIdxs = IntStream.range(0, finishIds.size())
				.filter(i -> finishIds.get(i).equals(stepIds.get(stepIdxs.get(0))))
				.boxed()
				.collect(Collectors.toList());
		assertThat(finishIdxs, hasSize(1));

		List<FinishTestItemRQ> finishRqs = finishRqCaptor.getAllValues();
		FinishTestItemRQ finishRq = finishRqs.get(finishIdxs.get(0));
		assertThat(finishRq.getStatus(), equalTo("FAILED"));

		finishIdxs = IntStream.range(0, finishIds.size()).filter(i -> finishIds.get(i).equals(testId)).boxed().collect(Collectors.toList());
		assertThat(finishIdxs, hasSize(1));

		finishRq = finishRqs.get(finishIdxs.get(0));
		assertThat(finishRq.getStatus(), equalTo("FAILED"));
	}

	@Test
	public void verify_scenario_reporter_ambiguous_item() {
		TestUtils.mockNestedSteps(client, nestedSteps);
		TestUtils.runTests(SimpleTestScenarioReporter.class);

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(stepIds.get(0)), stepCaptor.capture());

		List<StartTestItemRQ> rqs = stepCaptor.getAllValues();
		List<Integer> stepIdxs = IntStream.range(0, rqs.size())
				.filter(i -> "STEP".equals(rqs.get(i).getType()))
				.boxed()
				.collect(Collectors.toList());
		assertThat(stepIdxs, hasSize(1));

		ArgumentCaptor<String> finishIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<FinishTestItemRQ> finishRqCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(6)).finishTestItem(finishIdCaptor.capture(), finishRqCaptor.capture());

		List<String> finishIds = finishIdCaptor.getAllValues();
		List<Integer> finishIdxs = IntStream.range(0, finishIds.size())
				.filter(i -> finishIds.get(i).equals(nestedStepIds.get(stepIdxs.get(0))))
				.boxed()
				.collect(Collectors.toList());
		assertThat(finishIdxs, hasSize(1));

		List<FinishTestItemRQ> finishRqs = finishRqCaptor.getAllValues();
		FinishTestItemRQ finishRq = finishRqs.get(finishIdxs.get(0));
		assertThat(finishRq.getStatus(), equalTo("FAILED"));

		finishIdxs = IntStream.range(0, finishIds.size())
				.filter(i -> finishIds.get(i).equals(stepIds.get(0)))
				.boxed()
				.collect(Collectors.toList());
		assertThat(finishIdxs, hasSize(1));

		finishRq = finishRqs.get(finishIdxs.get(0));
		assertThat(finishRq.getStatus(), equalTo("FAILED"));
	}
}
