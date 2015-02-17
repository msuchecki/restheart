package com.softinstigate.restheart.handlers.applicationlogic;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.hal.Representation;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import io.undertow.util.FileUtils;
import io.undertow.util.Headers;
import org.pac4j.undertow.ProfileWrapper;
import org.pac4j.undertow.utils.StorageHelper;

import java.io.File;
import java.io.FileInputStream;
import java.time.YearMonth;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: msuchecki
 * Date: 17.02.15
 * Time: 15:53
 */
public class UploadHandler extends ApplicationLogicHandler {


    private String dbname = "naszadm";
    private String collection = "upload";
    private String path = "./";

    public UploadHandler(PipedHttpHandler next, Map<String, Object> args) {
        super(next, args);

        dbname = args.get("database").toString();
        collection = args.get("collection").toString();
        path = args.get("path").toString();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {

        ProfileWrapper profile = StorageHelper.getProfile(exchange);

        FormDataParser formDataParser = new MultiPartParserDefinition().create(exchange);

        FormData strings = formDataParser.parseBlocking();
        FormData.FormValue file = strings.getFirst("File");


        DBCollection col = CollectionDAO.getCollection(dbname, collection);

        BasicDBObject uploader = new BasicDBObject();
        uploader.append("_id", profile.getProfile().getId());
        uploader.append("name", profile.getProfile().getDisplayName());

        String filePath = path + "/" + YearMonth.now().toString() + "/";

        new File(path).mkdirs();

        DBObject q = BasicDBObjectBuilder.start()
                .add("uploader", uploader)
                .add("originalName", file.getFileName())
                .add("path", filePath)
                .add("contentType", file.getHeaders().getFirst("Content-Type"))
                .add("fileSize", file.getFile().length())
                .get();

        col.save(q);

        String fileId = q.get("_id").toString();

        FileUtils.copyFile(file.getFile(), new File(filePath + fileId));

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Representation.HAL_JSON_MEDIA_TYPE);
        exchange.setResponseCode(HttpStatus.SC_OK);
        exchange.getResponseSender().send(q.toString());
        exchange.endExchange();

    }
}
