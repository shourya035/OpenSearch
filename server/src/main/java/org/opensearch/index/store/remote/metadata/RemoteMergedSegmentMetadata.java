/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.index.store.remote.metadata;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.index.store.RemoteSegmentStoreDirectory.UploadedSegmentMetadata;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@ExperimentalApi
public class RemoteMergedSegmentMetadata {
    long primaryTerm;
    List<UploadedSegmentMetadata> uploadedSegmentsMetadata;
    String mergedSegmentId;
    String allocationId;

    public RemoteMergedSegmentMetadata(
        long primaryTerm,
        List<UploadedSegmentMetadata> uploadedSegmentMetadata,
        String allocationId,
        String mergedSegmentId
    ) {
        this.primaryTerm = primaryTerm;
        this.uploadedSegmentsMetadata = uploadedSegmentMetadata;
        this.allocationId = allocationId;
        this.mergedSegmentId = mergedSegmentId;
    }

    public void write(IndexOutput out) throws IOException {
        out.writeLong(primaryTerm);
        out.writeString(mergedSegmentId);
        out.writeString(allocationId);
        writeUploadedSegmentsMdToIndexOutput(uploadedSegmentsMetadata, out);
    }

    public static RemoteMergedSegmentMetadata read(IndexInput indexInput) throws IOException {
        long primaryTerm = indexInput.readLong();
        String mergedSegmentId = indexInput.readString();
        String allocationId = indexInput.readString();
        List<UploadedSegmentMetadata> uploadedSegmentsMetadata = readUploadedSegmentMd(indexInput);
        return new RemoteMergedSegmentMetadata(primaryTerm, uploadedSegmentsMetadata, allocationId, mergedSegmentId);
    }

    private static void writeUploadedSegmentsMdToIndexOutput(List<UploadedSegmentMetadata> uploadedSegmentMetadata, IndexOutput out) throws IOException {
        List<String> segmentMetadataToListOfString = uploadedSegmentMetadata.stream().map(UploadedSegmentMetadata::toString).toList();
        out.writeSetOfStrings(Set.copyOf(segmentMetadataToListOfString));
    }

    private static List<UploadedSegmentMetadata> readUploadedSegmentMd(IndexInput indexInput) throws IOException {
        return indexInput.readSetOfStrings().stream().map(UploadedSegmentMetadata::fromString).toList();
    }

    public long getPrimaryTerm() {
        return primaryTerm;
    }

    public List<UploadedSegmentMetadata> getUploadedSegmentsMetadata() {
        return uploadedSegmentsMetadata;
    }

    public String getAllocationId() {
        return allocationId;
    }

    public String getMergedSegmentId() {
        return mergedSegmentId;
    }
}
