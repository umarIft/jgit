/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.http.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServletUtilsTest {
	@Test
	public void emptyContextPath() {
		assertEquals("/foo/bar",
				ServletUtils.getEncodedPathInfo("/s", "/s/foo/bar", ""));
		assertEquals("/foo%2Fbar",
				ServletUtils.getEncodedPathInfo("/s", "/s/foo%2Fbar", ""));
	}

	@Test
	public void emptyServletPath() {
		assertEquals("/foo/bar",
				ServletUtils.getEncodedPathInfo("", "/c/foo/bar", "/c"));
		assertEquals("/foo%2Fbar",
				ServletUtils.getEncodedPathInfo("", "/c/foo%2Fbar", "/c"));
	}

	@Test
	public void trailingSlashes() {
		assertEquals("/foo/bar/",
				ServletUtils.getEncodedPathInfo("/s", "/c/s/foo/bar/", "/c"));
		assertEquals("/foo/bar/",
				ServletUtils.getEncodedPathInfo("/s", "/c/s/foo/bar///", "/c"));
		assertEquals("/foo%2Fbar/",
				ServletUtils.getEncodedPathInfo("/s", "/c/s/foo%2Fbar/", "/c"));
		assertEquals("/foo%2Fbar/", ServletUtils.getEncodedPathInfo("/s",
				"/c/s/foo%2Fbar///", "/c"));
	}

	@Test
	public void servletPathMatchesRequestPath() {
		assertEquals(null, ServletUtils.getEncodedPathInfo("/s", "/c/s", "/c"));
	}

	@Test
	public void testAcceptGzip() {
		assertFalse(ServletUtils.acceptsGzipEncoding((String) null));
		assertFalse(ServletUtils.acceptsGzipEncoding(""));

		assertTrue(ServletUtils.acceptsGzipEncoding("gzip"));
		assertTrue(ServletUtils.acceptsGzipEncoding("deflate,gzip"));
		assertTrue(ServletUtils.acceptsGzipEncoding("gzip,deflate"));

		assertFalse(ServletUtils.acceptsGzipEncoding("gzip(proxy)"));
		assertFalse(ServletUtils.acceptsGzipEncoding("proxy-gzip"));
	}
}
