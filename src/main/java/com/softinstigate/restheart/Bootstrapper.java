/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.softinstigate.restheart;

import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.db.PropsFixer;
import com.softinstigate.restheart.handlers.*;
import com.softinstigate.restheart.handlers.applicationlogic.ApplicationLogicHandler;
import com.softinstigate.restheart.handlers.collection.*;
import com.softinstigate.restheart.handlers.database.DeleteDBHandler;
import com.softinstigate.restheart.handlers.database.GetDBHandler;
import com.softinstigate.restheart.handlers.database.PatchDBHandler;
import com.softinstigate.restheart.handlers.database.PutDBHandler;
import com.softinstigate.restheart.handlers.document.DeleteDocumentHandler;
import com.softinstigate.restheart.handlers.document.GetDocumentHandler;
import com.softinstigate.restheart.handlers.document.PatchDocumentHandler;
import com.softinstigate.restheart.handlers.document.PutDocumentHandler;
import com.softinstigate.restheart.handlers.indexes.DeleteIndexHandler;
import com.softinstigate.restheart.handlers.indexes.GetIndexesHandler;
import com.softinstigate.restheart.handlers.indexes.PutIndexHandler;
import com.softinstigate.restheart.handlers.injectors.*;
import com.softinstigate.restheart.handlers.metadata.MetadataEnforcerHandler;
import com.softinstigate.restheart.handlers.root.GetRootHandler;
import com.softinstigate.restheart.security.AccessManager;
import com.softinstigate.restheart.security.handlers.AccessManagerHandler;
import com.softinstigate.restheart.security.handlers.CORSHandler;
import com.softinstigate.restheart.security.handlers.SecurityHandler;
import com.softinstigate.restheart.utils.LoggingInitializer;
import com.softinstigate.restheart.utils.ResourcesExtractor;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.*;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.HttpString;
import org.pac4j.core.client.Clients;
import org.pac4j.undertow.Config;
import org.pac4j.undertow.handlers.CallbackHandler;
import org.pac4j.undertow.handlers.LogoutHandler;
import org.pac4j.undertow.utils.HandlerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import static com.softinstigate.restheart.Configuration.RESTHEART_VERSION;
import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;

/**
 * @author Andrea Di Cesare
 */
public class Bootstrapper {

    private static Undertow server;

    private static final Logger logger = LoggerFactory.getLogger(Bootstrapper.class);

    private static final Map<String, File> tmpExtractedFiles = new HashMap<>();

    private static GracefulShutdownHandler hanldersPipe = null;

    private static Configuration conf;

    /**
     * main method
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        startServer(args);
    }

    /**
     * Startups the RESTHeart server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(String confFilePath) {
        startServer(new String[]{confFilePath});
    }

    /**
     * Shutdown the RESTHeart server
     */
    public static void shutdown() {
        stopServer();
    }

    private static void startServer(final String[] args) {
        if (args == null || args.length < 1) {
            conf = new Configuration();
        } else {
            conf = new Configuration(args[0]);
        }
        LoggingInitializer.setLogLevel(conf.getLogLevel());

        if (conf.isLogToFile()) {
            LoggingInitializer.startFileLogging(conf.getLogFilePath());
        }

        logger.info("starting RESTHeart ********************************************");

        logger.info("RESTHeart version {}", RESTHEART_VERSION);

        String mongoHosts = conf.getMongoServers().stream().map(s -> s.get(Configuration.MONGO_HOST_KEY) + ":" + s.get(Configuration.MONGO_PORT_KEY) + " ").reduce("", String::concat);

        logger.info("initializing mongodb connection pool to {}", mongoHosts);

        try {
            MongoDBClientSingleton.init(conf);

            logger.info("mongodb connection pool initialized");

            PropsFixer.fixAllMissingProps();
        } catch (Throwable t) {
            logger.error("error connecting to mongodb. exiting..", t);
            System.exit(-1);
        }

        try {
            startCoreSystem();
        } catch (Throwable t) {
            logger.error("error starting RESTHeart. exiting..", t);
            System.exit(-2);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                stopServer();
            }
        });

        if (conf.isLogToFile()) {
            logger.info("logging to {} with level {}", conf.getLogFilePath(), conf.getLogLevel());
        }

        if (!conf.isLogToConsole()) {
            logger.info("stopping logging to console ");
            LoggingInitializer.stopConsoleLogging();
        } else {
            logger.info("logging to console with level {}", conf.getLogLevel());
        }

        logger.info("RESTHeart started **********************************************");
    }

    private static void stopServer() {
        logger.info("stopping RESTHeart");
        logger.info("waiting for pending request to complete (up to 1 minute)");

        try {
            hanldersPipe.shutdown();
            hanldersPipe.awaitShutdown(60 * 1000); // up to 1 minute
        } catch (InterruptedException ie) {
            logger.error("error while waiting for pending request to complete", ie);
        }

        if (server != null) {
            try {
                server.stop();
            } catch (Throwable t) {
                logger.error("error stopping undertow server", t);
            }
        }

        try {
            MongoClient client = MongoDBClientSingleton.getInstance().getClient();
            client.fsync(false);
            client.close();
        } catch (Throwable t) {
            logger.error("error flushing and clonsing the mongo cliet", t);
        }

        tmpExtractedFiles.keySet().forEach(k -> {
                    try {
                        ResourcesExtractor.deleteTempDir(k, tmpExtractedFiles.get(k));
                    } catch (URISyntaxException | IOException ex) {
                        logger.error("error cleaning up temporary directory {}", tmpExtractedFiles.get(k).toString(), ex);
                    }
                }
        );

        logger.info("RESTHeart stopped");
    }

    private static void startCoreSystem() {
        if (conf == null) {
            logger.error("no configuration found. exiting..");
            System.exit(-1);
        }

        if (!conf.isHttpsListener() && !conf.isHttpListener() && !conf.isAjpListener()) {
            logger.error("no listener specified. exiting..");
            System.exit(-1);
        }

        IdentityManager identityManager = null;

        if (conf.getIdmImpl() == null) {
            logger.warn("***** no identity manager specified. authentication disabled.");
            identityManager = null;
        } else {
            try {
                Object idm = Class.forName(conf.getIdmImpl()).getConstructor(Map.class).newInstance(conf.getIdmArgs());
                identityManager = (IdentityManager) idm;
            } catch (ClassCastException | NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                logger.error("error configuring idm implementation {}", conf.getIdmImpl(), ex);
                System.exit(-3);
            }
        }

        AccessManager accessManager = null;

        if (conf.getAmImpl() == null && conf.getIdmImpl() != null) {
            logger.warn("***** no access manager specified. authenticated users can do anything.");
            accessManager = null;
        } else if (conf.getAmImpl() == null && conf.getIdmImpl() == null) {
            logger.warn("***** no access manager specified. users can do anything.");
            accessManager = null;
        } else {
            try {
                Object am = Class.forName(conf.getAmImpl()).getConstructor(Map.class).newInstance(conf.getAmArgs());
                accessManager = (AccessManager) am;
            } catch (ClassCastException | NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                logger.error("error configuring acess manager implementation {}", conf.getAmImpl(), ex);
                System.exit(-3);
            }
        }

        SSLContext sslContext = null;

        try {
            KeyManagerFactory kmf;
            KeyStore ks;

            if (conf.isUseEmbeddedKeystore()) {
                char[] storepass = "restheart".toCharArray();
                char[] keypass = "restheart".toCharArray();

                String storename = "rakeystore.jks";

                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");
                ks.load(Bootstrapper.class.getClassLoader().getResourceAsStream(storename), storepass);

                kmf.init(ks, keypass);
                sslContext.init(kmf.getKeyManagers(), null, null);
            } else {
                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");

                try (FileInputStream fis = new FileInputStream(new File(conf.getKeystoreFile()))) {
                    ks.load(fis, conf.getKeystorePassword().toCharArray());

                    kmf.init(ks, conf.getCertPassword().toCharArray());
                    sslContext.init(kmf.getKeyManagers(), null, null);
                }
            }
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException ex) {
            logger.error("couldn't start RESTHeart, error with specified keystore. exiting..", ex);
            System.exit(-1);
        } catch (FileNotFoundException ex) {
            logger.error("couldn't start RESTHeart, keystore file not found. exiting..", ex);
            System.exit(-1);
        } catch (IOException ex) {
            logger.error("couldn't start RESTHeart, error reading the keystore file. exiting..", ex);
            System.exit(-1);
        }

        Builder builder = Undertow.builder();

        if (conf.isHttpsListener()) {
            builder.addHttpsListener(conf.getHttpsPort(), conf.getHttpHost(), sslContext);
            logger.info("https listener bound at {}:{}", conf.getHttpsHost(), conf.getHttpsPort());
        }

        if (conf.isHttpListener()) {
            builder.addHttpListener(conf.getHttpPort(), conf.getHttpsHost());
            logger.info("http listener bound at {}:{}", conf.getHttpHost(), conf.getHttpPort());
        }

        if (conf.isAjpListener()) {
            builder.addAjpListener(conf.getAjpPort(), conf.getAjpHost());
            logger.info("ajp listener bound at {}:{}", conf.getAjpHost(), conf.getAjpPort());
        }

        LocalCachesSingleton.init(conf);

        if (conf.isLocalCacheEnabled()) {
            logger.info("local cache enabled");
        } else {
            logger.info("local cache not enabled");
        }

        hanldersPipe = getHandlersPipe(identityManager, accessManager);

        builder
                .setIoThreads(conf.getIoThreads())
                .setWorkerThreads(conf.getWorkerThreads())
                .setDirectBuffers(conf.isDirectBuffers())
                .setBufferSize(conf.getBufferSize())
                .setBuffersPerRegion(conf.getBuffersPerRegion())
                .setHandler(hanldersPipe);

        builder.build().start();
    }

    private static GracefulShutdownHandler getHandlersPipe(IdentityManager identityManager, AccessManager accessManager) {
        PipedHttpHandler coreHanlderChain
                = new DbPropsInjectorHandler(
                new CollectionPropsInjectorHandler(
                        new BodyInjectorHandler(
                                new MetadataEnforcerHandler(
                                        new RequestDispacherHandler(
                                                new GetRootHandler(),
                                                new GetDBHandler(),
                                                new PutDBHandler(),
                                                new DeleteDBHandler(),
                                                new PatchDBHandler(),
                                                new GetCollectionHandler(),
                                                new PostCollectionHandler(),
                                                new PutCollectionHandler(),
                                                new DeleteCollectionHandler(),
                                                new PatchCollectionHandler(),
                                                new GetDocumentHandler(),
                                                new PutDocumentHandler(),
                                                new DeleteDocumentHandler(),
                                                new PatchDocumentHandler(),
                                                new GetIndexesHandler(),
                                                new PutIndexHandler(),
                                                new DeleteIndexHandler()
                                        )
                                )
                        )
                )
        );

        PathHandler paths = path();

        Config config = pacConfig(identityManager);
        paths.addExactPath("/callback", CallbackHandler.build(config));
        paths.addExactPath("/logout", LogoutHandler.build(config));

        conf.getMongoMounts().stream().forEach(m -> {
            String url = (String) m.get(Configuration.MONGO_MOUNT_WHERE_KEY);
            String db = (String) m.get(Configuration.MONGO_MOUNT_WHAT_KEY);

            SessionHandlerImpl handlerChain = new SessionHandlerImpl(new CORSHandler(new RequestContextInjectorHandler(url, db, new OptionsHandler(coreHanlderChain))), config.getSessionManager(), config.getSessioncookieconfig());


            HttpHandler google2Client = HandlerHelper.requireAuthentication(new AccessManagerHandler(accessManager, handlerChain), config, "Google2Client", false);

            paths.addPrefixPath(url,  google2Client);

            logger.info("url {} bound to mongodb resource {}", url, db);
        });

        pipeStaticResourcesHandlers(conf, paths, identityManager, accessManager);

        pipeApplicationLogicHandlers(conf, paths, identityManager, accessManager);

        return new GracefulShutdownHandler(
                new RequestLimitingHandler(new RequestLimit(conf.getRequestLimit()),
                        new AllowedMethodsHandler(
                                new BlockingHandler(
                                        new GzipEncodingHandler(
                                                new ErrorHandler(
                                                        new HttpContinueAcceptingHandler(HandlerHelper.addSession(paths, config))
                                                ), conf.isForceGzipEncoding()
                                        )
                                ), // allowed methods
                                HttpString.tryFromString(RequestContext.METHOD.GET.name()),
                                HttpString.tryFromString(RequestContext.METHOD.POST.name()),
                                HttpString.tryFromString(RequestContext.METHOD.PUT.name()),
                                HttpString.tryFromString(RequestContext.METHOD.DELETE.name()),
                                HttpString.tryFromString(RequestContext.METHOD.PATCH.name()),
                                HttpString.tryFromString(RequestContext.METHOD.OPTIONS.name())
                        )
                )
        );
    }

    private static void pipeStaticResourcesHandlers(Configuration conf, PathHandler paths, IdentityManager identityManager, AccessManager accessManager) {
        // pipe the static resources specified in the configuration file
        if (conf.getStaticResourcesMounts() != null) {
            conf.getStaticResourcesMounts().stream().forEach(sr -> {
                try {
                    String path = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHAT_KEY);
                    String where = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHERE_KEY);
                    String welcomeFile = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY);
                    boolean embedded = (Boolean) sr.get(Configuration.STATIC_RESOURCES_MOUNT_EMBEDDED_KEY);
                    boolean secured = (Boolean) sr.get(Configuration.STATIC_RESOURCES_MOUNT_SECURED_KEY);

                    if (where == null || !where.startsWith("/")) {
                        logger.error("cannot bind static resources to {}. parameter 'where' must start with /", where);
                        return;
                    }

                    if (welcomeFile == null) {
                        welcomeFile = "index.html";
                    }

                    File file;

                    if (embedded) {
                        if (path.startsWith("/")) {
                            logger.error("cannot bind embedded static resources to {}. parameter 'where' cannot start with /. the path is relative to the jar root dir or classpath directory", where);
                            return;
                        }

                        try {
                            file = ResourcesExtractor.extract(path);

                            if (ResourcesExtractor.isResourceInJar(path)) {
                                tmpExtractedFiles.put(path, file);
                                logger.info("embedded static resources {} extracted in {}", path, file.toString());
                            }
                        } catch (URISyntaxException | IOException ex) {
                            logger.error("error extracting embedded static resource {}", path, ex);
                            return;
                        } catch (IllegalStateException ex) {
                            logger.error("error extracting embedded static resource {}", path, ex);

                            if ("browser".equals(path)) {
                                logger.error("**** did you downloaded the browser submodule before building?");
                                logger.error("**** to fix, run this command: $ git submodule update --init --recursive");
                            }
                            return;
                        }
                    } else {
                        if (!path.startsWith("/")) {
                            // this is to allow specifying the configuration file path relative to the jar (also working when running from classes)
                            URL location = Bootstrapper.class.getProtectionDomain().getCodeSource().getLocation();
                            File locationFile = new File(location.getPath());
                            file = new File(locationFile.getParent() + File.separator + path);
                        } else {
                            file = new File(path);
                        }
                    }

                    ResourceHandler handler = resource(new FileResourceManager(file, 3)).addWelcomeFiles(welcomeFile).setDirectoryListingEnabled(false);

                    if (secured) {
//                        paths.addPrefixPath(where, new SecurityHandler(new PipedWrappingHandler(null, handler), identityManager, accessManager));
//                        paths.addPrefixPath(where, HandlerHelper.requireAuthentication(handler, pacConfig(), "Google2Client", false));
//                        paths.addPrefixPath(where, new AccessManagerHandler(accessManager, new PipedWrappingHandler(null, HandlerHelper.requireAuthentication(handler, pacConfig(identityManager), "Google2Client", false))));
                        paths.addPrefixPath(where, HandlerHelper.requireAuthentication(new AccessManagerHandler(accessManager, new PipedWrappingHandler(null, handler)), pacConfig(identityManager), "Google2Client", false));
                    } else {
                        paths.addPrefixPath(where, handler);
                    }

                    logger.info("url {} bound to static resources {}. access manager: {}", where, path, secured);

                } catch (Throwable t) {
                    logger.error("cannot bind static resources to {}", sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHERE_KEY), t);
                }
            });
        }
    }

    private static Config pacConfig(IdentityManager identityManager) {
        Config config = new Config();
        config.setClients(buildClients(identityManager));
        return config;
    }


    public static Clients buildClients(IdentityManager identityManager) {

        Map<String, Object> pac4j = conf.getPac4j();

        String key = ((Map) pac4j.get("google")).get("key").toString();
        String secret = ((Map) pac4j.get("google")).get("secret").toString();
        String callbackUrl = ((Map) pac4j.get("google")).get("callback").toString();

        final Google2Client google2Client = new Google2Client(key, secret, new RolesManager(identityManager));

        return new Clients(callbackUrl, google2Client);
    }

    private static void pipeApplicationLogicHandlers(Configuration conf, PathHandler paths, IdentityManager identityManager, AccessManager accessManager) {
        if (conf.getApplicationLogicMounts() != null) {
            conf.getApplicationLogicMounts().stream().forEach(al -> {
                        try {
                            String alClazz = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHAT_KEY);
                            String alWhere = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE_KEY);
                            boolean alSecured = (Boolean) al.get(Configuration.APPLICATION_LOGIC_MOUNT_SECURED_KEY);
                            Object alArgs = al.get(Configuration.APPLICATION_LOGIC_MOUNT_ARGS_KEY);

                            if (alWhere == null || !alWhere.startsWith("/")) {
                                logger.error("cannot pipe application logic handler {}. parameter 'where' must start with /", alWhere);
                                return;
                            }

                            if (alArgs != null && !(alArgs instanceof Map)) {
                                logger.error("cannot pipe application logic handler {}. args are not defined as a map. it is a ", alWhere, alWhere.getClass());
                                return;

                            }

                            Object o = Class.forName(alClazz).getConstructor(PipedHttpHandler.class, Map.class
                            ).newInstance(null, (Map) alArgs);

                            if (o instanceof ApplicationLogicHandler) {
                                ApplicationLogicHandler alHandler = (ApplicationLogicHandler) o;

                                PipedHttpHandler handler = new CORSHandler(new RequestContextInjectorHandler("/_logic", "*", alHandler));

                                if (alSecured) {
                                    paths.addPrefixPath("/_logic" + alWhere, new SecurityHandler(handler, identityManager, accessManager));
                                } else {
                                    paths.addPrefixPath("/_logic" + alWhere, handler);
                                }

                                logger.info("url {} bound to application logic handler {}. access manager: {}", "/_logic" + alWhere, alClazz, alSecured);
                            } else {
                                logger.error("cannot pipe application logic handler {}. class {} does not extend ApplicationLogicHandler", alWhere, alClazz);
                            }

                        } catch (Throwable t) {
                            logger.error("cannot pipe application logic handler {}", al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE_KEY), t);
                        }
                    }
            );
        }
    }
}
