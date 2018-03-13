/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import javax.sound.sampled.Line;

import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Unit tests of {@link BlameCommand}
 */
public class BlameCommandTest extends RepositoryTestCase {

	private String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}

	@Test
	public void testSingleRevision() throws Exception {
		Git git = new Git(db);

		String[] content = new String[] { "first", "second", "third" };

		writeTrashFile("file.txt", join(content));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		BlameCommand command = new BlameCommand(db);
		command.setFilePath("file.txt");
		BlameResult lines = command.call();
		assertNotNull(lines);
		assertEquals(3, lines.getResultContents().size());

		for (int i = 0; i < 3; i++) {
			assertEquals(commit, lines.getSourceCommit(i));
			assertEquals(i, lines.getSourceLine(i));
		}
	}

	@Test
	public void testTwoRevisions() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "first", "second" };

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		String[] content2 = new String[] { "first", "second", "third" };

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("create file").call();

		BlameCommand command = new BlameCommand(db);
		command.setFilePath("file.txt");
		List<Line> lines = command.call();
		assertEquals(3, lines.size());

		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			assertNotNull(line);
			assertEquals(i, line.getNumber());
			if (i == 2)
				assertEquals(commit2, line.getCommit());
			else
				assertEquals(commit1, line.getCommit());
		}
	}

	@Test
	public void testRename() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c" };

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		writeTrashFile("file1.txt", join(content1));
		git.add().addFilepattern("file1.txt").call();
		git.rm().addFilepattern("file.txt").call();
		git.commit().setMessage("moving file").call();

		String[] content2 = new String[] { "a", "b", "c2" };

		writeTrashFile("file1.txt", join(content2));
		git.add().addFilepattern("file1.txt").call();
		RevCommit commit3 = git.commit().setMessage("editing file").call();

		BlameCommand command = new BlameCommand(db);
		command.setFilePath("file1.txt");
		List<Line> lines = command.call();

		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			assertNotNull(line);
			assertEquals(i, line.getNumber());
			if (i == 2)
				assertEquals(commit3, line.getCommit());
			else
				assertEquals(commit1, line.getCommit());
		}
	}

	@Test
	public void testDeleteTraillingLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c", "d" };
		String[] content2 = new String[] { "a", "d" };

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		List<Line> lines = command.call();
		assertEquals(content2.length, lines.size());

		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			assertNotNull(line);
			assertEquals(i, line.getNumber());
			assertEquals(commit1, line.getCommit());
		}
	}

	@Test
	public void testDeleteMiddleLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c", "d", "e" };
		String[] content2 = new String[] { "a", "c", "e" };

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		List<Line> lines = command.call();
		assertEquals(content2.length, lines.size());

		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			assertNotNull(line);
			assertEquals(i, line.getNumber());
			assertEquals(commit1, line.getCommit());
		}
	}

	@Test
	public void testEditAllLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "1" };
		String[] content2 = new String[] { "b", "2" };

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("create file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		List<Line> lines = command.call();
		assertEquals(content2.length, lines.size());

		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			assertNotNull(line);
			assertEquals(i, line.getNumber());
			assertEquals(commit2, line.getCommit());
		}
	}

	@Test
	public void testMiddleClearAllLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c" };

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", "");
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("create file").call();

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit3 = git.commit().setMessage("edit file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		List<Line> lines = command.call();
		assertEquals(content1.length, lines.size());

		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			assertNotNull(line);
			assertEquals(i, line.getNumber());
			assertEquals(commit3, line.getCommit());
		}
	}
}
