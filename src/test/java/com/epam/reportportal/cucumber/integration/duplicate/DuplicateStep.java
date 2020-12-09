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

package com.epam.reportportal.cucumber.integration.duplicate;

import com.epam.reportportal.cucumber.integration.feature.EmptySteps;
import cucumber.api.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateStep {
	private static final Logger LOGGER = LoggerFactory.getLogger(EmptySteps.class);

	@Then("I have duplicate step")
	public void i_have_duplicate_step() {
		LOGGER.info("Inside 'I have duplicate step'");
	}
}
