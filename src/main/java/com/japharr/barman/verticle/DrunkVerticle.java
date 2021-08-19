package com.japharr.barman.verticle;

import com.japharr.barman.service.proxy.BarmanService;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.serviceproxy.ServiceProxyBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DrunkVerticle extends AbstractVerticle {
  //private BarmanService service;
  private JWTAuth jwtAuth;
  private ServiceProxyBuilder serviceProxyBuilder;

  @Override
  public void start(Promise<Void> startPromise) {
    serviceProxyBuilder = new ServiceProxyBuilder(vertx)
      .setAddress("beers.service.app");

//    service = BarmanService
//      .createProxy(vertx, "beers.service.app");

    //service = serviceProxyBuilder.build(BarmanService.class);


    initJwtAuth(r -> {
      System.out.println("jwk fetched...");
      if(r.succeeded()) {
        this.jwtAuth = r.result();
        startWebApp((http) ->
          completeStartup(http, startPromise)
        );
      } else {
        r.cause().printStackTrace();
      }
    });
  }

  private void operation(RoutingContext rc) {
    var jwtUser =  rc.user();
    var token = jwtUser.principal().getString("access_token");
    System.out.println("token: " + token);
    BarmanService service = serviceProxyBuilder.setToken(token).build(BarmanService.class);
    service.giveMeARandomBeer("homer", b1 -> {
      if (b1.failed()) {
        System.err.println("Cannot get my first beer!");
        //startPromise.fail(b1.cause());
        b1.cause().printStackTrace();
        rc.response().end(b1.cause().getMessage());
        return;
      }
      System.out.println("My first beer is a " + b1.result() + " and it costs " + b1.result().getPrice() + "€");
      rc.response().end("My first beer is a " + b1.result() + " and it costs " + b1.result().getPrice() + "€");

      vertx.setTimer(1500, l -> {
        service.giveMeARandomBeer("homer", b2 -> {
          if(b2.failed()) {
            System.err.println("Cannot get my second beer!");
            //startPromise.fail(b2.cause());
            rc.response().end(b2.cause().getMessage());
            return;
          }
          System.out.println("My second beer is a " + b2.result() + " and it costs " + b2.result().getPrice() + "€"); // (6)
          rc.response().end("My second beer is a " + b2.result() + " and it costs " + b2.result().getPrice() + "€"); // (6)
          service.getMyBill("homer", billAr -> {
            System.out.println("My bill with the bar is " + billAr.result()); // (7)
            rc.response().end("My bill with the bar is " + billAr.result()); // (7)
            service.payMyBill("homer"); // (8)
            //startPromise.complete();
          });
        });
      });
    });
  }

  private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
    Router router = Router.router(vertx);

    router.get("/beer")
      .handler(JWTAuthHandler.create(jwtAuth))
      .handler(this::operation);

    vertx.createHttpServer()
      .requestHandler(router).listen(8790, next);
  }

  private void completeStartup(AsyncResult<HttpServer> http, Promise<Void> startPromise) {
    if (http.succeeded()) {
      System.out.println("the customer are ready to drink");
      //startPromise.complete();
    } else {
      http.cause().printStackTrace();
      //startPromise.fail(http.cause());
    }
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
