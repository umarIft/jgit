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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A singly-linked list with specific support for long-lived windows/iterators
 * that reflect updates to the list in front of the window, so that updates to
 * the list after the window's position are visible to the window after the
 * window is created. This class is required because windows found in
 * java.util.concurrent.* do not guarantee to reflect updates to the list after
 * construction.
 *
 * Nodes in the linked-list support a blocking {@link Node#next(long, TimeUnit)}
 * call until the next node is added.
 */
public class PublisherStream {
	private static abstract class Node {
		protected abstract PublisherPush get();

		protected abstract Node next(long time, TimeUnit unit)
				throws InterruptedException;

		protected abstract Node next();

		protected abstract void decrement() throws PublisherException;
	}

	private static class DataNode extends Node {
		private final CountDownLatch nextSet = new CountDownLatch(1);

		private final PublisherPush data;

		private volatile Node next;

		private final AtomicInteger refCount = new AtomicInteger();

		private DataNode(PublisherPush p, int count) {
			data = p;
			refCount.set(count);
		}

		@Override
		protected PublisherPush get() {
			return data;
		}

		/**
		 * Wait for {@code time} for the {@link #next} pointer to be populated.
		 *
		 * @param time
		 * @param unit
		 * @return the next item, or null if there is no next item and the call
		 *         timed out.
		 * @throws InterruptedException
		 */
		@Override
		protected Node next(long time, TimeUnit unit)
				throws InterruptedException {
			if (next != null)
				return next;
			nextSet.await(time, unit);
			return next;
		}

		@Override
		protected Node next() {
			return next;
		}

		protected void setNext(Node n) {
			if (n == null)
				throw new NullPointerException();
			next = n;
			nextSet.countDown();
		}

		@Override
		protected void decrement() throws PublisherException {
			if (refCount.decrementAndGet() == 0) {
				if (data != null)
					data.close();
			}
		}

		@Override
		public String toString() {
			return "Node[" + data + ", " + refCount.get() + "]";
		}
	}

	/**
	 * A node that only returns the delegate node as the next node.
	 */
	private static class ForwardingNode extends Node {
		private final Node delegate;

		private ForwardingNode(Node delegate) {
			this.delegate = delegate;
		}

		@Override
		protected PublisherPush get() {
			return null;
		}

		@Override
		protected Node next(long time, TimeUnit unit)
				throws InterruptedException {
			return delegate;
		}

		@Override
		protected Node next() {
			return delegate;
		}

		@Override
		protected void decrement() throws PublisherException {
			// Nothing
		}
	}

	/**
	 * A node with a different next pointer. Used for re-linking nodes in the
	 * rollback queue without affecting the underlying node next pointers.
	 */
	private static class LinkNode extends DataNode {
		private final Node dataNode;

		private Node nextNode;

		public LinkNode(Node data) {
			super(data.get(), 0);
			dataNode = data;
		}

		@Override
		protected PublisherPush get() {
			return dataNode.get();
		}

		@Override
		protected void setNext(Node n) {
			nextNode = n;
		}

		@Override
		protected Node next(long time, TimeUnit unit)
				throws InterruptedException {
			return nextNode;
		}

		@Override
		protected Node next() {
			return nextNode;
		}

		@Override
		protected void decrement() throws PublisherException {
			dataNode.decrement();
		}

		@Override
		public int hashCode() {
			return dataNode.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return dataNode.equals(obj);
		}
	}

	/**
	 * A window into the stream of {@link PublisherPush}es.
	 *
	 * <pre>
	 * Window window = stream.newWindow(2);
	 * window.prepend(new PublisherPush("pack-prepend"));
	 * PublisherPush prependedPush = window.next(1, TimeUnit.SECONDS);
	 * if (push == null) { timed out, no next node found }
	 * else { window.mark(); push...; }
	 * window.rollback("pack-prepend");
	 * PublisherPush packAfterPrepend = window.next(...);
	 * </pre>
	 */
	public abstract static class Window {
		private Node current;

		private final int markCapacity;

		private final List<Node> marked;

		private Window(final Node n, int capacity) {
			// Prepend one node that will skip over n during traversal
			current = new Node() {
				@Override
				protected PublisherPush get() {
					return null;
				}

				@Override
				protected Node next(long time, TimeUnit unit)
						throws InterruptedException {
					return n.next(time, unit);
				}

				@Override
				protected Node next() {
					return n.next();
				}

				@Override
				protected void decrement() throws PublisherException {
					// Nothing
				}
			};
			markCapacity = capacity;
			marked = new ArrayList<Node>(capacity + 1);
		}

		/**
		 * @param time
		 *            the time to block until the next item is added.
		 * @param unit
		 *            units for the time argument.
		 * @return the next item, blocking until one exists. Return null if the
		 *         call timed out before the next item was added.
		 * @throws InterruptedException
		 * @throws PublisherException
		 */
		PublisherPush next(long time, TimeUnit unit)
				throws InterruptedException, PublisherException {
			Node n;
			do {
				n = current.next(time, unit);
				if (n == null)
					return null; // timeout
				if (!marked.contains(current))
					current.decrement();
				current = n;
			} while (n.get() == null);
			return n.get();
		}

		/**
		 * Store the current item so the window's position can be reset to the
		 * current position by calling {@link #rollback(String)}. If the stream
		 * passes an item without marking it, that item's reference count will
		 * be decremented, so
		 *
		 * @throws PublisherException
		 */
		public void mark() throws PublisherException {
			marked.add(current);
			if (marked.size() > markCapacity) {
				Node n = marked.remove(0);
				n.decrement();
			}
		}

		/**
		 * Reset the current pointer to after the item's position.
		 *
		 * @param pushId
		 *            rollback to the item after the item with this pushId. If
		 *            pushId is null, rollback as far as possible.
		 * @return true if the stream was successfully rolled back so that the
		 *         next call to {@link #next(long, TimeUnit)} will return the
		 *         next item.
		 * @throws PublisherException
		 */
		public boolean rollback(String pushId) throws PublisherException {
			if (marked.size() == 0)
				return false;

			boolean matched = false;
			LinkNode prevNode = null;
			Node lastNode = current;
			for (Iterator<Node> it = marked.iterator(); it.hasNext(); ) {
				Node dataNode = it.next();
				if (matched) {
					it.remove();
					if (dataNode != lastNode) {
						// The last node in the marked list may be the current
						// node as well; don't add it twice
						LinkNode newNode = new LinkNode(dataNode);
						prevNode.setNext(newNode);
						prevNode = newNode;
					}
				} else if (dataNode.get().getPushId() == pushId
						|| pushId == null) {
					if (dataNode == lastNode)
						return true; // Current node is correct
					prevNode = new LinkNode(dataNode);
					current = prevNode;
					matched = true;
				}
			}
			if (prevNode != null)
				prevNode.setNext(lastNode); // Point back into current stream
			return matched;
		}

		/**
		 * Prepend an item to only this window. This should only be used before
		 * any calls to {@link #next(long, TimeUnit)}, or else
		 * {@link #rollback(String)} will not work properly.
		 *
		 * @param item
		 */
		public void prepend(PublisherPush item) {
			DataNode newNode = new DataNode(item, 1);
			newNode.setNext(current);
			current = new ForwardingNode(newNode);
		}

		/**
		 * Delete this window, and decrement all reference counts.
		 *
		 * @param last
		 * @throws PublisherException
		 */
		protected void delete(Node last) throws PublisherException {
			Node prev = null;
			for (Node n : marked) {
				prev = n;
				n.decrement();
			}
			// The current node may be the last item in the marked list; only
			// decrement once.
			if (prev == current)
				current = current.next();

			while (current != null) {
				current.decrement();
				if (current == last)
					break;
				current = current.next();
			}
			current = null;
		}

		/**
		 * Release all resources used by this window.
		 *
		 * @throws PublisherException
		 */
		abstract public void release() throws PublisherException;
	}

	private DataNode tail = new DataNode(null, 0);

	private int windowCount;

	/**
	 * @param item
	 *            to add to the end of the list.
	 * @throws PublisherException
	 */
	public synchronized void add(PublisherPush item) throws PublisherException {
		boolean hasWindows;
		synchronized (this) {
			hasWindows = windowCount != 0;
			if (hasWindows) {
				DataNode prev = tail;
				tail = new DataNode(item, windowCount);
				prev.setNext(tail);
			}
		}
		if (!hasWindows)
			item.close();
	}

	/**
	 * @param markCapacity
	 *            the number of marks to keep when calling
	 *            {@link Window#mark()}.
	 * @return a window that traverses this list starting after the tail,
	 *         even if new nodes are added.
	 */
	public synchronized Window newWindow(int markCapacity) {
		windowCount++;
		return new Window(tail, markCapacity) {
			@Override
			public void release() throws PublisherException {
				Node last;
				synchronized (PublisherStream.this) {
					windowCount--;
					last = tail;
				}
				delete(last);
			}
		};
	}
}
