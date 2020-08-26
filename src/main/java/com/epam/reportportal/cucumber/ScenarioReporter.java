/*
 * Copyright 2019 EPAM Systems
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

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import java.util.Calendar;

/**
 * Cucumber reporter for ReportPortal that reports scenarios as test methods.
 * <p>
 * Mapping between Cucumber and ReportPortal is as follows:
 * <ul>
 * <li>feature - TEST</li>
 * <li>scenario - STEP</li>
 * <li>step - log item</li>
 * </ul>
 * <p>
 * Dummy "Root Test Suite" is created because in current implementation of RP
 * test items cannot be immediate children of a launch
 * <p>
 * Background steps and hooks are reported as part of corresponding scenarios.
 * Outline example rows are reported as individual scenarios with [ROW NUMBER]
 * after the name.
 *
 * @author Sergey_Gvozdyukevich
 */
public class ScenarioReporter extends AbstractReporter {
	private static final String RP_STORY_TYPE = "SUITE";
	private static final String RP_TEST_TYPE = "STORY";
	private static final String RP_STEP_TYPE = "STEP";

	private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioReporter.class);

	protected Supplier<Maybe<String>> rootSuiteId = Suppliers.memoize(() -> {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName("Root User Story");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(RP_STORY_TYPE);
		return launch.get().startTestItem(rq);
	});

	@Override
	protected void beforeStep(Step step, Match match) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		StartTestItemRQ rq = Utils.buildStartStepRequest(context.getStepPrefix(), step, match, false);
		rq.setHasStats(false);
		context.setCurrentStepId(launch.get().startTestItem(context.getId(), rq));
	}

	@Override
	protected void afterStep(Result result) {
		reportResult(result, null);
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Utils.finishTestItem(launch.get(), context.getCurrentStepId(), Utils.mapStatus(result.getStatus()));
		context.setCurrentStepId(null);
	}

	@Override
	protected void beforeHooks(Boolean isBefore) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setHasStats(false);

		rq.setName(isBefore ? "Before hooks" : "After hooks");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(isBefore ? "BEFORE_TEST" : "AFTER_TEST");

		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		context.setHookStepId(launch.get().startTestItem(context.getId(), rq));
		context.setHookStatus(ItemStatus.PASSED);
	}

	@Override
	protected void afterHooks(Boolean isBefore) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Utils.finishTestItem(launch.get(), context.getHookStepId(), context.getHookStatus());
		context.setHookStepId(null);
	}

	@Override
	protected void hookFinished(Match match, Result result, Boolean isBefore) {
		reportResult(result, null);
		if (result.getStatus().equals("failed")) {
			getCurrentScenarioContext().setHookStatus(ItemStatus.FAILED);
		}
	}

	@Override
	protected String getFeatureTestItemType() {
		return RP_TEST_TYPE;
	}

	@Override
	protected String getScenarioTestItemType() {
		return RP_STEP_TYPE;
	}

	@Override
	protected Maybe<String> getRootItemId() {
		return rootSuiteId.get();
	}

	@Override
	protected void afterLaunch() {
		if (currentFeatureUri.get() == null) {
			LOGGER.debug("There is no scenarios in the launch");
			return;
		}
		Utils.finishTestItem(launch.get(), rootSuiteId.get());
		rootSuiteId = null;
		super.afterLaunch();
	}
}
