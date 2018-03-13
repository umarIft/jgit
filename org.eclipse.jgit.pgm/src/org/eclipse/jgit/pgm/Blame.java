/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Johannes E. Schindelin
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;

import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = false, usage = "usage_Blame")
class Blame extends TextBuiltin {
	private RawTextComparator comparator = RawTextComparator.DEFAULT;

	@Option(name = "-w", usage = "usage_ignoreWhitespace")
	void ignoreAllSpace(@SuppressWarnings("unused") boolean on) {
		comparator = RawTextComparator.WS_IGNORE_ALL;
	}

	@Option(name = "--abbrev", metaVar = "metaVar_n", usage = "usage_abbrevCommits")
	private int abbrev = 7;

	@Option(name = "-l", usage = "usage_blameLongRevision")
	private boolean showLongRevision;

	@Option(name = "-t", usage = "usage_blameRawTimestamp")
	private boolean showRawTimestamp;

	@Option(name = "-b", usage = "usage_blameShowBlankBoundary")
	private boolean showBlankBoundary;

	@Option(name = "--root", usage = "usage_blameShowRoot")
	private boolean root;

	@Option(name = "-L", metaVar = "metaVar_blameL", usage = "usage_blameRange")
	private String rangeString;

	@Argument(index = 0, required = false, metaVar = "metaVar_revision")
	private String revision;

	@Argument(index = 1, required = false, metaVar = "metaVar_file")
	private String file;

	private ObjectReader reader;

	private final Map<RevCommit, String> abbreviatedCommits = new HashMap<RevCommit, String>();

	private SimpleDateFormat dateFmt;

	private int begin;

	private int end;

	private BlameResult blame;

	@Override
	protected void run() throws Exception {
		if (file == null) {
			if (revision == null)
				throw die(CLIText.get().fileIsRequired);
			file = revision;
			revision = null;
		}

		if (!showBlankBoundary)
			root = db.getConfig().getBoolean("blame", "blankboundary", false);
		if (!root)
			root = db.getConfig().getBoolean("blame", "showroot", false);

		if (showRawTimestamp)
			dateFmt = new SimpleDateFormat("ZZZZ");
		else
			dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZZ");

		BlameGenerator generator = new BlameGenerator(db, file);
		reader = db.newObjectReader();
		try {
			generator.setTextComparator(comparator);
			if (revision != null)
				generator.setStartRevision(db.resolve(revision + "^{commit}"));

			blame = BlameResult.create(generator);
			begin = 0;
			end = blame.getResultContents().size();
			if (rangeString != null)
				parseRangeString();
			blame.computeRange(begin, end);

			int authorWidth = 8;
			int dateWidth = 8;
			for (int line = begin; line < end; line++) {
				authorWidth = Math.max(authorWidth, author(line).length());
				dateWidth = Math.max(dateWidth, date(line).length());
			}

			int lineWidth = 1 + (int) Math.log10(end + 1);
			String fmt = MessageFormat.format(
				" (%-{0}s %{1}s %{2}d) ",
				authorWidth,
				dateWidth,
				lineWidth);

			for (int line = begin; line < end; line++) {
				out.print(abbreviate(blame.getSourceCommit(line)));
				out.format(fmt, author(line), date(line), line + 1);
				out.flush();
				blame.getResultContents().writeLine(System.out, line);
				out.print('\n');
			}
		} finally {
			generator.release();
			reader.release();
		}
	}

	private void parseRangeString() {
		String beginStr, endStr;
		if (rangeString.startsWith("/")) {
			int c = rangeString.indexOf("/,", 1);
			if (c < 0) {
				beginStr = rangeString;
				endStr = String.valueOf(end);
			} else {
				beginStr = rangeString.substring(0, c);
				endStr = rangeString.substring(c + 2);
			}

		} else {
			int c = rangeString.indexOf(',');
			if (c < 0) {
				beginStr = rangeString;
				endStr = String.valueOf(end);
			} else if (c == 0) {
				beginStr = "0";
				endStr = rangeString.substring(1);
			} else {
				beginStr = rangeString.substring(0, c);
				endStr = rangeString.substring(c + 1);
			}
		}

		if (beginStr.startsWith("/")) {
			begin = findLine(0, beginStr);
		} else {
			begin = Integer.parseInt(beginStr);
		}

		if (endStr.startsWith("/")) {
			end = findLine(begin, endStr);
		} else if (endStr.startsWith("-")) {
			end = begin + Integer.parseInt(endStr);
		} else if (endStr.startsWith("+")) {
			end = begin + Integer.parseInt(endStr.substring(1));
		} else {
			end = Integer.parseInt(endStr);
		}
	}

	private int findLine(int b, String regex) {
		String re = regex.substring(1, regex.length() - 1);
		Pattern p = Pattern.compile(".*" + re + ".*");
		RawText text = blame.getResultContents();
		for (int line = b; line < text.size(); line++) {
			if (p.matcher(text.getString(line)).matches())
				return line;
		}
		return b;
	}

	private String author(int line) {
		PersonIdent author = blame.getSourceAuthor(line);
		if (author == null)
			return "";
		String name = author.getName();
		return name != null ? name : "";
	}

	private String date(int line) {
		PersonIdent author = blame.getSourceAuthor(line);
		if (author == null)
			return "";

		dateFmt.setTimeZone(author.getTimeZone());
		if (!showRawTimestamp)
			return dateFmt.format(author.getWhen());
		return String.format("%d %s", author.getWhen().getTime() / 1000L,
				dateFmt.format(author.getWhen()));
	}

	private String abbreviate(RevCommit commit) throws IOException {
		String r = abbreviatedCommits.get(commit);
		if (r != null)
			return r;

		if (showBlankBoundary && commit.getParentCount() == 0)
			commit = null;

		if (commit == null) {
			int len = showLongRevision ? OBJECT_ID_STRING_LENGTH : (abbrev + 1);
			StringBuilder b = new StringBuilder(len);
			for (int i = 0; i < len; i++)
				b.append(' ');
			r = b.toString();

		} else if (!root && commit.getParentCount() == 0) {
			if (showLongRevision)
				r = "^" + commit.name().substring(0, OBJECT_ID_STRING_LENGTH - 1);
			else
				r = "^" + reader.abbreviate(commit, abbrev).name();
		} else {
			if (showLongRevision)
				r = commit.name();
			else
				r = reader.abbreviate(commit, abbrev + 1).name();
		}

		abbreviatedCommits.put(commit, r);
		return r;
	}
}
