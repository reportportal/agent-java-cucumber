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

package com.epam.reportportal.cucumber.integration.embed.zip;

import com.epam.reportportal.util.test.CommonUtils;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.Objects;

public class EmbeddingStepdefs {
	public String type;

	@Given("I have a dummy step to attach an archive with correct mime type")
	public void i_have_a_dummy_step_to_make_a_screenshot_correct_type() throws InterruptedException {
		type = "image/jpeg";
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@Given("I have a dummy step to attach an archive with incorrect mime type")
	public void i_have_a_dummy_step_to_make_a_screenshot_incorrect_type() throws InterruptedException {
		type = "image/png";
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@Given("I have a dummy step to attach an archive with partially correct mime type")
	public void i_have_a_dummy_step_to_make_a_screenshot_partially_correct_type() throws InterruptedException {
		type = "jpeg";
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@After
	public void embedAnArchive(Scenario scenario) throws IOException {
		scenario.embed(IOUtils.toByteArray(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("files/demo.zip"))),
				type
		);
	}
}
