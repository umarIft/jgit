/*******************************************************************************
 * Copyright (c) 2016 Rewritten for JGIT from native GIT code by Yuriy Rotmistrov
 * Attention was paid to create streaming approach potentially allowing processing of huge files.
 * In future both Apply and Diff commands may be modified to benefit from streaming.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *    https://github.com/git/git/blob/74301d6edeb0e081a4ef864057952b6a7ff2b4be/builtin/apply.c - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.jgit.patch.binary;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Filter input stream intended for decoding base85 stream
 */
public class GitBinaryPatchInputStream extends FilterInputStream
{
	private BufferedReader sourceReader;
	private InputStream resultBuffer;
	private int linenr = 0;

	/**
	 * @param in
	 *            original base85-encoded stream
	 */
	public GitBinaryPatchInputStream(InputStream in)
	{
		super(in);
		sourceReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
	}

	public int read() throws IOException
	{
		if (available() > 0)
		{
			return resultBuffer.read();
		}
		else
		{
			return -1;
		}
	}

	public int read(byte b[], int off, int len) throws IOException
	{
		if (b == null)
		{
			throw new NullPointerException();
		}
		else if (off < 0 || len < 0 || len > b.length - off)
		{
			throw new IndexOutOfBoundsException();
		}
		else if (len == 0)
		{
			return 0;
		}

		int bytesRead = 0;
		int available;
		while (len > 0 && (available = available()) > 0)
		{
			int read = resultBuffer.read(b, off, Math.min(available, len));
			off += read;
			len -= read;
			bytesRead += read;
		}
		if (bytesRead == 0)
		{
			bytesRead = -1;
		}
		return bytesRead;
	}

	public long skip(long n) throws IOException
	{
		throw new IOException("Skip method is not supported"); //$NON-NLS-1$
	}

	public int available() throws IOException
	{
		if (resultBuffer == null || resultBuffer.available() == 0)
		{
			if (!readNextLine())
			{
				return 0;
			}
		}
		return resultBuffer.available();
	}

	public void close() throws IOException
	{
		sourceReader.close();
	}

	public synchronized void mark(int readlimit)
	{
		// Doing nothing
	}

	public synchronized void reset() throws IOException
	{
		// Doing nothing
	}

	public boolean markSupported()
	{
		return false;
	}

	private boolean readNextLine() throws IOException
	{
		/*
		 * Expect a line that begins with binary patch method ("literal" or
		 * "delta"), followed by the length of data before deflating. a sequence
		 * of 'length-byte' followed by base-85 encoded data should follow,
		 * terminated by a newline.
		 *
		 * Each 5-byte sequence of base-85 encodes up to 4 bytes, and we would
		 * limit the patch line to 66 characters, so one line can fit up to 13
		 * groups that would decode to 52 bytes max. The length byte 'A'-'Z'
		 * corresponds to 1-26 bytes, and 'a'-'z' corresponds to 27-52 bytes.
		 */

		String line = sourceReader.readLine();
		if (line == null)
		{
			return false;
		}

		linenr++;
		int byte_length, max_byte_length;
		// Line length include new line character at the end
		int llen = line.length() + 1;
		if (llen == 1)
		{
			/* consume the blank line */
			return false;
		}

		/*
		 * Minimum line is "A00000\n" which is 7-byte long, and the line length
		 * must be multiple of 5 plus 2.
		 */
		if ((llen < 7) || (llen - 2) % 5 != 0)
		{
			throw new IOException(String.format(
"corrupt binary patch at line %d: %s", //$NON-NLS-1$
							Integer.valueOf(linenr - 1), line));
		}
		max_byte_length = (llen - 2) / 5 * 4;
		byte_length = line.charAt(0);
		if ('A' <= byte_length && byte_length <= 'Z')
		{
			byte_length = byte_length - 'A' + 1;
		}
		else if ('a' <= byte_length && byte_length <= 'z')
		{
			byte_length = byte_length - 'a' + 27;
		}
		else
		{
			throw new IOException(String.format(
"corrupt binary patch at line %d: %s", //$NON-NLS-1$
							Integer.valueOf(linenr - 1), line));
		}

		/*
		 * if the input length was not multiple of 4, we would have filler at
		 * the end but the filler should never exceed 3 bytes
		 */
		if (max_byte_length < byte_length || byte_length <= max_byte_length - 4)
		{
			throw new IOException(String.format(
"corrupt binary patch at line %d: %s", //$NON-NLS-1$
							Integer.valueOf(linenr - 1), line));
		}

		try
		{
			resultBuffer = new ByteArrayInputStream(GitBase85Codec.decodeBase85(line.substring(1), byte_length));
		} catch (Exception e)
		{
			throw new IOException(
					String.format("corrupt binary patch at line %d: %s", //$NON-NLS-1$
							Integer.valueOf(linenr - 1), line),
					e);
		}

		return resultBuffer.available() > 0;
	}

}
