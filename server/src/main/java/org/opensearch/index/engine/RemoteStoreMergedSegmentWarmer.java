/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.index.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.StringHelper;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.index.shard.ReplicationGroup;
import org.opensearch.index.store.RemoteSegmentStoreDirectory;
import org.opensearch.index.store.remote.metadata.RemoteMergedSegmentMetadata;
import org.opensearch.indices.recovery.RecoverySettings;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of a {@link IndexWriter.IndexReaderWarmer} when remote store is enabled.
 *
 * @opensearch.internal
 */
public class RemoteStoreMergedSegmentWarmer implements IndexWriter.IndexReaderWarmer {
    private final Logger logger = LogManager.getLogger(RemoteStoreMergedSegmentWarmer.class);
    private final TransportService transportService;
    private final RecoverySettings recoverySettings;
    private final ClusterService clusterService;
    private final IndexShard indexShard;
    private final Directory storeDirectory;
    private final RemoteSegmentStoreDirectory remoteSegmentStoreDirectory;

    public RemoteStoreMergedSegmentWarmer(
        TransportService transportService,
        RecoverySettings recoverySettings,
        ClusterService clusterService,
        IndexShard indexShard
    ) {
        this.transportService = transportService;
        this.recoverySettings = recoverySettings;
        this.clusterService = clusterService;
        this.indexShard = indexShard;
        this.storeDirectory = indexShard.store().directory();
        this.remoteSegmentStoreDirectory = indexShard.getRemoteDirectory();
    }

    @Override
    public void warm(LeafReader leafReader) throws IOException {
        SegmentCommitInfo segmentCommitInfo = ((SegmentReader) leafReader).getSegmentInfo();
        Collection<String> filesAfterMerge = segmentCommitInfo.files();
        String mergedSegmentId = StringHelper.idToString(segmentCommitInfo.getId());
        // Upload files generated from merge to remote store
        long timeTakenForUpload = uploadNewSegments(filesAfterMerge);
        // Log time taken
        logger.info("Time taken to upload merged segments: {} ms", timeTakenForUpload);
        writeCheckpointsForReplica(filesAfterMerge, mergedSegmentId);

    }

    long uploadNewSegments(Collection<String> localSegmentsPostMerge) {
        ActionListener<Void> aggregatedListener = ActionListener.wrap(resp -> {}, ex -> {
            logger.warn(() -> new ParameterizedMessage("Exception: [{}] while uploading segment files", ex), ex);
            if (ex instanceof CorruptIndexException) {
                indexShard.failShard(ex.getMessage(), ex);
            }
        });
        long startTime = System.currentTimeMillis();
        localSegmentsPostMerge.forEach(src -> {
            logger.debug("Copying over segment {} to remote store", src);
            remoteSegmentStoreDirectory.copyFrom(storeDirectory, src, IOContext.DEFAULT, aggregatedListener, true);
        });
        return System.currentTimeMillis() - startTime;
    }

    void writeCheckpointsForReplica(
        Collection<String> localSegmentsPostMerge,
        String mergedSegmentId
    ) throws IOException {
        long primaryTerm = indexShard.getOperationPrimaryTerm();
        ReplicationGroup replicationGroup = indexShard.getReplicationGroup();
        Set<String> inSyncReplicaAllocationIds = replicationGroup.getInSyncAllocationIds()
            .stream()
            .filter(aId -> replicationGroup.getRoutingTable().primaryShard().allocationId().getId().equals(aId) == false)
            .collect(Collectors.toSet());
        for (String aId : inSyncReplicaAllocationIds) {
            RemoteMergedSegmentMetadata remoteMergedSegmentMetadata = RemoteMergedSegmentMetadata.builder()
                .primaryTerm(primaryTerm)
                .allocationId(aId)
                .files(Set.copyOf(localSegmentsPostMerge))
                .mergedSegmentId(mergedSegmentId)
                .build();
            remoteSegmentStoreDirectory.uploadMergedSegmentMetadata(remoteMergedSegmentMetadata, storeDirectory);
        }
    }
}
