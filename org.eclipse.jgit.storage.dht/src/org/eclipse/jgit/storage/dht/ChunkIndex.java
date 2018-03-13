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

package org.eclipse.jgit.storage.dht;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import static org.eclipse.jgit.lib.Constants.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/** Index into a {@link PackChunk}. */
public abstract class ChunkIndex {
	private static final int V1 = 0x01;

	static ChunkIndex fromBytes(ChunkKey key, byte[] index) throws DhtException {
		int v = index[0] & 0xff;
		switch (v) {
		case V1: {
			final int offsetFormat = index[1] & 7;
			switch (offsetFormat) {
			case 1:
				return new Offset1(index, key);
			case 2:
				return new Offset2(index, key);
			case 3:
				return new Offset3(index, key);
			case 4:
				return new Offset4(index, key);
			default:
				throw new DhtException(MessageFormat.format(
						DhtText.get().unsupportedChunkIndex,
						Integer.toHexString(NB.decodeUInt16(index, 0)), key));
			}
		}
		default:
			throw new DhtException(MessageFormat.format(
					DhtText.get().unsupportedChunkIndex,
					Integer.toHexString(v), key));
		}
	}

	/**
	 * Format the chunk index and return its binary representation.
	 *
	 * @param list
	 *            the list of objects that appear in the chunk. This list will
	 *            be sorted in-place if it has more than 1 element.
	 * @return binary representation of the chunk's objects and their starting
	 *         offsets. The format is private to this class.
	 */
	static byte[] create(List<? extends PackedObjectInfo> list) {
		int cnt = list.size();
		sortObjectList(list);

		int fanoutFormat = 0;
		int[] buckets = null;
		if (64 < cnt) {
			buckets = new int[256];
			for (PackedObjectInfo oe : list)
				buckets[oe.getFirstByte()]++;
			fanoutFormat = selectFanoutFormat(buckets);
		}

		int offsetFormat = selectOffsetFormat(list);
		byte[] index = new byte[2 // header
				+ 256 * fanoutFormat // (optional) fanout
				+ cnt * OBJECT_ID_LENGTH // ids
				+ cnt * offsetFormat // offsets
		];
		index[0] = V1;
		index[1] = (byte) ((fanoutFormat << 7) | offsetFormat);

		int ptr = 2;

		switch (fanoutFormat) {
		case 0:
			break;
		case 1:
			for (int i = 0; i < 256; i++, ptr++)
				index[ptr] = (byte) buckets[i];
			break;
		case 2:
			for (int i = 0; i < 256; i++, ptr += 2)
				NB.encodeInt16(index, ptr, buckets[i]);
			break;
		case 3:
			for (int i = 0; i < 256; i++, ptr += 3)
				encodeInt24(index, ptr, buckets[i]);
			break;
		case 4:
			for (int i = 0; i < 256; i++, ptr += 4)
				NB.encodeInt32(index, ptr, buckets[i]);
			break;
		}

		for (PackedObjectInfo oe : list) {
			oe.copyRawTo(index, ptr);
			ptr += OBJECT_ID_LENGTH;
		}

		switch (offsetFormat) {
		case 1:
			for (PackedObjectInfo oe : list)
				index[ptr++] = (byte) oe.getOffset();
			break;

		case 2:
			for (PackedObjectInfo oe : list) {
				NB.encodeInt16(index, ptr, (int) oe.getOffset());
				ptr += 2;
			}
			break;

		case 3:
			for (PackedObjectInfo oe : list) {
				encodeInt24(index, ptr, (int) oe.getOffset());
				ptr += 3;
			}
			break;

		case 4:
			for (PackedObjectInfo oe : list) {
				NB.encodeInt32(index, ptr, (int) oe.getOffset());
				ptr += 4;
			}
			break;
		}

		return index;
	}

	private static int selectFanoutFormat(int[] buckets) {
		int fmt = 1;
		int max = 1 << (8 * fmt);

		for (int cnt : buckets) {
			while (max <= cnt && fmt < 4) {
				fmt++;
				max = 1 << (8 * fmt);
			}
			if (fmt == 4)
				return fmt;
		}
		return fmt;
	}

	private static int selectOffsetFormat(List<? extends PackedObjectInfo> list) {
		int fmt = 1;
		int max = 1 << (8 * fmt);

		for (PackedObjectInfo oe : list) {
			while (max <= oe.getOffset() && fmt < 4) {
				fmt++;
				max = 1 << (8 * fmt);
			}
			if (fmt == 4)
				return fmt;
		}
		return fmt;
	}

	@SuppressWarnings("unchecked")
	private static void sortObjectList(List<? extends PackedObjectInfo> list) {
		Collections.sort(list);
	}

	final byte[] index;

	final int[] fanout;

	final int idTable;

	final int offsetTable;

	final int count;

	ChunkIndex(byte[] index, ChunkKey key) throws DhtException {
		final int ctl = index[1];
		final int fanoutFormat = (ctl >>> 3) & 7;
		final int offsetFormat = ctl & 7;

		switch (fanoutFormat) {
		case 0:
			fanout = null; // no fanout, too small
			break;

		case 1: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += index[2 + i] & 0xff;
				fanout[i] = last;
			}
			break;
		}
		case 2: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += NB.decodeUInt16(index, 2 + i * 2);
				fanout[i] = last;
			}
			break;
		}
		case 3: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += decodeUInt24(index, 2 + i * 3);
				fanout[i] = last;
			}
			break;
		}
		case 4: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += NB.decodeInt32(index, 2 + i * 4);
				fanout[i] = last;
			}
			break;
		}
		default:
			throw new DhtException(MessageFormat.format(
					DhtText.get().unsupportedChunkIndex,
					Integer.toHexString(NB.decodeUInt16(index, 0)), key));
		}

		this.index = index;
		this.idTable = 2 + 256 * fanoutFormat;

		int recsz = OBJECT_ID_LENGTH + offsetFormat;
		this.count = (index.length - idTable) / recsz;
		this.offsetTable = idTable + count * OBJECT_ID_LENGTH;
	}

	/**
	 * Get the total number of objects described by this index.
	 *
	 * @return number of objects in this index and its associated chunk.
	 */
	public int getObjectCount() {
		return count;
	}

	/**
	 * Get an ObjectId from this index.
	 *
	 * @param nth
	 *            the object to return. Must be in range [0, getObjectCount).
	 * @return the object id.
	 */
	public ObjectId getObjectId(int nth) {
		return ObjectId.fromRaw(index, idPosition(nth));
	}

	/** @return the size of this index, in bytes. */
	int getIndexSize() {
		int sz = index.length;
		if (fanout != null)
			sz += 12 + 256 * 4;
		return sz;
	}

	/**
	 * Search for an object in the index.
	 *
	 * @param objId
	 *            the object to locate.
	 * @return offset of the object in the corresponding chunk; -1 if not found.
	 */
	int findOffset(AnyObjectId objId) {
		int hi, lo;

		if (fanout != null) {
			int fb = objId.getFirstByte();
			lo = fb == 0 ? 0 : fanout[fb - 1];
			hi = fanout[fb];
		} else {
			lo = 0;
			hi = count;
		}

		do {
			final int mid = (lo + hi) >>> 1;
			final int cmp = objId.compareTo(index, idPosition(mid));
			if (cmp < 0)
				hi = mid;
			else if (cmp == 0)
				return getOffset(mid);
			else
				lo = mid + 1;
		} while (lo < hi);
		return -1;
	}

	abstract int getOffset(int nth);

	private int idPosition(int nth) {
		return idTable + (nth * OBJECT_ID_LENGTH);
	}

	private static class Offset1 extends ChunkIndex {
		Offset1(byte[] index, ChunkKey key) throws DhtException {
			super(index, key);
		}

		int getOffset(int nth) {
			return index[offsetTable + nth] & 0xff;
		}
	}

	private static class Offset2 extends ChunkIndex {
		Offset2(byte[] index, ChunkKey key) throws DhtException {
			super(index, key);
		}

		int getOffset(int nth) {
			return NB.decodeUInt16(index, offsetTable + (nth * 2));
		}
	}

	private static class Offset3 extends ChunkIndex {
		Offset3(byte[] index, ChunkKey key) throws DhtException {
			super(index, key);
		}

		int getOffset(int nth) {
			return decodeUInt24(index, offsetTable + (nth * 3));
		}
	}

	private static class Offset4 extends ChunkIndex {
		Offset4(byte[] index, ChunkKey key) throws DhtException {
			super(index, key);
		}

		int getOffset(int nth) {
			return NB.decodeInt32(index, offsetTable + (nth * 4));
		}
	}

	private static void encodeInt24(byte[] intbuf, int offset, int v) {
		intbuf[offset + 2] = (byte) v;
		v >>>= 8;

		intbuf[offset + 1] = (byte) v;
		v >>>= 8;

		intbuf[offset] = (byte) v;
	}

	private static int decodeUInt24(byte[] intbuf, int offset) {
		int r = intbuf[offset] << 8;

		r |= intbuf[offset + 1] & 0xff;
		r <<= 8;

		r |= intbuf[offset + 2] & 0xff;
		return r;
	}
}