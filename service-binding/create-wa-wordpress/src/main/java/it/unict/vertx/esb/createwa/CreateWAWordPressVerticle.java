package it.unict.vertx.esb.createwa;

import io.vertx.core.DeploymentOptions;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class CreateWAWordPressVerticle extends MicroServiceVerticle {
	
	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(CreateWAWordPressAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
	    publishHttpEndpoint("create-wa-wordpress", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Create WA WordPress (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }

}
