/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.FileMode.GITLINK;
import static org.eclipse.jgit.lib.FileMode.SYMLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_GITLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_SYMLINK;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.DirCacheNameConflictException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Tree of references in the reference graph.
 * <p>
 * The root corresponds to the {@code "refs/"} subdirectory, for example the
 * default reference {@code "refs/heads/master"} is stored at path
 * {@code "heads/master"} in a {@code RefTree}.
 * <p>
 * Normal references are stored as {@link FileMode#GITLINK} tree entries. The
 * ObjectId in the tree entry is the ObjectId the reference refers to.
 * <p>
 * Symbolic references are stored as {@link FileMode#SYMLINK} entries, with the
 * blob storing the name of the target reference.
 * <p>
 * Annotated tags also store the peeled object using a {@code GITLINK} entry
 * with the suffix <code>"^{}"</code>, for example {@code "tags/v1.0"} stores
 * the annotated tag object, while <code>"tags/v1.0^{}"</code> stores the commit
 * the tag annotates.
 * <p>
 * {@code HEAD} is a special case and stored as {@code "..HEAD"}.
 */
public class RefTree {
	private static final int MAX_SYMBOLIC_REF_DEPTH = 5;
	static final String ROOT_DOTDOT = ".."; //$NON-NLS-1$

	/** Suffix applied to GITLINK to indicate its the peeled value of a tag. */
	public static final String PEELED_SUFFIX = "^{}"; //$NON-NLS-1$

	/**
	 * Create an empty reference tree.
	 *
	 * @return a new empty reference tree.
	 */
	public static RefTree newEmptyTree() {
		return new RefTree(null, DirCache.newInCore());
	}

	/**
	 * Load a reference tree.
	 *
	 * @param reader
	 *            reader to scan the reference tree with. This reader may be
	 *            retained by the RefTree for the life of the tree in order to
	 *            support lazy loading of entries.
	 * @param tree
	 *            the tree to read.
	 * @return the ref tree read from the commit.
	 * @throws IOException
	 *             the repository cannot be accessed through the reader.
	 * @throws CorruptObjectException
	 *             a tree object is corrupt and cannot be read.
	 * @throws IncorrectObjectTypeException
	 *             a tree object wasn't actually a tree.
	 * @throws MissingObjectException
	 *             a reference tree object doesn't exist.
	 */
	public static RefTree read(ObjectReader reader, RevTree tree)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		return new RefTree(reader, DirCache.read(reader, tree));
	}

	/** Borrowed reader to access the repository. */
	private final ObjectReader reader;
	private DirCache contents;
	private Map<ObjectId, String> pendingBlobs;

	private RefTree(ObjectReader reader, DirCache dc) {
		this.reader = reader;
		this.contents = dc;
	}

	/**
	 * Read one reference.
	 *
	 * @param name
	 *            name of the reference to read.
	 * @return the reference; null if it does not exist.
	 * @throws IOException
	 *             cannot read a symbolic reference target.
	 */
	@Nullable
	public Ref exactRef(String name) throws IOException {
		Ref r = readRef(name);
		if (r == null) {
			return null;
		} else if (r.isSymbolic()) {
			return resolve(r, 0);
		}

		DirCacheEntry p = contents.getEntry(peeledPath(name));
		if (p != null && p.getRawMode() == TYPE_GITLINK) {
			return new ObjectIdRef.PeeledTag(PACKED, r.getName(),
					r.getObjectId(), p.getObjectId());
		}
		return r;
	}

	private Ref readRef(String name) throws IOException {
		DirCacheEntry e = contents.getEntry(refPath(name));
		return e != null ? toRef(e, name) : null;
	}

	private Ref toRef(DirCacheEntry e, String name) throws IOException {
		int mode = e.getRawMode();
		if (mode == TYPE_GITLINK) {
			ObjectId id = e.getObjectId();
			return new ObjectIdRef.PeeledNonTag(PACKED, name, id);
		}

		if (mode == TYPE_SYMLINK) {
			ObjectId id = e.getObjectId();
			String n = pendingBlobs != null ? pendingBlobs.get(id) : null;
			if (n == null) {
				byte[] bin = reader.open(id, OBJ_BLOB).getCachedBytes();
				n = RawParseUtils.decode(bin);
			}
			Ref dst = new ObjectIdRef.Unpeeled(NEW, n, null);
			return new SymbolicRef(name, dst);
		}

		return null; // garbage file or something; not a reference.
	}

	private Ref resolve(Ref ref, int depth) throws IOException {
		if (ref.isSymbolic() && depth < MAX_SYMBOLIC_REF_DEPTH) {
			Ref r = readRef(ref.getTarget().getName());
			if (r == null) {
				return ref;
			}
			Ref dst = resolve(r, depth + 1);
			return new SymbolicRef(ref.getName(), dst);
		}
		return ref;
	}

	/**
	 * Attempt a batch of commands against this RefTree.
	 * <p>
	 * The batch is applied atomically, either all commands apply at once, or
	 * they all reject and the RefTree is left unmodified.
	 * <p>
	 * On success (when this method returns {@code true}) the command results
	 * are left as-is (probably {@code NOT_ATTEMPTED}). Result fields are set
	 * only when this method returns {@code false} to indicate failure.
	 *
	 * @param cmdList
	 *            to apply. All commands should still have result NOT_ATTEMPTED.
	 * @return true if the commands applied; false if they were rejected.
	 */
	public boolean apply(Collection<Command> cmdList) {
		try {
			DirCacheEditor ed = contents.editor();
			for (Command cmd : cmdList) {
				apply(ed, cmd);
			}
			ed.finish();
			return true;
		} catch (DirCacheNameConflictException e) {
			String r1 = refName(e.getPath1());
			String r2 = refName(e.getPath2());
			for (Command cmd : cmdList) {
				if (r1.equals(cmd.getRefName())
						|| r2.equals(cmd.getRefName())) {
					cmd.setResult(LOCK_FAILURE);
					break;
				}
			}
			return abort(cmdList);
		} catch (LockFailureException e) {
			return abort(cmdList);
		}
	}

	private void apply(DirCacheEditor ed, final Command cmd) {
		String path = refPath(cmd.getRefName());
		Ref oldRef = cmd.getOldRef();
		final Ref newRef = cmd.getNewRef();

		if (newRef == null) {
			checkRef(contents.getEntry(path), cmd);
			ed.add(new DeletePath(path));
			cleanupPeeledRef(ed, oldRef);
			return;
		}

		if (newRef.isSymbolic()) {
			final String dst = newRef.getTarget().getName();
			ed.add(new PathEdit(path) {
				@Override
				public void apply(DirCacheEntry ent) {
					checkRef(ent, cmd);
					ObjectId id = Command.symref(dst);
					ent.setFileMode(SYMLINK);
					ent.setObjectId(id);
					if (pendingBlobs == null) {
						pendingBlobs = new HashMap<>(4);
					}
					pendingBlobs.put(id, dst);
				}
			}.setReplace(false));
			cleanupPeeledRef(ed, oldRef);
			return;
		}

		ed.add(new PathEdit(path) {
			@Override
			public void apply(DirCacheEntry ent) {
				checkRef(ent, cmd);
				ent.setFileMode(GITLINK);
				ent.setObjectId(newRef.getObjectId());
			}
		}.setReplace(false));

		if (newRef.getPeeledObjectId() != null) {
			ed.add(new PathEdit(peeledPath(newRef.getName())) {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(GITLINK);
					ent.setObjectId(newRef.getPeeledObjectId());
				}
			}.setReplace(false));
		} else {
			cleanupPeeledRef(ed, oldRef);
		}
	}

	private static void checkRef(@Nullable DirCacheEntry ent, Command cmd) {
		if (!cmd.checkRef(ent)) {
			cmd.setResult(LOCK_FAILURE);
			throw new LockFailureException();
		}
	}

	private static void cleanupPeeledRef(DirCacheEditor ed, Ref ref) {
		if (ref != null && !ref.isSymbolic()
				&& (!ref.isPeeled() || ref.getPeeledObjectId() != null)) {
			ed.add(new DeletePath(peeledPath(ref.getName())));
		}
	}

	private static boolean abort(Iterable<Command> cmdList) {
		for (Command cmd : cmdList) {
			if (cmd.getResult() == NOT_ATTEMPTED) {
				reject(cmd, JGitText.get().transactionAborted);
			}
		}
		return false;
	}

	private static void reject(Command cmd, String msg) {
		cmd.setResult(REJECTED_OTHER_REASON, msg);
	}

	/**
	 * Convert a path name in a RefTree to the reference name known by Git.
	 *
	 * @param path
	 *            name read from the RefTree structure, for example
	 *            {@code "heads/master"}.
	 * @return reference name for the path, {@code "refs/heads/master"}.
	 */
	public static String refName(String path) {
		if (path.startsWith(ROOT_DOTDOT)) {
			return path.substring(2);
		}
		return R_REFS + path;
	}

	private static String refPath(String name) {
		if (name.startsWith(R_REFS)) {
			return name.substring(R_REFS.length());
		}
		return ROOT_DOTDOT + name;
	}

	private static String peeledPath(String name) {
		return refPath(name) + PEELED_SUFFIX;
	}

	/**
	 * Write this reference tree.
	 *
	 * @param inserter
	 *            inserter to use when writing trees to the object database.
	 *            Caller is responsible for flushing the inserter before trying
	 *            to read the objects, or exposing them through a reference.
	 * @return the top level tree.
	 * @throws IOException
	 *             a tree could not be written.
	 */
	public ObjectId writeTree(ObjectInserter inserter) throws IOException {
		if (pendingBlobs != null) {
			for (String s : pendingBlobs.values()) {
				inserter.insert(OBJ_BLOB, encode(s));
			}
			pendingBlobs = null;
		}
		return contents.writeTree(inserter);
	}

	/** @return a deep copy of this RefTree. */
	public RefTree copy() {
		RefTree r = new RefTree(null, DirCache.newInCore());
		DirCacheBuilder b = r.contents.builder();
		for (int i = 0; i < contents.getEntryCount(); i++) {
			b.add(new DirCacheEntry(contents.getEntry(i)));
		}
		b.finish();
		if (pendingBlobs != null) {
			r.pendingBlobs = new HashMap<>(pendingBlobs);
		}
		return r;
	}

	/** Releases the ObjectReader remembered by the tree. */
	public void close() {
		if (reader != null) {
			reader.close();
		}
	}

	private static class LockFailureException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
