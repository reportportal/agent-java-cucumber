/*
 *  Copyright 2020 EPAM Systems
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.feature.FailedSteps;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.restendpoint.http.MultiPartRequest;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class FailedTest {

	private static final String EXPECTED_ERROR = "java.lang.IllegalStateException: " + FailedSteps.ERROR_MESSAGE;

	@CucumberOptions(features = "src/test/resources/features/FailedScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class FailedScenarioReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/FailedScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class FailedStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());
	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("nested_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedSteps = nestedStepIds.stream()
			.map(id -> Pair.of(stepIds.get(0), id))
			.collect(Collectors.toList());

	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestUtils.mockNestedSteps(client, nestedSteps);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@Test
	public void verify_failed_step_reporting_scenario_reporter() {
		TestUtils.runTests(FailedScenarioReporter.class);

		verify(client).startTestItem(any());
		verify(client).startTestItem(same(suiteId), any());
		verify(client).startTestItem(same(testId), any());
		verify(client, times(3)).startTestItem(same(stepIds.get(0)), any());
		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(nestedStepIds.get(1)), finishCaptor.capture());
		verify(client).finishTestItem(same(stepIds.get(0)), finishCaptor.capture());
		verify(client).finishTestItem(same(testId), finishCaptor.capture());

		List<FinishTestItemRQ> finishRqs = finishCaptor.getAllValues();
		finishRqs.subList(0, finishRqs.size() - 1).forEach(e -> assertThat(e.getStatus(), equalTo(ItemStatus.FAILED.name())));

		ArgumentCaptor<MultiPartRequest> logRqCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, atLeastOnce()).log(logRqCaptor.capture());

		List<MultiPartRequest> logs = logRqCaptor.getAllValues();
		List<SaveLogRQ> expectedErrorList = logs.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.map(MultiPartRequest.MultiPartSerialized::getRequest)
				.filter(l -> l instanceof List)
				.flatMap(l -> ((List<?>) l).stream())
				.filter(l -> l instanceof SaveLogRQ)
				.map(l -> (SaveLogRQ) l)
				.filter(l -> l.getMessage() != null && l.getMessage().startsWith(EXPECTED_ERROR))
				.collect(Collectors.toList());
		assertThat(expectedErrorList, hasSize(1));
		SaveLogRQ expectedError = expectedErrorList.get(0);
		assertThat(expectedError.getItemUuid(), equalTo(nestedStepIds.get(1)));
	}

	@Test
	public void verify_failed_step_reporting_step_reporter() {
		TestUtils.runTests(FailedStepReporter.class);

		verify(client).startTestItem(any());
		verify(client).startTestItem(same(suiteId), any());
		verify(client, times(3)).startTestItem(same(testId), any());
		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepIds.get(1)), finishCaptor.capture());
		verify(client).finishTestItem(same(testId), finishCaptor.capture());

		finishCaptor.getAllValues().forEach(e -> assertThat(e.getStatus(), equalTo(ItemStatus.FAILED.name())));

		ArgumentCaptor<MultiPartRequest> logRqCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, atLeastOnce()).log(logRqCaptor.capture());

		List<MultiPartRequest> logs = logRqCaptor.getAllValues();
		List<SaveLogRQ> expectedErrorList = logs.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.map(MultiPartRequest.MultiPartSerialized::getRequest)
				.filter(l -> l instanceof List)
				.flatMap(l -> ((List<?>) l).stream())
				.filter(l -> l instanceof SaveLogRQ)
				.map(l -> (SaveLogRQ) l)
				.filter(l -> l.getMessage() != null && l.getMessage().startsWith(EXPECTED_ERROR))
				.collect(Collectors.toList());
		assertThat(expectedErrorList, hasSize(1));
		SaveLogRQ expectedError = expectedErrorList.get(0);
		assertThat(expectedError.getItemUuid(), equalTo(stepIds.get(1)));
	}
}
