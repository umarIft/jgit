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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Test for push certificate parsing. */
public class PushCertificateParserTest {
	// Example push certificate generated by C git 2.2.0.
	private static final String INPUT = "001ccertificate version 0.1\n"
			+ "0041pusher Dave Borowitz <dborowitz@google.com> 1433954361 -0700\n"
			+ "0024pushee git://localhost/repo.git\n"
			+ "002anonce 1433954361-bde756572d665bba81d8\n"
			+ "0005\n"
			+ "00680000000000000000000000000000000000000000"
			+ " 6c2b981a177396fb47345b7df3e4d3f854c6bea7"
			+ " refs/heads/master\n"
			+ "0022-----BEGIN PGP SIGNATURE-----\n"
			+ "0016Version: GnuPG v1\n"
			+ "0005\n"
			+ "0045iQEcBAABAgAGBQJVeGg5AAoJEPfTicJkUdPkUggH/RKAeI9/i/LduuiqrL/SSdIa\n"
			+ "00459tYaSqJKLbXz63M/AW4Sp+4u+dVCQvnAt/a35CVEnpZz6hN4Kn/tiswOWVJf4CO7\n"
			+ "0045htNubGs5ZMwvD6sLYqKAnrM3WxV/2TbbjzjZW6Jkidz3jz/WRT4SmjGYiEO7aA+V\n"
			+ "00454ZdIS9f7sW5VsHHYlNThCA7vH8Uu48bUovFXyQlPTX0pToSgrWV3JnTxDNxfn3iG\n"
			+ "0045IL0zTY/qwVCdXgFownLcs6J050xrrBWIKqfcWr3u4D2aCLyR0v+S/KArr7ulZygY\n"
			+ "0045+SOklImn8TAZiNxhWtA6ens66IiammUkZYFv7SSzoPLFZT4dC84SmGPWgf94NoQ=\n"
			+ "000a=XFeC\n"
			+ "0020-----END PGP SIGNATURE-----\n"
			+ "0012push-cert-end\n";

	private Repository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("repo"));
	}

	private static SignedPushConfig newEnabledConfig() {
		Config cfg = new Config();
		cfg.setString("receive", null, "certnonceseed", "sekret");
		return SignedPushConfig.KEY.parse(cfg);
	}

	private static SignedPushConfig newDisabledConfig() {
		return SignedPushConfig.KEY.parse(new Config());
	}

	@Test
	public void noCert() throws Exception {
		PushCertificateParser parser =
				new PushCertificateParser(db, newEnabledConfig());
		assertTrue(parser.enabled());
		assertNull(parser.build());

		ObjectId oldId = ObjectId.zeroId();
		ObjectId newId =
				ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		String rawLine =
				oldId.name() + " " + newId.name() + " refs/heads/master";
		ReceiveCommand cmd = BaseReceivePack.parseCommand(rawLine);

		parser.addCommand(cmd, rawLine);
		parser.addCommand(rawLine);
		assertNull(parser.build());
	}

	@Test
	public void disabled() throws Exception {
		PacketLineIn pckIn = newPacketLineIn(INPUT);
		PushCertificateParser parser =
				new PushCertificateParser(db, newDisabledConfig());
		assertFalse(parser.enabled());
		assertNull(parser.build());

		parser.receiveHeader(pckIn, false);
		parser.addCommand(pckIn.readStringRaw());
		assertEquals(PushCertificateParser.BEGIN_SIGNATURE, pckIn.readStringRaw());
		parser.receiveSignature(pckIn);
		assertNull(parser.build());
	}

	@Test
	public void disabledParserStillRequiresCorrectSyntax() throws Exception {
		PacketLineIn pckIn = newPacketLineIn("001ccertificate version XYZ\n");
		PushCertificateParser parser =
				new PushCertificateParser(db, newDisabledConfig());
		assertFalse(parser.enabled());
		try {
			parser.receiveHeader(pckIn, false);
			fail("Expected PackProtocolException");
		} catch (PackProtocolException e) {
			assertEquals(
					"Push certificate has missing or invalid value for certificate"
						+ " version: XYZ",
					e.getMessage());
		}
		assertNull(parser.build());
	}

	@Test
	public void parseCertFromPktLine() throws Exception {
		PacketLineIn pckIn = newPacketLineIn(INPUT);
		PushCertificateParser parser =
				new PushCertificateParser(db, newEnabledConfig());
		parser.receiveHeader(pckIn, false);
		parser.addCommand(pckIn.readStringRaw());
		assertEquals(PushCertificateParser.BEGIN_SIGNATURE, pckIn.readStringRaw());
		parser.receiveSignature(pckIn);

		PushCertificate cert = parser.build();
		assertEquals("0.1", cert.getVersion());
		assertEquals("Dave Borowitz", cert.getPusherIdent().getName());
		assertEquals("dborowitz@google.com",
				cert.getPusherIdent().getEmailAddress());
		assertEquals(1433954361000L, cert.getPusherIdent().getWhen().getTime());
		assertEquals(-7 * 60, cert.getPusherIdent().getTimeZoneOffset());
		assertEquals("git://localhost/repo.git", cert.getPushee());
		assertEquals("1433954361-bde756572d665bba81d8", cert.getNonce());

		assertNotEquals(cert.getNonce(), parser.getAdvertiseNonce());
		assertEquals(PushCertificate.NonceStatus.BAD, cert.getNonceStatus());

		assertEquals(1, cert.getCommands().size());
		ReceiveCommand cmd = cert.getCommands().get(0);
		assertEquals("refs/heads/master", cmd.getRefName());
		assertEquals(ObjectId.zeroId(), cmd.getOldId());
		assertEquals("6c2b981a177396fb47345b7df3e4d3f854c6bea7",
				cmd.getNewId().name());

		assertEquals(concatPacketLines(INPUT, 0, 6), cert.toText());

		String signature = concatPacketLines(INPUT, 6, 17);
		assertTrue(signature.startsWith(PushCertificateParser.BEGIN_SIGNATURE));
		assertTrue(signature.endsWith(PushCertificateParser.END_SIGNATURE));
		assertEquals(signature, cert.getSignature());
	}

	@Test
	public void testConcatPacketLines() throws Exception {
		String input = "000bline 1\n000bline 2\n000bline 3\n";
		assertEquals("line 1\n", concatPacketLines(input, 0, 1));
		assertEquals("line 1\nline 2\n", concatPacketLines(input, 0, 2));
		assertEquals("line 2\nline 3\n", concatPacketLines(input, 1, 3));
		assertEquals("line 2\nline 3\n", concatPacketLines(input, 1, 4));
	}

	private static String concatPacketLines(String input, int begin, int end)
			throws IOException {
		StringBuilder result = new StringBuilder();
		int i = 0;
		PacketLineIn pckIn = newPacketLineIn(input);
		while (i < end) {
			String line;
			try {
				line = pckIn.readStringRaw();
			} catch (EOFException e) {
				break;
			}
			if (++i > begin) {
				result.append(line);
			}
		}
		return result.toString();
	}

	private static PacketLineIn newPacketLineIn(String input) {
		return new PacketLineIn(new ByteArrayInputStream(Constants.encode(input)));
	}
}
