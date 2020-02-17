package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.TestLaunch;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.reactivex.Maybe;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rp.com.google.common.base.Suppliers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public class LaunchSystemAttributesTest {

	private static final Map<String, Pattern> properties = new HashMap<>();

	private final StepReporter stepReporter = new StepReporter();

	@Mock
	private ReportPortal reportPortal;

	@Mock
	private Maybe<String> launchId;

	@BeforeClass
	public static void initKeys() {
		properties.put("os", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("jvm", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("agent", Pattern.compile("^test-agent\\|test-1\\.0$"));
	}

	@Before
	public void initLaunch() {
		MockitoAnnotations.initMocks(this);
		stepReporter.RP = Suppliers.memoize(() -> {

			when(reportPortal.getParameters()).thenReturn(new ListenerParameters());
			ListenerParameters parameters = reportPortal.getParameters();

			StartLaunchRQ rq = new StartLaunchRQ();
			rq.setAttributes(parameters.getAttributes() == null ? new HashSet<>() : parameters.getAttributes());
			rq.getAttributes().addAll(SystemAttributesExtractor.extract("agent.properties"));

			when(reportPortal.newLaunch(any(StartLaunchRQ.class))).thenReturn(new TestLaunch(parameters, rq, launchId));
			return reportPortal.newLaunch(rq);
		});
	}

	@Test
	public void shouldRetrieveSystemAttributes() {
		TestLaunch testLaunch = (TestLaunch) stepReporter.RP.get();
		StartLaunchRQ startLaunchRequest = testLaunch.getStartLaunchRQ();

		Assert.assertNotNull(startLaunchRequest.getAttributes());
		Assert.assertEquals(3, startLaunchRequest.getAttributes().size());

		startLaunchRequest.getAttributes().forEach(attribute -> {
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
