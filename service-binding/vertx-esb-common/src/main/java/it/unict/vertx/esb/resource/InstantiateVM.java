package it.unict.vertx.esb.resource;

import io.vertx.ext.web.RoutingContext;

public interface InstantiateVM {
	
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
	
	public void createVM(RoutingContext routingContext);

	public void checkVM(RoutingContext routingContext);
}
