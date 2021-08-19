package com.japharr.barman.verticle;

import com.japharr.barman.service.proxy.BarmanService;
import com.japharr.barman.service.proxy.impl.BarmanServiceImpl;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.jwt.authorization.JWTAuthorization;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.serviceproxy.ServiceAuthInterceptor;
import io.vertx.serviceproxy.ServiceBinder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BarmanVerticle extends AbstractVerticle {
  @Override
  public void start() throws Exception {
    BarmanService service = new BarmanServiceImpl(WebClient.create(vertx));

    initJwtAuth(r -> {
      JWTAuth jwtAuth = r.result();

      new ServiceBinder(vertx)
        .setAddress("beers.service.app")
        .addInterceptor(new ServiceAuthInterceptor()
          .setAuthenticationProvider(jwtAuth)
          .addAuthorization(PermissionBasedAuthorization.create("USER"))
          .setAuthorizationProvider(JWTAuthorization.create("realm_access/roles"))
        )
        .register(BarmanService.class, service);
    });
  }

  public void initJwtAuth(Handler<AsyncResult<JWTAuth>> handler) {
    System.out.println("initJwtAuth fetched...");

    var webClient = WebClient.create(vertx);

    var jwtConfig = new JsonObject().put("issuer", "http://localhost:7070/auth/realms/myrealm"); //config.getJsonObject("jwt");
    var issuer = jwtConfig.getString("issuer");
    System.out.println("issuer: " + issuer);
    var issuerUri = URI.create(issuer);

    // derive JWKS uri from Keycloak issuer URI
    var jwksUri = URI.create(jwtConfig.getString("jwksUri", String.format("%s://%s:%d%s",
      issuerUri.getScheme(), issuerUri.getHost(), issuerUri.getPort(), issuerUri.getPath() + "/protocol/openid-connect/certs")));

    webClient.get(jwksUri.getPort(), jwksUri.getHost(), jwksUri.getPath())
      .as(BodyCodec.jsonObject())
      .send(ar -> {
        if (!ar.succeeded()) {
          //startup.bootstrap.fail(String.format("Could not fetch JWKS from URI: %s", jwksUri));
          handler.handle(Future.failedFuture(String.format("Could not fetch JWKS from URI: %s", jwksUri)));
          System.out.println("could not fetch jwks");
          return;
        }

        var response = ar.result();

        var jwksResponse = response.body();
        var keys = jwksResponse.getJsonArray("keys");

        // Configure JWT validation options
        var jwtOptions = new JWTOptions();
        jwtOptions.setIssuer(issuer);

        // extract JWKS from keys array
        var jwks = ((List<Object>) keys.getList()).stream()
          .map(o -> new JsonObject((Map<String, Object>) o))
          .collect(Collectors.toList());

        // configure JWTAuth
        var jwtAuthOptions = new JWTAuthOptions();
        jwtAuthOptions.setJwks(jwks);
        jwtAuthOptions.setJWTOptions(jwtOptions);

        JWTAuth jwtAuth = JWTAuth.create(vertx, jwtAuthOptions);
        handler.handle(Future.succeededFuture(jwtAuth));
      });
  }
}
