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

package com.epam.reportportal.cucumber.integration.callback.scenario;

import com.epam.reportportal.cucumber.AbstractReporter;
import com.epam.reportportal.cucumber.util.ItemTreeUtils;
import com.epam.reportportal.service.tree.ItemTreeReporter;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;

import java.util.Calendar;

public class CallbackReportingSteps {
	public static final String STEP_TEXT = "I have a step for callback reporting";

	@Given(STEP_TEXT)
	public void a_step_for_callback_reporting() throws InterruptedException {
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@After
	public void after(Scenario scenario) throws InterruptedException {
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		if (scenario.getName().contains("failure")) {
			ItemTreeUtils.retrieveLeaf(
					"src/test/resources/features/CallbackReportingScenario.feature",
					3,
					STEP_TEXT,
					AbstractReporter.ITEM_TREE
			).ifPresent(itemLeaf -> {
				sendFinishRequest(itemLeaf, "FAILED", "secondTest");
				attachLog(itemLeaf);
			});
		} else {
			ItemTreeUtils.retrieveLeaf(
					"src/test/resources/features/CallbackReportingScenario.feature",
					6,
					STEP_TEXT,
					AbstractReporter.ITEM_TREE
			).ifPresent(itemLeaf -> sendFinishRequest(itemLeaf, "PASSED", "firstTest"));
		}
	}

	private void sendFinishRequest(TestItemTree.TestItemLeaf testResultLeaf, String status, String description) {
		FinishTestItemRQ finishTestItemRQ = new FinishTestItemRQ();
		finishTestItemRQ.setDescription(description);
		finishTestItemRQ.setStatus(status);
		finishTestItemRQ.setEndTime(Calendar.getInstance().getTime());
		//noinspection ResultOfMethodCallIgnored
		ItemTreeReporter.finishItem(AbstractReporter.getReportPortal().getClient(),
				finishTestItemRQ,
				AbstractReporter.ITEM_TREE.getLaunchId(),
				testResultLeaf
		)
				.blockingGet();
	}

	private void attachLog(TestItemTree.TestItemLeaf testItemLeaf) {
		ItemTreeReporter.sendLog(AbstractReporter.getReportPortal().getClient(),
				"ERROR",
				"Error message",
				Calendar.getInstance().getTime(),
				AbstractReporter.ITEM_TREE.getLaunchId(),
				testItemLeaf
		);
	}
}
