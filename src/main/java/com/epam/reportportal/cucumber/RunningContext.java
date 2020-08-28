package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import gherkin.formatter.model.Step;
import io.reactivex.Maybe;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Queue;

public class RunningContext {

	public static class FeatureContext {
		private Maybe<String> id;
		private final StartTestItemRQ itemRq;

		public FeatureContext(StartTestItemRQ startRq) {
			itemRq = startRq;
		}

		public void setId(Maybe<String> newId) {
			id = newId;
		}

		public Maybe<String> getId() {
			return id;
		}

		public StartTestItemRQ getItemRq() {
			return itemRq;
		}
	}

	public static class ScenarioContext {

		private boolean inBackground;
		private String stepPrefix;
		private Maybe<String> currentStepId;
		private Maybe<String> hookStepId;
		private ItemStatus hookStatus;

		private final Queue<Step> steps;
		private final Queue<String> outlineIterations;

		private Maybe<String> id;
		private ItemStatus status;

		public ScenarioContext() {
			stepPrefix = "";
			steps = new ArrayDeque<>();
			outlineIterations = new ArrayDeque<>();
			status = ItemStatus.PASSED;
		}

		public ScenarioContext(Maybe<String> newId) {
			this();
			id = newId;
		}

		@Nonnull
		public Queue<String> getOutlineIterations() {
			return outlineIterations;
		}

		public void setId(Maybe<String> id) {
			this.id = id;
		}

		public boolean isInBackground() {
			return inBackground;
		}

		public void setInBackground(boolean inBackground) {
			this.inBackground = inBackground;
		}

		public String getStepPrefix() {
			return stepPrefix;
		}

		public void setStepPrefix(String prefix) {
			stepPrefix = prefix;
		}

		public Maybe<String> getCurrentStepId() {
			return currentStepId;
		}

		public void setCurrentStepId(Maybe<String> currentStepId) {
			this.currentStepId = currentStepId;
		}

		public Maybe<String> getHookStepId() {
			return hookStepId;
		}

		public void setHookStepId(Maybe<String> hookStepId) {
			this.hookStepId = hookStepId;
		}

		public ItemStatus getHookStatus() {
			return hookStatus;
		}

		public void setHookStatus(ItemStatus hookStatus) {
			this.hookStatus = hookStatus;
		}

		public Maybe<String> getId() {
			return id;
		}

		public void addStep(Step step) {
			steps.add(step);
		}

		public Step getNextStep() {
			return steps.poll();
		}

		public boolean noMoreSteps() {
			return steps.isEmpty();
		}

		public void updateStatus(ItemStatus newStatus) {
			if (status != newStatus) {
				if (ItemStatus.FAILED != status) {
					status = newStatus;
				}
			}
		}

		public ItemStatus getStatus() {
			return status;
		}
	}
}