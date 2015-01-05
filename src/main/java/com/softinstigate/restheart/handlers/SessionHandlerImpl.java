package com.softinstigate.restheart.handlers;

import io.undertow.UndertowMessages;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;

/**
 * Created with IntelliJ IDEA.
 * User: msuchecki
 * Date: 02.01.15
 * Time: 17:45
 */
public class SessionHandlerImpl extends PipedHttpHandler {

	private volatile SessionManager sessionManager;

	private final SessionConfig sessionConfig;

	public SessionHandlerImpl(PipedHttpHandler next, final SessionManager sessionManager, final SessionConfig sessionConfig) {
		super(next);
		this.sessionConfig = sessionConfig;
		if (sessionManager == null) {
			throw UndertowMessages.MESSAGES.sessionManagerMustNotBeNull();
		}
		this.sessionManager = sessionManager;
	}


	@Override
	public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {

		exchange.putAttachment(SessionManager.ATTACHMENT_KEY, sessionManager);
		exchange.putAttachment(SessionConfig.ATTACHMENT_KEY, sessionConfig);
		final UpdateLastAccessTimeListener handler = new UpdateLastAccessTimeListener(sessionConfig, sessionManager);
		exchange.addExchangeCompleteListener(handler);
		next.handleRequest(exchange, context);
	}

	private static class UpdateLastAccessTimeListener implements ExchangeCompletionListener {

		private final SessionConfig sessionConfig;
		private final SessionManager sessionManager;

		private UpdateLastAccessTimeListener(final SessionConfig sessionConfig, final SessionManager sessionManager) {
			this.sessionConfig = sessionConfig;
			this.sessionManager = sessionManager;
		}

		@Override
		public void exchangeEvent(final HttpServerExchange exchange, final NextListener next) {
			try {
				final Session session = sessionManager.getSession(exchange, sessionConfig);
				if (session != null) {
					session.requestDone(exchange);
				}
			} finally {
				next.proceed();
			}
		}
	}
}
