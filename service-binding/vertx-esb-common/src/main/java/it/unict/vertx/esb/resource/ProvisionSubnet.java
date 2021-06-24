package it.unict.vertx.esb.resource;

import io.vertx.ext.web.RoutingContext;

public interface ProvisionSubnet {

	public void createSubnet(RoutingContext routingContext);
	
	public void checkSubnet(RoutingContext routingContext);
}
