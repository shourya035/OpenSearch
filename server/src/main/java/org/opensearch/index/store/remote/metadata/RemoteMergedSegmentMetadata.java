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

import java.io.IOException;
import java.util.Set;

@ExperimentalApi
public class RemoteMergedSegmentMetadata {
    long primaryTerm;
    Set<String> files;
    String mergedSegmentId;
    String allocationId;

    RemoteMergedSegmentMetadata(
        long primaryTerm,
        Set<String> files,
        String allocationId,
        String mergedSegmentId
    ) {
        this.primaryTerm = primaryTerm;
        this.files = files;
        this.allocationId = allocationId;
        this.mergedSegmentId = mergedSegmentId;
    }

    public void write(IndexOutput out) throws IOException {
        out.writeLong(primaryTerm);
        out.writeSetOfStrings(files);
        out.writeString(mergedSegmentId);
        out.writeString(allocationId);
    }

    public RemoteMergedSegmentMetadata read(IndexInput indexInput) throws IOException {
        long primaryTerm = indexInput.readLong();
        Set<String> files = indexInput.readSetOfStrings();
        String mergedSegmentId = indexInput.readString();
        String allocationId = indexInput.readString();
        return new RemoteMergedSegmentMetadata(primaryTerm, files, allocationId, mergedSegmentId);
    }

    public long getPrimaryTerm() {
        return primaryTerm;
    }

    public Set<String> getFiles() {
        return files;
    }

    public String getAllocationId() {
        return allocationId;
    }

    public String getMergedSegmentId() {
        return mergedSegmentId;
    }

    public static Builder builder() {
        return new Builder();
    }

    @ExperimentalApi
    public static class Builder {
        private long primaryTerm;
        private Set<String> files;
        private String mergedSegmentId;
        private String allocationId;

        public Builder primaryTerm(long primaryTerm) {
            this.primaryTerm = primaryTerm;
            return this;
        }

        public Builder files(Set<String> files) {
            this.files = files;
            return this;
        }

        public Builder mergedSegmentId(String mergedSegmentId) {
            this.mergedSegmentId = mergedSegmentId;
            return this;
        }

        public Builder allocationId(String allocationId) {
            this.allocationId = allocationId;
            return this;
        }

        public RemoteMergedSegmentMetadata build() {
            return new RemoteMergedSegmentMetadata(primaryTerm, files, allocationId, mergedSegmentId);
        }
    }
}
