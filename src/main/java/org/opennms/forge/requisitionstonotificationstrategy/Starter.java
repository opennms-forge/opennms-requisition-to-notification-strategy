package org.opennms.forge.requisitionstonotificationstrategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.opennms.netmgt.provision.persist.requisition.RequisitionCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Starter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Starter.class);
    
    private final static String INPUT_FILE_PARAMETER = "input-xml";
    private final static String OUTPUT_FOLDER_PARAMETER = "output-folder";
    private static File outFolder;
    private static File inFile;

    public static void main(String[] args) {
        LOGGER.info("Starting Notification and DestinationPath generation...");
        if (System.getProperty(OUTPUT_FOLDER_PARAMETER, null) != null && System.getProperty(INPUT_FILE_PARAMETER, null) != null) {

            inFile = new File(System.getProperty(INPUT_FILE_PARAMETER));
            outFolder = new File(System.getProperty(OUTPUT_FOLDER_PARAMETER));
            LOGGER.debug("Input  File :: {}", inFile.getAbsolutePath());
            LOGGER.debug("Output Folder :: {}", outFolder.getAbsolutePath());
            if (inFile.exists() && inFile.canRead()) {
                LOGGER.info("Can read Input file {}", inFile.getAbsolutePath());
                if (outFolder.exists() && outFolder.isDirectory() && outFolder.canWrite()) {
                    LOGGER.info("Output Folder is ok {}", outFolder.getAbsolutePath());
                    if (isRequisitionsFile(inFile)) {
                        LOGGER.info("Input file provides requisitions");
                        LOGGER.info("Starting generation...");
                        NotificationTokenBasedNotificationGenerator notificationGenerator = new NotificationTokenBasedNotificationGenerator(inFile, outFolder, buildProperties());
                        try {
                            notificationGenerator.generateNotificationStrategy();
                        } catch (Exception ex) {
                            LOGGER.error("Generation of the notification strategy caused a problem", ex);
                        }
                    } else {
                        LOGGER.info("Input file is not a valid requisitions file");
                    }
                } else {
                    LOGGER.info("Output folder has a problem :: {}", outFolder.getAbsolutePath());
                    LOGGER.info("Output folder exists        :: {}", outFolder.exists());
                    LOGGER.info("Output folder is Directory  :: {}", outFolder.isDirectory());
                    LOGGER.info("Output folder is writeable  :: {}", outFolder.canWrite());
                }
            } else {
                LOGGER.info("Input file can not be read {}", inFile.getAbsolutePath());
            }
        } else {
            LOGGER.info("Please provide the following parameters: -D" + OUTPUT_FOLDER_PARAMETER + " -D" + INPUT_FILE_PARAMETER);
        }
    }

    private static Properties buildProperties() {
        final String PROPERTIES_FILE_NAME = "config.properties";
        final File PROPERTIES_FILE = new File(PROPERTIES_FILE_NAME);
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(PROPERTIES_FILE)) {
            properties.load(input);
            LOGGER.info("Using properties from file: {}", PROPERTIES_FILE.getAbsolutePath());
        } catch (IOException ex) {
            LOGGER.info("Reading properties failed. Fallback to default values. file: {}", PROPERTIES_FILE.getAbsolutePath());
        }
        return properties;
    }

    private static boolean isRequisitionsFile(File inFile) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(RequisitionCollection.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            RequisitionCollection requisitions = (RequisitionCollection) jaxbUnmarshaller.unmarshal(inFile);
            LOGGER.debug("Unmarshalled {} requisitions", requisitions.size());
            return true;
        } catch (ClassCastException | JAXBException ex) {
            LOGGER.debug("Input file {} is not a valid requisitions file", inFile.getAbsolutePath(), ex);
            return false;
        }
    }
}