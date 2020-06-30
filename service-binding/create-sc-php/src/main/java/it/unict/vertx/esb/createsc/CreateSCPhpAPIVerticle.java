package it.unict.vertx.esb.createsc;

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
import it.unict.vertx.esb.packet.create.CreateSC;
import it.unict.vertx.esb.packet.create.CreateWS;

public class CreateSCPhpAPIVerticle extends AbstractVerticle implements CreateSC {
	
	private String keyName, scriptName;

	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		keyName = System.getProperty("user.dir") + config().getString("key.name");
		scriptName = System.getProperty("user.dir") + config().getString("script.name");
				
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per installare PHP
		router.post("/sc/create").handler(this::create);
		 
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
	public void create(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();
		
		Set<String> usr = properties.keySet().stream()
				.filter(s -> s.matches("^appserver\\d?\\.username"))
				.collect(Collectors.toSet());
		
		Set<String> addr = properties.keySet().stream()
				.filter(s -> s.matches("^appserver\\d?\\.address"))
				.collect(Collectors.toSet());
		
		String appUsername = (String) properties.get(usr.toArray()[0]);
		String appAddress = (String) properties.get(addr.toArray()[0]);
		
		//String appUsername = (String) properties.get("appserver.username");
		//String appAddress = (String) properties.get("appserver.address");
		
		vertx.executeBlocking(future -> {
			String command = "nc -z -w 2 " + appAddress + " 22 > /dev/null";
			int exitValue = executeCommand(command, true);
			
			while(exitValue != 0) {
				try {
					Thread.sleep(5000);
					exitValue = executeCommand(command, true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			command = "ssh -o 'UserKnownHostsFile=/dev/null' -o 'StrictHostKeyChecking=no' -i " 
					+ keyName + " " + appUsername + "@" + appAddress 
					+ " 'bash -s' < " + scriptName + " | tail -n 1";
			
			String result = executeCommand(command);
			future.complete(result);
		}, res -> {
			if (res.succeeded()) {
				JsonObject output = new JsonObject((String) res.result());
				String code = output.getString("code");
				String message = output.getString("message");
				
				CreateSC.Status mappedStatus = CreateSC.Status.OK; 
				int responseCode = 200;
				
				if (!code.equals("0")) {
					responseCode = 500;
					mappedStatus = CreateSC.Status.ERROR;
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
	
	private int executeCommand(String command, boolean exit) {
		int exitValue = -1;
		try {
			Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});
			process.waitFor();

			exitValue = process.exitValue();
			System.out.println("'executeCommand': exitValue --> " + exitValue);
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
		return exitValue;
	}

}