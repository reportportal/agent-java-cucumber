package com.epam.reportportal.cucumber.integration.feature;

import com.epam.reportportal.util.test.CommonUtils;
import cucumber.api.java.After;
import cucumber.api.java.Before;

public class DummyHooks {

	@Before
	public void beforePause() throws InterruptedException {
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@After
	public void afterPause() throws InterruptedException {
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}
}
