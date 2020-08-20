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

import java.util.Calendar;

/**
 * Cucumber reporter for ReportPortal that reports individual steps as test
 * methods.
 * <p>
 * Mapping between Cucumber and ReportPortal is as follows:
 * <ul>
 * <li>feature - SUITE</li>
 * <li>scenario - TEST</li>
 * <li>step - STEP</li>
 * </ul>
 * Background steps are reported as part of corresponding scenarios. Outline
 * example rows are reported as individual scenarios with [ROW NUMBER] after the
 * name. Hooks are reported as BEFORE/AFTER_METHOD items (NOTE: all screenshots
 * created in hooks will be attached to these, and not to the actual failing
 * steps!)
 *
 * @author Sergey_Gvozdyukevich
 */
public class StepReporter extends AbstractReporter {

	public StepReporter() {
		super();
	}

	@Override
	protected Maybe<String> getRootItemId() {
		return null;
	}

	@Override
	protected void beforeStep(Step step, Match match) {
		RunningContext.ScenarioContext context = currentScenarioContext.get();
		context.setCurrentStepId(launch.get()
				.startTestItem(context.getId(), Utils.buildStartStepRequest(currentFeatureContext.get().getStepPrefix(), step, match)));
	}

	@Override
	protected void afterStep(Result result) {
		reportResult(result, null);
		Utils.finishTestItem(launch.get(), currentScenarioContext.get().getCurrentStepId(), Utils.mapStatus(result.getStatus()));
		currentScenarioContext.get().setCurrentStepId(null);
	}

	@Override
	protected void beforeHooks(Boolean isBefore) {
		StartTestItemRQ rq = new StartTestItemRQ();

		rq.setName(isBefore ? "Before hooks" : "After hooks");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(isBefore ? "BEFORE_TEST" : "AFTER_TEST");

		currentScenarioContext.get().setHookStepId(launch.get().startTestItem(currentScenarioContext.get().getId(), rq));
		currentScenarioContext.get().setHookStatus(ItemStatus.PASSED);
	}

	@Override
	protected void afterHooks(Boolean isBefore) {
		Utils.finishTestItem(launch.get(), currentScenarioContext.get().getHookStepId(), currentScenarioContext.get().getHookStatus());
		currentScenarioContext.get().setHookStepId(null);
	}

	@Override
	protected void hookFinished(Match match, Result result, Boolean isBefore) {
		reportResult(result, (isBefore ? "Before" : "After") + " hook: " + match.getLocation());
		if (result.getStatus().equals("failed")) {
			currentScenarioContext.get().setHookStatus(ItemStatus.FAILED);
		}
	}

	@Override
	protected String getFeatureTestItemType() {
		return "SUITE";
	}

	@Override
	protected String getScenarioTestItemType() {
		return "SCENARIO";
	}
}
