package com.epam.reportportal.cucumber.integration.feature;

import com.epam.reportportal.annotations.Step;
import com.epam.reportportal.annotations.attribute.Attribute;
import com.epam.reportportal.annotations.attribute.AttributeValue;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.util.test.CommonUtils;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NestedSteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(NestedSteps.class);

	public static final long PARAM1 = 7L;

	public static final String PARAM2 = "second param";

	@Given("^I have a step$")
	public void i_have_empty_step() throws InterruptedException {
		LOGGER.info("Inside 'I have a step'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		and_a_step_inside_step();
	}

	@Step("A step inside step")
	public void and_a_step_inside_step() throws InterruptedException {
		LOGGER.info("Inside 'and_a_step_inside_nested_step'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		and_a_step_inside_nested_step();
	}

	@Step("A step inside nested step")
	public void and_a_step_inside_nested_step() {
		LOGGER.info("Inside 'and_a_step_inside_nested_step'");
	}

	@When("I have one more step")
	public void i_have_one_more_empty_step() throws InterruptedException {
		LOGGER.info("Inside 'I have one more step'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		with_a_step_with_parameters(PARAM1, PARAM2);
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		with_a_step_with_attributes();
	}

	@Step("A step with parameters")
	public void with_a_step_with_parameters(long one, String two) throws InterruptedException {
		LOGGER.info("Inside 'with_a_step_with_parameters': '" + one + "'; '" + two + "'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@Step("A step with attributes")
	@Attributes(attributes = @Attribute(key = "key", value = "value"), attributeValues = @AttributeValue("tag"))
	public void with_a_step_with_attributes() throws InterruptedException {
		LOGGER.info("Inside 'with_a_step_with_attributes'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}
}
