package com.epam.reportportal.cucumber.integration;

import com.epam.reportportal.cucumber.StepReporter;
import com.epam.reportportal.service.ReportPortal;

public class TestStepReporter extends StepReporter {
	public static final ThreadLocal<ReportPortal> RP = new ThreadLocal<>();

	@Override
	protected ReportPortal buildReportPortal() {
		return RP.get();
	}
}
