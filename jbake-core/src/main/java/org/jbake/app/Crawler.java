package org.jbake.app;

import com.orientechnologies.orient.core.record.impl.ODocument;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.jbake.app.Crawler.Attributes.Status;
import org.jbake.app.configuration.JBakeConfiguration;
import org.jbake.app.configuration.JBakeConfigurationFactory;
import org.jbake.model.DocumentModel;
import org.jbake.model.DocumentStatus;
import org.jbake.model.DocumentTypes;
import org.jbake.model.ModelAttributes;
import org.jbake.util.HtmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * Crawls a file system looking for content.
 *
 * @author Jonathan Bullock <a href="mailto:jonbullock@gmail.com">jonbullock@gmail.com</a>
 */
public class Crawler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Crawler.class);
    private final ContentStore db;
    private JBakeConfiguration config;
    private Parser parser;

    /**
     * @param db     Database instance for content
     * @param source Base directory where content directory is located
     * @param config Project configuration
     * @deprecated Use {@link #Crawler(ContentStore, JBakeConfiguration)} instead.
     * <p>
     * Creates new instance of Crawler.
     */
    @Deprecated
    public Crawler(ContentStore db, File source, CompositeConfiguration config) {
        this.db = db;
        this.config = new JBakeConfigurationFactory().createDefaultJbakeConfiguration(source, config);
        this.parser = new Parser(this.config);
    }

    /**
     * Creates new instance of Crawler.
     *
     * @param db     Database instance for content
     * @param config Project configuration
     */
    public Crawler(ContentStore db, JBakeConfiguration config) {
        this.db = db;
        this.config = config;
        this.parser = new Parser(config);
    }

    public void crawl() {
        crawl(config.getContentFolder());

        LOGGER.info("Content detected:");
        for (String docType : DocumentTypes.getDocumentTypes()) {
            long count = db.getDocumentCount(docType);
            if (count > 0) {
                LOGGER.info("Parsed {} files of type: {}", count, docType);
            }
        }

    }

    /**
     * Crawl all files and folders looking for content.
     *
     * @param path Folder to start from
     */
    private void crawl(File path) {
        File[] contents = path.listFiles(FileUtil.getFileFilter());
        if (contents != null) {
            Arrays.sort(contents);
            for (File sourceFile : contents) {
                if (sourceFile.isFile()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Processing [").append(sourceFile.getPath()).append("]... ");
                    String sha1 = buildHash(sourceFile);
                    String uri = buildURI(sourceFile);
                    boolean process = true;
                    DocumentStatus status = DocumentStatus.NEW;
                    for (String docType : DocumentTypes.getDocumentTypes()) {
                        status = findDocumentStatus(docType, uri, sha1);
                        if (status == DocumentStatus.UPDATED) {
                            sb.append(" : modified ");
                            db.deleteContent(docType, uri);

                        } else if (status == DocumentStatus.IDENTICAL) {
                            sb.append(" : same ");
                            process = false;
                        }
                        if (!process) {
                            break;
                        }
                    }
                    if (DocumentStatus.NEW == status) {
                        sb.append(" : new ");
                    }
                    if (process) { // new or updated
                        crawlSourceFile(sourceFile, sha1, uri);
                    }
                    LOGGER.info("{}", sb);
                }
                if (sourceFile.isDirectory()) {
                    crawl(sourceFile);
                }
            }
        }
    }

    private String buildHash(final File sourceFile) {
        String sha1;
        try {
            sha1 = FileUtil.sha1(sourceFile);
        } catch (Exception e) {
            LOGGER.error("unable to build sha1 hash for source file '{}'", sourceFile);
            sha1 = "";
        }
        return sha1;
    }

    private String buildURI(final File sourceFile) {
        String uri = FileUtil.asPath(sourceFile).replace(FileUtil.asPath(config.getContentFolder()), "");

        if (useNoExtensionUri(uri)) {
            // convert URI from xxx.html to xxx/index.html
            uri = createNoExtensionUri(uri);
        } else {
            uri = createUri(uri);
        }

        // strip off leading / to enable generating non-root based sites
        if (uri.startsWith(FileUtil.URI_SEPARATOR_CHAR)) {
            uri = uri.substring(1);
        }

        return uri;
    }

    // TODO: Refactor - parametrize the following two methods into one.
    // commons-codec's URLCodec could be used when we add that dependency.
    private String createUri(String uri) {
        try {
            return FileUtil.URI_SEPARATOR_CHAR
                + FilenameUtils.getPath(uri)
                + URLEncoder.encode(FilenameUtils.getBaseName(uri), StandardCharsets.UTF_8.name())
                + config.getOutputExtension();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Missing UTF-8 encoding??", e); // Won't happen unless JDK is broken.
        }
    }

    private String createNoExtensionUri(String uri) {
        try {
            return FileUtil.URI_SEPARATOR_CHAR
                + FilenameUtils.getPath(uri)
                + URLEncoder.encode(FilenameUtils.getBaseName(uri), StandardCharsets.UTF_8.name())
                + FileUtil.URI_SEPARATOR_CHAR
                + "index"
                + config.getOutputExtension();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Missing UTF-8 encoding??", e); // Won't happen unless JDK is broken.
        }
    }

    private boolean useNoExtensionUri(String uri) {
        boolean noExtensionUri = config.getUriWithoutExtension();
        String noExtensionUriPrefix = config.getPrefixForUriWithoutExtension();

        return noExtensionUri
            && (noExtensionUriPrefix != null)
            && (noExtensionUriPrefix.length() > 0)
            && uri.startsWith(noExtensionUriPrefix);
    }

    private void crawlSourceFile(final File sourceFile, final String sha1, final String uri) {
        try {
            DocumentModel fileContents = parser.processFile(sourceFile);
            if (fileContents != null) {
                addAdditionalDocumentAttributes(fileContents, sourceFile, sha1, uri);

                if (config.getImgPathUpdate()) {
                    // Prevent image source url's from breaking
                    HtmlUtil.fixImageSourceUrls(fileContents, config);
                }

                ODocument doc = new ODocument(fileContents.getType());
                doc.fromMap(fileContents);
                doc.save();
            } else {
                LOGGER.warn("{} has an invalid header, it has been ignored!", sourceFile);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed crawling file: " + sourceFile.getPath() + " " + ex.getMessage(), ex);
        }

    }

    private void addAdditionalDocumentAttributes(DocumentModel documentModel, File sourceFile, String sha1, String uri) {
        documentModel.setRootPath(getPathToRoot(sourceFile));
        documentModel.setSha1(sha1);
        documentModel.setRendered(false);
        documentModel.setFile(sourceFile.getPath());
        documentModel.setSourceUri(uri);
        documentModel.setUri(uri);

        if (documentModel.getStatus().equals(Status.PUBLISHED_DATE)
                && (documentModel.getDate() != null)
                && new Date().after(documentModel.getDate())) {
            documentModel.setStatus(Status.PUBLISHED);
        }

        if (config.getUriWithoutExtension()) {
            documentModel.setNoExtensionUri(uri.replace("/index.html", "/"));
        }
    }

    private String getPathToRoot(File sourceFile) {
        return FileUtil.getUriPathToContentRoot(config, sourceFile);
    }

    private DocumentStatus findDocumentStatus(String docType, String uri, String sha1) {
        DocumentList match = db.getDocumentStatus(docType, uri);
        if (!match.isEmpty()) {
            DocumentModel documentModel = match.getDocumentModel(0);
            String oldHash = documentModel.getSha1();
            if (!(oldHash.equals(sha1)) || Boolean.FALSE.equals(documentModel.getRendered())) {
                return DocumentStatus.UPDATED;
            } else {
                return DocumentStatus.IDENTICAL;
            }
        } else {
            return DocumentStatus.NEW;
        }
    }

    public abstract static class Attributes {

        public static final String ALLTAGS = "alltags";
        public static final String PUBLISHED_DATE = "published_date";
        public static final String DB = "db";

        private Attributes() {
        }

        /**
         * Possible values of the {@link ModelAttributes#STATUS} property
         *
         * @author ndx
         */
        public abstract static class Status {
            public static final String PUBLISHED_DATE = "published-date";
            public static final String PUBLISHED = "published";
            public static final String DRAFT = "draft";

            private Status() {
            }
        }

    }
}
