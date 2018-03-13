package org.eclipse.jgit.util;

/**
 * An enum describing the different hooks a user can implement to customize his
 * repositories.
 * 
 * @since 3.6
 */
public enum Hook {
	/**
	 * Literal for the "pre-commit" git hook.
	 * <p>
	 * This hook is invoked by git commit, and can be bypassed with the
	 * "no-verify" option. It takes no parameter, and is invoked before
	 * obtaining the proposed commit log message and making a commit.
	 * </p>
	 * <p>
	 * A non-zero exit code from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	PRE_COMMIT("pre-commit"), //$NON-NLS-1$

	/**
	 * Literal for the "prepare-commit-msg" git hook.
	 * <p>
	 * This hook is invoked by git commit right after preparing the default
	 * message, and before any editing possibility is displayed to the user.
	 * </p>
	 * <p>
	 * A non-zero exit code from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	PREPARE_COMMIT_MSG("prepare-commit-msg"), //$NON-NLS-1$

	/**
	 * Literal for the "pre-rebase" git hook.
	 * <p>
	 * </p>
	 * This hook is invoked right before the rebase operation runs. It accepts
	 * up to two parameters, the first being the upstream from which the branch
	 * to rebase has been forked. If the tip of the series of commits to rebase
	 * is HEAD, the other parameter is unset. Otherwise, that tip is passed as
	 * the second parameter of the script.
	 * <p>
	 * A non-zero exit code from the called hook means that the rebase should be
	 * aborted.
	 * </p>
	 */
	PRE_REBASE("pre-rebase"); //$NON-NLS-1$

	private final String name;

	private Hook(String name) {
		this.name = name;
	}

	/**
	 * @return The name of this hook.
	 */
	public String getName() {
		return name;
	}
}
