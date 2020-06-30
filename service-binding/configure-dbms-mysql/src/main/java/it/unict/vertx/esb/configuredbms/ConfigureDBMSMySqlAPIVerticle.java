package it.unict.vertx.esb.configuredbms;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.packet.configure.ConfigureDBMS;
import it.unict.vertx.esb.packet.create.CreateDBMS;

public class ConfigureDBMSMySqlAPIVerticle extends AbstractVerticle implements ConfigureDBMS {

	private String keyName, scriptName;

	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		keyName = System.getProperty("user.dir") + config().getString("key.name");
		scriptName = System.getProperty("user.dir") + config().getString("script.name");
				
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per configurare il DBMS
		router.post("/dbms/configure").handler(this::configure);
		 
		vertx.createHttpServer().requestHandler(router::accept)
			.listen(config().getInteger("http.port"), ar -> {
	         if (ar.succeeded()) {
	        	 System.out.println("Server started");
	         } else {
	        	 System.out.println("Cannot start the server: " + ar.cause());
	         }
	      });
		
	}
	
	@Override
	public void configure(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();
		
		Set<String> usr = properties.keySet().stream()
				.filter(s -> s.matches("^dbserver\\d?\\.username"))
				.collect(Collectors.toSet());
		
		Set<String> addr = properties.keySet().stream()
				.filter(s -> s.matches("^dbserver\\d?\\.address"))
				.collect(Collectors.toSet());
		
		String dbUsername = (String) properties.get(usr.toArray()[0]);
		String dbAddress = (String) properties.get(addr.toArray()[0]);
		
		//String dbUsername = (String) properties.get("dbserver.username");
		//String dbAddress = (String) properties.get("dbserver.address");
		int dbPort = (int) properties.get("db.port");
		
		String command = "ssh -o 'UserKnownHostsFile=/dev/null' -o 'StrictHostKeyChecking=no' -i " 
		+ keyName + " " + dbUsername + "@" + dbAddress + " 'bash -s' < " 
		+ scriptName + " '" + dbPort + "' '" + dbAddress + "' | tail -n 1";
		
		vertx.executeBlocking(future -> {
					String result = executeCommand(command);
					future.complete(result);
				}, res -> {
					if (res.succeeded()) {
						JsonObject output = new JsonObject((String) res.result());
						String code = output.getString("code");
						String message = output.getString("message");
						
						ConfigureDBMS.Status mappedStatus = ConfigureDBMS.Status.OK; 
						int responseCode = 200;
						
						if (!code.equals("0")) {
							responseCode = 500;
							mappedStatus = ConfigureDBMS.Status.ERROR;
						}
					
						JsonObject responseBody = new JsonObject();
						responseBody.put("status", mappedStatus.value());
						responseBody.put("message", message);
						
						routingContext.response()
							.setStatusCode(responseCode)
						    .putHeader("content-type", "application/json; charset=utf-8")
						    .end(responseBody.encode());
					}
				});
		
	}
	
	private String executeCommand(String command) {
		StringBuffer output = new StringBuffer();
		
		try {
			Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});			
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line);
			}
			
			process.waitFor();
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
		return output.toString();
		
	}

}
