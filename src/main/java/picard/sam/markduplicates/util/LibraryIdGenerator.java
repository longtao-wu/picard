/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package picard.sam.markduplicates.util;

import htsjdk.samtools.ReservedTagConstants;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.Histogram;
import picard.sam.DuplicationMetrics;
import picard.sam.DuplicationMetricsFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A class to generate library Ids and keep duplication metrics by library IDs.
 *
 * @author nhomer
 */
public class LibraryIdGenerator {

    private static final String UNKNOWN_LIBRARY = "Unknown Library";
   
    private final SAMFileHeader header;
    private final Map<String, Short> libraryIds = new HashMap<String, Short>(); // from library string to library id
    private short nextLibraryId = 1;
    private final Map<String, DuplicationMetrics> metricsByLibrary = new TreeMap<>();
    private final Histogram<Short> opticalDuplicatesByLibraryId = new Histogram<>();
    private final Histogram<Double> duplicateCountHist = new Histogram<>("set_size", "all_sets");
    private final Histogram<Double> nonOpticalDuplicateCountHist = new Histogram<>("set_size", "non_optical_sets");
    private final Histogram<Double> opticalDuplicateCountHist = new Histogram<>("set_size", "optical_sets");

    public LibraryIdGenerator(final SAMFileHeader header) {
        this(header, false);
    }

    public LibraryIdGenerator(final SAMFileHeader header, final boolean flowMetrics) {
        this.header = header;

        for (final SAMReadGroupRecord readGroup : header.getReadGroups()) {
            final String library = LibraryIdGenerator.getReadGroupLibraryName(readGroup);
            DuplicationMetrics metrics = metricsByLibrary.get(library);
            if (metrics == null) {
                metrics = DuplicationMetricsFactory.createMetrics(flowMetrics);
                metrics.LIBRARY = library;
                metricsByLibrary.put(library, metrics);
            }
        }
    }

    public Map<String, Short> getLibraryIdsMap() {
        return this.libraryIds;
    }

    public Map<String, DuplicationMetrics> getMetricsByLibraryMap() {
        return this.metricsByLibrary;
    }

    public Histogram<Short> getOpticalDuplicatesByLibraryIdMap() {
        return this.opticalDuplicatesByLibraryId;
    }

    public Histogram<Double> getDuplicateCountHist() {
        return this.duplicateCountHist;
    }

    public Histogram<Double> getNonOpticalDuplicateCountHist() {
        return this.nonOpticalDuplicateCountHist;
    }

    public Histogram<Double> getOpticalDuplicateCountHist() {
        return this.opticalDuplicateCountHist;
    }

    public static String getReadGroupLibraryName(final SAMReadGroupRecord readGroup) {
        return Optional.ofNullable(readGroup.getLibrary())
                .orElse(UNKNOWN_LIBRARY);
    }
   
    /**
     * Gets the library name from the header for the record. If the RG tag is not present on
     * the record, or the library isn't denoted on the read group, a constant string is
     * returned.
     */
    public static String getLibraryName(final SAMFileHeader header, final SAMRecord rec) {
        final String readGroupId = (String) rec.getAttribute(ReservedTagConstants.READ_GROUP_ID);

        if (readGroupId != null) {
            final SAMReadGroupRecord rg = header.getReadGroup(readGroupId);
            if (rg != null) {
                final String libraryName = rg.getLibrary();
                if (null != libraryName) return libraryName;
            }
        }

        return UNKNOWN_LIBRARY;
    }

    /** Get the library ID for the given SAM record. */
    public short getLibraryId(final SAMRecord rec) {
        final String library = getLibraryName(this.header, rec);
        Short libraryId = this.libraryIds.get(library);

        if (libraryId == null) {
            libraryId = this.nextLibraryId++;
            this.libraryIds.put(library, libraryId);
        }

        return libraryId;
    }

    public DuplicationMetrics getMetricsByLibrary(final String library) {
        return this.metricsByLibrary.get(library);
    }

    public void addMetricsByLibrary(final String library, final DuplicationMetrics metrics) {
        this.metricsByLibrary.put(library, metrics);
    }

    public long getNumberOfOpticalDuplicateClusters() {
        return (long) this.opticalDuplicatesByLibraryId.getSumOfValues();
    }
}
