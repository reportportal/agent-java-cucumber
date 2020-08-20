package com.epam.reportportal.cucumber.integration.feature;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.testng.Assert.assertEquals;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class ParametersTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ParametersTest.class);
	private int itemsCount;

	@Given("I have (\\d+) (\\w+) in my pocket")
	public void iHaveNumberItemInMyPocket(int number, String item) {
		itemsCount = number;
		LOGGER.info("I have {} {} in my pocket", number, item);

	}

	@When("^I eat one$")
	public void iEatOne() {
		itemsCount -= 1;
		LOGGER.info("I eat one");
	}

	@Then("I have (\\d+) in my pocket")
	public void iHaveResultInMyPocket(int result) {
		assertEquals(result, itemsCount);
		LOGGER.info("I have {} in my pocket", result);
	}
}
