package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.restendpoint.http.MultiPartRequest;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
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

	@CucumberOptions(features = "src/test/resources/features/BasicInlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class InlineParametersTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DocStringParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class DocstringParameterTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DataTableParameter.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class DataTableParameterTestStepReporter extends AbstractTestNGCucumberTests {
	}

	private static final String DOCSTRING_PARAM = "My very long parameter\nWith some new lines";
	private static final String TABLE_PARAM = Utils.formatDataTable(Arrays.asList(Arrays.asList("key", "value"),
			Arrays.asList("myKey", "myValue")
	));

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("test_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, TestUtils.standardParameters(), executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@SuppressWarnings("rawtypes")
	public static final Pair[] PARAMETERS = new Pair[] { Pair.of("java.lang.String", "first"), Pair.of("int", String.valueOf(123)),
			Pair.of("java.lang.String", "second"), Pair.of("int", String.valueOf(12345)), Pair.of("java.lang.String", "third"),
			Pair.of("int", String.valueOf(12345678)) };

	public static final String[] STEP_NAMES = new String[] { String.format("When I have parameter %s", PARAMETERS[0].getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS[1].getValue()),
			String.format("When I have parameter %s", PARAMETERS[2].getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS[3].getValue()),
			String.format("When I have parameter %s", PARAMETERS[4].getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS[5].getValue()) };

	@Test
	public void verify_agent_retrieves_parameters_from_request() {
		TestUtils.runTests(RunOutlineParametersTestStepReporter.class);

		verify(client, times(3)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testIds.get(0)), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(1)), captor.capture());
		verify(client, times(5)).startTestItem(same(testIds.get(2)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<StartTestItemRQ> filteredItems = items.stream()
				.filter(i -> "STEP".equals(i.getType()) && !i.getName().startsWith("Given"))
				.collect(Collectors.toList());
		assertThat(filteredItems, hasSize(6));
		filteredItems.forEach(i -> assertThat(i.getParameters(), hasSize(1)));

		List<String> itemNames = filteredItems.stream().map(StartTestItemRQ::getName).collect(Collectors.toList());
		IntStream.range(0, itemNames.size())
				.filter(i -> i % 2 == 0)
				.forEach(i -> assertThat(itemNames.subList(i, i + 2), containsInAnyOrder(STEP_NAMES[i], STEP_NAMES[i + 1])));

		List<Pair<String, String>> params = filteredItems.stream()
				.flatMap(i -> i.getParameters().stream())
				.map(p -> Pair.of(p.getKey(), p.getValue()))
				.collect(Collectors.toList());
		assertThat(params, containsInAnyOrder(PARAMETERS));
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

	@Test
	public void verify_inline_parameters() {
		TestUtils.runTests(InlineParametersTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items.get(1).getParameters(), allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = items.get(1).getParameters().get(0);
		assertThat(param1.getKey(), equalTo("int"));
		assertThat(param1.getValue(), equalTo("42"));

		assertThat(items.get(2).getParameters(), allOf(notNullValue(), hasSize(1)));
		ParameterResource param2 = items.get(2).getParameters().get(0);
		assertThat(param2.getKey(), equalTo("java.lang.String"));
		assertThat(param2.getValue(), equalTo("string"));

		assertThat(items.get(3).getParameters(), allOf(notNullValue(), hasSize(1)));
		ParameterResource param3 = items.get(3).getParameters().get(0);
		assertThat(param3.getKey(), equalTo("my name"));
		assertThat(param3.getValue(), equalTo("string"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_docstring_parameters() {
		TestUtils.runTests(DocstringParameterTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<ParameterResource> params = items.get(2).getParameters();
		assertThat(params, allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = params.get(0);
		assertThat(param1.getKey(), equalTo("java.lang.String"));
		assertThat(param1.getValue(), equalTo(DOCSTRING_PARAM));

		ArgumentCaptor<MultiPartRequest> logCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, times(4)).log(logCaptor.capture());
		List<String> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.flatMap(l -> ((List<SaveLogRQ>) l.getRequest()).stream())
				.filter(l -> l.getItemUuid().equals(tests.get(0).getValue().get(2)))
				.map(SaveLogRQ::getMessage)
				.collect(Collectors.toList());

		assertThat(logs, hasSize(2));
		assertThat(logs, not(hasItem(equalTo("\"\"\"\n" + DOCSTRING_PARAM + "\n\"\"\""))));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_data_table_parameters() {
		TestUtils.runTests(DataTableParameterTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<ParameterResource> params = items.get(1).getParameters();
		assertThat(params, allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = params.get(0);
		assertThat(param1.getKey(), equalTo("cucumber.api.DataTable"));
		assertThat(param1.getValue(), equalTo(TABLE_PARAM));

		ArgumentCaptor<MultiPartRequest> logCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, times(3)).log(logCaptor.capture());
		List<MultiPartRequest> logRqs = logCaptor.getAllValues();

		List<String> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.flatMap(l -> ((List<SaveLogRQ>) l.getRequest()).stream())
				.filter(l -> l.getItemUuid().equals(tests.get(0).getValue().get(1)))
				.map(SaveLogRQ::getMessage)
				.collect(Collectors.toList());

		assertThat(logs, hasSize(1));
		assertThat(logs, not(hasItem(equalTo(TABLE_PARAM))));
	}
}
