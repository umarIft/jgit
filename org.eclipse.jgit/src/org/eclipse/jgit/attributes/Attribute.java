/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
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

package org.eclipse.jgit.attributes;

import org.eclipse.jgit.util.CompareUtils;

/**
 *
 */
public class Attribute {

	private final String key;

	private final AttributeValue value;

	/**
	 * @param key
	 * @param value
	 */
	public Attribute(String key, AttributeValue value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Attribute) {
			final Attribute attribute = (Attribute) obj;
			return CompareUtils.areEqual(key, attribute.getKey())
					&& CompareUtils.areEqual(value, attribute.getValue());
		}
		return false;
	}

	@Override
	public int hashCode() {
		if (value != null) 
			return key.hashCode() ^ value.hashCode();
		else 
			return key.hashCode();
	}

	public String asString() {
		if (value == AttributeValue.SET) 
			return key;
		else if (value == AttributeValue.UNSET) 
			return "-" + key; //$NON-NLS-1$
		else if (value == null) 
			return "!" + key; //$NON-NLS-1$
		else
			return key + "=" + value; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return asString();
	}

	/**
	 * @return ...
	 */
	public String getKey() {
		return key;
	}

	/**
	 * @return ...
	 */
	public AttributeValue getValue() {
		return value;
	}
}