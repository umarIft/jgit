package org.eclipse.jgit.api.errors;

/**
 * Exception thrown when a rebase is rejected by a the pre-rebase hook.
 * 
 * @since 3.6
 */
public class RejectedRebaseException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 */
	public RejectedRebaseException(String message) {
		super(message);
	}
}
