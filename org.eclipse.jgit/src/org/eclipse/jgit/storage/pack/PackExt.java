/*
 * Copyright (C) 2013, Google Inc.
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

package org.eclipse.jgit.storage.pack;

/** A pack file extension. */
public class PackExt {

	/** A pack file extension. */
	public static final PackExt PACK = new PackExt("pack"); //$NON-NLS-1$

	/** A pack index file extension. */
	public static final PackExt INDEX = new PackExt("idx"); //$NON-NLS-1$

	private static final PackExt[] VALUES = new PackExt[] { PACK, INDEX };

	private final String ext;

	/**
	 * @param ext
	 *            the file extension.
	 */
	public PackExt(String ext) {
		this.ext = ext;
	}

	/** @return the file extension. */
	public String getExtension() {
		return ext;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof PackExt) {
			return ((PackExt) obj).getExtension().equals(getExtension());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return getExtension().hashCode();
	}

	@Override
	public String toString() {
		return String.format("PackExt[%s]", getExtension()); //$NON-NLS-1$
	}

	/** @return all of the PackExt values. */
	public static PackExt[] values() {
		return VALUES;
	}
}
