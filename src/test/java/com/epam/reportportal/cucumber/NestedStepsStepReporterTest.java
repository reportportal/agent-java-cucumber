package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

public class NestedStepsStepReporterTest {

	@CucumberOptions(features = "src/test/resources/features/NestedStepsFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class NestedStepsStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList());
	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("nested_step_"))
			.limit(3)
			.collect(Collectors.toList());
	private final String nestedNestedStepId = CommonUtils.namedId("double_nested_step_");
	private final List<Pair<String, String>> firstLevelNestedStepIds = Stream.concat(Stream.of(Pair.of(stepIds.get(1),
			nestedStepIds.get(0)
	)), nestedStepIds.stream().skip(1).map(i -> Pair.of(stepIds.get(2), i)))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestUtils.mockNestedSteps(client, firstLevelNestedStepIds);
		TestUtils.mockNestedSteps(client, Pair.of(nestedStepIds.get(0), nestedNestedStepId));
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	public static final List<String> FIRST_LEVEL_NAMES = Arrays.asList("A step inside step",
			"A step with parameters",
			"A step with attributes"
	);

	@Test
	public void test_step_reporter_nested_steps() {
		TestUtils.runTests(NestedStepsStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(4)).startTestItem(same(testId), any());

		ArgumentCaptor<String> parentIdCapture = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<StartTestItemRQ> itemRequestCapture = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1 + 4 + 3 + 1)).startTestItem(parentIdCapture.capture(), itemRequestCapture.capture());

		List<String> parentIdList = parentIdCapture.getAllValues();
		List<StartTestItemRQ> itemRequestList = itemRequestCapture.getAllValues();

		Map<String, List<StartTestItemRQ>> firstLevelRequests = IntStream.range(0, parentIdList.size())
				.filter(i -> stepIds.contains(parentIdList.get(i)))
				.mapToObj(i -> Pair.of(parentIdList.get(i), itemRequestList.get(i)))
				.collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
		assertThat(firstLevelRequests.keySet(), hasSize(2));
		Iterator<Map.Entry<String, List<StartTestItemRQ>>> entryIterator = firstLevelRequests.entrySet().iterator();
		Map.Entry<String, List<StartTestItemRQ>> firstPair = entryIterator.next();
		Map.Entry<String, List<StartTestItemRQ>> secondPair = entryIterator.next();

		if (firstPair.getValue().size() > secondPair.getValue().size()) {
			Map.Entry<String, List<StartTestItemRQ>> tmp = firstPair;
			firstPair = secondPair;
			secondPair = tmp;
		}

		StartTestItemRQ firstLevelRq1 = firstPair.getValue().get(0);
		assertThat(firstLevelRq1.getName(), equalTo(FIRST_LEVEL_NAMES.get(0)));
		assertThat(firstLevelRq1.isHasStats(), equalTo(Boolean.FALSE));

		List<StartTestItemRQ> firstLevelRqs2 = secondPair.getValue();
		IntStream.range(1, FIRST_LEVEL_NAMES.size()).forEach(i -> {
			assertThat(firstLevelRqs2.get(i - 1).getName(), equalTo(FIRST_LEVEL_NAMES.get(i)));
			assertThat(firstLevelRqs2.get(i - 1).isHasStats(), equalTo(Boolean.FALSE));
		});

		StartTestItemRQ stepWithAttributes = firstLevelRqs2.get(1);
		Set<ItemAttributesRQ> attributes = stepWithAttributes.getAttributes();
		assertThat(attributes, allOf(notNullValue(), hasSize(2)));
		List<Pair<String, String>> kvAttributes = attributes.stream()
				.map(a -> Pair.of(a.getKey(), a.getValue()))
				.collect(Collectors.toList());
		List<Pair<String, String>> keyAndValueList = kvAttributes.stream().filter(kv -> kv.getKey() != null).collect(Collectors.toList());
		assertThat(keyAndValueList, hasSize(1));
		assertThat(keyAndValueList.get(0).getKey(), equalTo("key"));
		assertThat(keyAndValueList.get(0).getValue(), equalTo("value"));

		List<Pair<String, String>> tagList = kvAttributes.stream().filter(kv -> kv.getKey() == null).collect(Collectors.toList());
		assertThat(tagList, hasSize(1));
		assertThat(tagList.get(0).getValue(), equalTo("tag"));

		Map<String, List<StartTestItemRQ>> secondLevelSteps = IntStream.range(0, parentIdList.size())
				.filter(i -> nestedStepIds.contains(parentIdList.get(i)))
				.mapToObj(i -> Pair.of(parentIdList.get(i), itemRequestList.get(i)))
				.collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
		assertThat(secondLevelSteps.entrySet(), hasSize(1));
		List<StartTestItemRQ> secondLevelRqs = secondLevelSteps.values().iterator().next();
		assertThat(secondLevelRqs, hasSize(1));

		StartTestItemRQ secondLevelRq = secondLevelRqs.get(0);
		assertThat(secondLevelRq.getName(), equalTo("A step inside nested step"));
		assertThat(secondLevelRq.isHasStats(), equalTo(Boolean.FALSE));
	}
}
