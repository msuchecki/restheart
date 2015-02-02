package com.softinstigate.restheart.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Created by wkaminski on 28.01.15.
 */
public abstract class FileConfigurable {

    protected static final Logger logger = getLogger();

    protected static Logger getLogger() {
        return LoggerFactory.getLogger(FileConfigurable.class);
    }



    protected void loadConfig(Map<String, Object> arguments) {
        if (arguments == null) {
            logger.error("missing required argument conf-file");
            throw new IllegalArgumentException("\"missing required arguments conf-file");
        }

        Object _confFilePath = arguments.getOrDefault("conf-file", "security.yml");

        if (_confFilePath == null || !(_confFilePath instanceof String)) {
            logger.error("missing required argument conf-file");
            throw new IllegalArgumentException("\"missing required arguments conf-file");
        }

        String confFilePath = (String) _confFilePath;

        if (!confFilePath.startsWith("/")) {
            // this is to allow specifying the configuration file path relative to the jar (also working when running from classes)
            URL location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
            File locationFile = new File(location.getPath());
            confFilePath = locationFile.getParent() + File.separator + confFilePath;
        }



        try (FileInputStream fis =new FileInputStream(new File(confFilePath))){

            init((Map<String, Object>) new Yaml().load(fis));
        } catch (FileNotFoundException fnef) {
            logger.error("configuration file not found.", fnef);
            throw new IllegalArgumentException("configuration file not found.", fnef);
        } catch (RuntimeException re) {
            logger.error("wrong configuration file format.", re);
            throw new IllegalArgumentException("wrong configuration file format.", re);
        } catch (IOException e) {
            logger.error("IO exception.", e);
        }
    }

    protected abstract void init(Map<String, Object> conf);

}