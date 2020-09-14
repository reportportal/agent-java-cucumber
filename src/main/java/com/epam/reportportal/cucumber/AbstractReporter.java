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
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;
import rp.com.google.common.io.ByteSource;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.epam.reportportal.cucumber.Utils.extractAttributes;
import static com.epam.reportportal.cucumber.Utils.getCodeRef;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.createKey;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.retrieveLeaf;
import static java.util.Optional.ofNullable;
import static rp.com.google.common.base.Strings.isNullOrEmpty;
import static rp.com.google.common.base.Throwables.getStackTraceAsString;

/**
 * Abstract Cucumber formatter/reporter for Report Portal
 *
 * @author Sergey Gvozdyukevich
 * @author Andrei Varabyeu
 * @author Vadzim Hushchanskou
 */
public abstract class AbstractReporter implements Formatter, Reporter {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReporter.class);
	private static final String AGENT_PROPERTIES_FILE = "agent.properties";
	private static final int DEFAULT_CAPACITY = 16;

	public static final TestItemTree ITEM_TREE = new TestItemTree();
	private static volatile ReportPortal REPORT_PORTAL = ReportPortal.builder().build();

	protected static final String COLON_INFIX = ": ";
	protected static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	protected final ThreadLocal<RunningContext.FeatureContext> currentFeatureContext = new ThreadLocal<>();
	protected final ThreadLocal<RunningContext.ScenarioContext> currentScenarioContext = new ThreadLocal<>();

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

	public static ReportPortal getReportPortal() {
		return REPORT_PORTAL;
	}

	protected static void setReportPortal(ReportPortal reportPortal) {
		REPORT_PORTAL = reportPortal;
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

	private void addToTree(RunningContext.FeatureContext featureContext, RunningContext.ScenarioContext scenarioContext) {
		retrieveLeaf(featureContext.getUri(), ITEM_TREE).ifPresent(suiteLeaf -> suiteLeaf.getChildItems()
				.put(createKey(scenarioContext.getLine()), TestItemTree.createTestItemLeaf(scenarioContext.getId(), DEFAULT_CAPACITY)));
	}

	private void addToTree(RunningContext.FeatureContext context) {
		ITEM_TREE.getTestItems()
				.put(createKey(context.getUri()), TestItemTree.createTestItemLeaf(context.getId(), DEFAULT_CAPACITY));
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
		RunningContext.FeatureContext featureContext = currentFeatureContext.get();
		if (null == featureContext.getId()) {
			StartTestItemRQ startFeatureRq = featureContext.getItemRq();
			Optional<Maybe<String>> root = getRootItemId();
			startFeatureRq.setStartTime(Calendar.getInstance().getTime());
			Maybe<String> currentFeatureId = root.map(i -> launch.get().startTestItem(i, startFeatureRq))
					.orElseGet(() -> launch.get().startTestItem(startFeatureRq));
			featureContext.setId(currentFeatureId);
			addToTree(featureContext);
		}
		String uri = featureContext.getUri();
		String description = Utils.getDescription(uri);
		String codeRef = Utils.getCodeRef(uri, scenario.getLine());
		Launch myLaunch = launch.get();
		Maybe<String> id = Utils.startNonLeafNode(
				myLaunch,
				featureContext.getId(),
				Utils.buildStatementName(scenario, null, outlineIteration),
				description,
				codeRef,
				scenario.getTags(),
				getScenarioTestItemType()
		);
		RunningContext.ScenarioContext scenarioContext = getCurrentScenarioContext();
		scenarioContext.setId(id);
		scenarioContext.setLine(scenario.getLine());
		scenarioContext.setFeatureUri(uri);
		if (myLaunch.getParameters().isCallbackReportingEnabled()) {
			addToTree(featureContext, scenarioContext);
		}
	}

	private void removeFromTree(RunningContext.FeatureContext featureContext, RunningContext.ScenarioContext scenarioContext) {
		retrieveLeaf(featureContext.getUri(), ITEM_TREE).ifPresent(suiteLeaf -> suiteLeaf.getChildItems()
				.remove(createKey(scenarioContext.getLine())));
	}

	/**
	 * Finish Cucumber scenario
	 */
	protected void afterScenario() {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Utils.finishTestItem(launch.get(), context.getId(), context.getStatus());
		currentScenarioContext.set(null);
		removeFromTree(currentFeatureContext.get(), context);
	}

	/**
	 * Define Start Cucumber feature RQ
	 *
	 * @param feature Step feature
	 */
	protected void beforeFeature(Feature feature) {
		//define start feature RQ in this method, because only here we can receive Feature details
		RunningContext.FeatureContext featureContext = currentFeatureContext.get();
		String featureKeyword = feature.getKeyword();
		String featureName = feature.getName();
		StartTestItemRQ startFeatureRq = new StartTestItemRQ();
		startFeatureRq.setDescription(Utils.getDescription(featureContext.getUri()));
		startFeatureRq.setCodeRef(getCodeRef(featureContext.getUri(), 0));
		startFeatureRq.setName(Utils.buildNodeName(featureKeyword, AbstractReporter.COLON_INFIX, featureName, null));
		startFeatureRq.setAttributes(extractAttributes(feature.getTags()));
		startFeatureRq.setType(getFeatureTestItemType());
		featureContext.setItemRq(startFeatureRq);
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

	protected RunningContext.ScenarioContext getCurrentScenarioContext() {
		RunningContext.ScenarioContext context = currentScenarioContext.get();
		if (context == null) {
			context = new RunningContext.ScenarioContext();
			currentScenarioContext.set(context);
		}
		return context;
	}

	protected StartTestItemRQ buildStartStepRequest(Step step, String stepPrefix, Match match) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(Utils.buildStatementName(step, stepPrefix, null));
		rq.setDescription(Utils.buildMultilineArgument(step));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("STEP");
		String codeRef = Utils.getCodeRef(match);
		rq.setParameters(Utils.getParameters(codeRef, match));
		rq.setCodeRef(codeRef);
		rq.setTestCaseId(ofNullable(Utils.getTestCaseId(match, codeRef)).map(TestCaseIdEntry::getId).orElse(null));
		rq.setAttributes(Utils.getAttributes(match));
		return rq;
	}

	/**
	 * Start Cucumber step
	 *
	 * @param step  Step object
	 * @param match Match object
	 */
	protected void beforeStep(Step step, Match match) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		StartTestItemRQ rq = buildStartStepRequest(step, context.getStepPrefix(), match);
		Launch myLaunch = launch.get();
		Maybe<String> stepId = myLaunch.startTestItem(context.getId(), rq);
		context.setCurrentStepId(stepId);
		String stepText = step.getName();
		context.setCurrentText(stepText);

		if (myLaunch.getParameters().isCallbackReportingEnabled()) {
			addToTree(context, stepText, stepId);
		}
	}

	/**
	 * Finish Cucumber step
	 *
	 * @param result Step result
	 */
	protected void afterStep(Result result) {
		reportResult(result, null);
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Launch myLaunch = launch.get();
		myLaunch.getStepReporter().finishPreviousStep();
		Utils.finishTestItem(myLaunch, context.getCurrentStepId(), Utils.mapStatus(result.getStatus()));
		context.setCurrentStepId(null);
		context.setCurrentText(null);
	}

	/**
	 * Extension point to customize test creation event/request
	 *
	 * @param isBefore a cucumber hook type object
	 * @return Request to ReportPortal
	 */
	protected StartTestItemRQ buildStartHookRequest(boolean isBefore) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setType(isBefore ? "BEFORE_TEST" : "AFTER_TEST");
		rq.setName(isBefore ? "Before hooks" : "After hooks");
		rq.setStartTime(Calendar.getInstance().getTime());
		return rq;
	}

	/**
	 * Called when before/after-hooks are started
	 *
	 * @param isBefore - if true, before-hook is finished, if false - after-hook
	 */
	protected void beforeHooks(boolean isBefore) {
		StartTestItemRQ rq = buildStartHookRequest(isBefore);

		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		context.setHookStepId(launch.get().startTestItem(context.getId(), rq));
		context.setHookStatus(ItemStatus.PASSED);
	}

	/**
	 * Called when before/after-hooks are finished
	 *
	 * @param isBefore - if true, before-hook is finished, if false - after-hook
	 */
	protected void afterHooks(Boolean isBefore) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Launch myLaunch = launch.get();
		myLaunch.getStepReporter().finishPreviousStep();
		Utils.finishTestItem(myLaunch, context.getHookStepId(), context.getHookStatus());
		context.setHookStepId(null);
	}

	/**
	 * Called when a specific before/after-hook is finished
	 *
	 * @param match    Match object
	 * @param result   Hook result
	 * @param isBefore - if true, before-hook, if false - after-hook
	 */
	protected void hookFinished(Match match, Result result, Boolean isBefore) {
		reportResult(result, (isBefore ? "Before" : "After") + " hook: " + match.getLocation());
		getCurrentScenarioContext().setHookStatus(Utils.mapStatus(result.getStatus()));
	}

	/**
	 * Report test item result and error (if present)
	 *
	 * @param result  - Cucumber result object
	 * @param message - optional message to be logged in addition
	 */
	protected void reportResult(Result result, String message) {
		String cukesStatus = result.getStatus();
		String level = Utils.mapLevel(cukesStatus);
		if (message != null) {
			Utils.sendLog(message, level);
		}
		String errorMessage = result.getErrorMessage();
		if (errorMessage != null) {
			Utils.sendLog(errorMessage, level);
		}
		if (result.getError() != null) {
			Utils.sendLog(getStackTraceAsString(result.getError()), level);
		}
		RunningContext.ScenarioContext currentScenario = getCurrentScenarioContext();
		currentScenario.updateStatus(Utils.mapStatus(result.getStatus()));
	}

	/**
	 * Return RP test item name mapped to Cucumber feature
	 *
	 * @return test item name
	 */
	@Nonnull
	protected abstract String getFeatureTestItemType();

	/**
	 * Return RP test item name mapped to Cucumber scenario
	 *
	 * @return test item name
	 */
	@Nonnull
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
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		if (!context.isInBackground() && context.noMoreSteps()) {
			beforeHooks(false);
		}
	}

	@Override
	public void after(Match match, Result result) {
		hookFinished(match, result, false);
	}

	@Override
	public void match(Match match) {
		beforeStep(getCurrentScenarioContext().getNextStep(), match);
	}

	private static final ThreadLocal<Tika> TIKA_THREAD_LOCAL = ThreadLocal.withInitial(Tika::new);

	private volatile MimeTypes mimeTypes = null;

	private MimeTypes getMimeTypes() {
		if (mimeTypes == null) {
			mimeTypes = MimeTypes.getDefaultMimeTypes();
		}
		return mimeTypes;
	}

	/**
	 * Send a log with data attached.
	 *
	 * @param mimeType an attachment type
	 * @param data     data to attach
	 */
	@Override
	public void embedding(String mimeType, byte[] data) {
		String type = mimeType;
		try {
			type = TIKA_THREAD_LOCAL.get().detect(new ByteArrayInputStream(data));
		} catch (IOException e) {
			// nothing to do we will use bypassed mime type
			LOGGER.warn("Mime-type not found", e);
		}
		String prefix = "";
		try {
			MediaType mt = getMimeTypes().forName(type).getType();
			prefix = mt.getType();
		} catch (MimeTypeException e) {
			LOGGER.warn("Mime-type not found", e);
		}
		ReportPortal.emitLog(new ReportPortalMessage(ByteSource.wrap(data), type, prefix), "UNKNOWN", Calendar.getInstance().getTime());
	}

	@Override
	public void write(String text) {
		Utils.sendLog(text, "INFO");
	}

	@Override
	public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
		//TODO find out when this is called
	}

	@Override
	public void uri(String uri) {
		currentFeatureContext.set(new RunningContext.FeatureContext(uri));
		Maybe<String> launchId = launch.get().start();
		ITEM_TREE.setLaunchId(launchId);
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
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Queue<String> iterations = context.getOutlineIterations();
		IntStream.range(1, examples.getRows().size()).forEach(it -> iterations.add(String.format("[%d]", it)));
	}

	@Override
	public void startOfScenarioLifeCycle(Scenario scenario) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		String iteration = context.getOutlineIterations().poll();
		context.setInBackground(false);
		beforeScenario(scenario, iteration);
		beforeHooks(true);
	}

	@Override
	public void background(Background background) {
		afterHooks(true);
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		context.setInBackground(true);
		context.setStepPrefix(background.getKeyword().toUpperCase() + AbstractReporter.COLON_INFIX);
	}

	@Override
	public void scenario(Scenario scenario) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		if (!context.isInBackground()) { // if there was no background
			afterHooks(true);
		} else {
			context.setInBackground(false);
		}
		context.setStepPrefix("");
	}

	@Override
	public void step(Step step) {
		RunningContext.ScenarioContext context = currentScenarioContext.get();
		if (context != null) {
			// Skip scenario outlines steps without initialized parameters
			context.addStep(step);
		}
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

	protected abstract Optional<Maybe<String>> getRootItemId();

	protected void addToTree(RunningContext.ScenarioContext scenarioContext, String text, Maybe<String> stepId) {
		retrieveLeaf(
				scenarioContext.getFeatureUri(),
				scenarioContext.getLine(),
				ITEM_TREE
		).ifPresent(scenarioLeaf -> scenarioLeaf.getChildItems().put(createKey(text), TestItemTree.createTestItemLeaf(stepId, 0)));
	}

	protected void removeFromTree(RunningContext.ScenarioContext scenarioContext, String text) {
		retrieveLeaf(
				scenarioContext.getFeatureUri(),
				scenarioContext.getLine(),
				ITEM_TREE
		).ifPresent(scenarioLeaf -> scenarioLeaf.getChildItems().remove(createKey(text)));
	}
}
