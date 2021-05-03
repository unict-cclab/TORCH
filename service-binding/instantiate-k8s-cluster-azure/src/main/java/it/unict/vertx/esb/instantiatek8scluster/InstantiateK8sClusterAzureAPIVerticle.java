package it.unict.vertx.esb.instantiatek8scluster;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import rx.Observable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.resource.InstantiateCluster;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.containerservice.ContainerServiceVMSizeTypes;
import com.microsoft.azure.management.containerservice.KubernetesCluster;
import com.microsoft.azure.management.resources.fluentcore.model.Indexable;
import com.microsoft.azure.management.Azure;
import com.microsoft.rest.LogLevel;

import io.kubernetes.client.util.KubeConfig;
//import io.fabric8.kubernetes.client.Config;

public class InstantiateK8sClusterAzureAPIVerticle extends AbstractVerticle implements InstantiateCluster {

	private String credentialsFilePath, region, resourceGroup;
	private String username, keyName, agentPool, servicePrincipalClientId, servicePrincipalSecret;
	
	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		credentialsFilePath = System.getProperty("user.dir") + config().getString("credentials.file");
		
		region = config().getString("region");
		resourceGroup = config().getString("resource.group");
        username = config().getString("username");
        keyName = config().getString("key.name");
        agentPool = config().getString("agent.pool");

        servicePrincipalClientId= config().getString("service.principal.client.id") ;
        servicePrincipalSecret= config().getString("service.principal.secret");		
		
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per creare istanze e controllarne lo stato
		router.post("/clusters").handler(this::createCluster);
		router.get("/clusters/:id").handler(this::checkCluster);
		 
		vertx.createHttpServer().requestHandler(router::accept)
			.listen(config().getInteger("http.port"), ar -> {
	         if (ar.succeeded()) {
	        	 System.out.println("Cluster started");
	         } else {
	        	 System.out.println("Cannot start the cluster: " + ar.cause());
	         }
	      });
		
	}	
	
	@Override
	public void createCluster(RoutingContext routingContext) {
		
		// Recupero i parametri della richiesta
        JsonObject requestBody = routingContext.getBodyAsJson();
        String name = requestBody.getString("name");
        
        File credentialsFile = new File(credentialsFilePath);
        JsonObject responseBody = new JsonObject();
        int responseCode = 202;
        
		try {
	        AzureTokenCredentials credentials = ApplicationTokenCredentials.fromFile(credentialsFile);

	        //Autenticazione
			Azure azure = Azure
			        .configure()
			        .withLogLevel(LogLevel.NONE)
			        .authenticate(credentials)
			        .withDefaultSubscription();
			
			SSHShell.SshPublicPrivateKey sshKeys = SSHShell.generateSSHKeys("", "key-pair-" + name);
			 			
            KubernetesCluster kubernetesCluster = azure.kubernetesClusters().define(name)
                    .withRegion(region)
                    .withExistingResourceGroup(resourceGroup)
                    .withLatestVersion() // Kubernetes v1.18.14 
                    .withRootUsername(username)
                    .withSshKey(sshKeys.getSshPublicKey())
                    .withServicePrincipalClientId(servicePrincipalClientId)
                    .withServicePrincipalSecret(servicePrincipalSecret)
                    .defineAgentPool(agentPool)
                    	.withVirtualMachineSize(ContainerServiceVMSizeTypes.STANDARD_D1_V2)
                    	.withAgentPoolVirtualMachineCount(2)                 	
                    	.attach()
                    .withDnsPrefix("dns-" + name)
                    .create();
			
//            Observable<Indexable> k8ClusterObservable = azure.kubernetesClusters().define(name)
//                    .withRegion(region)
//                    .withExistingResourceGroup(resourceGroup)
//                    .withLatestVersion() // Kubernetes v1.18.14 
//                    .withRootUsername(username)
//                    .withSshKey(sshKeys.getSshPublicKey())
//                    .withServicePrincipalClientId(servicePrincipalClientId)
//                    .withServicePrincipalSecret(servicePrincipalSecret)
//                    .defineAgentPool(agentPool)
//                    	.withVirtualMachineSize(ContainerServiceVMSizeTypes.STANDARD_D1_V2)
//                    	.withAgentPoolVirtualMachineCount(2)                 	
//                    	.attach()
//                    .withDnsPrefix("dns-" + name)
//                    .createAsync();
//            
//            KubernetesCluster kubernetesCluster = (KubernetesCluster) k8ClusterObservable.map(cluster -> {
//                return cluster;
//            }).toBlocking().last();
            
            String id = kubernetesCluster.id().replace("/", ";");
            String provisioningState = kubernetesCluster.provisioningState();

            if (provisioningState.equals("failed/InternalOperationError")){
                responseBody.put("message", "Failed to create the cluster");
                responseBody.put("details", "");
                responseCode = 500;

            } else{
                responseBody.put("id", id);
            }
            
            routingContext.response().
                    setStatusCode(responseCode).
                    putHeader("content-type", "application/json; charset=utf-8").
                    end(responseBody.encode());            
			
		} catch (Exception e) {
			e.printStackTrace();
			
            responseBody.put("message", "Failed to create the cluster");
            responseBody.put("details", e.getMessage());
			responseCode = 500;
			
			routingContext
			.response()
			.setStatusCode(responseCode)
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(responseBody.encode());			
		}
		
	}

	@Override
	public void checkCluster(RoutingContext routingContext) {
		
		// Recupero i parametri della richiesta
		String id = routingContext.request().getParam("id").replace(";", "/");
				
	    File credentialsFile = new File(credentialsFilePath);
		InstantiateCluster.Status mappedStatus = InstantiateCluster.Status.UNRECOGNIZED;             
        JsonObject responseBody = new JsonObject();
        int responseCode = 200;	    		
		
		try {
  	        AzureTokenCredentials credentials = ApplicationTokenCredentials.fromFile(credentialsFile);

			//Autenticazione
  			Azure azure = Azure
  			        .configure()
  			        .withLogLevel(LogLevel.NONE)
  			        .authenticate(credentials)
  			        .withDefaultSubscription();
  			
            KubernetesCluster kubernetesCluster = azure.kubernetesClusters().getById(id);
            String provisioningState = kubernetesCluster.provisioningState();

            switch (provisioningState) {
                case "Succeeded":
        			mappedStatus = InstantiateCluster.Status.OK;
        			responseBody.put("status", mappedStatus.value());
        			
//                  String userKubeConfig = new String(kubernetesCluster.userKubeConfigContent(), StandardCharsets.UTF_8);
                    String adminKubeConfig = new String(kubernetesCluster.adminKubeConfigContent(), StandardCharsets.UTF_8);                                        
                    KubeConfig kc = KubeConfig.loadKubeConfig(new StringReader(adminKubeConfig));                        
                                        
                    byte[] caBytes = KubeConfig.getDataOrFile(kc.getCertificateAuthorityData(), kc.getCertificateAuthorityFile());
                    byte[] ccBytes = KubeConfig.getDataOrFile(kc.getClientCertificateData(), kc.getClientCertificateFile());                    
                    byte[] ckBytes = KubeConfig.getDataOrFile(kc.getClientKeyData(), kc.getClientKeyFile());
                    
                    responseBody.put("endpoint", kc.getServer());
                    responseBody.put("ca", new String(caBytes));
                    responseBody.put("cert", new String(ccBytes));
                    responseBody.put("key", new String(ckBytes));
                    
//                    Config config = Config.fromKubeconfig(adminKubeConfig);   
//                    String endpoint = config.getMasterUrl();
//                    String ca = config.getCaCertData();
//                    String cert = config.getClientCertData();
//                    String key = config.getClientKeyData();
//                    
//                    responseBody.put("endpoint", endpoint);
//                    responseBody.put("ca", ca);
//                    responseBody.put("cert", cert);
//                    responseBody.put("key", key);
                    
                    break;
                case "failed/InternalOperationError":
                    mappedStatus = InstantiateCluster.Status.ERROR;
                    responseBody.put("status", mappedStatus.value());
                    responseCode = 500;
                    break;
                case "creating":
                    mappedStatus = InstantiateCluster.Status.WIP;
                    responseBody.put("status", mappedStatus.value());
                    responseBody.put("message", "Failed to create the cluster");
        			responseBody.put("details", "");                    
                    break;
        		default:
        			responseBody.put("status", mappedStatus.value());
        			break;                    
            }
            routingContext.response().
                    setStatusCode(responseCode).
                    putHeader("content-type", "application/json; charset=utf-8").
                    end(responseBody.encode());            
  			
		} catch (Exception e) {
			e.printStackTrace();
			
			mappedStatus = InstantiateCluster.Status.ERROR;
            
            responseBody.put("status", mappedStatus.value());
            responseBody.put("message", "Failed to create the cluster");
			responseBody.put("details", e.getMessage());
			responseCode = 500;			
						
			routingContext
			.response()
			.setStatusCode(responseCode)
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(responseBody.encode());			
		}
	}

}
