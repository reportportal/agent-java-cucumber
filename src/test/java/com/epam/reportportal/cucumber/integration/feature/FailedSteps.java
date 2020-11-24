/*
 *  Copyright 2020 EPAM Systems
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.reportportal.cucumber.integration.feature;

import cucumber.api.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailedSteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(FailedSteps.class);

	public static final String ERROR_MESSAGE = "A failed step";

	@Given("I have a failed step")
	public void i_have_a_failed_step() {
		LOGGER.info("Inside 'I have a failed step'");
		throw new IllegalStateException(ERROR_MESSAGE);
	}
}
