/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.cucumber;

import io.reactivex.Maybe;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Cucumber reporter for ReportPortal that reports individual steps as test
 * methods.
 * <p>
 * Mapping between Cucumber and ReportPortal is as follows:
 * <ul>
 * <li>feature - SUITE</li>
 * <li>scenario - TEST</li>
 * <li>step - STEP</li>
 * </ul>
 * Background steps are reported as part of corresponding scenarios. Outline
 * example rows are reported as individual scenarios with [ROW NUMBER] after the
 * name. Hooks are reported as BEFORE/AFTER_METHOD items (NOTE: all screenshots
 * created in hooks will be attached to these, and not to the actual failing
 * steps!)
 *
 * @deprecated Use {@link ScenarioReporter}, since the semantic of this class is completely broken and will be removed
 */
@Deprecated
public class StepReporter extends AbstractReporter {
	private static final String RP_STORY_TYPE = "STORY";
	private static final String RP_TEST_TYPE = "SCENARIO";

	public StepReporter() {
		super();
	}

	@Override
	@Nonnull
	protected Optional<Maybe<String>> getRootItemId() {
		return Optional.empty();
	}

	@Override
	@Nonnull
	protected String getFeatureTestItemType() {
		return RP_STORY_TYPE;
	}

	@Override
	@Nonnull
	protected String getScenarioTestItemType() {
		return RP_TEST_TYPE;
	}
}
