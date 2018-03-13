/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.subtree.PathBasedContext;
import org.eclipse.jgit.subtree.SubtreeSplitter;

/**
 */
public class SubtreeSplitCommand extends GitCommand<SubtreeSplitResult> {

	private ObjectId start;

	private ArrayList<PathBasedContext> pathContexts = new ArrayList<PathBasedContext>();

	private Set<ObjectId> toRewrite = new HashSet<ObjectId>();

	/**
	 * @param repo
	 */
	public SubtreeSplitCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @param start
	 *            The commit to start the walk from.
	 */
	public void setStart(ObjectId start) {
		this.start = start;
	}

	/**
	 * @param commit
	 *            The commit to rewrite
	 */
	public void addCommitToRewrite(ObjectId commit) {
		if (this.toRewrite != SubtreeSplitter.REWRITE_ALL) {
			this.toRewrite.add(commit);
		}
	}

	/**
	 * Rewrite all commits reachable from the start commit.
	 */
	public void setRewriteAll() {
		this.toRewrite = SubtreeSplitter.REWRITE_ALL;
	}
	/**
	 * @param path
	 *            The subtree to split out
	 */
	public void addSplitPath(String path) {
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		pathContexts.add(new PathBasedContext(path, path));
	}

	public SubtreeSplitResult call() throws Exception {
		checkCallable();

		RevWalk walk = new RevWalk(repo);
		try {
			SubtreeSplitter splitter = new SubtreeSplitter(this.repo, walk);
			splitter.splitSubtrees(start, pathContexts, toRewrite);
			setCallable(false);
			return new SubtreeSplitResult(
					splitter.getSubtreeContexts(),
					splitter.getRewrittenCommits());
		} finally {
			walk.release();
		}
	}

}
