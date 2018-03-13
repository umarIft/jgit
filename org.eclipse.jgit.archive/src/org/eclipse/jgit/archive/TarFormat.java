/*
 * Copyright (C) 2012 Google Inc.
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
package org.eclipse.jgit.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * Unix TAR format (ustar + some PAX extensions).
 */
public class TarFormat implements ArchiveCommand.Format<ArchiveOutputStream> {
	private static final List<String> SUFFIXES =
			Collections.unmodifiableList(Arrays.asList(".tar"));

	public ArchiveOutputStream createArchiveOutputStream(OutputStream s) {
		TarArchiveOutputStream out = new TarArchiveOutputStream(s, "UTF-8"); //$NON-NLS-1$
		out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
		out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
		return out;
	}

	public void putEntry(ArchiveOutputStream out,
			String path, FileMode mode, ObjectLoader loader)
			throws IOException {
		if (FileMode.SYMLINK.equals(mode)) {
			final TarArchiveEntry entry = new TarArchiveEntry(
					path, TarConstants.LF_SYMLINK);
			entry.setLinkName(new String(
					loader.getCachedBytes(100), "UTF-8")); //$NON-NLS-1$
			out.putArchiveEntry(entry);
			out.closeArchiveEntry();
			return;
		}

		// TarArchiveEntry detects directories by checking
		// for '/' at the end of the filename.
		if (path.endsWith("/") != FileMode.TREE.equals(mode)) {
			throw new IllegalArgumentException(
					"TarFormat.putEntry: path " //$NON-NLS-1$
					+ path + " does not match mode " //$NON-NLS-1$
					+ mode);
		}
		final TarArchiveEntry entry = new TarArchiveEntry(path);
		if (FileMode.TREE.equals(mode)) {
			out.putArchiveEntry(entry);
			out.closeArchiveEntry();
			return;
		}

		if (FileMode.REGULAR_FILE.equals(mode)) {
			// ok
		} else if (FileMode.EXECUTABLE_FILE.equals(mode)) {
			entry.setMode(mode.getBits());
		} else {
			// Unsupported mode (e.g., GITLINK).
			throw new IllegalArgumentException(
					"TarFormat.putEntry: Unsupported mode " //$NON-NLS-1$
					+ mode);
		}
		entry.setSize(loader.getSize());
		out.putArchiveEntry(entry);
		loader.copyTo(out);
		out.closeArchiveEntry();
	}

	public Iterable<String> suffixes() {
		return SUFFIXES;
	}
}
