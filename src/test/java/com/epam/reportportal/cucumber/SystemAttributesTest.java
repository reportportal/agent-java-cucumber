/*
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SystemAttributesTest {
	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class SimpleTestStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	@BeforeEach
	public void setup() {
		ListenerParameters params = TestUtils.standardParameters();
		params.setAttributes(Collections.singleton(new ItemAttributesRQ("key", "value")));
		ReportPortal reportPortal = ReportPortal.create(client, params, executorService);
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@Test
	public void verify_start_launch_request_contains_system_attributes() {
		TestUtils.runTests(SimpleTestStepReporter.class);

		ArgumentCaptor<StartLaunchRQ> startCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(client).startLaunch(startCaptor.capture());

		StartLaunchRQ launchStart = startCaptor.getValue();

		Set<ItemAttributesRQ> attributes = launchStart.getAttributes();
		assertThat(attributes, allOf(notNullValue(), hasSize(greaterThan(0))));
		Set<String> attributesStr = attributes.stream()
				.filter(ItemAttributesRQ::isSystem)
				.map(e -> e.getKey() + ":" + e.getValue())
				.collect(Collectors.toSet());
		assertThat(attributesStr, hasSize(4));
		assertThat(attributesStr, hasItem("skippedIssue:true"));
		assertThat(attributesStr, hasItem("agent:test-agent|test-1.0"));
		assertThat(attributesStr, hasItem(startsWith("os:")));
		assertThat(attributesStr, hasItem(startsWith("jvm:")));
	}
}
