package cucumber.runtime.java;

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.annotations.TestCaseIdKey;
import com.epam.reportportal.cucumber.AbstractReporter;
import com.epam.reportportal.cucumber.StepReporter;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import cucumber.api.java.ObjectFactory;
import cucumber.runtime.StepDefinition;
import cucumber.runtime.StepDefinitionMatch;
import cucumber.runtime.xstream.LocalizedXStreams;
import gherkin.formatter.Argument;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Step;
import io.reactivex.Maybe;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rp.com.google.common.collect.Lists;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public class CodeRefTest {

	private StepReporter stepReporter;

	@Mock
	private AbstractReporter.ScenarioContext scenarioContext;

	@Mock
	private ReportPortalClient reportPortalClient;

	@Mock
	private ListenerParameters listenerParameters;

	@Before
	public void initLaunch() {
		MockitoAnnotations.initMocks(this);
		when(listenerParameters.getEnable()).thenReturn(true);
		when(listenerParameters.getBaseUrl()).thenReturn("http://example.com");
		when(listenerParameters.getIoPoolSize()).thenReturn(10);
		when(listenerParameters.getBatchLogsSize()).thenReturn(5);
		stepReporter = new StepReporter() {
			{
				when(scenarioContext.getNextStep()).thenReturn(getStep());
				when(scenarioContext.getId()).thenReturn(Maybe.create(emitter -> {
					emitter.onSuccess("scenarioId");
					emitter.onComplete();
				}));
				currentScenario = scenarioContext;
			}

			@Override
			protected ReportPortal buildReportPortal() {

				return ReportPortal.create(reportPortalClient, listenerParameters);
			}
		};
	}

	@Test
	public void shouldSendCaseIdWhenNotParametrized() throws NoSuchMethodException {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(t -> Maybe.create(emitter -> {
			StartLaunchRS rs = new StartLaunchRS();
			rs.setId("launchId");
			emitter.onSuccess(rs);
			emitter.onComplete();
		}).cache());

		ArrayList<String> parameterValues = Lists.newArrayList("1", "parameter");

		Step step = getStep();
		stepReporter.match(getMatch(step, getStepDefinition(this.getClass().getDeclaredMethod("testCaseIdMethod")), parameterValues));

		ArgumentCaptor<StartTestItemRQ> startTestItemRQArgumentCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(reportPortalClient, times(1)).startTestItem(anyString(), startTestItemRQArgumentCaptor.capture());

		StartTestItemRQ request = startTestItemRQArgumentCaptor.getValue();
		assertNotNull(request);
		assertNotNull(request.getTestCaseId());
		assertEquals("SimpleCaseId", request.getTestCaseId());
	}

	@TestCaseId(value = "SimpleCaseId")
	public void testCaseIdMethod() {

	}

	@Test
	public void shouldSendCaseIdWhenParametrized() throws NoSuchMethodException {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(t -> Maybe.create(emitter -> {
			StartLaunchRS rs = new StartLaunchRS();
			rs.setId("launchId");
			emitter.onSuccess(rs);
			emitter.onComplete();
		}).cache());

		ArrayList<String> parameterValues = Lists.newArrayList("Parametrized Case Id");

		Step step = getStep();

		stepReporter.match(getMatch(step,
				getStepDefinition(this.getClass().getDeclaredMethod("testCaseIdParametrizedMethod", String.class)),
				parameterValues
		));

		ArgumentCaptor<StartTestItemRQ> startTestItemRQArgumentCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(reportPortalClient, times(1)).startTestItem(anyString(), startTestItemRQArgumentCaptor.capture());

		StartTestItemRQ request = startTestItemRQArgumentCaptor.getValue();
		assertNotNull(request);
		assertNotNull(request.getTestCaseId());
		assertEquals("Parametrized Case Id", request.getTestCaseId());
	}

	@TestCaseId(parametrized = true)
	public void testCaseIdParametrizedMethod(@TestCaseIdKey String caseIdKey) {

	}

	@Test
	public void shouldSendCaseIdWhenParametrizedWithoutKey() throws NoSuchMethodException {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(t -> Maybe.create(emitter -> {
			StartLaunchRS rs = new StartLaunchRS();
			rs.setId("launchId");
			emitter.onSuccess(rs);
			emitter.onComplete();
		}).cache());

		ArrayList<String> parameterValues = Lists.newArrayList("123", "Parametrized Case Id");

		Step step = getStep();

		stepReporter.match(getMatch(step,
				getStepDefinition(this.getClass().getDeclaredMethod("testCaseIdParametrizedMethodWithoutKey", Integer.class, String.class)),
				parameterValues
		));

		ArgumentCaptor<StartTestItemRQ> startTestItemRQArgumentCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(reportPortalClient, times(1)).startTestItem(anyString(), startTestItemRQArgumentCaptor.capture());

		StartTestItemRQ request = startTestItemRQArgumentCaptor.getValue();
		assertNotNull(request);
		assertNotNull(request.getTestCaseId());
		assertEquals("cucumber.runtime.java.CodeRefTest.testCaseIdParametrizedMethodWithoutKey[123,Parametrized Case Id]",
				request.getTestCaseId()
		);
	}

	@TestCaseId(parametrized = true)
	public void testCaseIdParametrizedMethodWithoutKey(Integer index, String caseIdKey) {

	}

	private Match getMatch(Step step, StepDefinition stepDefinition, List<String> parameterValues) {
		return new StepDefinitionMatch(parameterValues.stream()
				.map(it -> new Argument(RandomUtils.nextInt(), it))
				.collect(Collectors.toList()), stepDefinition, "featurePath", step, mock(LocalizedXStreams.class));
	}

	private JavaStepDefinition getStepDefinition(Method method) {
		ObjectFactory objectFactory = mock(ObjectFactory.class);
		return new JavaStepDefinition(method, Pattern.compile("dummy"), 100L, objectFactory);
	}

	private Type[] getTypes() {
		return new Type[] { Integer.class, String.class };
	}

	private Step getStep() {
		return new Step(Collections.emptyList(), "Given", "Parametrized", 1, Collections.emptyList(), null);
	}
}
