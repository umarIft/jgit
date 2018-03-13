/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.lfs.lib;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Abstraction of a repository for storing large objects
 *
 * @since 4.1
 */
public interface LargeFileRepository {

	/**
	 * @param id
	 *            id of the object
	 * @return {@code true} if the object exists, {@code false} otherwise
	 */
	public boolean exists(AnyLongObjectId id);

	/**
	 * @param id
	 *            id of the object
	 * @return length of the object content in bytes
	 * @throws IOException
	 */
	public long getLength(AnyLongObjectId id) throws IOException;

	/**
	 * Get a channel to read the object's content. The caller is responsible to
	 * close the channel
	 *
	 * @param id
	 *            id of the object to read
	 * @return the channel to read large object byte stream from
	 * @throws IOException
	 */
	public ReadableByteChannel getReadChannel(AnyLongObjectId id)
			throws IOException;

	/**
	 * Get a channel to write the object's content. The caller is responsible to
	 * close the channel.
	 *
	 * @param id
	 *            id of the object to write
	 * @return the channel to write large object byte stream to
	 * @throws IOException
	 */
	public WritableByteChannel getWriteChannel(AnyLongObjectId id)
			throws IOException;

	/**
	 * Call this to abort write
	 */
	public void abortWrite();
}
