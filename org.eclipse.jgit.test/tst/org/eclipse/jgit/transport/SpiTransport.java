/*
 * Copyright (C) 2012, GitHub Inc.
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

import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

/**
 * Transport protocol contributed via service provider
 */
public class SpiTransport extends Transport {

	/**
	 * Transport protocol scheme
	 */
	public static final String SCHEME = "testspi";

	/**
	 * Instance
	 */
	public static final TransportProtocol PROTO = new TransportProtocol() {

		@Override
		public String getName() {
			return "Test SPI Transport Protocol";
		}

		@Override
		public Set<String> getSchemes() {
			return Collections.singleton(SCHEME);
		}

		@Override
		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException, TransportException {
			throw new NotSupportedException("not supported");
		}
	};

	private SpiTransport(Repository local, URIish uri) {
		super(local, uri);
	}

	@Override
	public FetchConnection openFetch() throws NotSupportedException,
			TransportException {
		throw new NotSupportedException("not supported");
	}

	@Override
	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		throw new NotSupportedException("not supported");
	}

	@Override
	public void close() {
		// Intentionally left blank
	}
}
