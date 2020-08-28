package com.epam.reportportal.cucumber.integration.feature;

import com.epam.reportportal.annotations.TestCaseId;
import cucumber.api.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestCaseIdOnMethodSteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestCaseIdOnMethodSteps.class);
	public static final String TEST_CASE_ID = "My test case id";


	@Given("I have a test case ID on a step definition method")
	@TestCaseId(TEST_CASE_ID)
	public void i_have_a_test_case_id_on_a_stepdef_method() {
		LOGGER.info("Inside 'i_have_a_test_case_id_on_a_stepdef_method' method");
	}
}
