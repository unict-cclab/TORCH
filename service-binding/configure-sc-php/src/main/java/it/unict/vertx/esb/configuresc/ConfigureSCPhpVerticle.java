package it.unict.vertx.esb.configuresc;

import io.vertx.core.DeploymentOptions;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class ConfigureSCPhpVerticle extends MicroServiceVerticle {
	
	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(ConfigureSCPhpAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
	    publishHttpEndpoint("configure-sc-php", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Configure SC PHP (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }	

}
