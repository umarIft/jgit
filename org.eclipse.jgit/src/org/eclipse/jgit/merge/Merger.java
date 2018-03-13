/*
 * Copyright (C) 2008-2013, Google Inc.
 * Copyright (C) 2016, Laurent Delaigue <laurent.delaigue@obeo.fr>
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

package org.eclipse.jgit.merge;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.errors.NoMergeBaseException.MergeBaseFailureReason;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Instance of a specific {@link MergeStrategy} for a single {@link Repository}.
 */
public abstract class Merger {
	/**
	 * The repository this merger operates on.
	 * <p>
	 * Null if and only if the merger was constructed with {@link
	 * #Merger(ObjectInserter)}. Callers that want to assume the repo is not null
	 * (e.g. because of a previous check that the merger is not in-core) may use
	 * {@link #nonNullRepo()}.
	 */
	@Nullable
	protected final Repository db;

	/** Reader to support {@link #walk} and other object loading. */
	protected ObjectReader reader;

	/** A RevWalk for computing merge bases, or listing incoming commits. */
	protected RevWalk walk;

	private ObjectInserter inserter;

	/** The original objects supplied in the merge; this can be any tree-ish. */
	protected RevObject[] sourceObjects;

	/** If {@link #sourceObjects}[i] is a commit, this is the commit. */
	protected RevCommit[] sourceCommits;

	/** The trees matching every entry in {@link #sourceObjects}. */
	protected RevTree[] sourceTrees;

	/**
	 * A progress monitor.
	 *
	 * @since 4.2
	 */
	protected ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	/**
	 * Create a new merge instance for a repository.
	 *
	 * @param local
	 *            the repository this merger will read and write data on.
	 */
	protected Merger(final Repository local) {
		if (local == null) {
			throw new IllegalArgumentException(JGitText.get().repositoryIsRequired);
		}
		db = local;
		inserter = local.newObjectInserter();
		reader = inserter.newReader();
		walk = new RevWalk(reader);
	}

	/**
	 * Create a new in-core merge instance from an inserter.
	 *
	 * @param oi
	 *            the inserter to write objects to.
	 * @since 4.7
	 */
	protected Merger(ObjectInserter oi) {
		db = null;
		inserter = oi;
		reader = oi.newReader();
		walk = new RevWalk(reader);
	}

	/**
	 * @return the repository this merger operates on.
	 */
	@Nullable
	public Repository getRepository() {
		return db;
	}

	/**
	 * @return non-null repository instance
	 * @throws IllegalArgumentException
	 *             if the merger was constructed without a repository.
	 * @since 4.7
	 */
	protected Repository nonNullRepo() {
		if (db == null) {
			throw new IllegalArgumentException(JGitText.get().repositoryIsRequired);
		}
		return db;
	}

	/** @return an object writer to create objects in {@link #getRepository()}. */
	public ObjectInserter getObjectInserter() {
		return inserter;
	}

	/**
	 * Set the inserter this merger will use to create objects.
	 * <p>
	 * If an inserter was already set on this instance (such as by a prior set,
	 * or a prior call to {@link #getObjectInserter()}), the prior inserter as
	 * well as the in-progress walk will be released.
	 *
	 * @param oi
	 *            the inserter instance to use. Must be associated with the
	 *            repository instance returned by {@link #getRepository()}.
	 */
	public void setObjectInserter(ObjectInserter oi) {
		walk.close();
		reader.close();
		inserter.close();
		inserter = oi;
		reader = oi.newReader();
		walk = new RevWalk(reader);
	}

	/**
	 * Merge together two or more tree-ish objects.
	 * <p>
	 * Any tree-ish may be supplied as inputs. Commits and/or tags pointing at
	 * trees or commits may be passed as input objects.
	 *
	 * @param tips
	 *            source trees to be combined together. The merge base is not
	 *            included in this set.
	 * @return true if the merge was completed without conflicts; false if the
	 *         merge strategy cannot handle this merge or there were conflicts
	 *         preventing it from automatically resolving all paths.
	 * @throws IncorrectObjectTypeException
	 *             one of the input objects is not a commit, but the strategy
	 *             requires it to be a commit.
	 * @throws IOException
	 *             one or more sources could not be read, or outputs could not
	 *             be written to the Repository.
	 */
	public boolean merge(final AnyObjectId... tips) throws IOException {
		return merge(true, tips);
	}

	/**
	 * Merge together two or more tree-ish objects.
	 * <p>
	 * Any tree-ish may be supplied as inputs. Commits and/or tags pointing at
	 * trees or commits may be passed as input objects.
	 *
	 * @since 3.5
	 * @param flush
	 *            whether to flush the underlying object inserter when finished to
	 *            store any content-merged blobs and virtual merged bases; if
	 *            false, callers are responsible for flushing.
	 * @param tips
	 *            source trees to be combined together. The merge base is not
	 *            included in this set.
	 * @return true if the merge was completed without conflicts; false if the
	 *         merge strategy cannot handle this merge or there were conflicts
	 *         preventing it from automatically resolving all paths.
	 * @throws IncorrectObjectTypeException
	 *             one of the input objects is not a commit, but the strategy
	 *             requires it to be a commit.
	 * @throws IOException
	 *             one or more sources could not be read, or outputs could not
	 *             be written to the Repository.
	 */
	public boolean merge(final boolean flush, final AnyObjectId... tips)
			throws IOException {
		sourceObjects = new RevObject[tips.length];
		for (int i = 0; i < tips.length; i++)
			sourceObjects[i] = walk.parseAny(tips[i]);

		sourceCommits = new RevCommit[sourceObjects.length];
		for (int i = 0; i < sourceObjects.length; i++) {
			try {
				sourceCommits[i] = walk.parseCommit(sourceObjects[i]);
			} catch (IncorrectObjectTypeException err) {
				sourceCommits[i] = null;
			}
		}

		sourceTrees = new RevTree[sourceObjects.length];
		for (int i = 0; i < sourceObjects.length; i++)
			sourceTrees[i] = walk.parseTree(sourceObjects[i]);

		try {
			boolean ok = mergeImpl();
			if (ok && flush)
				inserter.flush();
			return ok;
		} finally {
			if (flush)
				inserter.close();
			reader.close();
		}
	}

	/**
	 * @return the ID of the commit that was used as merge base for merging, or
	 *         null if no merge base was used or it was set manually
	 * @since 3.2
	 */
	public abstract ObjectId getBaseCommitId();

	/**
	 * Return the merge base of two commits.
	 *
	 * @param a
	 *            the first commit in {@link #sourceObjects}.
	 * @param b
	 *            the second commit in {@link #sourceObjects}.
	 * @return the merge base of two commits
	 * @throws IncorrectObjectTypeException
	 *             one of the input objects is not a commit.
	 * @throws IOException
	 *             objects are missing or multiple merge bases were found.
	 * @since 3.0
	 */
	protected RevCommit getBaseCommit(RevCommit a, RevCommit b)
			throws IncorrectObjectTypeException, IOException {
		walk.reset();
		walk.setRevFilter(RevFilter.MERGE_BASE);
		walk.markStart(a);
		walk.markStart(b);
		final RevCommit base = walk.next();
		if (base == null)
			return null;
		final RevCommit base2 = walk.next();
		if (base2 != null) {
			throw new NoMergeBaseException(
					MergeBaseFailureReason.MULTIPLE_MERGE_BASES_NOT_SUPPORTED,
					MessageFormat.format(
					JGitText.get().multipleMergeBasesFor, a.name(), b.name(),
					base.name(), base2.name()));
		}
		return base;
	}

	/**
	 * Open an iterator over a tree.
	 *
	 * @param treeId
	 *            the tree to scan; must be a tree (not a treeish).
	 * @return an iterator for the tree.
	 * @throws IncorrectObjectTypeException
	 *             the input object is not a tree.
	 * @throws IOException
	 *             the tree object is not found or cannot be read.
	 */
	protected AbstractTreeIterator openTree(final AnyObjectId treeId)
			throws IncorrectObjectTypeException, IOException {
		return new CanonicalTreeParser(null, reader, treeId);
	}

	/**
	 * Execute the merge.
	 * <p>
	 * This method is called from {@link #merge(AnyObjectId[])} after the
	 * {@link #sourceObjects}, {@link #sourceCommits} and {@link #sourceTrees}
	 * have been populated.
	 *
	 * @return true if the merge was completed without conflicts; false if the
	 *         merge strategy cannot handle this merge or there were conflicts
	 *         preventing it from automatically resolving all paths.
	 * @throws IncorrectObjectTypeException
	 *             one of the input objects is not a commit, but the strategy
	 *             requires it to be a commit.
	 * @throws IOException
	 *             one or more sources could not be read, or outputs could not
	 *             be written to the Repository.
	 */
	protected abstract boolean mergeImpl() throws IOException;

	/**
	 * @return resulting tree, if {@link #merge(AnyObjectId[])} returned true.
	 */
	public abstract ObjectId getResultTreeId();

	/**
	 * Set a progress monitor.
	 *
	 * @param monitor
	 *            Monitor to use, can be null to indicate no progress reporting
	 *            is desired.
	 * @since 4.2
	 */
	public void setProgressMonitor(ProgressMonitor monitor) {
		if (monitor == null) {
			this.monitor = NullProgressMonitor.INSTANCE;
		} else {
			this.monitor = monitor;
		}
	}
}
