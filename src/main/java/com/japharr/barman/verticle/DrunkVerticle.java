package com.japharr.barman.verticle;

import com.japharr.barman.service.proxy.BarmanService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class DrunkVerticle extends AbstractVerticle {
  @Override
  public void start(Promise<Void> startPromise) {
    BarmanService service = BarmanService.createProxy(vertx, "beers.service.app");

    service.giveMeARandomBeer("homer", b1 -> {
      if (b1.failed()) {
        System.err.println("Cannot get my first beer!");
        startPromise.fail(b1.cause());
        return;
      }
      System.out.println("My first beer is a " + b1.result() + " and it costs " + b1.result().getPrice() + "€");

      vertx.setTimer(1500, l -> {
        service.giveMeARandomBeer("homer", b2 -> {
          if(b2.failed()) {
            System.err.println("Cannot get my second beer!");
            startPromise.fail(b1.cause());
            return;
          }
          System.out.println("My second beer is a " + b2.result() + " and it costs " + b2.result().getPrice() + "€"); // (6)
          service.getMyBill("homer", billAr -> {
            System.out.println("My bill with the bar is " + billAr.result()); // (7)
            service.payMyBill("homer"); // (8)
            startPromise.complete();
          });
        });
      });
    });
  }
}
