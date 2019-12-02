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

import com.epam.reportportal.listeners.Statuses;
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
	protected Maybe<String> currentStepId;
	protected Maybe<String> hookStepId;
	protected String hookStatus;

	public StepReporter() {
		super();
		currentStepId = null;
		hookStepId = null;
		hookStatus = null;
	}

	@Override
	protected Maybe<String> getRootItemId() {
		return null;
	}

	@Override
	protected void beforeStep(Step step, Match match) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(Utils.buildStatementName(step, stepPrefix, " ", null));
		rq.setDescription(Utils.buildMultilineArgument(step));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("STEP");
		String codeRef = Utils.getCodeRef(match);
		rq.setCodeRef(codeRef);
		rq.setTestCaseId(Utils.getTestCaseId(match, codeRef));
		rq.setAttributes(Utils.getAttributes(match));
		currentStepId = RP.get().startTestItem(currentScenario.getId(), rq);
	}

	@Override
	protected void afterStep(Result result) {
		reportResult(result, null);
		Utils.finishTestItem(RP.get(), currentStepId, Utils.mapStatus(result.getStatus()));
		currentStepId = null;
	}

	@Override
	protected void beforeHooks(Boolean isBefore) {
		StartTestItemRQ rq = new StartTestItemRQ();

		rq.setName(isBefore ? "Before hooks" : "After hooks");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(isBefore ? "BEFORE_TEST" : "AFTER_TEST");

		hookStepId = RP.get().startTestItem(currentScenario.getId(), rq);

		hookStatus = Statuses.PASSED;
	}

	@Override
	protected void afterHooks(Boolean isBefore) {
		Utils.finishTestItem(RP.get(), hookStepId, hookStatus);
		hookStepId = null;
	}

	@Override
	protected void hookFinished(Match match, Result result, Boolean isBefore) {
		reportResult(result, (isBefore ? "Before" : "After") + " hook: " + match.getLocation());
		if (result.getStatus().equals("failed")) {
			hookStatus = Statuses.FAILED;
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
