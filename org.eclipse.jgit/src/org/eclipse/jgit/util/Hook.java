package org.eclipse.jgit.util;

/**
 * An enum describing the different hooks a user can implement to customize his
 * repositories.
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
	 * A non-zero return from the called hook means that the commit should be
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
	 * A non-zero return from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	PREPARE_COMMIT_MSG("prepare-commit-msg"), //$NON-NLS-1$

	/**
	 * Literal for the "commit-msg" git hook.
	 * <p>
	 * This hook is invoked by git commit, and can be bypassed with the
	 * "no-verify" option. Its single parameter is the path to the file
	 * containing the prepared commit message (typically
	 * "&lt;gitdir>/COMMIT-EDITMSG").
	 * </p>
	 * <p>
	 * A non-zero return from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	COMMIT_MSG("commit-msg"), //$NON-NLS-1$

	/**
	 * Literal for the "post-commit" git hook.
	 * <p>
	 * This hook is invoked by git commit. It takes no parameter and is invoked
	 * after a commit has been made.
	 * </p>
	 * <p>
	 * The return value of this hook has no significance.
	 * </p>
	 */
	POST_COMMIT("post-commit"); //$NON-NLS-1$

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
