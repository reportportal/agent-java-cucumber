/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/epam/ReportPortal
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

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.ItemAttributeResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.epam.reportportal.cucumber.Utils.extractTags;

/**
 * Abstract Cucumber formatter/reporter for Report Portal
 *
 * @author Sergey Gvozdyukevich
 * @author Andrei Varabyeu
 */
public abstract class AbstractReporter implements Formatter, Reporter {
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReporter.class);

	protected static final String COLON_INFIX = ": ";

	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	/* formatter context */
	protected String currentFeatureUri;

	protected Maybe<Long> currentFeatureId;
	protected StartTestItemRQ startFeatureRq;
	protected ScenarioContext currentScenario;
	protected String stepPrefix;

	private Queue<String> outlineIterations;
	private Boolean inBackground;

	private AtomicBoolean finished = new AtomicBoolean(false);

	protected Supplier<Launch> RP = Suppliers.memoize(new Supplier<Launch>() {

		/* should no be lazy */
		private final Date startTime = Calendar.getInstance().getTime();

		@Override
		public Launch get() {
			final ReportPortal reportPortal = buildReportPortal();

			ListenerParameters parameters = reportPortal.getParameters();

			StartLaunchRQ rq = new StartLaunchRQ();
			rq.setName(parameters.getLaunchName());
			rq.setStartTime(startTime);
			rq.setMode(parameters.getLaunchRunningMode());
			rq.setAttributes(parameters.getAttributes() == null ? new HashSet<ItemAttributeResource>() : parameters.getAttributes());
			rq.setDescription(parameters.getDescription());

			final Boolean skippedAnIssue = parameters.getSkippedAnIssue();
			final ItemAttributeResource skippedIssueAttr = new ItemAttributeResource();
			skippedIssueAttr.setKey(SKIPPED_ISSUE_KEY);
			skippedIssueAttr.setValue(skippedAnIssue == null ? "true" : skippedAnIssue.toString());
			skippedIssueAttr.setSystem(true);
			rq.getAttributes().add(skippedIssueAttr);

			Launch launch = reportPortal.newLaunch(rq);
			finished = new AtomicBoolean(false);
			return launch;
		}
	});

	protected AbstractReporter() {
		outlineIterations = new ArrayDeque<String>();
		stepPrefix = "";
		inBackground = false;
	}

	/**
	 * Extension point to customize ReportPortal instance
	 *
	 * @return ReportPortal
	 */
	protected ReportPortal buildReportPortal() {
		return ReportPortal.builder().build();
	}

	/**
	 * Finish RP launch
	 */
	protected void afterLaunch() {
		FinishExecutionRQ finishLaunchRq = new FinishExecutionRQ();
		finishLaunchRq.setEndTime(Calendar.getInstance().getTime());
		RP.get().finish(finishLaunchRq);
	}

	/**
	 * Define Start Cucumber feature RQ
	 *
	 * @param feature Step feature
	 */
	protected void beforeFeature(Feature feature) {
		//define start feature RQ in this method, because only here we can receive Feature details
		startFeatureRq = new StartTestItemRQ();
		startFeatureRq.setDescription(Utils.
				buildStatementName(feature, null, AbstractReporter.COLON_INFIX, null));
		startFeatureRq.setName(currentFeatureUri);
		startFeatureRq.setAttributes(extractTags(feature.getTags()));
		startFeatureRq.setType(getFeatureTestItemType());
	}

	/**
	 * Finish Cucumber feature
	 */
	protected void afterFeature() {
		if (null != currentFeatureId) {
			Utils.finishTestItem(RP.get(), currentFeatureId);
			currentFeatureId = null;
		}
	}

	/**
	 * Start Cucumber Feature (if not started) and Scenario
	 *
	 * @param scenario         Scenario
	 * @param outlineIteration - suffix to append to scenario name, can be null
	 */
	protected void beforeScenario(Scenario scenario, String outlineIteration) {
		// start Feature here, because it should be started only if at least one Scenario is included.
		// By this reason, it cannot be started in #beforeFeature method,
		// because it will be executed even if all Scenarios in the Feature are excluded.
		if (null == currentFeatureId) {
			Maybe<Long> root = getRootItemId();
			startFeatureRq.setStartTime(Calendar.getInstance().getTime());
			if (null == root) {
				currentFeatureId = RP.get().startTestItem(startFeatureRq);
			} else {
				currentFeatureId = RP.get().startTestItem(root, startFeatureRq);
			}
		}
		Maybe<Long> id = Utils.startNonLeafNode(
				RP.get(),
				currentFeatureId,
				Utils.buildStatementName(scenario, null, AbstractReporter.COLON_INFIX, outlineIteration),
				currentFeatureUri + ":" + scenario.getLine(),
				scenario.getTags(),
				getScenarioTestItemType()
		);
		currentScenario = new ScenarioContext(id);
	}

	/**
	 * Finish Cucumber scenario
	 */
	protected void afterScenario() {
		Utils.finishTestItem(RP.get(), currentScenario.getId(), currentScenario.getStatus());
		currentScenario = null;
	}

	/**
	 * Start Cucumber step
	 *
	 * @param step Step object
	 */
	protected abstract void beforeStep(Step step);

	/**
	 * Finish Cucumber step
	 *
	 * @param result Step result
	 */
	protected abstract void afterStep(Result result);

	/**
	 * Called when before/after-hooks are started
	 *
	 * @param isBefore - if true, before-hook is started, if false - after-hook
	 */
	protected abstract void beforeHooks(Boolean isBefore);

	/**
	 * Called when before/after-hooks are finished
	 *
	 * @param isBefore - if true, before-hook is finished, if false - after-hook
	 */
	protected abstract void afterHooks(Boolean isBefore);

	/**
	 * Called when a specific before/after-hook is finished
	 *
	 * @param match    Match object
	 * @param result   Hook result
	 * @param isBefore - if true, before-hook, if false - after-hook
	 */
	protected abstract void hookFinished(Match match, Result result, Boolean isBefore);

	/**
	 * Report test item result and error (if present)
	 *
	 * @param result  - Cucumber result object
	 * @param message - optional message to be logged in addition
	 */
	protected void reportResult(Result result, String message) {
		String cukesStatus = result.getStatus();
		String level = Utils.mapLevel(cukesStatus);
		String errorMessage = result.getErrorMessage();
		if (errorMessage != null) {
			Utils.sendLog(errorMessage, level, null);
		}

		if (message != null) {
			Utils.sendLog(message, level, null);
		}

		if (currentScenario != null) {
			currentScenario.updateStatus(Utils.mapStatus(result.getStatus()));
		}
	}

	/**
	 * Return RP test item name mapped to Cucumber feature
	 *
	 * @return test item name
	 */
	protected abstract String getFeatureTestItemType();

	/**
	 * Return RP test item name mapped to Cucumber scenario
	 *
	 * @return test item name
	 */
	protected abstract String getScenarioTestItemType();

	/********************************
	 * Cucumber interfaces implementations
	 ********************************/
	@Override
	public void before(Match match, Result result) {
		hookFinished(match, result, true);
	}

	@Override
	public void result(Result result) {
		afterStep(result);
		if (!inBackground && currentScenario.noMoreSteps()) {
			beforeHooks(false);
		}
	}

	@Override
	public void after(Match match, Result result) {
		hookFinished(match, result, false);
	}

	@Override
	public void match(Match match) {
		beforeStep(currentScenario.getNextStep());
	}

	@Override
	public void embedding(String mimeType, byte[] data) {
		File file = new File();
		file.setName(UUID.randomUUID().toString());
		file.setContent(data);
		file.setContentType(mimeType);
		Utils.sendLog("embedding", "UNKNOWN", file);
	}

	@Override
	public void write(String text) {
		Utils.sendLog(text, "INFO", null);
	}

	@Override
	public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
		//TODO find out when this is called
	}

	@Override
	public void uri(String uri) {
		currentFeatureUri = uri;
	}

	@Override
	public void feature(Feature feature) {
		beforeFeature(feature);
	}

	@Override
	public void scenarioOutline(ScenarioOutline scenarioOutline) {
		// noop
	}

	@Override
	public void examples(Examples examples) {
		int num = examples.getRows().size();
		// examples always have headers; therefore up to num - 1
		for (int i = 1; i < num; i++) {
			outlineIterations.add(" [" + i + "]");
		}
	}

	@Override
	public void startOfScenarioLifeCycle(Scenario scenario) {
		inBackground = false;
		beforeScenario(scenario, outlineIterations.poll());
		beforeHooks(true);
	}

	@Override
	public void background(Background background) {
		afterHooks(true);
		inBackground = true;
		stepPrefix = background.getKeyword().toUpperCase() + AbstractReporter.COLON_INFIX;
	}

	@Override
	public void scenario(Scenario scenario) {
		if (!inBackground) { // if there was no background
			afterHooks(true);
		} else {
			inBackground = false;
		}
		stepPrefix = "";
	}

	@Override
	public void step(Step step) {
		if (currentScenario != null) {
			currentScenario.addStep(step);
		}
		// otherwise it's a step collection in an outline, useless.
	}

	@Override
	public void endOfScenarioLifeCycle(Scenario scenario) {
		afterHooks(false);
		afterScenario();
	}

	@Override
	public void done() {
		// noop
	}

	@Override
	public void close() {
		if (finished.compareAndSet(false, true)) {
			afterLaunch();
		}
	}

	@Override
	public void eof() {
		afterFeature();
	}

	protected abstract Maybe<Long> getRootItemId();

	public static class ScenarioContext {
		private Maybe<Long> id;
		private Queue<Step> steps;
		private String status;

		public ScenarioContext(Maybe<Long> newId) {
			id = newId;
			steps = new ArrayDeque<Step>();
			status = Statuses.PASSED;
		}

		public Maybe<Long> getId() {
			return id;
		}

		public void addStep(Step step) {
			steps.add(step);
		}

		public Step getNextStep() {
			return steps.poll();
		}

		public boolean noMoreSteps() {
			return steps.isEmpty();
		}

		public void updateStatus(String newStatus) {
			if (!newStatus.equals(status)) {
				if (Statuses.FAILED.equals(status) || Statuses.FAILED.equals(newStatus)) {
					status = Statuses.FAILED;
				} else {
					status = Statuses.SKIPPED;
				}
			}
		}

		public String getStatus() {
			return status;
		}

	}

}
