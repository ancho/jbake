package org.jbake.app.configuration;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.jbake.app.JBakeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Provides Configuration related functions.
 *
 * @author Jonathan Bullock <a href="mailto:jonbullock@gmail.com">jonbullock@gmail.com</a>
 */
public class ConfigUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUtil.class);
    private static final String LEGACY_CONFIG_FILE = "custom.properties";
    private static final String CONFIG_FILE = "jbake.properties";
    private static final String DEFAULT_CONFIG_FILE = "default.properties";

    private CompositeConfiguration load(File source) throws ConfigurationException {

        if (!source.exists()) {
            throw new JBakeException("The given source folder '" + source.getAbsolutePath() + "' does not exist.");
        }
        if (!source.isDirectory()) {
            throw new JBakeException("The given source folder is not a directory.");
        }

        CompositeConfiguration config = new CompositeConfiguration();
        config.setListDelimiter(',');
        File customConfigFile = new File(source, LEGACY_CONFIG_FILE);
        if (customConfigFile.exists()) {
            displayLegacyConfigFileWarningIfRequired();
            config.addConfiguration(new PropertiesConfiguration(customConfigFile));
        }
        customConfigFile = new File(source, CONFIG_FILE);
        if (customConfigFile.exists()) {
            config.addConfiguration(new PropertiesConfiguration(customConfigFile));
        }
        config.addConfiguration(new PropertiesConfiguration(DEFAULT_CONFIG_FILE));
        config.addConfiguration(new SystemConfiguration());
        return config;
    }

    private void displayLegacyConfigFileWarningIfRequired() {
        LOGGER.warn("You have defined a part of your JBake configuration in {}", LEGACY_CONFIG_FILE);
        LOGGER.warn("Usage of this file is being deprecated, please rename this file to: {} to remove this warning", CONFIG_FILE);
    }

    public JBakeConfiguration loadConfig(File source) throws ConfigurationException {
        CompositeConfiguration configuration = load(source);
        return new DefaultJBakeConfiguration(source, configuration);
    }

     /**
     * Given a file inside content it return
     * the relative path to get to the root.
     * 
     * Example: /content and /content/tags/blog will return '../..'
     * 
     * @param sourceFile the file to calculate relative path for
     * @return
     */
    static public String getPathToRoot(JBakeConfiguration config, File rootPath, File sourceFile) {
        File parentPath = sourceFile.getParentFile();
        int parentCount = 0;
        while (!parentPath.equals(rootPath) && parentPath.getParentFile()!=null) {
            parentPath = parentPath.getParentFile();
            parentCount++;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parentCount; i++) {
            sb.append("../");
        }
        if (config.getUriWithoutExtension()) {
            sb.append("../");
        }
        return sb.toString();
    }

    static public String getPathtoDestinationRoot(JBakeConfiguration config, File sourceFile) {
        return getPathToRoot(config, config.getDestinationFolder(), sourceFile);
    }   
    
    static public String getPathToContentRoot(JBakeConfiguration config, File sourceFile) {
        return getPathToRoot(config, config.getContentFolder(), sourceFile);
    }
}
