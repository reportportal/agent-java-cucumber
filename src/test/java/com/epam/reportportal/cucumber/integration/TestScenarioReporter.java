package com.epam.reportportal.cucumber.integration;

import com.epam.reportportal.cucumber.ScenarioReporter;
import com.epam.reportportal.service.ReportPortal;

public class TestScenarioReporter extends ScenarioReporter {
	public static final ThreadLocal<ReportPortal> RP = new ThreadLocal<>();

	@Override
	protected ReportPortal buildReportPortal() {
		return RP.get();
	}
}
