/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
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

package org.eclipse.jgit.nls;

import java.util.Locale;
import java.util.MissingResourceException;

import junit.framework.TestCase;

public class TestTranslationBundle extends TestCase {

	public void testMissingPropertiesFile() {
		try {
			new NoPropertiesBundle().load(Locale.ROOT);
			fail("Expected MissingResourceException");
		} catch (MissingResourceException e) {
			// pass
		}
	}

	public void testMissingString() {
		try {
			new MissingPropertyBundle().load(Locale.ROOT);
			fail("Expected MissingResourceException");
		} catch (MissingResourceException e) {
			// pass
		}
	}

	public void testNonTranslatedBundle() {
		NonTranslatedBundle bundle = new NonTranslatedBundle();

		bundle.load(Locale.ROOT);
		assertEquals(Locale.ROOT, bundle.getEffectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);

		bundle.load(Locale.ENGLISH);
		assertEquals(Locale.ROOT, bundle.getEffectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);

		bundle.load(Locale.GERMAN);
		assertEquals(Locale.ROOT, bundle.getEffectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);
	}

	public void testGermanTranslation() {
		GermanTranslatedBundle bundle = new GermanTranslatedBundle();

		bundle.load(Locale.ROOT);
		assertEquals(Locale.ROOT, bundle.getEffectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);

		bundle.load(Locale.GERMAN);
		assertEquals(Locale.GERMAN, bundle.getEffectiveLocale());
		assertEquals("Guten Morgen {0}", bundle.goodMorning);
	}

}
