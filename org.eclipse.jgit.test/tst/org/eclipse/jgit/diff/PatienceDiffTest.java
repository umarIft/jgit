/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.diff;

import org.eclipse.jgit.diff.DiffPerformanceTest.CharArray;
import org.eclipse.jgit.diff.DiffPerformanceTest.CharCmp;

public class PatienceDiffTest extends AbstractDiffTestCase {
	@Override
	protected DiffAlgorithm algorithm() {
		PatienceDiff pd = new PatienceDiff();
		pd.setFallbackAlgorithm(null);
		return pd;
	}

	public void testEdit_NoUniqueMiddleSideA() {
		EditList r = diff(t("aRRSSz"), t("aSSRRz"));
		assertEquals(1, r.size());
		assertEquals(new Edit(1, 5, 1, 5), r.get(0));
	}

	public void testEdit_NoUniqueMiddleSideB() {
		EditList r = diff(t("aRSz"), t("aSSRRz"));
		assertEquals(1, r.size());
		assertEquals(new Edit(1, 3, 1, 5), r.get(0));
	}

	public void testPerformanceTestDeltaLength() {
		String a = DiffTestDataGenerator.generateSequence(40000, 971, 3);
		String b = DiffTestDataGenerator.generateSequence(40000, 1621, 5);
		CharArray ac = new CharArray(a);
		CharArray bc = new CharArray(b);

		PatienceDiff pd = new PatienceDiff();
		EditList r;

		pd.setFallbackAlgorithm(null);
		r = pd.diff(new CharCmp(), ac, bc);
		assertEquals(25, r.size());

		pd.setFallbackAlgorithm(HistogramDiff.INSTANCE);
		r = pd.diff(new CharCmp(), ac, bc);
		assertEquals(71, r.size());

		pd.setFallbackAlgorithm(MyersDiff.INSTANCE);
		r = pd.diff(new CharCmp(), ac, bc);
		assertEquals(73, r.size());
	}
}
