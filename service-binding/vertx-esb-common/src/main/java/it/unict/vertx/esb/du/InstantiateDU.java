package it.unict.vertx.esb.du;

import io.vertx.ext.web.RoutingContext;

public interface InstantiateDU {
	
	public enum Status {
		OK,
		WIP,
		ERROR,
		UNRECOGNIZED;
		
		public static Status forValue(String value) {
			if (value != null) {
				for (Status s : Status.values()) {
					if (s.name().equalsIgnoreCase(value))
						return s;
				}
			}
			return Status.UNRECOGNIZED;
		}
		
		public String value() {
	        return name().toLowerCase();
		}
	}
	
	public void createDU(RoutingContext routingContext);

	public void checkDU(RoutingContext routingContext);
	
	public void configureDU(RoutingContext routingContext);
}
