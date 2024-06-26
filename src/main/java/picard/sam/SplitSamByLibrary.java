/*
 * The MIT License
 *
 * Copyright (c) 2009-2016 The Broad Institute
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
package picard.sam;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.help.DocumentedFeature;
import picard.cmdline.CommandLineProgram;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import picard.cmdline.StandardOptionDefinitions;
import picard.cmdline.programgroups.ReadDataManipulationProgramGroup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Command-line program to split a SAM/BAM/CRAM file into separate files based on
 * library name.
 *
 * <p> This tool takes a SAM or BAM file and separates all the reads
 * into one SAM or BAM file per library name. Reads that do not have
 * a read group specified or whose read group does not have a library name
 * are written to a file called 'unknown.' The format (SAM or BAM) of the
 * output files matches that of the input file.</p>
 *
 * <h3>Inputs</h3>
 * <ul>
 *     <li> The BAM or SAM file to be split </li>
 *     <li> The directory where the library SAM or BAM files should be written </li>
 * </ul>
 *
 * <h3>Output/h3>
 * <ul>
 *     <li> One SAM or BAM file per library name  </li>
 * </ul>
 *
 * <p>
 * <h4>Usage example: </h4>
 * <pre>
 *     java -jar picard.jar SplitSamByLibrary \
 *          I=input_reads.bam \
 *          O=/output/directory/
 * </pre>
 * </p>
 *
 * @author ktibbett@broadinstitute.org
 */
@CommandLineProgramProperties(
        summary = SplitSamByLibrary.USAGE_DETAILS,
        oneLineSummary = SplitSamByLibrary.USAGE_SUMMARY,
        programGroup = ReadDataManipulationProgramGroup.class)
@DocumentedFeature
public class SplitSamByLibrary extends CommandLineProgram {

    static final String USAGE_SUMMARY = "Splits a SAM/BAM/CRAM file into individual files by library";
    static final String USAGE_DETAILS = "Takes a SAM/BAM/CRAM file and separates all the reads " +
            "into one output file per library name.  Reads that do not have " +
            "a read group specified or whose read group does not have a library name " +
            "are written to a file called 'unknown.' The format (SAM/BAM/CRAM) of the  " +
            "output files matches that of the input file." +
            "<br />"+
            "<h4>Usage example:</h4>" +
            "<pre>" +
            "java -jar picard.jar SplitSamByLibrary <br />" +
            "      I=input_reads.bam <br />" +
            "      O=/output/directory/ <br />"+
            "</pre>";

    @Argument(shortName = StandardOptionDefinitions.INPUT_SHORT_NAME,
            doc = "The SAM, BAM of CRAM file to be split. ")
    public File INPUT;
    @Argument(shortName = StandardOptionDefinitions.OUTPUT_SHORT_NAME,
            doc = "The directory where the per-library output files should be written " +
                    "(defaults to the current directory). ", optional = true)
    public File OUTPUT = new File(".").getAbsoluteFile();

    private static final Log log = Log.getInstance(SplitSamByLibrary.class);

    public static final int NO_LIBRARIES_SPECIFIED_IN_HEADER = 2;

    @Override
    protected int doWork()  {
        IOUtil.assertFileIsReadable(INPUT);
        IOUtil.assertDirectoryIsWritable(OUTPUT);

        SAMFileWriter unknown = null;
        final Map<String, SAMFileWriter> libraryToWriter = new HashMap<>();
        try(SamReader reader = SamReaderFactory.makeDefault().referenceSequence(REFERENCE_SEQUENCE).open(INPUT)) {
            final Map<String, List<SAMReadGroupRecord>> libraryToRg = new HashMap<>();
            final SAMFileWriterFactory factory = new SAMFileWriterFactory();
            final String extension = "." + reader.type().fileExtension();

            final SAMFileHeader unknownHeader = reader.getFileHeader().clone();
            unknownHeader.setReadGroups(new ArrayList<>());

            for (final SAMReadGroupRecord rg : reader.getFileHeader().getReadGroups()) {
                final String lib = rg.getLibrary();
                if (lib != null) {
                    if (!libraryToRg.containsKey(lib)) {
                        libraryToRg.put(lib, new ArrayList<>());
                    }
                    libraryToRg.get(lib).add(rg);
                } else {
                    unknownHeader.addReadGroup(rg);
                }
            }

            if (libraryToRg.isEmpty()) {
                log.error("No individual libraries are " +
                        "specified in the header of " + INPUT.getAbsolutePath());
                return NO_LIBRARIES_SPECIFIED_IN_HEADER;
            }

            for (Map.Entry<String, List<SAMReadGroupRecord>> entry : libraryToRg.entrySet()) {
                final String lib = entry.getKey();
                final SAMFileHeader header = reader.getFileHeader().clone();
                header.setReadGroups(entry.getValue());
                libraryToWriter.put(lib, factory.makeWriter(header, true,
                        new File(OUTPUT, IOUtil.makeFileNameSafe(lib) + extension),
                        REFERENCE_SEQUENCE));
            }

            for (final SAMRecord sam : reader) {
                final SAMReadGroupRecord rg = sam.getReadGroup();
                if (rg != null && rg.getLibrary() != null) {
                    libraryToWriter.get(rg.getLibrary()).addAlignment(sam);
                } else {
                    if (unknown == null) {
                        unknown = factory.makeWriter(unknownHeader, true,
                                new File(OUTPUT, "unknown" + extension),
                                REFERENCE_SEQUENCE);
                    }
                    unknown.addAlignment(sam);
                }
            }
        } catch (IOException e) {
            log.error("Trouble while reading %s", INPUT.getAbsolutePath());
            log.error(e.getMessage());
        }

        if (unknown != null) {
            unknown.close();
        }

        for (SAMFileWriter writer : libraryToWriter.values()) {
            writer.close();
        }

        return 0;
    }
}
