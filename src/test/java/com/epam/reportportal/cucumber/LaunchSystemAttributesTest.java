package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.reactivex.Maybe;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rp.com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public class LaunchSystemAttributesTest {

	private static final Map<String, Pattern> properties = new HashMap<>();

	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	private StepReporter stepReporter;

	@Mock
	private ReportPortalClient reportPortalClient;

	@Mock
	private ListenerParameters listenerParameters;

	@BeforeClass
	public static void initKeys() {
		properties.put("os", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("jvm", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("agent", Pattern.compile("^test-agent\\|test-1\\.0$"));
	}

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
	}

	@Test
	public void shouldRetrieveSystemAttributes() {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(t -> Maybe.create(emitter -> {
			emitter.onSuccess("launchId");
			emitter.onComplete();
		}).cache());

		stepReporter.RP.get().start().blockingGet();

		ArgumentCaptor<StartLaunchRQ> launchRQArgumentCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(reportPortalClient, times(1)).startLaunch(launchRQArgumentCaptor.capture());

		StartLaunchRQ startLaunchRequest = launchRQArgumentCaptor.getValue();

		Assert.assertNotNull(startLaunchRequest.getAttributes());

		List<ItemAttributesRQ> attributes = Lists.newArrayList(startLaunchRequest.getAttributes());
		attributes.removeIf(attribute -> attribute.getKey().equals(SKIPPED_ISSUE_KEY));

		Assert.assertEquals(3, attributes.size());

		attributes.forEach(attribute -> {
			Assert.assertTrue(attribute.isSystem());

			Pattern pattern = getPattern(attribute);
			Assert.assertNotNull(pattern);
			Assert.assertTrue(pattern.matcher(attribute.getValue()).matches());
		});

	}

	private Pattern getPattern(ItemAttributesRQ attribute) {
		return ofNullable(properties.get(attribute.getKey())).orElse(null);

	}

}
