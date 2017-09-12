package gov.nist.hla.ii.config;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ConfigParser
 */
public class ConfigParser {

    public static <T> T parseConfig(File configFile, final Class<T> clazz) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        T configObj = mapper.readValue(configFile, clazz);

        return configObj;
    }
}
