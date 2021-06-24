package it.unict.vertx.esb.packet.configure;

public interface ConfigureDBMS extends Configure {
	
	public enum Status {
		// Stati relativi alla configurazione del DBMS
		OK,
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

}
