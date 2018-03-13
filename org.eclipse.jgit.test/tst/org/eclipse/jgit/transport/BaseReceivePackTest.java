/*
 * Copyright (C) 2015, Google Inc.
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
 *	 notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *	 copyright notice, this list of conditions and the following
 *	 disclaimer in the documentation and/or other materials provided
 *	 with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *	 names of its contributors may be used to endorse or promote
 *	 products derived from this software without specific prior
 *	 written permission.
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

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/** Tests for base receive-pack utilities. */
public class BaseReceivePackTest {
	private static final String CMD_STRING =
			"0000000000000000000000000000000000000000"
			+ " deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
			+ " refs/heads/master";

	@Test
	public void parseCommand() {
		String input = CMD_STRING;
		ReceiveCommand cmd = BaseReceivePack.parseCommand(input);
		assertEquals(ObjectId.zeroId(), cmd.getOldId());
		assertEquals("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
				cmd.getNewId().name());
		assertEquals("refs/heads/master", cmd.getRefName());
	}

	@Test
	public void parseCommandWithTrailingNewline() {
		String input = CMD_STRING + '\n';
		ReceiveCommand cmd = BaseReceivePack.parseCommand(input);
		assertEquals(ObjectId.zeroId(), cmd.getOldId());
		assertEquals("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
				cmd.getNewId().name());
		assertEquals("refs/heads/master", cmd.getRefName());
	}
}
