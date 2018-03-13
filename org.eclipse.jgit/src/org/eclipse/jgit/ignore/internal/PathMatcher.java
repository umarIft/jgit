/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de>
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
package org.eclipse.jgit.ignore.internal;

import static org.eclipse.jgit.ignore.internal.Strings.checkWildCards;
import static org.eclipse.jgit.ignore.internal.Strings.count;
import static org.eclipse.jgit.ignore.internal.Strings.getPathSeparator;
import static org.eclipse.jgit.ignore.internal.Strings.isWildCard;
import static org.eclipse.jgit.ignore.internal.Strings.split;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.internal.Strings.PatternState;

/**
 * Matcher built by patterns consists of multiple path segments.
 * <p>
 * This class is immutable and thread safe.
 */
public class PathMatcher extends AbstractMatcher {

	private static final WildMatcher WILD = WildMatcher.INSTANCE;

	private final List<IMatcher> matchers;

	private final char slash;

	private final boolean beginning;

	private PathMatcher(String pattern, Character pathSeparator,
			boolean dirOnly)
			throws InvalidPatternException {
		super(pattern, dirOnly);
		slash = getPathSeparator(pathSeparator);
		beginning = pattern.indexOf(slash) == 0;
		if (isSimplePathWithSegments(pattern))
			matchers = null;
		else
			matchers = createMatchers(split(pattern, slash), pathSeparator,
					dirOnly);
	}

	private boolean isSimplePathWithSegments(String path) {
		return !isWildCard(path) && path.indexOf('\\') < 0
				&& count(path, slash, true) > 0;
	}

	private static List<IMatcher> createMatchers(List<String> segments,
			Character pathSeparator, boolean dirOnly)
			throws InvalidPatternException {
		List<IMatcher> matchers = new ArrayList<>(segments.size());
		for (int i = 0; i < segments.size(); i++) {
			String segment = segments.get(i);
			IMatcher matcher = createNameMatcher0(segment, pathSeparator,
					dirOnly);
			if (matcher == WILD && i > 0
					&& matchers.get(matchers.size() - 1) == WILD)
				// collapse wildmatchers **/** is same as **
				continue;
			matchers.add(matcher);
		}
		return matchers;
	}

	/**
	 *
	 * @param pattern
	 * @param pathSeparator
	 *            if this parameter isn't null then this character will not
	 *            match at wildcards(* and ? are wildcards).
	 * @param dirOnly
	 * @return never null
	 * @throws InvalidPatternException
	 */
	public static IMatcher createPathMatcher(String pattern,
			Character pathSeparator, boolean dirOnly)
			throws InvalidPatternException {
		pattern = trim(pattern);
		char slash = Strings.getPathSeparator(pathSeparator);
		// ignore possible leading and trailing slash
		int slashIdx = pattern.indexOf(slash, 1);
		if (slashIdx > 0 && slashIdx < pattern.length() - 1)
			return new PathMatcher(pattern, pathSeparator, dirOnly);
		return createNameMatcher0(pattern, pathSeparator, dirOnly);
	}

	/**
	 * Trim trailing spaces, unless they are escaped with backslash, see
	 * https://www.kernel.org/pub/software/scm/git/docs/gitignore.html
	 *
	 * @param pattern
	 *            non null
	 * @return trimmed pattern
	 */
	private static String trim(String pattern) {
		while (pattern.length() > 0
				&& pattern.charAt(pattern.length() - 1) == ' ') {
			if (pattern.length() > 1
					&& pattern.charAt(pattern.length() - 2) == '\\') {
				// last space was escaped by backslash: remove backslash and
				// keep space
				pattern = pattern.substring(0, pattern.length() - 2) + " "; //$NON-NLS-1$
				return pattern;
			}
			pattern = pattern.substring(0, pattern.length() - 1);
		}
		return pattern;
	}

	private static IMatcher createNameMatcher0(String segment,
			Character pathSeparator, boolean dirOnly)
			throws InvalidPatternException {
		// check if we see /** or ** segments => double star pattern
		if (WildMatcher.WILDMATCH.equals(segment)
				|| WildMatcher.WILDMATCH2.equals(segment))
			return WILD;

		PatternState state = checkWildCards(segment);
		switch (state) {
		case LEADING_ASTERISK_ONLY:
			return new LeadingAsteriskMatcher(segment, pathSeparator, dirOnly);
		case TRAILING_ASTERISK_ONLY:
			return new TrailingAsteriskMatcher(segment, pathSeparator, dirOnly);
		case COMPLEX:
			return new WildCardMatcher(segment, pathSeparator, dirOnly);
		default:
			return new NameMatcher(segment, pathSeparator, dirOnly, true);
		}
	}

	@Override
	public boolean matches(String path, boolean assumeDirectory,
			boolean pathMatch) {
		if (matchers == null) {
			return simpleMatch(path, assumeDirectory, pathMatch);
		}
		return iterate(path, 0, path.length(), assumeDirectory, pathMatch);
	}

	/*
	 * Stupid but fast string comparison: the case where we don't have to match
	 * wildcards or single segments (mean: this is multi-segment path which must
	 * be at the beginning of the another string)
	 */
	private boolean simpleMatch(String path, boolean assumeDirectory,
			boolean pathMatch) {
		boolean hasSlash = path.indexOf(slash) == 0;
		if (beginning && !hasSlash) {
			path = slash + path;
		}
		if (!beginning && hasSlash) {
			path = path.substring(1);
		}
		if (path.equals(pattern)) {
			// Exact match: must meet directory expectations
			return !dirOnly || assumeDirectory;
		}
		/*
		 * Add slashes for startsWith check. This avoids matching e.g.
		 * "/src/new" to /src/newfile" but allows "/src/new" to match
		 * "/src/new/newfile", as is the git standard
		 */
		String prefix = pattern + slash;
		if (pathMatch) {
			return path.equals(prefix) && (!dirOnly || assumeDirectory);
		}
		if (path.startsWith(prefix)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean matches(String segment, int startIncl, int endExcl,
			boolean assumeDirectory) {
		throw new UnsupportedOperationException(
				"Path matcher works only on entire paths"); //$NON-NLS-1$
	}

	private boolean iterate(final String path, final int startIncl,
			final int endExcl, boolean assumeDirectory, boolean pathMatch) {
		int matcher = 0;
		int right = startIncl;
		boolean match = false;
		int lastWildmatch = -1;
		// ** matches may get extended if a later match fails. When that
		// happens, we must extend the ** by exactly one segment.
		// wildmatchBacktrackPos records the end of the segment after a **
		// match, so that we can reset correctly.
		int wildmatchBacktrackPos = -1;
		while (true) {
			int left = right;
			right = path.indexOf(slash, right);
			if (right == -1) {
				if (left < endExcl) {
					match = matches(matcher, path, left, endExcl,
							assumeDirectory);
				} else {
					// a/** should not match a/ or a
					match = match && matchers.get(matcher) != WILD;
				}
				if (match) {
					if (matcher < matchers.size() - 1
							&& matchers.get(matcher) == WILD) {
						// ** can match *nothing*: a/**/b match also a/b
						matcher++;
						match = matches(matcher, path, left, endExcl,
								assumeDirectory);
					} else if (dirOnly && !assumeDirectory) {
						// Directory expectations not met
						return false;
					}
				}
				return match && matcher + 1 == matchers.size();
			}
			if (wildmatchBacktrackPos < 0) {
				wildmatchBacktrackPos = right;
			}
			if (right - left > 0) {
				match = matches(matcher, path, left, right, assumeDirectory);
			} else {
				// path starts with slash???
				right++;
				continue;
			}
			if (match) {
				boolean wasWild = matchers.get(matcher) == WILD;
				int previousWildmatch = lastWildmatch;
				int previousBacktrackPos = wildmatchBacktrackPos;
				if (wasWild) {
					lastWildmatch = matcher;
					wildmatchBacktrackPos = -1;
					// ** can match *nothing*: a/**/b match also a/b
					right = left - 1;
				}
				matcher++;
				if (matcher == matchers.size()) {
					// We had a prefix match here.
					if (!pathMatch) {
						return true;
					} else {
						if (right == endExcl - 1) {
							// Extra slash at the end: actually a full match.
							// Must meet directory expectations
							return !dirOnly || assumeDirectory;
						}
						// Prefix matches only if pattern ended with /**
						if (wasWild) {
							return true;
						}
						if (previousWildmatch >= 0) {
							// Consider pattern **/x and input x/x.
							// We've matched the prefix x/ so far: we
							// must try to extend the **!
							matcher = previousWildmatch + 1;
							right = previousBacktrackPos;
							wildmatchBacktrackPos = -1;
						} else {
							return false;
						}
					}
				}
			} else if (lastWildmatch != -1) {
				matcher = lastWildmatch + 1;
				right = wildmatchBacktrackPos;
				wildmatchBacktrackPos = -1;
			} else {
				return false;
			}
			right++;
		}
	}

	private boolean matches(int matcherIdx, String path, int startIncl,
			int endExcl,
			boolean assumeDirectory) {
		IMatcher matcher = matchers.get(matcherIdx);
		return matcher.matches(path, startIncl, endExcl, assumeDirectory);
	}
}
