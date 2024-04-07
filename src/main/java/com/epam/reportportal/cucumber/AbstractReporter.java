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

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.utils.*;
import com.epam.reportportal.utils.files.ByteSource;
import com.epam.reportportal.utils.http.ContentType;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import gherkin.formatter.Argument;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.epam.reportportal.cucumber.Utils.*;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.createKey;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.retrieveLeaf;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

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
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String GET_LOCATION_METHOD_NAME = "getLocation";
	private static final String METHOD_OPENING_BRACKET = "(";
	private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
	private static final String ERROR_FORMAT = "Error:\n%s";
	private static final String DESCRIPTION_ERROR_FORMAT = "%s\n" + ERROR_FORMAT;

	public static final TestItemTree ITEM_TREE = new TestItemTree();
	private static volatile ReportPortal REPORT_PORTAL = ReportPortal.builder().build();

	protected static final String COLON_INFIX = ": ";
	protected static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	protected final ThreadLocal<RunningContext.FeatureContext> currentFeatureContext = new ThreadLocal<>();
	protected final ThreadLocal<RunningContext.ScenarioContext> currentScenarioContext = new ThreadLocal<>();

	/**
	 * This map uses to record the description of the scenario and the step to append the error to the description.
	 */
	private final Map<String, String> descriptionsMap = new ConcurrentHashMap<>();
	/**
	 * This map uses to record scenario errors to append to the description.
	 */
	private final Map<String, Throwable> scenarioErrorMap = new ConcurrentHashMap<>();

	private AtomicBoolean finished = new AtomicBoolean(false);

	protected final Supplier<Launch> launch = new MemoizingSupplier<>(new Supplier<Launch>() {

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
			HashSet<ItemAttributesRQ> attributes = new HashSet<>(parameters.getAttributes());
			rq.setAttributes(attributes);
			attributes.addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE, AbstractReporter.class.getClassLoader()));
			rq.setDescription(parameters.getDescription());
			rq.setRerun(parameters.isRerun());
			if (isNotBlank(parameters.getRerunOf())) {
				rq.setRerunOf(parameters.getRerunOf());
			}

			Boolean skippedAnIssue = parameters.getSkippedAnIssue();
			ItemAttributesRQ skippedIssueAttr = new ItemAttributesRQ();
			skippedIssueAttr.setKey(SKIPPED_ISSUE_KEY);
			skippedIssueAttr.setValue(skippedAnIssue == null ? "true" : skippedAnIssue.toString());
			skippedIssueAttr.setSystem(true);
			attributes.add(skippedIssueAttr);

			Launch launch = reportPortal.newLaunch(rq);
			finished = new AtomicBoolean(false);
			return launch;
		}
	});

	@Nonnull
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
				.put(createKey(scenarioContext.getLine()), TestItemTree.createTestItemLeaf(scenarioContext.getId())));
	}

	private void addToTree(RunningContext.FeatureContext context) {
		ITEM_TREE.getTestItems().put(createKey(context.getUri()), TestItemTree.createTestItemLeaf(context.getId()));
	}

	/**
	 * Returns a scenario name
	 *
	 * @param scenario Cucumber's Scenario object
	 * @return scenario name
	 */
	@Nonnull
	protected String buildScenarioName(@Nonnull Scenario scenario) {
		return Utils.buildName(scenario.getKeyword(), COLON_INFIX, scenario.getName());
	}

	/**
	 * Extension point to customize scenario creation event/request
	 *
	 * @param scenario Cucumber's Scenario object
	 * @param uri      a scenario relative path
	 * @return start test item request ready to send on RP
	 */
	@Nonnull
	protected StartTestItemRQ buildStartScenarioRequest(@Nonnull Scenario scenario, @Nonnull String uri) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(buildScenarioName(scenario));
		String description = getDescription(scenario, uri);
		String codeRef = getCodeRef(uri, scenario.getLine());
		rq.setDescription(description);
		rq.setCodeRef(codeRef);
		rq.setAttributes(extractAttributes(scenario.getTags()));
		rq.setStartTime(Calendar.getInstance().getTime());
		String type = getScenarioTestItemType();
		rq.setType(type);
		if ("STEP".equals(type)) {
			rq.setTestCaseId(ofNullable(getTestCaseId(codeRef, null)).map(TestCaseIdEntry::getId).orElse(null));
		}
		return rq;
	}

	private RunningContext.ScenarioContext getCurrentScenarioContext() {
		RunningContext.ScenarioContext context = currentScenarioContext.get();
		if (context == null) {
			context = new RunningContext.ScenarioContext();
			currentScenarioContext.set(context);
		}
		return context;
	}

	/**
	 * Start Cucumber Feature
	 *
	 * @param startFeatureRq feature start request
	 * @return feature item id
	 */
	@Nonnull
	protected Maybe<String> startFeature(@Nonnull StartTestItemRQ startFeatureRq) {
		Optional<Maybe<String>> root = getRootItemId();
		startFeatureRq.setStartTime(Calendar.getInstance().getTime());
		Launch myLaunch = launch.get();
		return root.map(i -> myLaunch.startTestItem(i, startFeatureRq)).orElseGet(() -> myLaunch.startTestItem(startFeatureRq));
	}

	/**
	 * Start Cucumber Scenario
	 *
	 * @param featureId       parent feature item id
	 * @param startScenarioRq scenario start request
	 * @return scenario item id
	 */
	@Nonnull
	protected Maybe<String> startScenario(@Nonnull Maybe<String> featureId, @Nonnull StartTestItemRQ startScenarioRq) {
		return launch.get().startTestItem(featureId, startScenarioRq);
	}

	/**
	 * Start Cucumber Feature (if not started) and Scenario
	 *
	 * @param scenario         Scenario
	 * @param outlineIteration - suffix to append to scenario name, can be null
	 */
	@SuppressWarnings("unused")
	protected void beforeScenario(Scenario scenario, String outlineIteration) {
		// start Feature here, because it should be started only if at least one Scenario is included.
		// By this reason, it cannot be started in #beforeFeature method,
		// because it will be executed even if all Scenarios in the Feature are excluded.
		RunningContext.FeatureContext featureContext = currentFeatureContext.get();
		Launch myLaunch = launch.get();
		//noinspection ReactiveStreamsUnusedPublisher
		if (null == featureContext.getId()) {
			featureContext.setId(startFeature(featureContext.getItemRq()));
			addToTree(featureContext);
		}
		String uri = featureContext.getUri();
		StartTestItemRQ rq = buildStartScenarioRequest(scenario, uri);
		RunningContext.ScenarioContext scenarioContext = getCurrentScenarioContext();
		scenarioContext.setId(startScenario(featureContext.getId(), rq));
		scenarioContext.setLine(scenario.getLine());
		scenarioContext.setFeatureUri(uri);
		descriptionsMap.put(scenarioContext.getId().blockingGet(), ofNullable(rq.getDescription()).orElse(StringUtils.EMPTY));
		if (myLaunch.getParameters().isCallbackReportingEnabled()) {
			addToTree(featureContext, scenarioContext);
		}
	}

	private void removeFromTree(RunningContext.FeatureContext featureContext, RunningContext.ScenarioContext scenarioContext) {
		retrieveLeaf(featureContext.getUri(), ITEM_TREE).ifPresent(suiteLeaf -> suiteLeaf.getChildItems()
				.remove(createKey(scenarioContext.getLine())));
	}

	/**
	 * Build finish test item request object
	 *
	 * @param itemId item ID reference
	 * @param status item result status
	 * @return finish request
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishTestItemRequest(@Nonnull Maybe<String> itemId, @Nullable ItemStatus status,
														  @Nullable Throwable error) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		if (status == ItemStatus.FAILED) {
			Optional<String> currentDescription = Optional.ofNullable(descriptionsMap.get(itemId.blockingGet()));
			currentDescription.flatMap(description -> Optional.ofNullable(error)
							.map(errorMessage -> resolveDescriptionErrorMessage(description, errorMessage)))
					.ifPresent(rq::setDescription);
		}
		ofNullable(status).ifPresent(s -> rq.setStatus(s.name()));
		rq.setEndTime(Calendar.getInstance().getTime());
		return rq;
	}

	/**
	 * Resolve description
	 * @param currentDescription Current description
	 * @param error Error message
	 * @return Description with error
	 */
	private String resolveDescriptionErrorMessage(String currentDescription, Throwable error) {
		return Optional.ofNullable(currentDescription)
				.filter(StringUtils::isNotBlank)
				.map(description -> format(DESCRIPTION_ERROR_FORMAT, currentDescription, error))
				.orElse(format(ERROR_FORMAT, error));
	}

	/**
	 * Finish a test item with specified status
	 *
	 * @param itemId an ID of the item
	 * @param status the status of the item
	 */
	protected void finishTestItem(@Nullable Maybe<String> itemId, @Nullable ItemStatus status, @Nullable Throwable error) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return;
		}
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(itemId, buildFinishTestItemRequest(itemId, status, error));
	}

	/**
	 * Finish Cucumber scenario
	 */
	protected void afterScenario() {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		finishTestItem(context.getId(), context.getStatus(), scenarioErrorMap.get(context.getId().blockingGet()));
		currentScenarioContext.remove();
		removeFromTree(currentFeatureContext.get(), context);
	}

	/**
	 * Extension point to customize feature creation event/request
	 *
	 * @param feature a Cucumber's Feature object
	 * @param uri     a path to the feature
	 * @return Request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartFeatureRequest(@Nonnull Feature feature, @Nonnull String uri) {
		String featureKeyword = feature.getKeyword();
		String featureName = feature.getName();
		StartTestItemRQ startFeatureRq = new StartTestItemRQ();
		startFeatureRq.setDescription(getDescription(feature, uri));
		startFeatureRq.setCodeRef(getCodeRef(uri, 0));
		startFeatureRq.setName(Utils.buildName(featureKeyword, AbstractReporter.COLON_INFIX, featureName));
		startFeatureRq.setAttributes(extractAttributes(feature.getTags()));
		startFeatureRq.setType(getFeatureTestItemType());
		return startFeatureRq;
	}

	/**
	 * Define Start Cucumber feature RQ
	 *
	 * @param feature Step feature
	 */
	protected void beforeFeature(Feature feature) {
		//define start feature RQ in this method, because only here we can receive Feature details
		RunningContext.FeatureContext featureContext = currentFeatureContext.get();
		featureContext.setItemRq(buildStartFeatureRequest(feature, featureContext.getUri()));
	}

	/**
	 * Finish current Cucumber feature
	 */
	protected void afterFeature() {
		RunningContext.FeatureContext currentFeature = currentFeatureContext.get();
		//noinspection ReactiveStreamsUnusedPublisher
		if (null != currentFeature && null != currentFeature.getId()) {
			finishTestItem(currentFeature.getId());
		}
	}

	/**
	 * Extension point to customize step creation event/request
	 *
	 * @param step       a Cucumber's Step object
	 * @param stepPrefix a prefix of the step (e.g. 'Background')
	 * @param match      a Cucumber's Match object
	 * @return Request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartStepRequest(@Nonnull Step step, @Nullable String stepPrefix, @Nonnull Match match) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(Utils.buildName(stepPrefix, step.getKeyword(), step.getName()));
		rq.setDescription(buildMultilineArgument(step));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("STEP");
		String codeRef = getCodeRef(match);
		rq.setParameters(getParameters(step, codeRef, match));
		rq.setCodeRef(codeRef);
		rq.setTestCaseId(ofNullable(getTestCaseId(match, codeRef)).map(TestCaseIdEntry::getId).orElse(null));
		rq.setAttributes(getAttributes(match));
		return rq;
	}

	/**
	 * Start Cucumber Step
	 *
	 * @param scenarioId  parent scenario item id
	 * @param startStepRq step start request
	 * @return step item id
	 */
	@Nonnull
	protected Maybe<String> startStep(@Nonnull Maybe<String> scenarioId, @Nonnull StartTestItemRQ startStepRq) {
		return launch.get().startTestItem(scenarioId, startStepRq);
	}

	private void addToTree(@Nonnull RunningContext.ScenarioContext scenarioContext, @Nullable String text, @Nullable Maybe<String> stepId) {
		retrieveLeaf(scenarioContext.getFeatureUri(),
				scenarioContext.getLine(),
				ITEM_TREE
		).ifPresent(scenarioLeaf -> scenarioLeaf.getChildItems().put(createKey(text), TestItemTree.createTestItemLeaf(stepId)));
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
		Maybe<String> stepId = startStep(context.getId(), rq);
		context.setCurrentStepId(stepId);
		String stepText = step.getName();
		if (rq.isHasStats()) {
			descriptionsMap.put(stepId.blockingGet(), ofNullable(rq.getDescription()).orElse(StringUtils.EMPTY));
		}

		if (launch.get().getParameters().isCallbackReportingEnabled()) {
			addToTree(context, stepText, stepId);
		}
	}

	/**
	 * Finish Cucumber step
	 *
	 * @param result Step result
	 */
	protected void afterStep(@Nonnull Result result) {
		reportResult(result, null);
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		finishTestItem(context.getCurrentStepId(), mapStatus(result.getStatus()), result.getError());
		context.setCurrentStepId(null);
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
	 * Start before/after-hook item on Report Portal
	 *
	 * @param parentId  parent item id
	 * @param rq hook start request
	 * @return hook item id
	 */
	@Nonnull
	protected Maybe<String> startHook(@Nonnull Maybe<String> parentId, @Nonnull StartTestItemRQ rq) {
		return launch.get().startTestItem(parentId, rq);
	}

	/**
	 * Called when before/after-hooks are started
	 *
	 * @param isBefore - if true, before-hook is finished, if false - after-hook
	 */
	protected void beforeHooks(boolean isBefore) {
		StartTestItemRQ rq = buildStartHookRequest(isBefore);

		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		context.setHookStepId(startHook(context.getId(), rq));
		context.setHookStatus(ItemStatus.PASSED);
	}

	/**
	 * Called when before/after-hooks are finished
	 *
	 * @param isBefore - if true, before-hook is finished, if false - after-hook
	 */
	@SuppressWarnings("unused")
	protected void afterHooks(Boolean isBefore) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		finishTestItem(context.getHookStepId(), context.getHookStatus(), null);
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
		getCurrentScenarioContext().setHookStatus(mapStatus(result.getStatus()));
	}

	/**
	 * Report test item result and error (if present)
	 *
	 * @param result  - Cucumber result object
	 * @param message - optional message to be logged in addition
	 */
	protected void reportResult(@Nonnull Result result, @Nullable String message) {
		String cukesStatus = result.getStatus();
		String level = mapLevel(cukesStatus);
		if (message != null) {
			sendLog(message, level);
		}
		String errorMessage = result.getErrorMessage();
		if (errorMessage != null) {
			sendLog(errorMessage, level);
		} else if (result.getError() != null) {
			sendLog(getStackTrace(result.getError()), level);
		}
		RunningContext.ScenarioContext currentScenario = getCurrentScenarioContext();
		ItemStatus itemStatus = mapStatus(result.getStatus());
		currentScenario.updateStatus(itemStatus);
		if (itemStatus == ItemStatus.FAILED) {
			scenarioErrorMap.put(currentScenario.getId().blockingGet(), result.getError());
		}
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

	@Nullable
	private static String getDataType(@Nonnull byte[] data) {
		try {
			return MimeTypeDetector.detect(ByteSource.wrap(data), null);
		} catch (IOException e) {
			LOGGER.warn("Unable to detect MIME type", e);
		}
		return null;
	}

	/**
	 * Send a log with data attached.
	 *
	 * @param mimeType an attachment type
	 * @param data     data to attach
	 */
	@Override
	public void embedding(String mimeType, byte[] data) {
		String type = ofNullable(mimeType).filter(ContentType::isValidType).orElseGet(() -> getDataType(data));
		String attachmentName = ofNullable(type).map(t -> t.substring(0, t.indexOf("/"))).orElse("");
		ReportPortal.emitLog(new ReportPortalMessage(ByteSource.wrap(data), type, attachmentName),
				"UNKNOWN",
				Calendar.getInstance().getTime()
		);
	}

	@Override
	public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
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

	/**
	 * Returns the ID of the root item of the launch
	 *
	 * @return the ID of the root item of the launch
	 */
	protected abstract Optional<Maybe<String>> getRootItemId();

	/**
	 * Return a Test Case ID for a feature file
	 *
	 * @param codeRef   a code reference
	 * @param arguments a scenario arguments
	 * @return Test Case ID entity or null if it's not possible to calculate
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	private TestCaseIdEntry getTestCaseId(@Nullable String codeRef, @Nullable List<Argument> arguments) {
		return TestCaseIdUtils.getTestCaseId(codeRef, (List<Object>) ARGUMENTS_TRANSFORM.apply(arguments));
	}

	/**
	 * Return a Test Case ID for mapped code
	 *
	 * @param match   Cucumber's Match object
	 * @param codeRef a code reference
	 * @return Test Case ID entity or null if it's not possible to calculate
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	protected TestCaseIdEntry getTestCaseId(@Nonnull Match match, @Nullable String codeRef) {
		try {
			Method method = retrieveMethod(match);
			if (method == null) {
				return getTestCaseId(codeRef, match.getArguments());
			}
			return TestCaseIdUtils.getTestCaseId(method.getAnnotation(TestCaseId.class),
					method,
					codeRef,
					(List<Object>) ARGUMENTS_TRANSFORM.apply(match.getArguments())
			);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			return getTestCaseId(codeRef, match.getArguments());
		}
	}

	/**
	 * Build an item description for a scenario
	 *
	 * @param scenario a Cucumber's Scenario object
	 * @param uri      a feature URI
	 * @return item description
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected String getDescription(@Nonnull Scenario scenario, @Nonnull String uri) {
		return uri;
	}

	/**
	 * Build an item description for a feature
	 *
	 * @param feature a Cucumber's Feature object
	 * @param uri     a feature URI
	 * @return item description
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected String getDescription(@Nonnull Feature feature, @Nonnull String uri) {
		return uri;
	}

	/**
	 * Finish a test item with no specific status
	 *
	 * @param itemId an ID of the item
	 */
	protected void finishTestItem(Maybe<String> itemId) {
		finishTestItem(itemId, null, null);
	}

	/**
	 * Returns code reference for mapped code
	 *
	 * @param match Cucumber's Match object
	 * @return a code reference, or null if not possible to determine (ambiguous, undefined, etc.)
	 */
	@Nullable
	protected String getCodeRef(@Nonnull Match match) {
		try {
			Field stepDefinitionField = match.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
			stepDefinitionField.setAccessible(true);
			Object javaStepDefinition = stepDefinitionField.get(match);
			Method getLocationMethod = javaStepDefinition.getClass().getDeclaredMethod(GET_LOCATION_METHOD_NAME, boolean.class);
			getLocationMethod.setAccessible(true);
			String fullCodeRef = String.valueOf(getLocationMethod.invoke(javaStepDefinition, true));
			if (!fullCodeRef.isEmpty()) {
				int openingBracketIndex = fullCodeRef.indexOf(METHOD_OPENING_BRACKET);
				if (openingBracketIndex > 0) {
					return fullCodeRef.substring(0, fullCodeRef.indexOf(METHOD_OPENING_BRACKET));
				} else {
					return fullCodeRef;
				}
			} else {
				return match.getLocation();
			}
		} catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			return match.getLocation();
		}
	}

	/**
	 * Returns code reference for feature files by URI and text line number
	 *
	 * @param uri  a feature URI
	 * @param line a scenario line number
	 * @return a code reference
	 */
	@Nonnull
	protected String getCodeRef(@Nonnull String uri, int line) {
		return uri + ":" + line;
	}

	/**
	 * Map Cucumber statuses to RP statuses
	 *
	 * @param cukesStatus - Cucumber status
	 * @return RP status
	 */
	@Nonnull
	protected ItemStatus mapStatus(@Nullable String cukesStatus) {
		if (isBlank(cukesStatus)) {
			return ItemStatus.FAILED;
		}
		ItemStatus status = STATUS_MAPPING.get(cukesStatus.toLowerCase());
		return null == status ? ItemStatus.FAILED : status;
	}

	/**
	 * Map Cucumber statuses to RP log levels
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular log level
	 */
	@Nonnull
	protected String mapLevel(@Nullable String cukesStatus) {
		if (isBlank(cukesStatus)) {
			return "ERROR";
		}
		String level = LOG_LEVEL_MAPPING.get(cukesStatus.toLowerCase());
		return null == level ? "ERROR" : level;
	}

	/**
	 * Send a log entry to Report Portal with 'INFO' level.
	 *
	 * @param text a log text to send
	 */
	@Override
	public void write(String text) {
		sendLog(text);
	}

	/**
	 * Send a text log entry to Report Portal with 'INFO' level, using current datetime as timestamp
	 *
	 * @param message a text message
	 */
	protected void sendLog(final String message) {
		sendLog(message, "INFO");
	}

	/**
	 * Send a text log entry to Report Portal using current datetime as timestamp
	 *
	 * @param message a text message
	 * @param level   a log level, see standard Log4j / logback logging levels
	 */
	protected void sendLog(final String message, final String level) {
		ReportPortal.emitLog(message, level, Calendar.getInstance().getTime());
	}

	/**
	 * Returns a list of parameters for a step
	 *
	 * @param step    Cucumber's Step object
	 * @param codeRef a method code reference to retrieve parameter types
	 * @param match   Cucumber's Match object
	 * @return a list of parameters or empty list if none
	 */
	@Nonnull
	protected List<ParameterResource> getParameters(@Nonnull Step step, @Nullable String codeRef, @Nonnull Match match) {
		List<Pair<String, String>> params = ofNullable(match.getArguments()).map(a -> IntStream.range(0, a.size())
				.mapToObj(i -> Pair.of("arg" + i, a.get(i).getVal()))
				.collect(Collectors.toList())).orElse(new ArrayList<>());

		ofNullable(step.getDocString()).map(DocString::getValue)
				.filter(ds -> !ds.isEmpty())
				.ifPresent(ds -> params.add(Pair.of("docstring", StringEscapeUtils.escapeHtml4(ds))));
		ofNullable(step.getRows()).filter(rows -> !rows.isEmpty())
				.ifPresent(rows -> params.add(Pair.of("datatable",
						Utils.formatDataTable(rows.stream().map(Row::getCells).collect(Collectors.toList()))
				)));
		return params.isEmpty() ? Collections.emptyList() : ParameterUtils.getParameters(codeRef, params);
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	@Nonnull
	protected Set<ItemAttributesRQ> extractAttributes(@Nonnull List<Tag> tags) {
		Set<ItemAttributesRQ> result = new HashSet<>();
		for (Tag tag : tags) {
			result.add(new ItemAttributesRQ(null, tag.getName()));
		}
		return result;
	}

	/**
	 * Returns static attributes defined by {@link Attributes} annotation in code.
	 *
	 * @param match Cucumber's Match object
	 * @return a set of attributes or null if no such method provided by the match object
	 */
	@Nullable
	protected Set<ItemAttributesRQ> getAttributes(Match match) {
		try {
			Method method = retrieveMethod(match);
			if (method == null) {
				return null;
			}
			Attributes attributesAnnotation = method.getAnnotation(Attributes.class);
			if (attributesAnnotation != null) {
				return AttributeParser.retrieveAttributes(attributesAnnotation);
			}
		} catch (NoSuchFieldException | IllegalAccessException e) {
			return null;
		}
		return null;
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	protected String buildMultilineArgument(Step step) {
		StringBuilder marg = new StringBuilder();
		ofNullable(step.getRows()).map(rows -> rows.stream().map(Row::getCells).collect(Collectors.toList()))
				.filter(t -> !t.isEmpty())
				.ifPresent(t -> marg.append(formatDataTable(t)));
		ofNullable(step.getDocString()).map(DocString::getValue)
				.filter(ds -> !ds.isEmpty())
				.ifPresent(ds -> marg.append(DOCSTRING_DECORATOR).append(ds).append(DOCSTRING_DECORATOR));
		return marg.toString();
	}
}
