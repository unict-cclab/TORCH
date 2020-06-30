package it.unict.vertx.esb.configuredb;

import io.vertx.core.DeploymentOptions;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class ConfigureDBMySqlVerticle extends MicroServiceVerticle {
	
	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(ConfigureDBMySqlAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
	    publishHttpEndpoint("configure-db-mysql", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Configure DB MySql (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }

}
