package it.unict.vertx.esb.broker;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.servicediscovery.types.HttpEndpoint;
import it.unict.vertx.esb.common.MicroServiceVerticle;
import it.unict.vertx.esb.packet.configure.ConfigureSC;
import it.unict.vertx.esb.packet.configure.ConfigureWA;
import it.unict.vertx.esb.packet.start.StartDB;
import it.unict.vertx.esb.packet.start.StartDBMS;
import it.unict.vertx.esb.packet.start.StartSC;
import it.unict.vertx.esb.packet.start.StartWA;
import it.unict.vertx.esb.packet.start.StartWS;

public class ServiceBrokerAPIVerticle extends MicroServiceVerticle {
	private HttpClient instantiateVM, addStorage;
	private HttpClient createDBMS, configureDBMS;
	private HttpClient createDB, configureDB;
	private HttpClient createWS, configureWS;
	private HttpClient createWA, configureWA;
	private HttpClient createSC;
//	private HttpClient instantiateDU, instantiateCluster;
	private Map<String, HttpClient> instantiateDU, instantiateCluster;
	
	private int statusCode;
	
	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		discoverServices(future);
		
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());
		 
//		 // Inizializzazione della sessione (in-memory e basata su cookie)
//		 router.route().handler(CookieHandler.create());
//		 router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
		 		 
		 // API per creare risorse e controllarne lo stato
		 router.post("/resources").handler(this::createResource);
		 router.get("/resources/:category/:id").handler(this::checkResource);

		 // API per creare deployment unit, controllarne lo stato e configurarle
		 router.post("/dus/create").handler(this::createDU);
		 router.post("/dus/check").handler(this::checkDU);
		 router.post("/dus/configure").handler(this::configureDU);
		 
		 // API per creare, configurare e avviare pacchetti software
		 router.post("/packets/create").handler(this::createPacket);
		 router.post("/packets/configure").handler(this::configurePacket);
		 router.post("/packets/start").handler(this::startPacket);
		 
		 vertx.createHttpServer().requestHandler(router::accept)
		 	.listen(config().getInteger("http.port"), ar -> {
	          if (ar.succeeded()) {
	            System.out.println("Server started");
	          } else {
	            System.out.println("Cannot start the server: " + ar.cause());
	          }
	        });
	}
	
	private void discoverServices(Future<Void> future) {
//		// Discovery del servizio InstantiateVM
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "instantiate-vm-openstack"),
//				client -> {
//					if (client.failed()) {
//						future.fail("InstantiateVM discovery failed: " + client.cause());
//					} else {
//						instantiateVM = client.result();
//						System.out.println("InstantiateVM discovery succeeded");
//					}
//				});
		
		instantiateCluster = new HashMap<String, HttpClient>();
		instantiateDU = new HashMap<String, HttpClient>();
		
		// Discovery del servizio InstantiateDU
		HttpEndpoint.getClient(
				discovery,
				new JsonObject().put("name", "instantiate-du-kubernetes"),
				client -> {
					if (client.failed()) {
						future.fail("InstantiateDU-Kubernetes discovery failed: " + client.cause());
					} else {
						//instantiateDU = client.result();
						instantiateDU.put("kubernetes", client.result());
						System.out.println("InstantiateDU-Kubernetes discovery succeeded");
					}
				});

		// Discovery del servizio InstantiateCluster
		HttpEndpoint.getClient(
				discovery,
				new JsonObject().put("name", "instantiate-k8s-cluster-openstack"),
				client -> {
					if (client.failed()) {
						future.fail("InstantiateCluster-Kubernetes discovery failed: " + client.cause());
					} else {
						//instantiateCluster = client.result();
						instantiateCluster.put("kubernetes", client.result());
						System.out.println("InstantiateCluster-Kubernetes discovery succeeded");
					}
				});
		
		// Discovery del servizio InstantiateCluster
		HttpEndpoint.getClient(
				discovery,
				new JsonObject().put("name", "instantiate-swarm-cluster-openstack"),
				client -> {
					if (client.failed()) {
						future.fail("InstantiateCluster-Swarm discovery failed: " + client.cause());
					} else {
						//instantiateCluster = client.result();
						instantiateCluster.put("swarm", client.result());
						System.out.println("InstantiateCluster-Swarm discovery succeeded");
					}
				});
		
		// Discovery del servizio InstantiateDU
		HttpEndpoint.getClient(
				discovery,
				new JsonObject().put("name", "instantiate-du-swarm"),
				client -> {
					if (client.failed()) {
						future.fail("InstantiateDU-Swarm discovery failed: " + client.cause());
					} else {
						//instantiateDU = client.result();
						instantiateDU.put("swarm", client.result());
						System.out.println("InstantiateDUSwarm-Swarm discovery succeeded");
					}
				});

//		// Discovery del servizio AddStorage
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "add-storage-openstack"),
//				client -> {
//					if (client.failed()) {
//						future.fail("AddStorage discovery failed: "
//								+ client.cause());
//					} else {
//						addStorage = client.result();
//						System.out.println("AddStorage discovery succeeded");
//					}
//				});
//
//		// Discovery del servizio CreateDBMSMySql
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "create-dbms-mysql"),
//				client -> {
//					if (client.failed()) {
//						future.fail("CreateDBMS discovery failed: "
//								+ client.cause());
//					} else {
//						createDBMS = client.result();
//						System.out.println("CreateDBMS discovery succeeded");
//					}
//				});
//
//		// Discovery del servizio ConfigureDBMSMySql
//		HttpEndpoint
//				.getClient(
//						discovery,
//						new JsonObject().put("name", "configure-dbms-mysql"),
//						client -> {
//							if (client.failed()) {
//								future.fail("ConfigureDBMS discovery failed: "
//										+ client.cause());
//							} else {
//								configureDBMS = client.result();
//								System.out
//										.println("ConfigureDBMS discovery succeeded");
//							}
//						});
//
//		// Discovery del servizio CreateDBMySql
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "create-db-mysql"),
//				client -> {
//					if (client.failed()) {
//						future.fail("CreateDB discovery failed: "
//								+ client.cause());
//					} else {
//						createDB = client.result();
//						System.out.println("CreateDB discovery succeeded");
//					}
//				});
//
//		// Discovery del servizio ConfigureDBMySql
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "configure-db-mysql"),
//				client -> {
//					if (client.failed()) {
//						future.fail("ConfigureDB discovery failed: "
//								+ client.cause());
//					} else {
//						configureDB = client.result();
//						System.out.println("ConfigureDB discovery succeeded");
//					}
//				});
//
//		// Discovery del servizio CreateWSApache
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "create-ws-apache"),
//				client -> {
//					if (client.failed()) {
//						future.fail("CreateWS discovery failed: "
//								+ client.cause());
//					} else {
//						createWS = client.result();
//						System.out.println("CreateWS discovery succeeded");
//					}
//				});
//
//		// Discovery del servizio ConfigureWSApache
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "configure-ws-apache"),
//				client -> {
//					if (client.failed()) {
//						future.fail("ConfigureWS discovery failed: "
//								+ client.cause());
//					} else {
//						configureWS = client.result();
//						System.out.println("ConfigureWS discovery succeeded");
//					}
//				});
//
//		// Discovery del servizio CreateSCPHP
//		HttpEndpoint.getClient(
//				discovery,
//				new JsonObject().put("name", "create-sc-php"),
//				client -> {
//					if (client.failed()) {
//						future.fail("CreateSCPhp discovery failed: "
//								+ client.cause());
//					} else {
//						createSC = client.result();
//						System.out.println("CreateSCPhp discovery succeeded");
//					}
//				});
//
//		// Discovery del servizio CreateWAWordPress
//		HttpEndpoint.getClient(discovery, new JsonObject().put("name",
//				"create-wa-wordpress"), client -> {
//			if (client.failed()) {
//				future.fail("CreateWAWordPress discovery failed: "
//						+ client.cause());
//			} else {
//				createWA = client.result();
//				System.out.println("CreateWAWordPress discovery succeeded");
//			}
//		});
//
//		// Discovery del servizio ConfigureWAWordPress
//		HttpEndpoint.getClient(discovery, new JsonObject().put("name",
//				"configure-wa-wordpress"), client -> {
//			if (client.failed()) {
//				future.fail("ConfigureWAWordPress discovery failed: "
//						+ client.cause());
//			} else {
//				configureWA = client.result();
//				System.out.println("ConfigureWAWordPress discovery succeeded");
//			}
//		});
		
	}
	 
	private void createResource(RoutingContext routingContext) {
		Future<String> future = Future.future();
		
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		String category = requestBody.getString("category");
//		String businessKey = requestBody.getString("businessKey");
		JsonObject properties = requestBody.getJsonObject("properties");
		String platform = properties.getString("platform");
		
		if(name == null || category == null || properties == null)
			// Se i parametri sono vuoti, allora viene restituita una Bad Request
			routingContext.response().setStatusCode(400).end();
		
//		// TODO: Recupero della sessione. Aspetti da chiarire:
//		// 1) la sessione e' veramente utile per la gestione dello scenario?
//		// 2) la business key e' sufficiente?
//		Session session = routingContext.session();
//		if (session.get("businessKey") == null)
//			session.put("businessKey", businessKey);
		
		JsonObject body = new JsonObject()
			.put("name", name)
			.put("properties", properties);		
		
		switch(category) {
		case "vm": 
			instantiateVM.post("/vms", response -> {
				response.exceptionHandler(future::fail); /* Eccezione sulla response da gestire */
				
				statusCode = response.statusCode();
				response.bodyHandler(buffer -> {
					future.complete(buffer.toString());
				});
				
		  })
			.exceptionHandler(future::fail) /* Eccezione sulla request da gestire */
//			.exceptionHandler(x -> future.fail(x)) /* Eccezione sulla request da gestire */
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(body.encode());
//			.setChunked(true)
//			.write(body.encode()) /* Inserimento del body nella richiesta */
//			.end();
			break;
		case "storage":
			addStorage.post("/volumes", response -> {
				response.exceptionHandler(future::fail);
				
				statusCode = response.statusCode();
				response.bodyHandler(buffer -> {
					future.complete(buffer.toString());
				});
			})
			.exceptionHandler(future::fail)
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(body.encode());
			break;
		case "cluster":
			//instantiateCluster.post("/clusters", response -> {
			instantiateCluster.get(platform).post("/clusters", response -> {
				response.exceptionHandler(future::fail);

				statusCode = response.statusCode();
				response.bodyHandler(buffer -> {
					future.complete(buffer.toString());
				});
			})
					.exceptionHandler(future::fail)
					.putHeader("content-type", "application/json; charset=utf-8")
					.end(body.encode());
			break;
		case "network": break;
		case "subnet": break;
		}
		
		future.setHandler(ar -> {
			/* Controllo se il future è in stato di failure:  */
//			if (future.failed());		--> Significa che c'e' stato un failure o nella richiesta o nella risposta
//			if(future.succeeded());	--> Significa che la richiesta e' andata a buon fine, ma lo status-code della response potrebbe comunque corrispondere a un errore.
			routingContext.response()
		      .setStatusCode(statusCode)
		      .putHeader("content-type", "application/json; charset=utf-8")
		      .end(future.result());
	        });
		
	}
	
	private void checkResource(RoutingContext routingContext) {
		Future<String> future = Future.future();
		
		String categoryParam = routingContext.request().getParam("category");
		String[] categoryParamSplit = categoryParam.split(":");
		String category = categoryParamSplit[0];
		
		String id = routingContext.request().getParam("id");
		
		switch(category) {
		case "vm": 
			instantiateVM.get("/vms/" + encode(id), response -> {                              
			    response.exceptionHandler(future::fail); /* Eccezione sulla response da gestire */
			    
			    statusCode = response.statusCode();
			    response.bodyHandler(buffer -> {
			    	future.complete(buffer.toString());
			    });
			})
		    .exceptionHandler(future::fail)  /* Eccezione sulla request da gestire */                                                
		    .end();  			
			break;
		case "storage":
			addStorage.get("/volumes/" + encode(id), response -> {
				response.exceptionHandler(future::fail);
				
				statusCode = response.statusCode();
				response.bodyHandler(buffer -> {
					future.complete(buffer.toString());
				});
			})
			.exceptionHandler(future::fail)
			.end();
			break;
		case "cluster":
			String platform = categoryParamSplit[1];
			//instantiateCluster.get("/clusters/" + encode(id), response -> {
			instantiateCluster.get(platform).get("/clusters/" + encode(id), response -> {
				response.exceptionHandler(future::fail);

				statusCode = response.statusCode();
				response.bodyHandler(buffer -> {
					future.complete(buffer.toString());
				});
			})
					.exceptionHandler(future::fail)
					.end();
			break;
		case "network": break;
		case "subnet": break;
		}
		
		future.setHandler(ar -> {
			routingContext.response()
				.setStatusCode(statusCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(future.result());
	        });
		
	}
	
	private void createPacket(RoutingContext routingContext) {		
		Future<String> future = Future.future();
		
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		String category = requestBody.getString("category");
		JsonObject properties = requestBody.getJsonObject("properties");
		
		if(name == null || category == null || properties == null) {
			// Se i parametri sono vuoti, allora viene restituita una Bad Request
			routingContext.response().setStatusCode(400).end();
		} else {			
			JsonObject body = new JsonObject()
			.put("name", name)
			.put("properties", properties);		
			
			switch(category) {
			case "ws":
				createWS.post("/ws/create", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			case "sc":
				createSC.post("/sc/create", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			case "dbms":
				createDBMS.post("/dbms/create", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			case "db":
				createDB.post("/db/create", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			case "wa":
				createWA.post("/wa/create", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			}
			
			future.setHandler(ar -> {
				routingContext.response()
					.setStatusCode(statusCode)
					.putHeader("content-type", "application/json; charset=utf-8")
					.end(future.result());
		        });
		}
	}
	
	private void configurePacket(RoutingContext routingContext) {		
		Future<String> future = Future.future();
		
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		String category = requestBody.getString("category");
		JsonObject properties = requestBody.getJsonObject("properties");
		
		if(name == null || category == null || properties == null) {
			// Se i parametri sono vuoti, allora viene restituita una Bad Request
			routingContext.response().setStatusCode(400).end();
		} else {
			JsonObject body = new JsonObject()
			.put("name", name)
			.put("properties", properties);			
			
			switch(category) {
			case "ws":
				configureWS.post("/ws/configure", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			case "sc":
				statusCode = 200;
				JsonObject result = new JsonObject();
				
				result.put("status", ConfigureSC.Status.OK.value());
				result.put("message", "");
			
				future.complete(result.encode());
				break;
			case "dbms": 
				configureDBMS.post("/dbms/configure", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			case "db": 
				configureDB.post("/db/configure", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			case "wa":
				configureWA.post("/wa/configure", response -> {
					response.exceptionHandler(future::fail);
					
					statusCode = response.statusCode();
					response.bodyHandler(buffer -> {
						future.complete(buffer.toString());
					});
				})
				.exceptionHandler(future::fail)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(body.encode());
				break;
			}
			
			future.setHandler(ar -> {
				routingContext.response()
					.setStatusCode(statusCode)
					.putHeader("content-type", "application/json; charset=utf-8")
					.end(future.result());
		        });			
		}
	}

	private void startPacket(RoutingContext routingContext) {		
		Future<String> future = Future.future();
		
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		String category = requestBody.getString("category");
		JsonObject properties = requestBody.getJsonObject("properties");
		
		if(name== null || category == null || properties == null) {
			// Se i parametri sono vuoti, allora viene restituita una Bad Request
			routingContext.response().setStatusCode(400).end();
		} else {
			statusCode = 200;
			JsonObject result = new JsonObject();
						
			switch(category) {
			case "ws": 
				result.put("status", StartWS.Status.OK.value());
//				result.put("message", "Apache WS startup suceeded");
				break;
			case "sc": 
				result.put("status", StartSC.Status.OK.value());
//				result.put("message", "PHP startup suceeded");
				break;
			case "dbms":
				result.put("status", StartDBMS.Status.OK.value());
//				result.put("message", "MySQL DBMS startup suceeded");
				break;
			case "db": 
				result.put("status", StartDB.Status.OK.value());
//				result.put("message", "MySQL database startup suceeded");				
				break;
			case "wa": 
				result.put("status", StartWA.Status.OK.value());
//				result.put("message", "WordPress WA startup suceeded");				
				break;
			}
			
			result.put("message", "");	
						
			routingContext.response()
			.setStatusCode(statusCode)
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(result.encode());
			
		}
				
	}
	
	private void createDU(RoutingContext routingContext) {
		Future<String> future = Future.future();
		
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		JsonObject properties = requestBody.getJsonObject("properties");

		// Add check on cluster: is it an existing cluster or must we create it? Add a dashboard-managed parameter
		if(name == null || properties == null)
			// Se i parametri sono vuoti, allora viene restituita una Bad Request
			routingContext.response().setStatusCode(400).end();
		
		String platform = properties.getString("platform");
		
		//instantiateDU.post("/dus/create", response -> {
		instantiateDU.get(platform).post("/dus/create", response -> {
			response.exceptionHandler(future::fail); /* Eccezione sulla response da gestire */
			
			statusCode = response.statusCode();
			response.bodyHandler(buffer -> {
				future.complete(buffer.toString());
			});
			
		})
		.exceptionHandler(future::fail) /* Eccezione sulla request da gestire */
		.putHeader("content-type", "application/json; charset=utf-8")
		.end(requestBody.encode());
		
		future.setHandler(ar -> {
			/* Controllo se il future è in stato di failure:  */
//			if (future.failed());		--> Significa che c'e' stato un failure o nella richiesta o nella risposta
//			if(future.succeeded());	--> Significa che la richiesta e' andata a buon fine, ma lo status-code della response potrebbe comunque corrispondere a un errore.
			routingContext.response()
		      .setStatusCode(statusCode)
		      .putHeader("content-type", "application/json; charset=utf-8")
		      .end(future.result());
	        });
		
	}
	
	private void checkDU(RoutingContext routingContext) {
		Future<String> future = Future.future();
		
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		JsonObject properties = requestBody.getJsonObject("properties");
		//String id = (String) properties.getMap().get("id");
		String id = properties.getString("id");
		
		if(id == null)
			// Se i parametri sono vuoti, allora viene restituita una Bad Request
			routingContext.response().setStatusCode(400).end();
		
		System.out.println("checkDU --> name: " + name + " id: " + id);
		
		JsonObject body = new JsonObject()
		.put("name", name)
		.put("properties", properties);
		
		String platform = properties.getString("platform");
		 
		//instantiateDU.post("/dus/check", response -> {
		instantiateDU.get(platform).post("/dus/check", response -> {
		    response.exceptionHandler(future::fail); /* Eccezione sulla response da gestire */
		    
		    statusCode = response.statusCode();
		    response.bodyHandler(buffer -> {
		    	future.complete(buffer.toString());
		    });
		})
	    .exceptionHandler(future::fail)  /* Eccezione sulla request da gestire */                                                
	    .end(body.encode());  			
		
		future.setHandler(ar -> {
			routingContext.response()
				.setStatusCode(statusCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(future.result());
	        });
		
	}
	
	private void configureDU(RoutingContext routingContext) {
		Future<String> future = Future.future();
		
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		
		JsonObject properties = requestBody.getJsonObject("properties");
		String id = properties.getString("id");
		
		if(id == null)
			// Se i parametri sono vuoti, allora viene restituita una Bad Request
			routingContext.response().setStatusCode(400).end();
		
		System.out.println("configureDU --> name: " + name + " id: " + id);
		
		JsonObject body = new JsonObject()
		.put("name", name)
		.put("id", id);
		
		String platform = properties.getString("platform");
		 
		//instantiateDU.post("/dus/configure", response -> {   
		instantiateDU.get(platform).post("/dus/configure", response -> {   
		    response.exceptionHandler(future::fail); /* Eccezione sulla response da gestire */
		    
		    statusCode = response.statusCode();
		    response.bodyHandler(buffer -> {
		    	future.complete(buffer.toString());
		    });
		})
	    .exceptionHandler(future::fail)  /* Eccezione sulla request da gestire */                                                
	    .end(body.encode());  			
		
		future.setHandler(ar -> {
			routingContext.response()
				.setStatusCode(statusCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(future.result());
	        });
		
	}
	
	private static String encode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported encoding");
		}
	}
	
}
