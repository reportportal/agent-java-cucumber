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

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.epam.reportportal.cucumber.Utils.extractAttributes;
import static com.epam.reportportal.cucumber.Utils.getCodeRef;
import static rp.com.google.common.base.Strings.isNullOrEmpty;

/**
 * Abstract Cucumber formatter/reporter for Report Portal
 *
 * @author Sergey Gvozdyukevich
 * @author Andrei Varabyeu
 */
public abstract class AbstractReporter implements Formatter, Reporter {

	private static final String AGENT_PROPERTIES_FILE = "agent.properties";
	protected static final String COLON_INFIX = ": ";
	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	protected String stepPrefix;

	protected Queue<String> outlineIterations;
	private Boolean inBackground;

	protected final ThreadLocal<String> currentFeatureUri = new ThreadLocal<>();
	protected final ThreadLocal<RunningContext.FeatureContext> currentFeatureContext = new ThreadLocal<>();
	protected final ThreadLocal<RunningContext.ScenarioContext> currentScenarioContext = new ThreadLocal<>();

	// There is no event for recognizing end of feature in Cucumber.
	// This map is used to record the last scenario time and its feature uri.
	// End of feature occurs once launch is finished.
	private final Map<String, Date> featureEndTime = new ConcurrentHashMap<>();

	private AtomicBoolean finished = new AtomicBoolean(false);

	protected final Supplier<Launch> launch = Suppliers.memoize(new Supplier<Launch>() {

		/* should not be lazy */
		private final Date startTime = Calendar.getInstance().getTime();

		@Override
		public Launch get() {
			final ReportPortal reportPortal = buildReportPortal();

			ListenerParameters parameters = reportPortal.getParameters();

			StartLaunchRQ rq = new StartLaunchRQ();
			rq.setName(parameters.getLaunchName());
			rq.setStartTime(startTime);
			rq.setMode(parameters.getLaunchRunningMode());
			rq.setAttributes(parameters.getAttributes() == null ? new HashSet<>() : parameters.getAttributes());
			rq.getAttributes().addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE, AbstractReporter.class.getClassLoader()));
			rq.setDescription(parameters.getDescription());
			rq.setRerun(parameters.isRerun());
			if (!isNullOrEmpty(parameters.getRerunOf())) {
				rq.setRerunOf(parameters.getRerunOf());
			}

			Boolean skippedAnIssue = parameters.getSkippedAnIssue();
			ItemAttributesRQ skippedIssueAttr = new ItemAttributesRQ();
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
		outlineIterations = new ArrayDeque<>();
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
		launch.get().finish(finishLaunchRq);
	}

	/**
	 * Define Start Cucumber feature RQ
	 *
	 * @param feature Step feature
	 */
	protected void beforeFeature(Feature feature) {
		//define start feature RQ in this method, because only here we can receive Feature details
		String uri = currentFeatureUri.get();
		String featureKeyword = feature.getKeyword();
		String featureName = feature.getName();
		StartTestItemRQ startFeatureRq = new StartTestItemRQ();
		startFeatureRq.setDescription(Utils.getDescription(uri));
		startFeatureRq.setCodeRef(getCodeRef(uri, 0));
		startFeatureRq.setName(Utils.buildNodeName(featureKeyword, AbstractReporter.COLON_INFIX, featureName, null));
		startFeatureRq.setAttributes(extractAttributes(feature.getTags()));
		startFeatureRq.setType(getFeatureTestItemType());
		currentFeatureContext.set(new RunningContext.FeatureContext(startFeatureRq));
	}

	/**
	 * Finish Cucumber feature
	 */
	protected void afterFeature() {
		RunningContext.FeatureContext currentFeature = currentFeatureContext.get();
		currentFeatureContext.remove();
		if (null != currentFeature && null != currentFeature.getId()) {
			Utils.finishTestItem(launch.get(), currentFeature.getId());
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
		RunningContext.FeatureContext currentFeature = currentFeatureContext.get();
		if (null == currentFeature.getId()) {
			StartTestItemRQ startFeatureRq = currentFeature.getItemRq();
			Maybe<String> root = getRootItemId();
			startFeatureRq.setStartTime(Calendar.getInstance().getTime());
			Maybe<String> currentFeatureId;
			if (null == root) {
				currentFeatureId = launch.get().startTestItem(startFeatureRq);
			} else {
				currentFeatureId = launch.get().startTestItem(root, startFeatureRq);
			}
			currentFeature.setId(currentFeatureId);
		}
		String codeRef = currentFeatureUri.get() + ":" + scenario.getLine();
		Maybe<String> id = Utils.startNonLeafNode(
				launch.get(),
				currentFeature.getId(),
				Utils.buildStatementName(scenario, null, AbstractReporter.COLON_INFIX, outlineIteration),
				currentFeatureUri.get(),
				codeRef,
				scenario.getTags(),
				getScenarioTestItemType()
		);
		currentScenarioContext.set(new RunningContext.ScenarioContext(id));
	}

	/**
	 * Finish Cucumber scenario
	 */
	protected void afterScenario() {
		RunningContext.ScenarioContext context = currentScenarioContext.get();
		Utils.finishTestItem(launch.get(), context.getId(), context.getStatus());
	}

	/**
	 * Start Cucumber step
	 *
	 * @param step Step object
	 */
	protected abstract void beforeStep(Step step, Match match);

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
		RunningContext.ScenarioContext currentScenario = currentScenarioContext.get();
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
		if (!inBackground && currentScenarioContext.get().noMoreSteps()) {
			beforeHooks(false);
		}
	}

	@Override
	public void after(Match match, Result result) {
		hookFinished(match, result, false);
	}

	@Override
	public void match(Match match) {
		beforeStep(currentScenarioContext.get().getNextStep(), match);
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
		currentFeatureUri.set(uri);
		launch.get().start();
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
		// examples always have headers; therefore up to num - 1
		IntStream.range(1, examples.getRows().size()).forEach(it -> outlineIterations.add(String.format("[%d]", it)));
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
		RunningContext.ScenarioContext currentScenario = currentScenarioContext.get();
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

	protected abstract Maybe<String> getRootItemId();

}
