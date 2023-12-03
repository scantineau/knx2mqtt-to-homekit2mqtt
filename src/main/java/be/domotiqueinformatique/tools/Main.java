package be.domotiqueinformatique.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.Timer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Main {
    public static final Timer t = new Timer(true);

    public static void main(String[] args) throws SecurityException {
        Logger logger = Logger.getLogger(Main.class.getName());


        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.ALL);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(Level.ALL);
        }
        String path_to_properties = Optional.ofNullable(System.getenv("KNX2MQTT_PATH_TO_PROPERTIES"))
                .orElse("knx2mqtt.properties");
        logger.info(String.format("Loading properties from file '%s'", path_to_properties));
        try (InputStream input = new FileInputStream(path_to_properties)) {

            Properties prop = new Properties();
            // Init with current properties
            prop.putAll(System.getProperties());

            // Load a properties file
            prop.load(input);

            // Replace system properties
            System.setProperties(prop);

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Override properties with command line
        for (String s : args) {
            String[] sp = s.split("=", 2);
            if (sp.length != 2) {
                System.out.println("Invalid argument (no =): " + s);
                System.exit(1);
            }
            if (s.startsWith("knx2mqtt_to_homekit2mqtt.")) {
                System.setProperty(sp[0], sp[1]);
            } else {
                System.setProperty("knx2mqtt_to_homekit2mqtt." + sp[0], sp[1]);
            }
        }
        MQTTHandler.init();
    }
}
