/*
 * Copyright (C) 2012, Google Inc.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Subscribes to a single remote Publisher process with multiple repositories.
 */
public class Subscriber {
	private final Map<String, SubscribedRepository>
			repoSubscriptions = new HashMap<String, SubscribedRepository>();

	private String restartToken;

	private String lastPackNumber;

	/** @return fast restart token, or null if none. */
	public String getRestartToken() {
		return restartToken;
	}

	/** @param restart */
	public void setRestartToken(String restart) {
		restartToken = restart;
	}

	/** @return the last pack number. */
	public String getLastPackNumber() {
		return lastPackNumber;
	}

	/**
	 * Set the last pack number.
	 *
	 * @param number
	 */
	public void setLastPackNumber(String number) {
		lastPackNumber = number;
	}

	/**
	 * @param r
	 * @param repository
	 */
	public void putRepository(String r, SubscribedRepository repository) {
		repoSubscriptions.put(r, repository);
	}

	/**
	 * @param r
	 * @return the repository with this key, or null.
	 */
	public SubscribedRepository getRepository(String r) {
		return repoSubscriptions.get(r);
	}

	/**
	 * @return the set of all repository names this subscriber will connect to.
	 */
	public Set<String> getAllRepositories() {
		return Collections.unmodifiableSet(repoSubscriptions.keySet());
	}

	/** Reset the state of this subscriber. */
	public void reset() {
		List<RefSpec> clearSpecs = Collections.emptyList();
		for (SubscribedRepository sr : repoSubscriptions.values()) {
			if (sr != null)
				sr.setSubscribeSpecs(clearSpecs);
		}
		setRestartToken(null);
		setLastPackNumber(null);
	}

	/** Release all resources used by this Subscriber. */
	public void close() {
		for (SubscribedRepository sr : repoSubscriptions.values()) {
			if (sr != null)
				sr.close();
		}
		repoSubscriptions.clear();
	}
}
