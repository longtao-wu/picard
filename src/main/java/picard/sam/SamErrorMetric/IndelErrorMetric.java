/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
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


package picard.sam.SamErrorMetric;

/**
 * Metric to be used for InDel errors
 */
public class IndelErrorMetric extends BaseErrorMetric {

    /**
     * The number of insertions. Note: This is not the number of bases that have been inserted.
     */
    @MergeByAdding
    public long NUM_INSERTS = 0;

    /**
     * The (phred) rate of insertions. TODO mgatzen Does this make sense?
     */
    @NoMergingIsDerived
    public int INSERTS_Q = 0;

    /**
     * The number of deletions. Note: This is not the number of bases that have been deleted.
     */
    @MergeByAdding
    public long NUM_DELETIONS = 0L;

    /**
     * The (phred) rate of deletions. TODO mgatzen Does this make sense?
     */
    @NoMergingIsDerived
    public int DELETIONS_Q = 0;

    @Override
    public void calculateDerivedFields() {
        this.INSERTS_Q = computeQScore(NUM_INSERTS, TOTAL_BASES);
        this.DELETIONS_Q = computeQScore(NUM_DELETIONS, TOTAL_BASES);

    }

    public IndelErrorMetric(final String covariate,
                            final long nTotalBases,
                            final long nInserts,
                            final long nDeletions) {
        super(covariate, nTotalBases, nInserts + nDeletions);

        this.NUM_INSERTS = nInserts;
        this.NUM_DELETIONS = nDeletions;
    }

    // needed for reading in a metric from a file
    public IndelErrorMetric() {
    }
}