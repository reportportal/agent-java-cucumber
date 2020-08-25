package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.ParameterResource;
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

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class ParameterStepReporterTest {

	@CucumberOptions(features = "src/test/resources/features/BasicScenarioOutlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class RunOutlineParametersTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/TwoScenarioOutlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class RunTwoOutlineParametersTestStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("test_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, ? extends Collection<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	public static final List<Pair<String, Object>> PARAMETERS = Arrays.asList(
			Pair.of("String", "first"),
			Pair.of("int", 123),
			Pair.of("String", "second"),
			Pair.of("int", 12345),
			Pair.of("String", "third"),
			Pair.of("int", 12345678)
	);

	public static final List<String> STEP_NAMES = Arrays.asList(
			String.format("When I have parameter %s", PARAMETERS.get(0).getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS.get(1).getValue().toString()),
			String.format("When I have parameter %s", PARAMETERS.get(2).getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS.get(3).getValue().toString()),
			String.format("When I have parameter %s", PARAMETERS.get(4).getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS.get(5).getValue().toString())
	);

	@Test
	public void verify_agent_retrieves_parameters_from_request() {
		TestUtils.runTests(RunOutlineParametersTestStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(3)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testIds.get(0)), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(1)), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(2)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<StartTestItemRQ> filteredItems = items.stream()
				.filter(i -> "STEP".equals(i.getType()) && !i.getName().startsWith("Given"))
				.collect(Collectors.toList());
		IntStream.range(1, filteredItems.size()).mapToObj(i -> Pair.of(i, filteredItems.get(i))).forEach(e -> {
			StartTestItemRQ step = e.getValue();
			assertThat(step, notNullValue());
			String expectedName = STEP_NAMES.get(e.getKey());
			assertThat(step.getName(), equalTo(expectedName));
			assertThat(step.getParameters(), allOf(notNullValue(), hasSize(1)));
			ParameterResource param = e.getValue().getParameters().get(0);
			Pair<String, Object> expectedParam = PARAMETERS.get(e.getKey());

			assertThat(param.getKey(), equalTo(expectedParam.getKey()));
			assertThat(param.getValue(), equalTo(expectedParam.getValue().toString()));
		});
	}

	@Test
	public void verify_agent_retrieves_two_parameters_from_request() {
		TestUtils.runTests(RunTwoOutlineParametersTestStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(3)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testIds.get(0)), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(1)), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(2)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<StartTestItemRQ> twoParameterItems = items.stream()
				.filter(i -> "STEP".equals(i.getType()) && i.getName().startsWith("Given"))
				.collect(Collectors.toList());
		List<StartTestItemRQ> oneParameterItems = items.stream()
				.filter(i -> "STEP".equals(i.getType()) && i.getName().startsWith("Then"))
				.collect(Collectors.toList());
		twoParameterItems.forEach(i -> assertThat(i.getParameters(), allOf(notNullValue(), hasSize(2))));
		oneParameterItems.forEach(i -> assertThat(i.getParameters(), allOf(notNullValue(), hasSize(1))));
	}
}
