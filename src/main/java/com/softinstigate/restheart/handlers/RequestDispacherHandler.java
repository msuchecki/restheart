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
package com.softinstigate.restheart.handlers;

import com.softinstigate.restheart.handlers.root.GetRootHandler;
import com.softinstigate.restheart.handlers.collection.DeleteCollectionHandler;
import com.softinstigate.restheart.handlers.collection.GetCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PatchCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PostCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
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
import com.softinstigate.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import static com.softinstigate.restheart.handlers.RequestContext.METHOD;
import static com.softinstigate.restheart.handlers.RequestContext.TYPE;
import com.softinstigate.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare
 */
public class RequestDispacherHandler extends PipedHttpHandler {

    private final PipedHttpHandler rootGet;
    private final PipedHttpHandler dbGet;
    private final PipedHttpHandler dbPut;
    private final PipedHttpHandler dbDelete;
    private final PipedHttpHandler dbPatch;
    private final PipedHttpHandler collectionGet;
    private final PipedHttpHandler collectionPost;
    private final PipedHttpHandler collectionPut;
    private final PipedHttpHandler collectionDelete;
    private final PipedHttpHandler collectionPatch;
    private final PipedHttpHandler documentGet;
    private final PipedHttpHandler documentPut;
    private final PipedHttpHandler documentDelete;
    private final PipedHttpHandler documentPatch;
    private final PipedHttpHandler indexesGet;
    private final PipedHttpHandler indexPut;
    private final PipedHttpHandler indexDelete;

    /**
     * Creates a new instance of RequestDispacherHandler
     *
     * @param rootGet
     * @param dbGet
     * @param dbPut
     * @param dbDelete
     * @param dbPatch
     * @param collectionGet
     * @param collectionPost
     * @param collectionPut
     * @param collectionDelete
     * @param collectionPatch
     * @param documentGet
     * @param documentPut
     * @param documentDelete
     * @param documentPatch
     * @param indexesGet
     * @param indexDelete
     * @param indexPut
     */
    public RequestDispacherHandler(
            PipedHttpHandler rootGet,
            PipedHttpHandler dbGet,
            PipedHttpHandler dbPut,
            PipedHttpHandler dbDelete,
            PipedHttpHandler dbPatch,
            PipedHttpHandler collectionGet,
            PipedHttpHandler collectionPost,
            PipedHttpHandler collectionPut,
            PipedHttpHandler collectionDelete,
            PipedHttpHandler collectionPatch,
            PipedHttpHandler documentGet,
            PipedHttpHandler documentPut,
            PipedHttpHandler documentDelete,
            PipedHttpHandler documentPatch,
            PipedHttpHandler indexesGet,
            PipedHttpHandler indexPut,
            PipedHttpHandler indexDelete
    ) {

        super(null);
        this.rootGet = rootGet;
        this.dbGet = dbGet;
        this.dbPut = dbPut;
        this.dbDelete = dbDelete;
        this.dbPatch = dbPatch;
        this.collectionGet = collectionGet;
        this.collectionPost = collectionPost;
        this.collectionPut = collectionPut;
        this.collectionDelete = collectionDelete;
        this.collectionPatch = collectionPatch;
        this.documentGet = documentGet;
        this.documentPut = documentPut;
        this.documentDelete = documentDelete;
        this.documentPatch = documentPatch;
        this.indexesGet = indexesGet;
        this.indexPut = indexPut;
        this.indexDelete = indexDelete;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getType() == TYPE.ERROR) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (context.getMethod() == METHOD.OTHER) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (context.isReservedResource()) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "reserved resource");
            return;
        }

        if (context.getMethod() == METHOD.GET) {
            switch (context.getType()) {
                case ROOT:
                    rootGet.handleRequest(exchange, context);
                    return;
                case DB:
                    dbGet.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionGet.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentGet.handleRequest(exchange, context);
                    return;
                case COLLECTION_INDEXES:
                    indexesGet.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.POST) {
            switch (context.getType()) {
                case COLLECTION:
                    collectionPost.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.PUT) {
            switch (context.getType()) {
                case DB:
                    dbPut.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionPut.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentPut.handleRequest(exchange, context);
                    return;
                case INDEX:
                    indexPut.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.DELETE) {
            switch (context.getType()) {
                case DB:
                    dbDelete.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionDelete.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentDelete.handleRequest(exchange, context);
                    return;
                case INDEX:
                    indexDelete.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.PATCH) {
            switch (context.getType()) {
                case DB:
                    dbPatch.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionPatch.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentPatch.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
