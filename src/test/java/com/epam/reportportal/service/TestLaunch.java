package com.epam.reportportal.service;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.reactivex.Maybe;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public class TestLaunch extends Launch {

	private final StartLaunchRQ startLaunchRQ;
	private Maybe<String> launchId;

	public TestLaunch(ListenerParameters listenerParameters, StartLaunchRQ startLaunchRQ, Maybe<String> launchId) {
		super(listenerParameters);
		this.startLaunchRQ = startLaunchRQ;
		this.launchId = launchId;
	}

	@Override
	public Maybe<String> start() {
		return launchId;
	}

	@Override
	public void finish(FinishExecutionRQ rq) {

	}

	@Override
	public Maybe<String> startTestItem(StartTestItemRQ rq) {
		return null;
	}

	@Override
	public Maybe<String> startTestItem(Maybe<String> parentId, StartTestItemRQ rq) {
		return null;
	}

	@Override
	public Maybe<String> startTestItem(Maybe<String> parentId, Maybe<String> retryOf, StartTestItemRQ rq) {
		return null;
	}

	@Override
	public Maybe<OperationCompletionRS> finishTestItem(Maybe<String> itemId, FinishTestItemRQ rq) {
		return null;
	}

	public StartLaunchRQ getStartLaunchRQ() {
		return startLaunchRQ;
	}

	public Maybe<String> getLaunchId() {
		return launchId;
	}
}
