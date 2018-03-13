/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohnk@sap.com>
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
package org.eclipse.jgit.lfs.server;

import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lfs.errors.InvalidLongObjectIdException;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.LargeObjectRepository;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;

/**
 * Servlet supporting upload and download of large objects as defined by the
 * GitHub Large File Storage extension API extending git to allow separate
 * storage of large files
 * (https://github.com/github/git-lfs/tree/master/docs/api).
 *
 * @since 4.1
 */
@WebServlet(name = "LargeObjectServlet", urlPatterns = {
		"/lfs/objects/*" }, asyncSupported = true)
public class LargeObjectServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private LargeObjectRepository repository;

	private long timeout;

	/**
	 * @param repository
	 *            the repository storing the large objects
	 * @param timeout
	 *            timeout for object upload / download in milliseconds
	 */
	public LargeObjectServlet(LargeObjectRepository repository, long timeout) {
		this.repository = repository;
		this.timeout = timeout;
	}

	/**
	 * Handles object downloads
	 *
	 * @param request
	 *            servlet request
	 * @param response
	 *            servlet response
	 * @throws ServletException
	 *             if a servlet-specific error occurs
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		AnyLongObjectId obj = getObjectToTransfer(request, response);
		if (obj != null && exists(response, obj)) {
			AsyncContext context = request.startAsync();
			context.setTimeout(timeout);
			response.getOutputStream()
					.setWriteListener(new ObjectDownloadListener(repository,
							context, response, obj));
		}
	}

	private AnyLongObjectId getObjectToTransfer(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		String info = request.getPathInfo();
		if (info.length() != 65) {
			response.sendError(HttpStatus.SC_BAD_REQUEST, MessageFormat
					.format(LfsServerText.get().invalidPathInfo, info));
			return null;
		}
		try {
			return LongObjectId.fromString(info.substring(1, 65));
		} catch (InvalidLongObjectIdException e) {
			response.sendError(HttpStatus.SC_BAD_REQUEST, e.getMessage());
			return null;
		}
	}

	private boolean exists(HttpServletResponse response, AnyLongObjectId obj)
			throws IOException {
		if (!repository.exists(obj)) {
			response.sendError(HttpStatus.SC_NOT_FOUND, MessageFormat
					.format(LfsServerText.get().objectNotFound, obj));
			return false;
		}
		return true;
	}

	/**
	 * Handle object uploads
	 *
	 * @param request
	 *            servlet request
	 * @param response
	 *            servlet response
	 * @throws ServletException
	 *             if a servlet-specific error occurs
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	protected void doPut(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		AnyLongObjectId id = getObjectToTransfer(request, response);
		if (id != null) {
			AsyncContext context = request.startAsync();
			context.setTimeout(timeout);
			request.getInputStream().setReadListener(new ObjectUploadListener(
					repository, context, request, response, id));
		}
	}
}
