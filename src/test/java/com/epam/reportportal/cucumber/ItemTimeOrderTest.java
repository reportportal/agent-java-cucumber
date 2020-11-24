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

import com.epam.reportportal.cucumber.integration.TestScenarioReporterWithPause;
import com.epam.reportportal.cucumber.integration.TestStepReporterWithPause;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ItemTimeOrderTest {
	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporterWithPause" })
	public static class BellyScenarioReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporterWithPause" })
	public static class BellyStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());

	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestScenarioReporterWithPause.RP.set(reportPortal);
		TestStepReporterWithPause.RP.set(reportPortal);
	}

	@Test
	public void verify_time_order_scenario_reporter() {
		TestUtils.runTests(BellyScenarioReporter.class);

		ArgumentCaptor<StartLaunchRQ> launchCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(client).startLaunch(launchCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> itemCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(itemCaptor.capture());
		verify(client).startTestItem(same(suiteId), itemCaptor.capture());
		verify(client).startTestItem(same(testId), itemCaptor.capture());

		Date startTime = launchCaptor.getValue().getStartTime();
		List<StartTestItemRQ> items = itemCaptor.getAllValues();
		for (StartTestItemRQ item : items) {
			assertThat(item.getStartTime(), allOf(notNullValue(), greaterThanOrEqualTo(startTime)));
			startTime = item.getStartTime();
		}
	}

	@Test
	public void verify_time_order_step_reporter() {
		TestUtils.runTests(BellyStepReporter.class);

		ArgumentCaptor<StartLaunchRQ> launchCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(client).startLaunch(launchCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> itemCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(itemCaptor.capture());
		verify(client).startTestItem(same(suiteId), itemCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(5)).startTestItem(same(testId), stepCaptor.capture());

		Date startTime = launchCaptor.getValue().getStartTime();
		List<StartTestItemRQ> items = itemCaptor.getAllValues();
		for (StartTestItemRQ item : items) {
			assertThat(item.getStartTime(), allOf(notNullValue(), greaterThanOrEqualTo(startTime)));
			startTime = item.getStartTime();
		}

		for (StartTestItemRQ step : stepCaptor.getAllValues()) {
			assertThat(step.getStartTime(), allOf(notNullValue(), greaterThanOrEqualTo(startTime)));
		}
	}
}
