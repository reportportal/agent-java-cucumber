/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/agent-java-cucumber
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenersUtils;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.issue.Issue;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Function;

import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
	private static final String TABLE_SEPARATOR = "|";
	private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";

	private Utils() {

	}


	public static void finishTestItem(ReportPortal rp, Maybe<String> itemId) {
		finishTestItem(rp, itemId, null, "");
	}

	public static void finishTestItem(ReportPortal rp, Maybe<String> itemId, String status, String issueComments) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return;
		}

		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setStatus(status);
		rq.setEndTime(Calendar.getInstance().getTime());
		if (Statuses.SKIPPED.equals(status)) {
			Issue i = new Issue();
			i.setIssueType("NOT_ISSUE");
			rq.setIssue(i);
		} else if (!issueComments.isEmpty()) {
			Issue i = new Issue();
			i.setIssueType("AUTOMATION_BUG");
			i.setComment(issueComments);
			rq.setIssue(i);
		}

		rp.finishTestItem(itemId, rq);

	}

	public static Maybe<String> startNonLeafNode(ReportPortal rp, Maybe<String> rootItemId, String name, String description, List<Tag> tags,
			String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setDescription(description);
		rq.setName(name);
		rq.setTags(extractTags(tags));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);

		return rp.startTestItem(rootItemId, rq);
	}

	public static void sendLog(final String message, final String level, final File file) {

		ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
			@Nullable
			@Override
			public SaveLogRQ apply(@Nullable String item) {
				SaveLogRQ rq = new SaveLogRQ();
				rq.setMessage(message);
				rq.setTestItemId(item);
				rq.setLevel(level);
				rq.setLogTime(Calendar.getInstance().getTime());
				if (file != null) {
					rq.setFile(file);
				}
				return rq;
			}
		});
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	public static Set<String> extractTags(List<Tag> tags) {
		Set<String> returnTags = new HashSet<String>();
		for (Tag tag : tags) {
			returnTags.add(tag.getName());
		}
		return returnTags;
	}

	/**
	 * Map Cucumber statuses to RP log levels
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular log level
	 */
	public static String mapLevel(String cukesStatus) {
		String mapped = null;
		if (cukesStatus.equalsIgnoreCase("passed")) {
			mapped = "INFO";
		} else if (cukesStatus.equalsIgnoreCase("skipped")) {
			mapped = "WARN";
		} else {
			mapped = "ERROR";
		}
		return mapped;
	}

	/**
	 * Map Cucumber statuses to RP
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular status
	 */
	public static String mapStatus(String cukesStatus) {
		String mapped = null;
		if (cukesStatus.equalsIgnoreCase("passed")) {
			mapped = Statuses.PASSED;
		} else if (cukesStatus.equalsIgnoreCase("skipped")) {
			mapped = Statuses.SKIPPED;
		} else {
			mapped = Statuses.FAILED;
		}
		return mapped;
	}

	/**
	 * Generate statement representation
	 *
	 * @param stmt   - Cucumber statement
	 * @param prefix - substring to be prepended at the beginning (optional)
	 * @param infix  - substring to be inserted between keyword and name
	 * @param suffix - substring to be appended at the end (optional)
	 * @return transformed string
	 */
	public static String buildStatementName(BasicStatement stmt, String prefix, String infix, String suffix) {
		return (prefix == null ? "" : prefix) + stmt.getKeyword() + infix + stmt.getName() + (suffix == null ? "" : suffix);
	}

	/**
	 * Generate issue comments for undefined/pending scenarios
	 *
	 * @param result - Cucumber result object
	 * @return - generated comments
	 */
	public static String buildIssueComments(Result result) {
		String cukesStatus = result.getStatus();
		if (cukesStatus.equals("pending")) {
			return "Pending step";
		} else if (cukesStatus.equals("undefined")) {
			return "Undefined step";
		} else {
			return null;
		}
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	public static String buildMultilineArgument(Step step) {
		List<DataTableRow> table = step.getRows();
		DocString ds = step.getDocString();
		StringBuilder marg = new StringBuilder();
		if (table != null) {
			marg.append("\r\n");
			for (Row row : table) {
				marg.append(TABLE_SEPARATOR);
				for (String cell : row.getCells()) {
					marg.append(" ").append(cell).append(" ").append(TABLE_SEPARATOR);
				}

				marg.append("\r\n");
			}
		}

		if (ds != null) {
			marg.append(DOCSTRING_DECORATOR).append(ds.getValue()).append(DOCSTRING_DECORATOR);
		}
		return marg.toString();
	}
}
