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

import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import io.reactivex.Maybe;
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
	private static final String SEPARATOR = "-------------------------";

	private Supplier<Maybe<String>> rootSuiteId = Suppliers.memoize(new Supplier<Maybe<String>>() {
		@Override
		public Maybe<String> get() {
			StartTestItemRQ rq = new StartTestItemRQ();
			rq.setName("Root User Story");
			rq.setStartTime(Calendar.getInstance().getTime());
			rq.setType("STORY");
			return RP.get().startTestItem(rq);
		}
	});

	@Override
	protected void beforeStep(Step step) {
		String decoratedStepName = decorateMessage(Utils.buildStatementName(step, stepPrefix, " ", null));
		String multilineArg = Utils.buildMultilineArgument(step);
		Utils.sendLog(decoratedStepName + multilineArg, "INFO", null);
	}

	@Override
	protected void afterStep(Result result) {
		reportResult(result, decorateMessage("STEP " + result.getStatus().toUpperCase()));
	}

	@Override
	protected void beforeHooks(Boolean isBefore) {
		// noop
	}

	@Override
	protected void afterHooks(Boolean isBefore) {
		// noop
	}

	@Override
	protected void hookFinished(Match match, Result result, Boolean isBefore) {
		reportResult(result, null);
	}

	@Override
	protected String getFeatureTestItemType() {
		return "TEST";
	}

	@Override
	protected String getScenarioTestItemType() {
		return "STEP";
	}


	@Override
	protected Maybe<String> getRootItemId() {
		return rootSuiteId.get();
	}

	@Override
	protected void afterLaunch() {
		Utils.finishTestItem(RP.get(), rootSuiteId.get());
		rootSuiteId = null;

		super.afterLaunch();
	}

	/**
	 * Add separators to log item to distinguish from real log messages
	 *
	 * @param message to decorate
	 * @return decorated message
	 */
	private String decorateMessage(String message) {
		return ScenarioReporter.SEPARATOR + message + ScenarioReporter.SEPARATOR;
	}
}
