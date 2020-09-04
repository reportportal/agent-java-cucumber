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

package com.epam.reportportal.cucumber;

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.restendpoint.http.MultiPartRequest;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import cucumber.api.CucumberOptions;
import cucumber.api.testng.AbstractTestNGCucumberTests;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class EmbeddingTest {
	@CucumberOptions(features = "src/test/resources/features/ImageEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.image" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class ImageStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/TextEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.text" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class TextStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/PdfEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.pdf" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class PdfStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/ArchiveEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.zip" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class ZipStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList());
	private final List<Pair<String, ? extends Collection<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@Test
	public void verify_image_embedding() {
		TestUtils.runTests(ImageStepReporter.class);

		ArgumentCaptor<MultiPartRequest> logCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.flatMap(l -> ((List<SaveLogRQ>) l.getRequest()).stream())
				.filter(l -> Objects.nonNull(l.getFile()))
				.collect(Collectors.toList());

		assertThat(logs, hasSize(3));

		logs.forEach(l -> assertThat(l.getFile().getContentType(), equalTo("image/jpeg")));
	}

	@Test
	public void verify_text_embedding() {
		TestUtils.runTests(TextStepReporter.class);

		ArgumentCaptor<MultiPartRequest> logCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.flatMap(l -> ((List<SaveLogRQ>) l.getRequest()).stream())
				.filter(l -> Objects.nonNull(l.getFile()))
				.collect(Collectors.toList());

		assertThat(logs, hasSize(3));

		logs.forEach(l -> {
			SaveLogRQ.File file = l.getFile();
			assertThat(file.getContentType(), equalTo("text/plain"));
			assertThat(file.getName(), endsWith("txt"));
		});
	}

	@Test
	public void verify_pfd_embedding() {
		TestUtils.runTests(PdfStepReporter.class);

		ArgumentCaptor<MultiPartRequest> logCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.flatMap(l -> ((List<SaveLogRQ>) l.getRequest()).stream())
				.filter(l -> Objects.nonNull(l.getFile()))
				.collect(Collectors.toList());

		assertThat(logs, hasSize(3));

		logs.forEach(l -> assertThat(l.getFile().getContentType(), equalTo("application/pdf")));
	}

	@Test
	public void verify_archive_embedding() {
		TestUtils.runTests(ZipStepReporter.class);

		ArgumentCaptor<MultiPartRequest> logCaptor = ArgumentCaptor.forClass(MultiPartRequest.class);
		verify(client, times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(l -> l.getSerializedRQs().stream())
				.flatMap(l -> ((List<SaveLogRQ>) l.getRequest()).stream())
				.filter(l -> Objects.nonNull(l.getFile()))
				.collect(Collectors.toList());

		assertThat(logs, hasSize(3));

		logs.forEach(l -> assertThat(l.getFile().getContentType(), equalTo("application/zip")));
	}

}
