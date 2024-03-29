package com.japharr.barman.service.proxy;

import com.japharr.barman.model.Beer;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@VertxGen
@ProxyGen
public interface BarmanService {
  void giveMeARandomBeer(String customerName, Handler<AsyncResult<Beer>> handler); // (2)

  void getMyBill(String customerName, Handler<AsyncResult<Integer>> handler); // (3)

  void payMyBill(String customerName); // (4)

  static BarmanService createProxy(Vertx vertx, String address) { // (5)
    return new BarmanServiceVertxEBProxy(vertx, address);
  }
}
