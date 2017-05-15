/*
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

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.testutils.CommonTestUtils;
import org.apache.flink.metrics.groups.UnregisteredMetricsGroup;
import org.apache.flink.runtime.executiongraph.ExecutionGraph;
import org.apache.flink.runtime.executiongraph.ExecutionGraphBuilder;
import org.apache.flink.runtime.executiongraph.restart.NoRestartStrategy;
import org.apache.flink.runtime.instance.SlotProvider;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.tasks.ExternalizedCheckpointSettings;
import org.apache.flink.runtime.jobgraph.tasks.JobCheckpointingSettings;
import org.apache.flink.runtime.testingUtils.TestingUtils;
import org.apache.flink.util.SerializedValue;
import org.apache.flink.util.TestLogger;

import org.junit.Test;

import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This test validates that the checkpoint settings serialize correctly
 * in the presence of user-defined objects.
 */
public class CheckpointSettingsSerializableTest extends TestLogger {

	@Test
	public void testClassLoaderForCheckpointHooks() throws Exception {
		final ClassLoader classLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
		final Serializable outOfClassPath = CommonTestUtils.createObjectForClassNotInClassPath(classLoader);

		final MasterTriggerRestoreHook.Factory[] hooks = {
				new TestFactory(outOfClassPath) };
		final SerializedValue<MasterTriggerRestoreHook.Factory[]> serHooks = new SerializedValue<>(hooks);

		final JobCheckpointingSettings checkpointingSettings = new JobCheckpointingSettings(
				Collections.<JobVertexID>emptyList(),
				Collections.<JobVertexID>emptyList(),
				Collections.<JobVertexID>emptyList(),
				1000L,
				10000L,
				0L,
				1,
				ExternalizedCheckpointSettings.none(),
				null,
				serHooks,
				true);

		final JobGraph jobGraph = new JobGraph(new JobID(), "test job");
		jobGraph.setSnapshotSettings(checkpointingSettings);

		// to serialize/deserialize the job graph to see if the behavior is correct under
		// distributed execution
		final JobGraph copy = CommonTestUtils.createCopySerializable(jobGraph);

		final ExecutionGraph eg = ExecutionGraphBuilder.buildGraph(
				null,
				copy,
				new Configuration(),
				TestingUtils.defaultExecutor(),
				TestingUtils.defaultExecutor(),
				mock(SlotProvider.class),
				classLoader,
				new StandaloneCheckpointRecoveryFactory(),
				Time.seconds(10),
				new NoRestartStrategy(),
				new UnregisteredMetricsGroup(),
				10,
				log);

		assertEquals(1, eg.getCheckpointCoordinator().getNumberOfRegisteredMasterHooks());
	}

	// ------------------------------------------------------------------------

	private static final class TestFactory implements MasterTriggerRestoreHook.Factory {

		private static final long serialVersionUID = -612969579110202607L;
		
		private final Serializable payload;

		TestFactory(Serializable payload) {
			this.payload = payload;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <V> MasterTriggerRestoreHook<V> create() {
			MasterTriggerRestoreHook<V> hook = mock(MasterTriggerRestoreHook.class);
			when(hook.getIdentifier()).thenReturn("id");
			return hook;
		}
	}
}
