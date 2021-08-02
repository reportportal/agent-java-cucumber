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
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class ParameterScenarioReporterTest {

	@CucumberOptions(features = "src/test/resources/features/OneSimpleAndOneScenarioOutline.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class OneSimpleAndOneScenarioOutlineScenarioReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DocStringParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class DocstringParameterTest extends AbstractTestNGCucumberTests {
	}

	@CucumberOptions(features = "src/test/resources/features/DataTableParameter.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class DataTableParameterTest extends AbstractTestNGCucumberTests {
	}

	private static final String DOCSTRING_PARAM = "My very long parameter\nWith some new lines";
	private static final String TABLE_PARAM = Utils.formatDataTable(Arrays.asList(Arrays.asList("key", "value"),
			Arrays.asList("myKey", "myValue")
	));

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList());

	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("nested_step_"))
			.limit(9)
			.collect(Collectors.toList());

	private final List<Pair<String, String>> nestedStepMap = Stream.concat(IntStream.range(0, 4)
					.mapToObj(i -> Pair.of(stepIds.get(0), nestedStepIds.get(i))),
			IntStream.range(4, 9).mapToObj(i -> Pair.of(stepIds.get(1), nestedStepIds.get(i)))
	).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestUtils.mockNestedSteps(client, nestedStepMap);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	public static final List<Pair<String, Object>> PARAMETERS = Arrays.asList(Pair.of("String", "first"), Pair.of("int", 123));

	public static final List<String> STEP_NAMES = Arrays.asList(String.format("When I have parameter %s", PARAMETERS.get(0).getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS.get(1).getValue().toString())
	);

	@Test
	public void verify_agent_creates_correct_step_names() {
		TestUtils.runTests(OneSimpleAndOneScenarioOutlineScenarioReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(2)).startTestItem(same(testId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(stepIds.get(1)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues()
				.stream()
				.filter(e -> e.getName().startsWith("When") || e.getName().startsWith("Then"))
				.collect(Collectors.toList());
		IntStream.range(0, items.size()).forEach(i -> {
			StartTestItemRQ step = items.get(i);
			assertThat(step.getName(), equalTo(STEP_NAMES.get(i)));
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_agent_reports_docstring_parameter() {
		TestUtils.runTests(DocstringParameterTest.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(stepIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<ParameterResource> params = items.get(2).getParameters();
		assertThat(params, allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = params.get(0);
		assertThat(param1.getKey(), equalTo("java.lang.String"));
		assertThat(param1.getValue(), equalTo(DOCSTRING_PARAM));

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeast(2)).log(logCaptor.capture());
		List<String> logs = filterLogs(logCaptor, l -> l.getItemUuid().equals(nestedStepIds.get(2))).stream()
				.map(SaveLogRQ::getMessage)
				.collect(Collectors.toList());

		assertThat(logs, hasSize(2));
		assertThat(logs, hasItem(equalTo("\"\"\"\n" + DOCSTRING_PARAM + "\n\"\"\"")));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_agent_reports_data_table_parameter() {
		TestUtils.runTests(DataTableParameterTest.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(stepIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<ParameterResource> params = items.get(1).getParameters();
		assertThat(params, allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = params.get(0);
		assertThat(param1.getKey(), equalTo("cucumber.api.DataTable"));
		assertThat(param1.getValue(), equalTo(TABLE_PARAM));

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeast(1)).log(logCaptor.capture());
		List<String> logs = filterLogs(logCaptor, l -> l.getItemUuid().equals(nestedStepIds.get(1))).stream()
				.map(SaveLogRQ::getMessage)
				.collect(Collectors.toList());

		assertThat(logs, hasSize(2));
		assertThat(logs, hasItem(equalTo(TABLE_PARAM)));
	}
}
