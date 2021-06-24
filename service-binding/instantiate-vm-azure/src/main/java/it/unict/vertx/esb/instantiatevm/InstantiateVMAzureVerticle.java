package it.unict.vertx.esb.instantiatevm;

import io.vertx.core.DeploymentOptions;
import it.unict.vertx.esb.instantiatevm.InstantiateVMAzureAPIVerticle;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class InstantiateVMAzureVerticle extends MicroServiceVerticle {
	
	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(InstantiateVMAzureAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
	    publishHttpEndpoint("instantiate-vm-azure", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Instantiate VM Azure (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }

}
