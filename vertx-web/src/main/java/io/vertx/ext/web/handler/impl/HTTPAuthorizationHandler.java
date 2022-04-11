/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.web.handler.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.web.RoutingContext;

/**
 * This a common handler for auth handler that use the `Authorization` HTTP header.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public abstract class HTTPAuthorizationHandler<T extends AuthenticationProvider> extends AuthenticationHandlerImpl<T> {

  // this should match the IANA registry: https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml
  public enum Type {
    BASIC("Basic"),
    DIGEST("Digest"),
    BEARER("Bearer"),
    // these have no known implementation
    HOBA("HOBA"),
    MUTUAL("Mutual"),
    NEGOTIATE("Negotiate"),
    OAUTH("OAuth"),
    SCRAM_SHA_1("SCRAM-SHA-1"),
    SCRAM_SHA_256("SCRAM-SHA-256");

    private final String label;

    Type(String label) {
      this.label = label;
    }

    public boolean is(String other) {
      return label.equalsIgnoreCase(other);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  protected final Type type;
  protected final String realm;

  public HTTPAuthorizationHandler(T authProvider, Type type, String realm) {
    super(authProvider);
    this.type = type;
    this.realm = realm == null ? null : realm
      // escape quotes
      .replaceAll("\"", "\\\"");

    if (this.realm != null &&
      (this.realm.indexOf('\r') != -1 || this.realm.indexOf('\n') != -1)) {
      throw new IllegalArgumentException("Not allowed [\\r|\\n] characters detected on realm name");
    }
  }

  protected final void parseAuthorization(RoutingContext ctx, Handler<AsyncResult<String>> handler) {
    parseAuthorization(ctx, false, handler);
  }

  protected final void parseAuthorization(RoutingContext ctx, boolean optional, Handler<AsyncResult<String>> handler) {

    final HttpServerRequest request = ctx.request();
    final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);

    if (authorization == null) {
      if (optional) {
        // this is allowed
        handler.handle(Future.succeededFuture());
      } else {
        handler.handle(Future.failedFuture(UNAUTHORIZED));
      }
      return;
    }

    try {
      int idx = authorization.indexOf(' ');

      if (idx <= 0) {
        handler.handle(Future.failedFuture(BAD_REQUEST));
        return;
      }

      if (!type.is(authorization.substring(0, idx))) {
        handler.handle(Future.failedFuture(UNAUTHORIZED));
        return;
      }

      handler.handle(Future.succeededFuture(authorization.substring(idx + 1)));
    } catch (RuntimeException e) {
      handler.handle(Future.failedFuture(e));
    }
  }

  @Override
  public boolean setAuthenticateHeader(RoutingContext context) {
    if (realm != null && realm.length() > 0) {
      context.response()
        .headers()
        .add("WWW-Authenticate", type + " realm=\"" +realm + "\"");

      return true;
    }

    return false;
  }
}
