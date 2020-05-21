package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class ParametersTest {

	private StepReporter stepReporter;

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
			@Override
			protected ReportPortal buildReportPortal() {
				return ReportPortal.create(reportPortalClient, listenerParameters);
			}
		};

		stepReporter.currentFeatureId = Maybe.create(emitter -> {
			emitter.onSuccess("featureId");
			emitter.onComplete();
		});

		stepReporter.currentScenario = new AbstractReporter.ScenarioContext(Maybe.create(emitter -> {
			emitter.onSuccess("scenarioId");
			emitter.onComplete();
		}));
	}

	@Test
	public void verifyClientRetrievesParametersInRequest() {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(t -> Maybe.create(emitter -> {
			StartLaunchRS rs = new StartLaunchRS();
			rs.setId("launchId");
			emitter.onSuccess(rs);
			emitter.onComplete();
		}).cache());

		ArrayList<String> parameterTypes = Lists.newArrayList("int", "String");
		ArrayList<String> parameterValues = Lists.newArrayList("1", "parameter");

		stepReporter.beforeStep(getStep(), getMatch(parameterTypes, parameterValues));

		ArgumentCaptor<StartTestItemRQ> startTestItemRQArgumentCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(reportPortalClient, times(1)).startTestItem(anyString(), startTestItemRQArgumentCaptor.capture());

		StartTestItemRQ request = startTestItemRQArgumentCaptor.getValue();
		assertNotNull(request);
		assertNotNull(request.getParameters());
		assertEquals(2, request.getParameters().size());
		assertTrue(request.getParameters()
				.stream()
				.allMatch(it -> parameterValues.contains(it.getValue()) && parameterTypes.contains(it.getKey())));
	}

	private Match getMatch(List<String> parameterTypes, List<String> parameterValues) {
		return new Match(
				parameterValues.stream().map(it -> new Argument(RandomUtils.nextInt(), it)).collect(Collectors.toList()),
				String.format("com.test.Parametrized(%s)", String.join(",", parameterTypes))
		);
	}

	private Step getStep() {
		return new Step(Collections.emptyList(), "Given", "Parametrized", 1, Collections.emptyList(), null);
	}

}
