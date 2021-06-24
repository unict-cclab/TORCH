package it.unict.vertx.esb.broker;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class ServiceBrokerVerticle extends AbstractVerticle {

	@Override
	  public void start() throws Exception {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(ServiceBrokerAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

//	    // Publish the services in the discovery infrastructure.
//	    publishHttpEndpoint("service-broker", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
//	      if (ar.failed()) {
//	        ar.cause().printStackTrace();
//	      } else {
//	        System.out.println("Service Broker (Rest endpoint) published : " + ar.succeeded());
//	      }
//	    });
	  }
	
}
