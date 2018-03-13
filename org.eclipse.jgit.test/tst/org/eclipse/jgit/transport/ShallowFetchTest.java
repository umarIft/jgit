package org.eclipse.jgit.transport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UploadPack.RequestPolicy;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for shallow fetch related transport test.
 */
public class ShallowFetchTest {
	private URIish uri;

	private TestProtocol<Object> testProtocol;

	private Object ctx = new Object();

	private InMemoryRepository server;

	private InMemoryRepository client;

	private RevCommit commit0;

	private RevCommit commit1;

	private RevCommit tip;

	@Before
	public void setUp() throws Exception {
		server = newRepo("server");
		client = newRepo("client");
		testProtocol = new TestProtocol<>(
				new UploadPackFactory<Object>() {
					@Override
					public UploadPack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = new UploadPack(db);
						up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);
						return up;
					}
				}, null);
		uri = testProtocol.register(ctx, server);

		TestRepository<InMemoryRepository> remote =
				new TestRepository<>(server);
		commit0 = remote.commit().message("0").create();
		commit1 = remote.commit().message("1").parent(commit0).create();
		tip = remote.commit().message("2").parent(commit1).create();
		remote.update("master", tip);
	}

	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	@Test
	public void testShallow() throws Exception {
		// Create a local shallow commit for commit1
		try (ObjectInserter ins = client.newObjectInserter()) {
			byte[] data = commit1.getRawBuffer();
			ins.insert(Constants.OBJ_COMMIT, data);
			ins.flush();
		}
		assertFalse(client.hasObject(commit0.toObjectId()));
	}

	@Test
	public void testFetchParentOfShallow() throws Exception {
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit0.name())));
			assertTrue(client.hasObject(commit0.toObjectId()));
		}
	}
}
