package com.japharr.barman.verticle;

import com.japharr.barman.service.proxy.BarmanService;
import com.japharr.barman.service.proxy.impl.BarmanServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.impl.DiscoveryImpl;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.serviceproxy.ServiceBinder;

public class BarmanVerticle extends AbstractVerticle {
  private ServiceDiscovery discovery;
  private Record publishedRecord;

  @Override
  public void start() throws Exception {
    ServiceDiscovery discovery = new DiscoveryImpl(vertx,
      new ServiceDiscoveryOptions());

    BarmanService service = new BarmanServiceImpl(WebClient.create(vertx));

    new ServiceBinder(vertx)
      .setAddress("beers.service.app")
      .register(BarmanService.class, service);

    Record record = EventBusService.createRecord(
      "barman-service",
      "beers.service.app",
      BarmanService.class
    );

    discovery.publish(record, ar -> {
      if (ar.succeeded()) {
        // publication success
        publishedRecord = ar.result();
      } else {
        // publication failure
        ar.cause().printStackTrace();
      }
    });
  }
}
