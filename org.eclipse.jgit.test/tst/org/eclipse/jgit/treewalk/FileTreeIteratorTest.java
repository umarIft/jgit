/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.treewalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.security.MessageDigest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

public class FileTreeIteratorTest extends RepositoryTestCase {
	private final String[] paths = { "a,", "a,b", "a/b", "a0b" };

	private long[] mtime;

	@Before
	public void setUp() throws Exception {
		super.setUp();

		// We build the entries backwards so that on POSIX systems we
		// are likely to get the entries in the trash directory in the
		// opposite order of what they should be in for the iteration.
		// This should stress the sorting code better than doing it in
		// the correct order.
		//
		mtime = new long[paths.length];
		for (int i = paths.length - 1; i >= 0; i--) {
			final String s = paths[i];
			writeTrashFile(s, s);
			mtime[i] = new File(trash, s).lastModified();
		}
	}

	@Test
	public void testEmptyIfRootIsFile() throws Exception {
		final File r = new File(trash, paths[0]);
		assertTrue(r.isFile());
		final FileTreeIterator fti = new FileTreeIterator(r, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		assertTrue(fti.first());
		assertTrue(fti.eof());
	}

	@Test
	public void testEmptyIfRootDoesNotExist() throws Exception {
		final File r = new File(trash, "not-existing-file");
		assertFalse(r.exists());
		final FileTreeIterator fti = new FileTreeIterator(r, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		assertTrue(fti.first());
		assertTrue(fti.eof());
	}

	@Test
	public void testEmptyIfRootIsEmpty() throws Exception {
		final File r = new File(trash, "not-existing-file");
		assertFalse(r.exists());
		FileUtils.mkdir(r);

		final FileTreeIterator fti = new FileTreeIterator(r, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		assertTrue(fti.first());
		assertTrue(fti.eof());
	}

	@Test
	public void testSimpleIterate() throws Exception {
		final FileTreeIterator top = new FileTreeIterator(trash, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));

		assertTrue(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.REGULAR_FILE.getBits(), top.mode);
		assertEquals(paths[0], nameOf(top));
		assertEquals(paths[0].length(), top.getEntryLength());
		assertEquals(mtime[0], top.getEntryLastModified());

		top.next(1);
		assertFalse(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.REGULAR_FILE.getBits(), top.mode);
		assertEquals(paths[1], nameOf(top));
		assertEquals(paths[1].length(), top.getEntryLength());
		assertEquals(mtime[1], top.getEntryLastModified());

		top.next(1);
		assertFalse(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.TREE.getBits(), top.mode);

		final ObjectReader reader = db.newObjectReader();
		final AbstractTreeIterator sub = top.createSubtreeIterator(reader);
		assertTrue(sub instanceof FileTreeIterator);
		final FileTreeIterator subfti = (FileTreeIterator) sub;
		assertTrue(sub.first());
		assertFalse(sub.eof());
		assertEquals(paths[2], nameOf(sub));
		assertEquals(paths[2].length(), subfti.getEntryLength());
		assertEquals(mtime[2], subfti.getEntryLastModified());

		sub.next(1);
		assertTrue(sub.eof());

		top.next(1);
		assertFalse(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.REGULAR_FILE.getBits(), top.mode);
		assertEquals(paths[3], nameOf(top));
		assertEquals(paths[3].length(), top.getEntryLength());
		assertEquals(mtime[3], top.getEntryLastModified());

		top.next(1);
		assertTrue(top.eof());
	}

	@Test
	public void testComputeFileObjectId() throws Exception {
		final FileTreeIterator top = new FileTreeIterator(trash, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));

		final MessageDigest md = Constants.newMessageDigest();
		md.update(Constants.encodeASCII(Constants.TYPE_BLOB));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(paths[0].length()));
		md.update((byte) 0);
		md.update(Constants.encode(paths[0]));
		final ObjectId expect = ObjectId.fromRaw(md.digest());

		assertEquals(expect, top.getEntryObjectId());

		// Verify it was cached by removing the file and getting it again.
		//
		FileUtils.delete(new File(trash, paths[0]));
		assertEquals(expect, top.getEntryObjectId());
	}

	@Test
	public void testIsModifiedSymlink() throws Exception {
		File f = writeTrashFile("symlink", "content");
		Git git = new Git(db);
		git.add().addFilepattern("symlink").call();
		git.commit().setMessage("commit").call();

		// Modify previously committed DirCacheEntry and write it back to disk
		DirCacheEntry dce = db.readDirCache().getEntry("symlink");
		dce.setFileMode(FileMode.SYMLINK);
		DirCacheCheckout.checkoutEntry(db, f, dce);

		FileTreeIterator fti = new FileTreeIterator(trash, db.getFS(), db
				.getConfig().get(WorkingTreeOptions.KEY));
		while (!fti.getEntryPathString().equals("symlink"))
			fti.next(1);
		assertFalse(fti.isModified(dce, false));
	}

	/**
	 * Test case where submodule HEAD commit matches the object id in the index
	 * 
	 * @throws Exception
	 */
	@Test
	public void submoduleHeadMatchesIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
				.setDirectory(new File(db.getWorkTree(), path)).call();

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertTrue(indexIter.idEqual(workTreeIter));
	}

	/**
	 * Test case where submodule folder in the working tree has no .git
	 * directory
	 * 
	 * @throws Exception
	 */
	@Test
	public void submoduleWithNoGitDirectory() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		File submoduleRoot = new File(db.getWorkTree(), path);
		assertTrue(submoduleRoot.mkdir());
		assertTrue(new File(submoduleRoot, Constants.DOT_GIT).mkdir());

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		// Delete before walk begins so entry will be visited but repository
		// lookup will fail
		FileUtils.delete(submoduleRoot, FileUtils.RECURSIVE);

		assertTrue(walk.next());
		assertFalse(indexIter.idEqual(workTreeIter));
	}

	/**
	 * Test case where a repository exists in the submodule but the HEAD commit
	 * cannot be resolved
	 * 
	 * @throws Exception
	 */
	@Test
	public void submoduleWithNoHead() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		assertNotNull(Git.init().setDirectory(new File(db.getWorkTree(), path))
				.call().getRepository());

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertFalse(indexIter.idEqual(workTreeIter));
	}

	/**
	 * Test case where {@link FileTreeIterator} is created without a repository
	 * but the id of the submodule in the working tree can still be resolved and
	 * successfully matched against the index
	 * 
	 * @throws Exception
	 */
	@Test
	public void iteratorWithNoRepository() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
				.setDirectory(new File(db.getWorkTree(), path)).call();

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db.getWorkTree(),
				db.getFS(), db.getConfig().get(WorkingTreeOptions.KEY));
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertTrue(indexIter.idEqual(workTreeIter));
	}

	private static String nameOf(final AbstractTreeIterator i) {
		return RawParseUtils.decode(Constants.CHARSET, i.path, 0, i.pathLen);
	}
}
