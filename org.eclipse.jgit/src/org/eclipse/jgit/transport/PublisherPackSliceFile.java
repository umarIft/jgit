/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * File-backed Slice.
 */
public class PublisherPackSliceFile extends PublisherPackSlice {
	private final String fileName;

	/**
	 * @param policy
	 * @param allocator
	 * @param buf
	 * @param fileName
	 */
	public PublisherPackSliceFile(LoadPolicy policy, Allocator allocator,
			byte[] buf, String fileName) {
		super(policy, allocator, buf);
		this.fileName = fileName;
	}

	@Override
	protected byte[] doLoad() throws IOException {
		RandomAccessFile file = new RandomAccessFile(fileName, "r");
		try {
			byte[] buf = new byte[(int) file.length()];
			file.read(buf);
			return buf;
		} finally {
			file.close();
		}
	}

	@Override
	protected void doStore(byte[] buffer) throws IOException {
		FileOutputStream out = new FileOutputStream(fileName);
		try {
			out.write(buffer);
			out.flush();
		} finally {
			out.close();
		}
	}

	@Override
	protected void doStoredWrite(OutputStream out, int position, int length)
			throws IOException {
		RandomAccessFile file = new RandomAccessFile(fileName, "r");
		try {
			byte buf[] = new byte[length];
			file.read(buf, position, length);
			out.write(buf);
		} finally {
			file.close();
		}
	}

	@Override
	public void close() {
		super.close();
		File f = new File(fileName);
		f.delete();
	}
}
