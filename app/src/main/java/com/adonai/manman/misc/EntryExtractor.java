package com.adonai.manman.misc;

import android.support.annotation.NonNull;
import android.util.Log;

import com.adonai.manman.Utils;
import com.adonai.manman.entities.RetrievedLocalManPage;
import com.j256.simplemagic.ContentType;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Retrieves needed data from almost every possible type of archive
 */
public class EntryExtractor {

    private static final List<Extractor> EXTRACTORS = new ArrayList<>();
    
    static {
        EXTRACTORS.add(new GzipExtractor());
        EXTRACTORS.add(new XzipExtractor());
        EXTRACTORS.add(new TarExtractor());
        EXTRACTORS.add(new ZipExtractor());
        EXTRACTORS.add(new ManPageExtractor());
    }
    
    
    
    public void retrieveManPages(File file, List<RetrievedLocalManPage> toPopulate) {
        // check that file is archive
        try {
            // wrap in buffered so mark is supported
            BufferedInputStream is = new BufferedInputStream(new FileInputStream(file));
            ContentType detected = Utils.getMimeSubtype(is);
            performExtract(file.getParent(), file.getName(), is, detected, toPopulate);
            is.close();
        } catch (IOException e) {
            Log.e("Man Man", "Filesystem", e);
        }
    }

    /**
     * This function is called by all the extractors in case they can't handle some inner stream
     * @param parentName name of parent entry (e.g. name of folder where archive is)
     * @param name name of entry that is currently parsed (e.g. name of archive)
     * @param is input stream that is currently parsed
     * @param type type of input stream that was detected by previous parser
     * @param toPopulate final list of retrieved pages that was provided by loader
     * @throws IOException in case of any stream errors
     */
    private static void performExtract(@NonNull String parentName,
                                       @NonNull String name,
                                       @NonNull InputStream is, 
                                       @NonNull ContentType type, 
                                       @NonNull List<RetrievedLocalManPage> toPopulate) throws IOException 
    {
        for(Extractor extractor : EXTRACTORS) {
            if(extractor.isResponsibleFor(type)) {
                extractor.parse(parentName, name, is, toPopulate);
            }
        }
    }

    interface Extractor {

        /**
         * Returns true if the extractor can handle this content type
         * @param type content type of stream that should be parsed
         * @return true if and only if parser can handle this type of stream
         */
        boolean isResponsibleFor(ContentType type);

        /**
         * Parse archive and populate provided list with retrieved entries (or hand it over to another parser)
         * @param parentName name of parent entry (e.g. name of archive)
         * @param entryName name of entry that is currently parsed (e.g. name of tar archive entry)
         * @param is input stream that should be parsed
         * @param toPopulate final list of retrieved pages that was provided by loader
         * @throws IOException in case of any stream errors
         */
        void parse(@NonNull String parentName, 
                   @NonNull String entryName, 
                   @NonNull InputStream is, 
                   @NonNull List<RetrievedLocalManPage> toPopulate) throws IOException;
    }
    
    private static class GzipExtractor implements Extractor {

        @Override
        public boolean isResponsibleFor(ContentType type) {
            return type == ContentType.GZIP;
        }

        @Override
        public void parse(@NonNull String parentName, 
                          @NonNull String name, 
                          @NonNull InputStream is, 
                          @NonNull List<RetrievedLocalManPage> toPopulate) throws IOException {
            BufferedInputStream gis = new BufferedInputStream(new GZIPInputStream(is));
            ContentType detected = Utils.getMimeSubtype(gis);
            performExtract(parentName, name, gis, detected, toPopulate);
        }
    }

    private static class XzipExtractor implements Extractor {

        @Override
        public boolean isResponsibleFor(ContentType type) {
            return type == ContentType.XZ;
        }

        @Override
        public void parse(@NonNull String parentName, 
                          @NonNull String name, 
                          @NonNull InputStream is, 
                          @NonNull List<RetrievedLocalManPage> toPopulate) throws IOException 
        {
            BufferedInputStream xis = new BufferedInputStream(new XZCompressorInputStream(is));
            ContentType detected = Utils.getMimeSubtype(xis);
            performExtract(parentName, name, xis, detected, toPopulate);
        }
    }
    
    private static class TarExtractor implements Extractor {

        @Override
        public boolean isResponsibleFor(ContentType type) {
            return type == ContentType.TAR || type == ContentType.GTAR || type == ContentType.USTAR;
        }

        @Override
        public void parse(@NonNull String parentName, 
                          @NonNull String name, 
                          @NonNull InputStream is, 
                          @NonNull List<RetrievedLocalManPage> toPopulate) throws IOException 
        {
            TarArchiveInputStream tis = new TarArchiveInputStream(is);
            TarArchiveEntry tarEntry;
            while ((tarEntry = tis.getNextTarEntry()) != null) {
                if(tarEntry.isDirectory())
                    continue;
                
                if(tarEntry.isFile()) {
                    BufferedInputStream entrySubStream = new BufferedInputStream(tis);
                    ContentType detected = Utils.getMimeSubtype(entrySubStream);
                    performExtract(parentName + " - " + name, tarEntry.getName(), entrySubStream, detected, toPopulate);
                }
            }
        }
    }
    
    private static class ZipExtractor implements Extractor {

        @Override
        public boolean isResponsibleFor(ContentType type) {
            return type == ContentType.ZIP;
        }

        @Override
        public void parse(@NonNull String parentName, 
                          @NonNull String name, 
                          @NonNull InputStream is, 
                          @NonNull List<RetrievedLocalManPage> toPopulate) throws IOException 
        {
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if(zipEntry.isDirectory())
                    continue;

                BufferedInputStream entrySubStream = new BufferedInputStream(zis);
                ContentType detected = Utils.getMimeSubtype(entrySubStream);
                performExtract(name, zipEntry.getName(), entrySubStream, detected, toPopulate);
            }
        }
    }
    
    private static class ManPageExtractor implements Extractor {

        @Override
        public boolean isResponsibleFor(ContentType type) {
            return type == ContentType.TROFF;
        }

        @Override
        public void parse(@NonNull String parentName, 
                          @NonNull String name, 
                          @NonNull InputStream is, 
                          @NonNull List<RetrievedLocalManPage> toPopulate) throws IOException {
            RetrievedLocalManPage manPage = new RetrievedLocalManPage();
            manPage.setParentEntry(parentName);
            manPage.setCommandName(name);
            manPage.setContent(IOUtils.toByteArray(is));
            toPopulate.add(manPage);
        }
    }
}
