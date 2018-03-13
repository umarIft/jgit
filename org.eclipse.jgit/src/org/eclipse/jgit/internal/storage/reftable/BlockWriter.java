/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.reftable.ReftableOutputStream.computeVarintSize;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.NB;

class BlockWriter {
	private final byte blockType;
	private final List<Entry> entries = new ArrayList<>();
	private final int blockSize;
	private final int restartInterval;

	private int bytesInKeyTable;
	private int restartCnt;

	BlockWriter(byte type, int bs, int ri) {
		blockType = type;
		blockSize = bs;
		restartInterval = ri;
	}

	byte[] lastKey() {
		return entries.get(entries.size() - 1).key;
	}

	int entryCount() {
		return entries.size();
	}

	void addIndex(byte[] lastKey, long blockOffset) {
		entries.add(new IndexEntry(lastKey, blockOffset));
	}

	void addFirst(Entry entry) throws BlockSizeTooSmallException {
		if (!tryAdd(entry, true)) {
			// Insanely long names need a larger block size.
			throw blockSizeTooSmall(entry);
		}
	}

	boolean tryAdd(Entry entry) {
		if (tryAdd(entry, true)) {
			return true;
		} else if (nextShouldBeRestart()) {
			// It was time for another restart, but the entry doesn't fit
			// with its complete name, as the block is nearly full. Try to
			// force it to fit with prefix compression rather than waste
			// the tail of the block with padding.
			return tryAdd(entry, false);
		}
		return false;
	}

	private boolean tryAdd(Entry entry, boolean tryRestart) {
		byte[] name = entry.key;
		int prefixLen = 0;
		boolean restart = tryRestart && nextShouldBeRestart();
		if (!restart) {
			byte[] prior = entries.get(entries.size() - 1).key;
			prefixLen = commonPrefix(prior, prior.length, name);
			if (prefixLen == 0) {
				restart = true;
			}
		}

		int entrySize = entry.size(prefixLen);
		if (computeBlockSize(entrySize, restart) > blockSize) {
			return false;
		}

		bytesInKeyTable += entrySize;
		entries.add(entry);
		if (restart) {
			entry.restart = true;
			restartCnt++;
		}
		return true;
	}

	private boolean nextShouldBeRestart() {
		int cnt = entries.size();
		return (cnt == 0 || ((cnt + 1) % restartInterval) == 0)
				&& restartCnt < MAX_RESTARTS;
	}

	private int computeBlockSize(int key, boolean restart) {
		return 4 // 4-byte block header
				+ bytesInKeyTable + key
				+ (restartCnt + (restart ? 1 : 0)) * 4
				+ 2; // 2-byte restart_count
	}

	void writeTo(ReftableOutputStream os) throws IOException {
		if (blockType == INDEX_BLOCK_TYPE) {
			selectIndexRestarts();
		}
		if (restartCnt > MAX_RESTARTS) {
			throw new IllegalStateException();
		}

		IntList restartOffsets = new IntList(restartCnt);
		byte[] prior = {};

		os.beginBlock(blockType);
		for (int entryIdx = 0; entryIdx < entries.size(); entryIdx++) {
			Entry entry = entries.get(entryIdx);
			if (entry.restart) {
				restartOffsets.add(os.bytesWrittenInBlock());
			}
			entry.writeKey(os, prior);
			entry.writeValue(os);
			prior = entry.key;
		}
		for (int i = 0; i < restartOffsets.size(); i++) {
			os.writeInt32(restartOffsets.get(i));
		}
		os.writeInt16(restartOffsets.size() - 1);
		os.flushBlock();
	}

	private void selectIndexRestarts() {
		// Indexes grow without bound, but the restart table has a limit.
		// Select restarts in the index as far apart as possible to stay
		// within the MAX_RESTARTS limit defined by the file format.
		int ir = Math.max(restartInterval, entries.size() / MAX_RESTARTS);
		for (int k = 0; k < entries.size(); k++) {
			if ((k % ir) == 0) {
				entries.get(k).restart = true;
			}
		}
	}

	private BlockSizeTooSmallException blockSizeTooSmall(Entry entry) {
		// Compute size required to fit this entry by itself.
		int min = computeBlockSize(entry.size(0), true);
		return new BlockSizeTooSmallException(min);
	}

	static int encodeLenAndType(int keyLen, int type) {
		return (keyLen << 2) | type;
	}

	static int commonPrefix(byte[] a, int n, byte[] b) {
		int len = Math.min(n, Math.min(a.length, b.length));
		for (int i = 0; i < len; i++) {
			if (a[i] != b[i]) {
				return i;
			}
		}
		return len;
	}


	static abstract class Entry {
		final byte[] key;
		boolean restart;

		Entry(byte[] key) {
			this.key = key;
		}

		void writeKey(ReftableOutputStream os, byte[] prior) {
			int pfx;
			int sfx;
			if (restart) {
				pfx = 0;
				sfx = key.length;
			} else {
				pfx = commonPrefix(prior, prior.length, key);
				sfx = key.length - pfx;
			}
			os.writeVarint(pfx);
			os.writeVarint(encodeLenAndType(sfx, type()));
			os.write(key, pfx, sfx);
		}

		int size(int prefixLen) {
			int suffixLen = key.length - prefixLen;
			return computeVarintSize(prefixLen)
					+ computeVarintSize(encodeLenAndType(suffixLen, type()))
					+ suffixLen
					+ valueSize();
		}

		abstract byte blockType();
		abstract int type();
		abstract int valueSize();
		abstract void writeValue(ReftableOutputStream os) throws IOException;
	}

	static class IndexEntry extends Entry {
		private final long blockOffset;

		IndexEntry(byte[] key, long blockOffset) {
			super(key);
			this.blockOffset = blockOffset;
		}

		@Override
		byte blockType() {
			return INDEX_BLOCK_TYPE;
		}

		@Override
		int type() {
			return 0;
		}

		@Override
		int valueSize() {
			return computeVarintSize(blockOffset);
		}

		@Override
		void writeValue(ReftableOutputStream os) {
			os.writeVarint(blockOffset);
		}
	}

	static class RefEntry extends Entry {
		private final Ref ref;

		RefEntry(Ref ref) {
			super(nameUtf8(ref));
			this.ref = ref;
		}

		@Override
		byte blockType() {
			return REF_BLOCK_TYPE;
		}

		@Override
		int type() {
			if (ref.isSymbolic()) {
				return 0x03;
			} else if (ref.getStorage() == NEW && ref.getObjectId() == null) {
				return 0x00;
			} else if (ref.getPeeledObjectId() != null) {
				return 0x02;
			} else {
				return 0x01;
			}
		}

		@Override
		int valueSize() {
			if (ref.isSymbolic()) {
				int nameLen = nameUtf8(ref.getTarget()).length;
				return computeVarintSize(nameLen) + nameLen;
			} else if (ref.getStorage() == NEW && ref.getObjectId() == null) {
				return 0;
			} else if (ref.getPeeledObjectId() != null) {
				return 2 * OBJECT_ID_LENGTH;
			}
			return OBJECT_ID_LENGTH;
		}

		@Override
		void writeValue(ReftableOutputStream os) throws IOException {
			if (ref.isSymbolic()) {
				os.writeVarintString(ref.getTarget().getName());
				return;
			}

			ObjectId id1 = ref.getObjectId();
			if (id1 == null) {
				if (ref.getStorage() == NEW) {
					return;
				}
				throw new IOException(JGitText.get().invalidId0);
			} else if (!ref.isPeeled()) {
				throw new IOException(JGitText.get().peeledRefIsRequired);
			}
			os.writeId(id1);

			ObjectId id2 = ref.getPeeledObjectId();
			if (id2 != null) {
				os.writeId(id2);
			}
		}

		private static byte[] nameUtf8(Ref ref) {
			return ref.getName().getBytes(UTF_8);
		}
	}

	static class LogEntry extends Entry {
		final ObjectId oldId;
		final ObjectId newId;
		final short tz;
		final byte[] name;
		final byte[] email;
		final byte[] msg;

		LogEntry(String refName, PersonIdent who,
				ObjectId oldId, ObjectId newId,
				String message) {
			super(key(refName, (int) (who.getWhen().getTime() / 1000)));

			this.oldId = oldId;
			this.newId = newId;
			this.tz = (short) who.getTimeZoneOffset();
			this.name = who.getName().getBytes(UTF_8);
			this.email = who.getEmailAddress().getBytes(UTF_8);
			this.msg = message.getBytes(UTF_8);
		}

		static byte[] key(String refName, int time) {
			byte[] name = refName.getBytes(UTF_8);
			byte[] key = Arrays.copyOf(name, name.length + 1 + 4);
			NB.encodeInt32(key, key.length - 4, reverseTime(time));
			return key;
		}

		@Override
		byte blockType() {
			return LOG_BLOCK_TYPE;
		}

		@Override
		int type() {
			return 0;
		}

		@Override
		int valueSize() {
			return 2 * OBJECT_ID_LENGTH
					+ 2
					+ computeVarintSize(name.length) + name.length
					+ computeVarintSize(email.length) + email.length
					+ computeVarintSize(msg.length) + msg.length;
		}

		@Override
		void writeValue(ReftableOutputStream os) {
			os.writeId(oldId);
			os.writeId(newId);
			os.writeInt16(tz);
			os.writeVarintString(name);
			os.writeVarintString(email);
			os.writeVarintString(msg);
		}

		static int reverseTime(int time) {
			return 0xffffffff - time;
		}
	}
}