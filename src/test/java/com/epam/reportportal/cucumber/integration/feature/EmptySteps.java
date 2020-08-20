package com.epam.reportportal.cucumber.integration.feature;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmptySteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(EmptySteps.class);

	@Given("I have empty step")
	public void i_have_empty_step() {
		LOGGER.info("Inside 'I have empty step'");
	}

	@Then("I have another empty step")
	public void i_have_another_empty_step() {
		LOGGER.info("Inside 'I have another empty step'");
	}
}
