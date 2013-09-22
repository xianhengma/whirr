/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.whirr.cli.command;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.whirr.Cluster;
import org.apache.whirr.ClusterController;
import org.apache.whirr.ClusterControllerFactory;
import org.apache.whirr.ClusterSpec;
import org.apache.whirr.state.ClusterStateStore;
import org.apache.whirr.state.ClusterStateStoreFactory;
import org.apache.whirr.state.MemoryClusterStateStore;
import org.apache.whirr.util.KeyPair;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.domain.Credentials;
import org.jclouds.scriptbuilder.domain.Statement;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.jclouds.compute.predicates.NodePredicates.withIds;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RunCmdCommandTest extends BaseCommandTest {

    @Test
    public void testCmdPathIsMandatory() throws Exception {
        RunCmdCommand command = new RunCmdCommand();

        int rc = command.run(null, out, err, Lists.<String>newArrayList());
        assertThat(rc, is(-1));

        assertThat(errBytes.toString(),
                containsString("Please specify the command to be executed."));
    }

    @Test
    public void testRunCmdByInstanceId() throws Exception {
        ClusterControllerFactory factory = mock(ClusterControllerFactory.class);
        ClusterController controller = mock(ClusterController.class);

        when(factory.create((String)any())).thenReturn(controller);

        RunCmdCommand command = new RunCmdCommand(factory);
        Map<String, File> keys = KeyPair.generateTemporaryFiles();

        int rc = command.run(null, out, System.err, Lists.newArrayList(
                "--command", "ls -la",
                "--instance-templates", "1 noop",
                "--instances", "A,B",
                "--cluster-name", "test-cluster",
                "--provider", "provider",
                "--identity", "myusername", "--credential", "mypassword",
                "--private-key-file", keys.get("private").getAbsolutePath()
        ));
        assertThat(rc, is(0));

        ArgumentCaptor<Predicate> predicate = ArgumentCaptor.forClass(Predicate.class);
        verify(controller).runScriptOnNodesMatching(
                (ClusterSpec) any(), predicate.capture(), (Statement) any());

        // check predicate equality by using the object string representation

        Predicate<NodeMetadata> expected = Predicates.and(
                Predicates.<NodeMetadata>alwaysTrue(), withIds("A", "B"));
        assertThat(predicate.getValue().toString(), is(expected.toString()));
    }

    @Test
    public void testRunCmdByRole() throws Exception {
        ClusterControllerFactory factory = mock(ClusterControllerFactory.class);
        ClusterController controller = mock(ClusterController.class);
        when(factory.create((String)any())).thenReturn(controller);

        ClusterStateStore memStore = new MemoryClusterStateStore();
        memStore.save(createTestCluster(
                new String[]{"reg/A", "reg/B"}, new String[]{"A", "B"}));

        ClusterStateStoreFactory stateStoreFactory = mock(ClusterStateStoreFactory.class);
        when(stateStoreFactory.create((ClusterSpec) any())).thenReturn(memStore);

        RunCmdCommand command = new RunCmdCommand(factory, stateStoreFactory);
        Map<String, File> keys = KeyPair.generateTemporaryFiles();

        int rc = command.run(null, out, System.err, Lists.newArrayList(
                "--instance-templates", "1 noop",
                "--command", "ls -la",
                "--roles", "A",
                "--cluster-name", "test-cluster",
                "--provider", "provider",
                "--identity", "myusername", "--credential", "mypassword",
                "--private-key-file", keys.get("private").getAbsolutePath()
        ));
        assertThat(rc, is(0));

        ArgumentCaptor<Predicate> predicate = ArgumentCaptor.forClass(Predicate.class);
        verify(controller).runScriptOnNodesMatching(
                (ClusterSpec)any(), predicate.capture(), (Statement) any());

        // check predicate equality by using the object string representation

        Predicate<NodeMetadata> expected = Predicates.and(
                Predicates.<NodeMetadata>alwaysTrue(), withIds("reg/A"));
        assertThat(predicate.getValue().toString(), is(expected.toString()));

    }

    private Cluster createTestCluster(String[] ids, String[] roles) {
        checkArgument(ids.length == roles.length, "each ID should have a role");

        Credentials credentials = new Credentials("dummy", "dummy");
        Set<Cluster.Instance> instances = Sets.newHashSet();

        for(int i = 0; i < ids.length; i++) {
            String ip = "127.0.0." + (i + 1);
            instances.add(new Cluster.Instance(credentials,
                    Sets.newHashSet(roles[i]), ip, ip, ids[i], null));
        }

        return new Cluster(instances);
    }

}
