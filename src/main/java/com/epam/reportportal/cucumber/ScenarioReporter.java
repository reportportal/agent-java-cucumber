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

import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Step;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import javax.annotation.Nonnull;
import java.util.Calendar;
import java.util.Optional;

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

	protected MemoizingSupplier<Maybe<String>> rootSuiteId = new MemoizingSupplier<>(() -> {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName("Root User Story");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(RP_STORY_TYPE);
		return launch.get().startTestItem(rq);
	});

	@Override
	protected StartTestItemRQ buildStartStepRequest(Step step, String stepPrefix, Match match) {
		StartTestItemRQ rq = super.buildStartStepRequest(step, stepPrefix, match);
		rq.setHasStats(false);
		return rq;
	}

	@Override
	protected void beforeStep(Step step, Match match) {
		super.beforeStep(step, match);
		String description = buildMultilineArgument(step).trim();
		if (!description.isEmpty()) {
			sendLog(description);
		}
	}

	@Override
	protected StartTestItemRQ buildStartHookRequest(boolean isBefore) {
		StartTestItemRQ rq = super.buildStartHookRequest(isBefore);
		rq.setHasStats(false);
		return rq;
	}

	@Override
	@Nonnull
	protected String getFeatureTestItemType() {
		return RP_TEST_TYPE;
	}

	@Override
	@Nonnull
	protected String getScenarioTestItemType() {
		return RP_STEP_TYPE;
	}

	@Override
	@Nonnull
	protected Optional<Maybe<String>> getRootItemId() {
		return Optional.of(rootSuiteId.get());
	}

	/**
	 * Finish root suite
	 */
	protected void finishRootItem() {
		if(rootSuiteId.isInitialized()) {
			finishTestItem(rootSuiteId.get());
			rootSuiteId = null;
		}
	}

	@Override
	protected void afterLaunch() {
		if (currentFeatureContext.get() == null || currentFeatureContext.get().getId() == null) {
			LOGGER.debug("There is no scenarios in the launch");
			return;
		}
		finishRootItem();
		super.afterLaunch();
	}
}
