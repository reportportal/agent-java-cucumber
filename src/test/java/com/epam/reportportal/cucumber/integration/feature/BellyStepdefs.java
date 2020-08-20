package com.epam.reportportal.cucumber.integration.feature;

import com.epam.reportportal.annotations.attribute.Attribute;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.annotations.attribute.MultiKeyAttribute;
import com.epam.reportportal.annotations.attribute.MultiValueAttribute;
import com.epam.reportportal.cucumber.integration.service.Belly;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BellyStepdefs {

	private final Belly belly = new Belly();

	@Attributes(attributes = { @Attribute(key = "key", value = "value") })
	@Given("^I have (\\d+) cukes in my belly$")
	public void I_have_cukes_in_my_belly(int cukes) {
		belly.eat(cukes);
	}

	@Attributes(attributes = { @Attribute(key = "key1", value = "value1"),
			@Attribute(key = "key2", value = "value2") }, multiKeyAttributes = { @MultiKeyAttribute(keys = { "k1", "k2" }, value = "v") })
	@When("^I wait (\\d+) hour$")
	public void I_wait(int hours) {
		belly.wait(hours);
	}

	@Attributes(multiValueAttributes = { @MultiValueAttribute(isNullKey = true, values = { "v1", "v2" }) })
	@Then("^my belly should growl$")
	public void my_belly_should_growl() {
		assertThat(belly.growl(), equalTo(Boolean.TRUE));
	}
}
