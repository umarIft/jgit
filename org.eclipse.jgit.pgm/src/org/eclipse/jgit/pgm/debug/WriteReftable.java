/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.pgm.debug;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command
class WriteReftable extends TextBuiltin {
	private static final int KIB = 1 << 10;
	private static final int MIB = 1 << 20;

	@Option(name = "--block-size")
	private int refBlockSize = 8 * KIB;

	@Option(name = "--log-block-size")
	private int logBlockSize = 8 * KIB;

	@Option(name = "--restart-interval")
	private int restartInterval;

	@Option(name = "--reflog-in")
	private String reflogIn;

	@Argument(index = 0)
	private String in;

	@Argument(index = 1)
	private String out;

	@SuppressWarnings({ "nls", "boxing" })
	@Override
	protected void run() throws Exception {
		List<Ref> refs = read(in);
		List<LogEntry> logs = readLog(reflogIn);

		ReftableWriter.Stats stats;
		try (OutputStream os = new FileOutputStream(out)) {
			ReftableWriter w = new ReftableWriter();
			w.setRefBlockSize(refBlockSize);
			w.setLogBlockSize(logBlockSize);
			w.setRestartInterval(restartInterval);
			w.begin(os);
			for (Ref r : refs) {
				w.writeRef(r);
			}
			for (LogEntry e : logs) {
				w.writeLog(e.ref, e.who, e.oldId, e.newId, e.message);
			}
			stats = w.finish().getStats();
		}

		int fileMiB = (int) Math.round(((double) stats.totalBytes()) / MIB);
		printf("Summary:");
		printf("  hash    : %s", stats.hash().name());
		printf("  file sz : %d MiB (%d bytes)", fileMiB, stats.totalBytes());
		printf("  padding : %d KiB", stats.paddingBytes() / KIB);
		errw.println();

		printf("Refs:");
		printf("  ref blk : %d", stats.refBlockSize());
		printf("  restarts: %d", stats.restartInterval());
		printf("  refs    : %d", refs.size());
		printf("  blocks  : %d", stats.refBlockCount());
		if (stats.refIndexKeys() > 0) {
			int idxSize = (int) Math.round(((double) stats.refIndexSize()) / KIB);
			int avgIdx = stats.refIndexSize() / stats.refIndexKeys();
			printf("  idx keys: %d", stats.refIndexKeys());
			printf("  idx sz  : %d KiB", idxSize);
			printf("  avg idx : %d bytes", avgIdx);
		}
		printf("  lookup  : %.1f", stats.diskSeeksPerRead());
		long avgPad = stats.paddingBytes() / stats.refBlockCount();
		printf("  avg pad : %d bytes / block", avgPad);
		printf("  avg ref : %d bytes", stats.refBytes() / refs.size());
		printf("  refs/blk: %d", refs.size() / stats.refBlockCount());
		errw.println();

		if (logs.size() > 0) {
			int logMiB = (int) Math.round(((double) stats.logBytes()) / MIB);
			printf("Log:");
			printf("  log blk : %d", stats.logBlockSize());
			printf("  logs    : %d", logs.size());
			printf("  log sz  : %d MiB (%d bytes)", logMiB, stats.logBytes());
			printf("  avg log : %d bytes", stats.logBytes() / logs.size());
			errw.println();
		}
	}

	private void printf(String fmt, Object... args) throws IOException {
		errw.println(String.format(fmt, args));
	}

	static List<Ref> read(String inputFile) throws IOException {
		List<Ref> refs = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(inputFile), UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				ObjectId id = ObjectId.fromString(line.substring(0, 40));
				String name = line.substring(41, line.length());
				if (name.endsWith("^{}")) { //$NON-NLS-1$
					int lastIdx = refs.size() - 1;
					Ref last = refs.get(lastIdx);
					refs.set(lastIdx, new ObjectIdRef.PeeledTag(PACKED,
							last.getName(), last.getObjectId(), id));
					continue;
				}

				Ref ref;
				if (name.equals(HEAD)) {
					ref = new SymbolicRef(name, new ObjectIdRef.Unpeeled(NEW,
							R_HEADS + MASTER, null));
				} else {
					ref = new ObjectIdRef.PeeledNonTag(PACKED, name, id);
				}
				refs.add(ref);
			}
		}
		Collections.sort(refs, (a, b) -> a.getName().compareTo(b.getName()));
		return refs;
	}

	private static List<LogEntry> readLog(String logPath)
			throws FileNotFoundException, IOException {
		if (logPath == null) {
			return Collections.emptyList();
		}

		List<LogEntry> log = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(logPath), UTF_8))) {
			@SuppressWarnings("nls")
			Pattern pattern = Pattern.compile("([^,]+)" // 1: ref
					+ ",([0-9]+)(?:[.][0-9]+)?" // 2: time
					+ ",([^,]+)" // 3: who
					+ ",([^,]+)" // 4: old
					+ ",([^,]+)" // 5: new
					+ ",(.*)"); // 6: msg
			String line;
			while ((line = br.readLine()) != null) {
				Matcher m = pattern.matcher(line);
				if (m.matches()) {
					String ref = m.group(1);
					long time = Long.parseLong(m.group(2), 10) * 1000L;
					String user = m.group(3);
					ObjectId oldId = parseId(m.group(4));
					ObjectId newId = parseId(m.group(5));
					String msg = m.group(6);
					String email = user + "@gerrit"; //$NON-NLS-1$
					PersonIdent who = new PersonIdent(user, email, time, -480);
					log.add(new LogEntry(ref, who, oldId, newId, msg));
				}
			}
		}
		Collections.sort(log, LogEntry::compare);
		return log;
	}

	private static ObjectId parseId(String s) {
		if ("NULL".equals(s)) { //$NON-NLS-1$
			return ObjectId.zeroId();
		}
		return ObjectId.fromString(s);
	}

	private static class LogEntry {
		static int compare(LogEntry a, LogEntry b) {
			int cmp = a.ref.compareTo(b.ref);
			if (cmp == 0) {
				cmp = Long.signum(b.time() - a.time());
			}
			return cmp;
		}

		final String ref;
		final PersonIdent who;
		final ObjectId oldId;
		final ObjectId newId;
		final String message;

		LogEntry(String ref, PersonIdent who,
				ObjectId oldId, ObjectId newId, String message) {
			this.ref = ref;
			this.who = who;
			this.oldId = oldId;
			this.newId = newId;
			this.message = message;
		}

		long time() {
			return who.getWhen().getTime();
		}
	}
}
