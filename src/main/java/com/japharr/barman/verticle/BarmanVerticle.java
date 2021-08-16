package com.japharr.barman.verticle;

import com.japharr.barman.service.proxy.BarmanService;
import com.japharr.barman.service.proxy.impl.BarmanServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import io.vertx.serviceproxy.ServiceBinder;

public class BarmanVerticle extends AbstractVerticle {
  @Override
  public void start() throws Exception {
    BarmanService service = new BarmanServiceImpl(WebClient.create(vertx));

    new ServiceBinder(vertx)
      .setAddress("beers.service.app")
      .register(BarmanService.class, service);
  }
}
