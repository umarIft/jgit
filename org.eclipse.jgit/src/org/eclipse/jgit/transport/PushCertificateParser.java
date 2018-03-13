/*
 * Copyright (C) 2015, Google Inc.
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

import static org.eclipse.jgit.transport.BaseReceivePack.parseCommand;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_PUSH_CERT;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;
import org.eclipse.jgit.util.IO;

/**
 * Parser for signed push certificates.
 *
 * @since 4.0
 */
public class PushCertificateParser {
	static final String BEGIN_SIGNATURE =
			"-----BEGIN PGP SIGNATURE-----"; //$NON-NLS-1$
	static final String END_SIGNATURE =
			"-----END PGP SIGNATURE-----"; //$NON-NLS-1$

	static final String VERSION = "certificate version"; //$NON-NLS-1$

	static final String PUSHER = "pusher"; //$NON-NLS-1$

	static final String PUSHEE = "pushee"; //$NON-NLS-1$

	static final String NONCE = "nonce"; //$NON-NLS-1$

	static final String END_CERT = "push-cert-end"; //$NON-NLS-1$

	private static final String VERSION_0_1 = "0.1"; //$NON-NLS-1$

	private static interface StringReader {
		/**
		 * @return the next string from the input, up to an optional newline, with
		 *         newline stripped if present
		 *
		 * @throws EOFException
		 *             if EOF was reached.
		 * @throws IOException
		 *             if an error occurred during reading.
		 */
		String read() throws EOFException, IOException;
	}

	private static class PacketLineReader implements StringReader {
		private final PacketLineIn pckIn;

		private PacketLineReader(PacketLineIn pckIn) {
			this.pckIn = pckIn;
		}

		@Override
		public String read() throws IOException {
			return pckIn.readString();
		}
	}

	private static class StreamReader implements StringReader {
		private final Reader reader;

		private StreamReader(Reader reader) {
			this.reader = reader;
		}

		@Override
		public String read() throws IOException {
			// Presize for a command containing 2 SHA-1s and some refname.
			String line = IO.readLine(reader, 41 * 2 + 64);
			if (line.isEmpty()) {
				throw new EOFException();
			} else if (line.charAt(line.length() - 1) == '\n') {
				line = line.substring(0, line.length() - 1);
			}
			return line;
		}
	}

	/**
	 * Parse a push certificate from a reader.
	 * <p>
	 * Differences from the {@link PacketLineIn} receiver methods:
	 * <ul>
	 * <li>Does not use pkt-line framing.</li>
	 * <li>Reads an entire cert in one call rather than depending on a loop in
	 *   the caller.</li>
	 * <li>Does not assume a {@code "push-cert-end"} line.</li>
	 * </ul>
	 *
	 * @param r
	 *            input reader; consumed only up until the end of the next
	 *            signature in the input.
	 * @return the parsed certificate, or null if the reader was at EOF.
	 * @throws PackProtocolException
	 *             if the certificate is malformed.
	 * @throws IOException
	 *             if there was an error reading from the input.
	 * @since 4.1
	 */
	public static PushCertificate fromReader(Reader r)
			throws PackProtocolException, IOException {
		PushCertificateParser parser = new PushCertificateParser();
		StreamReader reader = new StreamReader(r);
		parser.receiveHeader(reader);
		String line;
		try {
			while (!(line = reader.read()).isEmpty()) {
				if (line.equals(BEGIN_SIGNATURE)) {
					parser.receiveSignature(reader);
					break;
				}
				parser.addCommand(line);
			}
		} catch (EOFException e) {
			// EOF reached, but might have been at a valid state. Let build call below
			// sort it out.
		}
		return parser.build();
	}

	private boolean received;
	private String version;
	private PushCertificateIdent pusher;
	private String pushee;

	/** The nonce that was sent to the client. */
	private String sentNonce;

	/**
	 * The nonce the pusher signed.
	 * <p>
	 * This may vary from {@link #sentNonce}; see git-core documentation for
	 * reasons.
	 */
	private String receivedNonce;

	private NonceStatus nonceStatus;
	private String signature;

	/** Database we write the push certificate into. */
	private final Repository db;

	/**
	 * The maximum time difference which is acceptable between advertised nonce
	 * and received signed nonce.
	 */
	private final int nonceSlopLimit;

	private final boolean enabled;
	private final NonceGenerator nonceGenerator;
	private final List<ReceiveCommand> commands = new ArrayList<>();

	/**
	 * @param into
	 *            destination repository for the push.
	 * @param cfg
	 *            configuration for signed push.
	 */
	public PushCertificateParser(Repository into, SignedPushConfig cfg) {
		if (cfg != null) {
			nonceSlopLimit = cfg.getCertNonceSlopLimit();
			nonceGenerator = cfg.getNonceGenerator();
		} else {
			nonceSlopLimit = 0;
			nonceGenerator = null;
		}
		db = into;
		enabled = nonceGenerator != null;
	}

	private PushCertificateParser() {
		db = null;
		nonceSlopLimit = 0;
		nonceGenerator = null;
		enabled = true;
	}

	/**
	 * @return the parsed certificate, or null if push certificates are disabled.
	 * @throws IOException
	 *             if the push certificate has missing or invalid fields.
	 * @since 4.1
	 */
	public PushCertificate build() throws IOException {
		if (!received || !enabled) {
			return null;
		}
		try {
			return new PushCertificate(version, pusher, pushee, receivedNonce,
					nonceStatus, Collections.unmodifiableList(commands), signature);
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/**
	 * @return if the repository is configured to use signed pushes in this
	 *         context.
	 * @since 4.0
	 */
	public boolean enabled() {
		return enabled;
	}

	/**
	 * @return the whole string for the nonce to be included into the capability
	 *         advertisement, or null if push certificates are disabled.
	 * @since 4.0
	 */
	public String getAdvertiseNonce() {
		String nonce = sentNonce();
		if (nonce == null) {
			return null;
		}
		return CAPABILITY_PUSH_CERT + '=' + nonce;
	}

	private String sentNonce() {
		if (sentNonce == null && nonceGenerator != null) {
			sentNonce = nonceGenerator.createNonce(db,
					TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
		}
		return sentNonce;
	}

	private static String parseHeader(StringReader reader, String header)
			throws IOException {
		return parseHeader(reader.read(), header);
	}

	private static String parseHeader(String s, String header)
			throws IOException {
		if (s.isEmpty()) {
			throw new EOFException();
		}
		if (s.length() <= header.length()
				|| !s.startsWith(header)
				|| s.charAt(header.length()) != ' ') {
			throw new PackProtocolException(MessageFormat.format(
					JGitText.get().pushCertificateInvalidField, header));
		}
		return s.substring(header.length() + 1);
	}

	/**
	 * Receive a list of commands from the input encapsulated in a push
	 * certificate.
	 * <p>
	 * This method doesn't parse the first line {@code "push-cert \NUL
	 * &lt;capabilities&gt;"}, but assumes the first line including the
	 * capabilities has already been handled by the caller.
	 *
	 * @param pckIn
	 *            where we take the push certificate header from.
	 * @param stateless
	 *            affects nonce verification. When {@code stateless = true} the
	 *            {@code NonceGenerator} will allow for some time skew caused by
	 *            clients disconnected and reconnecting in the stateless smart
	 *            HTTP protocol.
	 * @throws IOException
	 *             if the certificate from the client is badly malformed or the
	 *             client disconnects before sending the entire certificate.
	 * @since 4.0
	 */
	public void receiveHeader(PacketLineIn pckIn, boolean stateless)
			throws IOException {
		receiveHeader(new PacketLineReader(pckIn));
		nonceStatus = nonceGenerator != null
				? nonceGenerator.verify(
					receivedNonce, sentNonce(), db, stateless, nonceSlopLimit)
				: NonceStatus.UNSOLICITED;
	}

	private void receiveHeader(StringReader reader) throws IOException {
		try {
			try {
				version = parseHeader(reader, VERSION);
			} catch (EOFException e) {
				return;
			}
			received = true;
			if (!version.equals(VERSION_0_1)) {
				throw new PackProtocolException(MessageFormat.format(
						JGitText.get().pushCertificateInvalidFieldValue, VERSION, version));
			}
			String rawPusher = parseHeader(reader, PUSHER);
			pusher = PushCertificateIdent.parse(rawPusher);
			if (pusher == null) {
				throw new PackProtocolException(MessageFormat.format(
						JGitText.get().pushCertificateInvalidFieldValue,
						PUSHER, rawPusher));
			}
			String next = reader.read();
			if (next.startsWith(PUSHEE)) {
				pushee = parseHeader(next, PUSHEE);
				receivedNonce = parseHeader(reader, NONCE);
			} else {
				receivedNonce = parseHeader(next, NONCE);
			}
			// An empty line.
			if (!reader.read().isEmpty()) {
				throw new PackProtocolException(
						JGitText.get().pushCertificateInvalidHeader);
			}
		} catch (EOFException eof) {
			throw new PackProtocolException(
					JGitText.get().pushCertificateInvalidHeader, eof);
		}
	}

	/**
	 * Read the PGP signature.
	 * <p>
	 * This method assumes the line
	 * {@code "-----BEGIN PGP SIGNATURE-----"} has already been parsed,
	 * and continues parsing until an {@code "-----END PGP SIGNATURE-----"} is
	 * found, followed by {@code "push-cert-end"}.
	 *
	 * @param pckIn
	 *            where we read the signature from.
	 * @throws IOException
	 *             if the signature is invalid.
	 * @since 4.0
	 */
	public void receiveSignature(PacketLineIn pckIn) throws IOException {
		StringReader reader = new PacketLineReader(pckIn);
		receiveSignature(reader);
		if (!reader.read().equals(END_CERT)) {
			throw new PackProtocolException(
					JGitText.get().pushCertificateInvalidSignature);
		}
	}

	private void receiveSignature(StringReader reader) throws IOException {
		received = true;
		try {
			StringBuilder sig = new StringBuilder(BEGIN_SIGNATURE).append('\n');
			String line;
			while (!(line = reader.read()).equals(END_SIGNATURE)) {
				sig.append(line).append('\n');
			}
			signature = sig.append(END_SIGNATURE).append('\n').toString();
		} catch (EOFException eof) {
			throw new PackProtocolException(
					JGitText.get().pushCertificateInvalidSignature, eof);
		}
	}

	/**
	 * Add a command to the signature.
	 *
	 * @param cmd
	 *            the command.
	 * @since 4.1
	 */
	public void addCommand(ReceiveCommand cmd) {
		commands.add(cmd);
	}

	/**
	 * Add a command to the signature.
	 *
	 * @param line
	 *            the line read from the wire that produced this
	 *            command, with optional trailing newline already trimmed.
	 * @throws PackProtocolException
	 *             if the raw line cannot be parsed to a command.
	 * @since 4.0
	 */
	public void addCommand(String line) throws PackProtocolException {
		commands.add(parseCommand(line));
	}
}
