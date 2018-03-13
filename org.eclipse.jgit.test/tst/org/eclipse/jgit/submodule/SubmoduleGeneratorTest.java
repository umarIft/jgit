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
package org.eclipse.jgit.submodule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleGenerator}
 */
public class SubmoduleGeneratorTest extends RepositoryTestCase {

	@Test
	public void repositoryWithNoSubmodules() throws IOException,
			ConfigInvalidException {
		SubmoduleGenerator gen = new SubmoduleGenerator(db);
		assertFalse(gen.next());
		assertNull(gen.getPath());
		assertEquals(ObjectId.zeroId(), gen.getObjectId());
		assertFalse(gen.hasGitDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertNull(gen.getDirectory());
		assertNull(gen.getDirectory());
		assertNull(gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertNull(gen.getModulesUrl());
		assertNull(gen.getRepository());
	}

	@Test
	public void repositoryWithRootLevelSubmodule() throws IOException,
			ConfigInvalidException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
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

		SubmoduleGenerator gen = new SubmoduleGenerator(db);
		assertTrue(gen.next());
		assertEquals(path, gen.getPath());
		assertEquals(id, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertEquals(new File(db.getWorkTree(), path + File.separatorChar
				+ Constants.DOT_GIT), gen.getGitDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertNull(gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertNull(gen.getModulesUrl());
		assertNull(gen.getRepository());
		assertFalse(gen.next());
	}

	@Test
	public void repositoryWithNestedSubmodule() throws IOException,
			ConfigInvalidException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub/dir/final";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		SubmoduleGenerator gen = new SubmoduleGenerator(db);
		assertTrue(gen.next());
		assertEquals(path, gen.getPath());
		assertEquals(id, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertEquals(new File(db.getWorkTree(), path + File.separatorChar
				+ Constants.DOT_GIT), gen.getGitDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertNull(gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertNull(gen.getModulesUrl());
		assertNull(gen.getRepository());
		assertFalse(gen.next());
	}

	@Test
	public void generatorFilteredToOneOfTwoSubmodules() throws IOException {
		final ObjectId id1 = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path1 = "sub1";
		final ObjectId id2 = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1235");
		final String path2 = "sub2";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path1) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id1);
			}
		});
		editor.add(new PathEdit(path2) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id2);
			}
		});
		editor.commit();

		SubmoduleGenerator gen = new SubmoduleGenerator(db,
				PathFilter.create(path1));
		assertTrue(gen.next());
		assertEquals(path1, gen.getPath());
		assertEquals(id1, gen.getObjectId());
		assertFalse(gen.next());
	}
}
