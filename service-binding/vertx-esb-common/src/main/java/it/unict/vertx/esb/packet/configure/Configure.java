package it.unict.vertx.esb.packet.configure;

import io.vertx.ext.web.RoutingContext;

public interface Configure {
	
	public void configure(RoutingContext routingContext);

}
