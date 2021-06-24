package it.unict.vertx.esb.instantiatek8scluster;

import io.vertx.core.DeploymentOptions;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class InstantiateK8sClusterAzureVerticle extends MicroServiceVerticle {
	
	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(InstantiateK8sClusterAzureAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
	    publishHttpEndpoint("instantiate-cluster-azure-kubernetes", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {	    
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Instantiate K8s Cluster Azure (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }

}
