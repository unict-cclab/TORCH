package it.unict.vertx.esb.configurewa;

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
import it.unict.vertx.esb.packet.configure.ConfigureWA;
import it.unict.vertx.esb.packet.configure.ConfigureWS;

public class ConfigureWAWordPressAPIVerticle extends AbstractVerticle implements ConfigureWA {
	
	private String keyName, scriptName;

	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		keyName = System.getProperty("user.dir") + config().getString("key.name");
		scriptName = System.getProperty("user.dir") + config().getString("script.name");
				
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per configurare WordPress
		router.post("/wa/configure").handler(this::configure);
		 
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
				.filter(s -> s.matches("^appserver\\d?\\.username"))
				.collect(Collectors.toSet());
		
		Set<String> appAddr = properties.keySet().stream()
				.filter(s -> s.matches("^appserver\\d?\\.address"))
				.collect(Collectors.toSet());
		
		Set<String> dbAddr = properties.keySet().stream()
				.filter(s -> s.matches("^dbserver\\d?\\.address"))
				.collect(Collectors.toSet());
		
		String appUsername = (String) properties.get(usr.toArray()[0]);
		String appAddress = (String) properties.get(appAddr.toArray()[0]);
		String dbAddress = (String) properties.get(dbAddr.toArray()[0]);
		
		//String appUsername = (String) properties.get("appserver.username");
		//String appAddress = (String) properties.get("appserver.address");
		String docRoot = (String) properties.get("doc.root");
		String contextRoot = (String) properties.get("context.root");
		String dbName = (String) properties.get("db.name");
		String dbUsr = (String) properties.get("db.usr");
		String dbPwd = (String) properties.get("db.pwd");
		//String dbAddress = (String) properties.get("dbserver.address");
		int dbPort = (int) properties.get("db.port");
		
		String command = "ssh -o 'UserKnownHostsFile=/dev/null' -o 'StrictHostKeyChecking=no' -i " 
				+ keyName + " " + appUsername + "@" + appAddress + " 'bash -s' < " + scriptName 
				+ " '" + docRoot + "' '" + contextRoot + "' '" + dbName + "' '" + dbUsr 
				+ "' '" + dbPwd + "' '" + dbAddress + "' '" + dbPort + "' | tail -n 1";
				
		vertx.executeBlocking(future -> {
			String result = executeCommand(command);
			future.complete(result);
		}, res -> {
			if (res.succeeded()) {
				JsonObject output = new JsonObject((String) res.result());
				String code = output.getString("code");
				String message = output.getString("message");
				
				ConfigureWA.Status mappedStatus = ConfigureWA.Status.OK; 
				int responseCode = 200;
				
				if (!code.equals("0")) {
					responseCode = 500;
					mappedStatus = ConfigureWA.Status.ERROR;
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
			System.out.println("output: " + output.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
//		JsonObject response = new JsonObject();
//		response.put("code", "0");
//		response.put("message", "{\"context.root\":\"/var/www/html/\"}");
		
		return output.toString();
//		return response.encode();
		
	}

}