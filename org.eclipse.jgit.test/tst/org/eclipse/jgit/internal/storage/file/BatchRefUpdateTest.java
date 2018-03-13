/*
 * Copyright (C) 2017 Google Inc.
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

package org.eclipse.jgit.internal.storage.file;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.LOCK_FAILURE;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.OK;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.TRANSACTION_ABORTED;
import static org.eclipse.jgit.lib.ObjectId.zeroId;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.StrictWorkMonitor;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CheckoutEntry;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@SuppressWarnings("boxing")
@RunWith(Parameterized.class)
public class BatchRefUpdateTest extends LocalDiskRepositoryTestCase {
	@Parameter
	public boolean atomic;

	@Parameters(name = "atomic={0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{ {Boolean.FALSE}, {Boolean.TRUE} });
	}

	private Repository diskRepo;
	private TestRepository<Repository> repo;
	private RefDirectory refdir;
	private RevCommit A;
	private RevCommit B;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		diskRepo = createBareRepository();
		StoredConfig cfg = diskRepo.getConfig();
		cfg.load();
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);
		cfg.save();

		refdir = (RefDirectory) diskRepo.getRefDatabase();
		refdir.setRetrySleepMs(Arrays.asList(0, 0));

		repo = new TestRepository<>(diskRepo);
		A = repo.commit().create();
		B = repo.commit(repo.getRevWalk().parseCommit(A));
	}

	@Test
	public void simpleNoForce() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters", UPDATE_NONFASTFORWARD));
		execute(newBatchUpdate(cmds));

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, REJECTED_NONFASTFORWARD);
			assertRefs(
					"refs/heads/master", A,
					"refs/heads/masters", B);
		} else {
			assertResults(cmds, OK, REJECTED_NONFASTFORWARD);
			assertRefs(
					"refs/heads/master", B,
					"refs/heads/masters", B);
		}
	}

	@Test
	public void simpleForce() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters", UPDATE_NONFASTFORWARD));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertResults(cmds, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/masters", A);
	}

	@Test
	public void nonFastForwardDoesNotDoExpensiveMergeCheck() throws IOException {
		writeLooseRef("refs/heads/master", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(B, A, "refs/heads/master", UPDATE_NONFASTFORWARD));
		try (RevWalk rw = new RevWalk(diskRepo) {
					@Override
					public boolean isMergedInto(RevCommit base, RevCommit tip) {
						throw new AssertionError("isMergedInto() should not be called");
					}
				}) {
			newBatchUpdate(cmds)
					.setAllowNonFastForwards(true)
					.execute(rw, new StrictWorkMonitor());
		}

		assertResults(cmds, OK);
		assertRefs("refs/heads/master", A);
	}

	@Test
	public void fileDirectoryConflict() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/master/x", CREATE),
				new ReceiveCommand(zeroId(), A, "refs/heads", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		if (atomic) {
			// Atomic update sees that master and master/x are conflicting, then marks
			// the first one in the list as LOCK_FAILURE and aborts the rest.
			assertResults(cmds,
					LOCK_FAILURE, TRANSACTION_ABORTED, TRANSACTION_ABORTED);
			assertRefs(
					"refs/heads/master", A,
					"refs/heads/masters", B);
		} else {
			// Non-atomic updates are applied in order: master succeeds, then master/x
			// fails due to conflict.
			assertResults(cmds, OK, LOCK_FAILURE, LOCK_FAILURE);
			assertRefs(
					"refs/heads/master", B,
					"refs/heads/masters", B);
		}
	}

	@Test
	public void conflictThanksToDelete() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/masters/x", CREATE),
				new ReceiveCommand(B, zeroId(), "refs/heads/masters", DELETE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertResults(cmds, OK, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/masters/x", A);
	}

	@Test
	public void updateToMissingObject() throws IOException {
		writeLooseRef("refs/heads/master", A);

		ObjectId bad =
				ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, bad, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		if (atomic) {
			assertResults(cmds, REJECTED_MISSING_OBJECT, TRANSACTION_ABORTED);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, REJECTED_MISSING_OBJECT, OK);
			assertRefs(
					"refs/heads/master", A,
					"refs/heads/foo2", B);
		}
	}

	@Test
	public void addMissingObject() throws IOException {
		writeLooseRef("refs/heads/master", A);

		ObjectId bad =
				ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), bad, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, REJECTED_MISSING_OBJECT);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, OK, REJECTED_MISSING_OBJECT);
			assertRefs("refs/heads/master", B);
		}
	}

	@Test
	public void oneNonExistentRef() throws IOException {
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/foo1", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		if (atomic) {
			assertResults(cmds, LOCK_FAILURE, TRANSACTION_ABORTED);
			assertRefs();
		} else {
			assertResults(cmds, LOCK_FAILURE, OK);
			assertRefs("refs/heads/foo2", B);
		}
	}

	@Test
	public void oneRefWrongOldValue() throws IOException {
		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(B, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		if (atomic) {
			assertResults(cmds, LOCK_FAILURE, TRANSACTION_ABORTED);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, LOCK_FAILURE, OK);
			assertRefs(
					"refs/heads/master", A,
					"refs/heads/foo2", B);
		}
	}

	@Test
	public void nonExistentRef() throws IOException {
		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(A, zeroId(), "refs/heads/foo2", DELETE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, LOCK_FAILURE);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, OK, LOCK_FAILURE);
			assertRefs("refs/heads/master", B);
		}
	}

	@Test
	public void noRefLog() throws IOException {
		writeRef("refs/heads/master", A);

		Map<String, ReflogEntry> oldLogs =
				getLastReflogs("refs/heads/master", "refs/heads/branch");
		assertEquals(Collections.singleton("refs/heads/master"), oldLogs.keySet());

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertResults(cmds, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/branch", B);
		assertReflogUnchanged(oldLogs, "refs/heads/master");
		assertReflogUnchanged(oldLogs, "refs/heads/branch");
	}

	@Test
	public void reflogDefaultIdent() throws IOException {
		writeRef("refs/heads/master", A);
		writeRef("refs/heads/branch2", A);

		Map<String, ReflogEntry> oldLogs = getLastReflogs(
				"refs/heads/master", "refs/heads/branch1", "refs/heads/branch2");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch1", CREATE));
		execute(
				newBatchUpdate(cmds)
						.setAllowNonFastForwards(true)
						.setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/branch1", B,
				"refs/heads/branch2", A);
		assertReflogEquals(
				reflog(A, B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(zeroId(), B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/branch1"));
		assertReflogUnchanged(oldLogs, "refs/heads/branch2");
	}

	@Test
	public void reflogAppendStatusNoMessage() throws IOException {
		writeRef("refs/heads/master", A);
		writeRef("refs/heads/branch1", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/branch1", UPDATE_NONFASTFORWARD),
				new ReceiveCommand(zeroId(), A, "refs/heads/branch2", CREATE));
		execute(
				newBatchUpdate(cmds)
						.setAllowNonFastForwards(true)
						.setRefLogMessage(null, true));

		assertResults(cmds, OK, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/branch1", A,
				"refs/heads/branch2", A);
		assertReflogEquals(
				// Always forced; setAllowNonFastForwards(true) bypasses the check.
				reflog(A, B, new PersonIdent(diskRepo), "forced-update"),
				getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(B, A, new PersonIdent(diskRepo), "forced-update"),
				getLastReflog("refs/heads/branch1"));
		assertReflogEquals(
				reflog(zeroId(), A, new PersonIdent(diskRepo), "created"),
				getLastReflog("refs/heads/branch2"));
	}

	@Test
	public void reflogAppendStatusFastForward() throws IOException {
		writeRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE));
		execute(newBatchUpdate(cmds).setRefLogMessage(null, true));

		assertResults(cmds, OK);
		assertRefs("refs/heads/master", B);
		assertReflogEquals(
				reflog(A, B, new PersonIdent(diskRepo), "fast-forward"),
				getLastReflog("refs/heads/master"));
	}

	@Test
	public void reflogAppendStatusWithMessage() throws IOException {
		writeRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/branch", CREATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", true));

		assertResults(cmds, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/branch", A);
		assertReflogEquals(
				reflog(A, B, new PersonIdent(diskRepo), "a reflog: fast-forward"),
				getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(zeroId(), A, new PersonIdent(diskRepo), "a reflog: created"),
				getLastReflog("refs/heads/branch"));
	}

	@Test
	public void reflogCustomIdent() throws IOException {
		writeRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		PersonIdent ident = new PersonIdent("A Reflog User", "reflog@example.com");
		execute(
				newBatchUpdate(cmds)
						.setRefLogMessage("a reflog", false)
						.setRefLogIdent(ident));

		assertResults(cmds, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/branch", B);
		assertReflogEquals(
				reflog(A, B, ident, "a reflog"),
				getLastReflog("refs/heads/master"),
				true);
		assertReflogEquals(
				reflog(zeroId(), B, ident, "a reflog"),
				getLastReflog("refs/heads/branch"),
				true);
	}

	@Test
	public void reflogDelete() throws IOException {
		writeRef("refs/heads/master", A);
		writeRef("refs/heads/branch", A);
		assertEquals(
				2, getLastReflogs("refs/heads/master", "refs/heads/branch").size());

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, zeroId(), "refs/heads/master", DELETE),
				new ReceiveCommand(A, B, "refs/heads/branch", UPDATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/branch", B);
		assertNull(getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(A, B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/branch"));
	}

	@Test
	public void reflogFileDirectoryConflict() throws IOException {
		writeRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, zeroId(), "refs/heads/master", DELETE),
				new ReceiveCommand(zeroId(), A, "refs/heads/master/x", CREATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/master/x", A);
		assertNull(getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(zeroId(), A, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/master/x"));
	}

	@Test
	public void reflogOnLockFailure() throws IOException {
		writeRef("refs/heads/master", A);

		Map<String, ReflogEntry> oldLogs =
				getLastReflogs("refs/heads/master", "refs/heads/branch");

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(A, B, "refs/heads/branch", UPDATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, LOCK_FAILURE);
			assertReflogUnchanged(oldLogs, "refs/heads/master");
			assertReflogUnchanged(oldLogs, "refs/heads/branch");
		} else {
			assertResults(cmds, OK, LOCK_FAILURE);
			assertReflogEquals(
					reflog(A, B, new PersonIdent(diskRepo), "a reflog"),
					getLastReflog("refs/heads/master"));
			assertReflogUnchanged(oldLogs, "refs/heads/branch");
		}
	}

	@Test
	public void overrideRefLogMessage() throws Exception {
		writeRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		cmds.get(0).setRefLogMessage("custom log", false);
		PersonIdent ident = new PersonIdent(diskRepo);
		execute(
				newBatchUpdate(cmds)
						.setRefLogIdent(ident)
						.setRefLogMessage("a reflog", true));

		assertResults(cmds, OK, OK);
		assertReflogEquals(
				reflog(A, B, ident, "custom log"),
				getLastReflog("refs/heads/master"),
				true);
		assertReflogEquals(
				reflog(zeroId(), B, ident, "a reflog: created"),
				getLastReflog("refs/heads/branch"),
				true);
	}

	@Test
	public void overrideDisableRefLog() throws Exception {
		writeRef("refs/heads/master", A);

		Map<String, ReflogEntry> oldLogs =
				getLastReflogs("refs/heads/master", "refs/heads/branch");

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		cmds.get(0).disableRefLog();
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", true));

		assertResults(cmds, OK, OK);
		assertReflogUnchanged(oldLogs, "refs/heads/master");
		assertReflogEquals(
				reflog(zeroId(), B, new PersonIdent(diskRepo), "a reflog: created"),
				getLastReflog("refs/heads/branch"));
	}

	@Test
	public void packedRefsLockFailure() throws Exception {
		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));

		LockFile myLock = refdir.lockPackedRefs();
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertFalse(getLockFile("refs/heads/master").exists());
			assertFalse(getLockFile("refs/heads/branch").exists());

			if (atomic) {
				assertResults(cmds, LOCK_FAILURE, TRANSACTION_ABORTED);
				assertRefs("refs/heads/master", A);
			} else {
				// Only operates on loose refs, doesn't care that packed-refs is locked.
				assertResults(cmds, OK, OK);
				assertRefs(
						"refs/heads/master", B,
						"refs/heads/branch", B);
			}
		} finally {
			myLock.unlock();
		}
	}

	@Test
	public void oneRefLockFailure() throws Exception {
		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE),
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE));

		LockFile myLock = new LockFile(refdir.fileFor("refs/heads/master"));
		assertTrue(myLock.lock());
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertFalse(LockFile.getLockFile(refdir.packedRefsFile).exists());
			assertFalse(getLockFile("refs/heads/branch").exists());

			if (atomic) {
				assertResults(cmds, TRANSACTION_ABORTED, LOCK_FAILURE);
				assertRefs("refs/heads/master", A);
			} else {
				assertResults(cmds, OK, LOCK_FAILURE);
				assertRefs(
						"refs/heads/branch", B,
						"refs/heads/master", A);
			}
		} finally {
			myLock.unlock();
		}
	}

	@Test
	public void singleRefUpdateDoesNotRequirePackedRefsLock() throws Exception {
		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE));

		LockFile myLock = refdir.lockPackedRefs();
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertFalse(getLockFile("refs/heads/master").exists());
			assertResults(cmds, OK);
			assertRefs("refs/heads/master", B);
		} finally {
			myLock.unlock();
		}
	}

	@Test
	public void atomicUpdateRespectsInProcessLock() throws Exception {
		assumeTrue(atomic);

		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));

		Thread t = new Thread(() -> {
			try {
				execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		ReentrantLock l = refdir.inProcessPackedRefsLock;
		l.lock();
		try {
			t.start();
			long timeoutSecs = 10;
			long startNanos = System.nanoTime();

			// Hold onto the lock until we observe the worker thread has attempted to
			// acquire it.
			while (l.getQueueLength() == 0) {
				long elapsedNanos = System.nanoTime() - startNanos;
				assertTrue(
						"timed out waiting for work thread to attempt to acquire lock",
						NANOSECONDS.toSeconds(elapsedNanos) < timeoutSecs);
				Thread.sleep(3);
			}

			// Once we unlock, the worker thread should finish the update promptly.
			l.unlock();
			t.join(SECONDS.toMillis(timeoutSecs));
			assertFalse(t.isAlive());
		} finally {
			if (l.isHeldByCurrentThread()) {
				l.unlock();
			}
		}

		assertResults(cmds, OK, OK);
		assertRefs(
				"refs/heads/master", B,
				"refs/heads/branch", B);
	}

	private void writeLooseRef(String name, AnyObjectId id) throws IOException {
		write(new File(diskRepo.getDirectory(), name), id.name() + "\n");
	}

	private void writeRef(String name, AnyObjectId id) throws IOException {
		RefUpdate u = diskRepo.updateRef(name);
		u.setRefLogMessage(getClass().getSimpleName(), false);
		u.setForceUpdate(true);
		u.setNewObjectId(id);
		RefUpdate.Result r = u.update();
		switch (r) {
			case NEW:
			case FORCED:
				return;
			default:
				throw new IOException("Got " + r + " while updating " + name);
		}
	}

	private BatchRefUpdate newBatchUpdate(List<ReceiveCommand> cmds) {
		BatchRefUpdate u = refdir.newBatchUpdate();
		if (atomic) {
			assertTrue(u.isAtomic());
		} else {
			u.setAtomic(false);
		}
		u.addCommand(cmds);
		return u;
	}

	private void execute(BatchRefUpdate u) throws IOException {
		execute(u, false);
	}

	private void execute(BatchRefUpdate u, boolean strictWork) throws IOException {
		try (RevWalk rw = new RevWalk(diskRepo)) {
			u.execute(rw,
					strictWork ? new StrictWorkMonitor() : NullProgressMonitor.INSTANCE);
		}
	}

	private void assertRefs(Object... args) throws IOException {
		if (args.length % 2 != 0) {
			throw new IllegalArgumentException(
					"expected even number of args: " + Arrays.toString(args));
		}

		Map<String, AnyObjectId> expected = new LinkedHashMap<>();
		for (int i = 0; i < args.length; i += 2) {
			expected.put((String) args[i], (AnyObjectId) args[i + 1]);
		}

		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		Ref actualHead = refs.remove(Constants.HEAD);
		if (actualHead != null) {
			String actualLeafName = actualHead.getLeaf().getName();
			assertEquals(
					"expected HEAD to point to refs/heads/master, got: " + actualLeafName,
					"refs/heads/master", actualLeafName);
			AnyObjectId expectedMaster = expected.get("refs/heads/master");
			assertNotNull("expected master ref since HEAD exists", expectedMaster);
			assertEquals(expectedMaster, actualHead.getObjectId());
		}

		Map<String, AnyObjectId> actual = new LinkedHashMap<>();
		refs.forEach((n, r) -> actual.put(n, r.getObjectId()));

		assertEquals(expected.keySet(), actual.keySet());
		actual.forEach((n, a) -> assertEquals(n, expected.get(n), a));
	}

	enum Result {
		OK(ReceiveCommand.Result.OK),
		LOCK_FAILURE(ReceiveCommand.Result.LOCK_FAILURE),
		REJECTED_NONFASTFORWARD(ReceiveCommand.Result.REJECTED_NONFASTFORWARD),
		REJECTED_MISSING_OBJECT(ReceiveCommand.Result.REJECTED_MISSING_OBJECT),
		TRANSACTION_ABORTED(ReceiveCommand::isTransactionAborted);

		final Predicate<? super ReceiveCommand> p;

		private Result(Predicate<? super ReceiveCommand> p) {
			this.p = p;
		}

		private Result(ReceiveCommand.Result result) {
			this(c -> c.getResult() == result);
		}
	}

	private void assertResults(
			List<ReceiveCommand> cmds, Result... expected) {
		if (expected.length != cmds.size()) {
			throw new IllegalArgumentException(
					"expected " + cmds.size() + " result args");
		}
		for (int i = 0; i < cmds.size(); i++) {
			ReceiveCommand c = cmds.get(i);
			Result r = expected[i];
			assertTrue(
					String.format(
							"result of command (%d) should be %s: %s %s%s",
							Integer.valueOf(i), r, c,
							c.getResult(),
							c.getMessage() != null ? " (" + c.getMessage() + ")" : ""),
					r.p.test(c));
		}
	}

	private Map<String, ReflogEntry> getLastReflogs(String... names)
			throws IOException {
		Map<String, ReflogEntry> result = new LinkedHashMap<>();
		for (String name : names) {
			ReflogEntry e = getLastReflog(name);
			if (e != null) {
				result.put(name, e);
			}
		}
		return result;
	}

	private ReflogEntry getLastReflog(String name) throws IOException {
		ReflogReader r = diskRepo.getReflogReader(name);
		if (r == null) {
			return null;
		}
		return r.getLastEntry();
	}

	private File getLockFile(String refName) {
		return LockFile.getLockFile(refdir.fileFor(refName));
	}

	private void assertReflogUnchanged(
			Map<String, ReflogEntry> old, String name) throws IOException {
		assertReflogEquals(old.get(name), getLastReflog(name), true);
	}

	private static void assertReflogEquals(
			ReflogEntry expected, ReflogEntry actual) {
		assertReflogEquals(expected, actual, false);
	}

	private static void assertReflogEquals(
			ReflogEntry expected, ReflogEntry actual, boolean strictTime) {
		if (expected == null) {
			assertNull(actual);
			return;
		}
		assertNotNull(actual);
		assertEquals(expected.getOldId(), actual.getOldId());
		assertEquals(expected.getNewId(), actual.getNewId());
		if (strictTime) {
			assertEquals(expected.getWho(), actual.getWho());
		} else {
			assertEquals(expected.getWho().getName(), actual.getWho().getName());
			assertEquals(
					expected.getWho().getEmailAddress(),
					actual.getWho().getEmailAddress());
		}
		assertEquals(expected.getComment(), actual.getComment());
	}

	private static ReflogEntry reflog(ObjectId oldId, ObjectId newId,
			PersonIdent who, String comment) {
		return new ReflogEntry() {
			@Override
			public ObjectId getOldId() {
				return oldId;
			}

			@Override
			public ObjectId getNewId() {
				return newId;
			}

			@Override
			public PersonIdent getWho() {
				return who;
			}

			@Override
			public String getComment() {
				return comment;
			}

			@Override
			public CheckoutEntry parseCheckout() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
