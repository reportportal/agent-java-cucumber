package com.epam.reportportal.cucumber.integration.feature;

import cucumber.api.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmbiguousSteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(AmbiguousSteps.class);

	@Given("I have an ambiguous step (\\w+)")
	public void i_have_an_ambiguous_step(String param) {
		LOGGER.info("Inside 'I have an ambiguous step', parameter: " + param);
	}

	@Given("I have an ambiguous step two")
	public void i_have_an_ambiguous_step_two() {
		LOGGER.info("Inside 'I have an ambiguous step two'");
	}
}
