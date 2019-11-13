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
	private static final String SEPARATOR = "-------------------------";
	private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioReporter.class);

	protected Supplier<Maybe<String>> rootSuiteId = Suppliers.memoize(new Supplier<Maybe<String>>() {
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
	protected void beforeStep(Step step, Match match) {
		String decoratedStepName = decorateMessage(Utils.buildStatementName(step, stepPrefix, " ", null));
		String multilineArg = Utils.buildMultilineArgument(step);
		if (!multilineArg.isEmpty()) {
			Utils.sendLog("!!!MARKDOWN_MODE!!!\r\n" + decoratedStepName + "\r\n" + multilineArg, "INFO", null);
		} else {
			Utils.sendLog(decoratedStepName, "INFO", null);
		}
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
		if (currentFeatureUri == null) {
			LOGGER.debug("There is no scenarios in the launch");
			return;
		}
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
