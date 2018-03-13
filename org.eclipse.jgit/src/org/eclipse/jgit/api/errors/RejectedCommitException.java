package org.eclipse.jgit.api.errors;

/**
 * Exception thrown when a commit is rejected by a hook (either pre-commit or
 * commit-msg).
 */
public class RejectedCommitException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 */
	public RejectedCommitException(String message) {
		super(message);
	}
}
