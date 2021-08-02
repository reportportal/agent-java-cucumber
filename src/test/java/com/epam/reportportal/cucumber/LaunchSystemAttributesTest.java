package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.reactivex.Maybe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
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

	@BeforeAll
	public static void initKeys() {
		properties.put("os", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("jvm", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("agent", Pattern.compile("^test-agent\\|test-1\\.0$"));
	}

	@BeforeEach
	public void initLaunch() {
		MockitoAnnotations.initMocks(this);
		stepReporter = new StepReporter() {
			@Override
			protected ReportPortal buildReportPortal() {
				return ReportPortal.create(reportPortalClient, TestUtils.standardParameters());
			}
		};
	}

	@Test
	public void shouldRetrieveSystemAttributes() {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(t -> Maybe.create(emitter -> {
			emitter.onSuccess("launchId");
			emitter.onComplete();
		}).cache());

		//noinspection ResultOfMethodCallIgnored
		stepReporter.launch.get().start().blockingGet();

		ArgumentCaptor<StartLaunchRQ> launchRQArgumentCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(reportPortalClient, times(1)).startLaunch(launchRQArgumentCaptor.capture());

		StartLaunchRQ startLaunchRequest = launchRQArgumentCaptor.getValue();

		assertThat(startLaunchRequest.getAttributes(), notNullValue());

		List<ItemAttributesRQ> attributes = new ArrayList<>(startLaunchRequest.getAttributes());
		attributes.removeIf(attribute -> attribute.getKey().equals(SKIPPED_ISSUE_KEY));

		assertThat(attributes, hasSize(3));
		attributes.forEach(attr -> {
			assertThat(attr.isSystem(), equalTo(Boolean.TRUE));
			Pattern pattern = LaunchSystemAttributesTest.this.getPattern(attr);
			assertThat(pattern, notNullValue());
			assertThat(attr.getValue(), allOf(notNullValue(), matchesPattern(pattern)));
		});
	}

	private Pattern getPattern(ItemAttributesRQ attribute) {
		return ofNullable(properties.get(attribute.getKey())).orElse(null);

	}

}
