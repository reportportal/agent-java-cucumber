/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/agent-java-cucumber
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.cucumber;

import java.util.Calendar;

import com.epam.reportportal.listeners.ReportPortalListenerContext;
import com.epam.reportportal.listeners.Statuses;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;

import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;

/**
 * Cucumber reporter for ReportPortal that reports individual steps as test
 * methods.
 * 
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
 * 
 */
public class StepReporter extends AbstractReporter {
	protected String currentStepId;
	protected String hookStepId;
	protected String hookStatus;

	public StepReporter() {
		super();
		currentStepId = null;
		hookStepId = null;
		hookStatus = null;
	}

	@Override
	protected void startRootItem() {
		// noop
	}

	@Override
	protected void finishRootItem() {
		// noop
	}

	@Override
	protected String getRootItemId() {
		return null;
	}

	@Override
	protected void beforeStep(Step step) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setLaunchId(currentLaunchId);
		rq.setName(Utils.buildStatementName(step, stepPrefix, " ", null));
		rq.setDescription(Utils.buildMultilineArgument(step));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("STEP");
		currentStepId = Utils.startTestItem(rq, currentScenario.getId());
		ReportPortalListenerContext.setRunningNowItemId(currentStepId);
	}

	@Override
	protected void afterStep(Result result) {
		reportResult(result, null);
		String comments = Utils.buildIssueComments(result);
		Utils.finishTestItem(currentStepId, Utils.mapStatus(result.getStatus()), comments == null ? "" : comments);
		currentStepId = null;
		ReportPortalListenerContext.setRunningNowItemId(null);
	}

	@Override
	protected void beforeHooks(Boolean isBefore) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setLaunchId(currentLaunchId);
		rq.setName(isBefore ? "Before hooks" : "After hooks");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(isBefore ? "BEFORE_TEST" : "AFTER_TEST");
		hookStepId = Utils.startTestItem(rq, currentScenario.getId());
		ReportPortalListenerContext.setRunningNowItemId(hookStepId);
		hookStatus = Statuses.PASSED;
	}

	@Override
	protected void afterHooks(Boolean isBefore) {
		Utils.finishTestItem(hookStepId, hookStatus, "");
		hookStepId = null;
		ReportPortalListenerContext.setRunningNowItemId(null);
	}

	@Override
	protected void hookFinished(Match match, Result result, Boolean isBefore) {
		reportResult(result, (isBefore ? "Before" : "After") + " hook: " + match.getLocation());
		if (result.getStatus().equals("failed")) {
			hookStatus = Statuses.FAILED;
		}
	}

	@Override
	protected String getLogDestination() {
		return currentStepId == null ? hookStepId : currentStepId;
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
