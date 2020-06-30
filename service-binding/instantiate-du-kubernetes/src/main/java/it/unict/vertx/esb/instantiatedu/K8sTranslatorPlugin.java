package it.unict.vertx.esb.instantiatedu;

import it.unict.vertx.esb.common.TranslatorPlugin;
import it.unict.vertx.esb.common.translator.utils.Container;
import it.unict.vertx.esb.common.translator.utils.DeploymentUnit;
import it.unict.vertx.esb.common.translator.utils.Volume;

import java.util.Map;

public class K8sTranslatorPlugin implements TranslatorPlugin {
    @Override
    public String translateDu(DeploymentUnit du) {
        String services = "";
        String volumes = "";
        String deployment = "apiVersion: apps/v1\n"+
                "kind: Deployment\n"+
                "metadata:\n" +
                "  name: " + du.getName().replaceAll("_", "-") + "\n" +
                "  labels:\n" +
                "    app: kubernetes-application\n" + // TODO: check the app naming. Maybe use UUID?
                "spec:\n" +
                "  selector:\n" +
                "    matchLabels:\n" +
                "      app: kubernetes-application\n" +
                "      tier: " + du.getName().replaceAll("_", "-") + "-containers" + "\n" +
                "  template:\n" +
                "    metadata:\n" +
                "      labels:\n" +
                "        app: kubernetes-application\n" +
                "        tier: " + du.getName().replaceAll("_", "-") + "-containers" + "\n" +
                "    spec:\n" +
                "      containers:\n";
        for (Container c : du.getContainers())
        {
            deployment += translateContainer(c);

            services += translateService(c, du);

            // For Volumes
            for (Volume v : c.getVolumes())
            {
                volumes += translateVolume(v);
            }
        }

        String s = services + volumes + deployment;
        return s;

    }

    private String translateService(Container c, DeploymentUnit du)
    {
        String servicePort = null;
        String targetPort = null;
        String service ="";
        String port = null;
         
        for (Map.Entry<String,String> entry : c.getProperties().entrySet())
        {
            if(entry.getKey().compareToIgnoreCase("port") == 0) {
                port = entry.getValue();
	            String[] ports = port.split(":");
	            servicePort = ports[0];
	            targetPort = ports[1];
	            break;
            }
        }

        service = service.concat("apiVersion: v1\n" +
                "kind: Service\n" +
                "metadata:\n" +
                "  name: " + c.getName().replaceAll("_", "-").concat("-service") + "\n" +
                "  labels:\n" +
                "    app: kubernetes-application\n" +
                "spec:\n");    
        
        if(c.getCategory().compareTo("wa") == 0)
            service = service.concat("  type: NodePort\n");
        service = service.concat("  ports:\n");
        if( port == null)
            service = service.concat("  - port: 80\n");
        else {
            service = service.concat("    - port: " + servicePort + "\n");
            service = service.concat("      targetPort: " + targetPort + "\n");
        }
        service = service.concat("  selector:\n" +
                "    app: kubernetes-application\n" +
                "    tier: " + du.getName().replaceAll("_", "-") + "-containers\n");
        service += "---\n";
        return service;
    }

    private String translateVolume(Volume v)
    {
        String s = "apiVersion: v1\n" +
                "kind: PersistentVolumeClaim\n" +
                "metadata:\n" +
                "  name: "+ v.getName().replaceAll("_","-").concat("-claim") + "\n" +
                "  labels:\n" +
                "    app: kubernetes-application\n" +
                "spec:\n" +
                "  accessModes:\n" +
                "    - ReadWriteOnce\n" +
                "  resources:\n" +
                "    requests:\n";
        String storage = null;
        for (Map.Entry<String,String> entry : v.getProperties().entrySet())
        {
            if(entry.getKey().compareToIgnoreCase("size") == 0)
                storage = entry.getValue().replaceAll("[b|B]", "");
        }
        if( storage == null)
            s = s.concat("      storage: 1G\n");
        else
            s = s.concat("      storage: " + storage.replaceAll(" ", "") + "\n");
        s = s.concat("---\n");
        return s;
    }

    private String translateContainer(Container c)
    {
        String targetPort = null;    	
    	
        String s = "      - image: "+ c.getImage() +"\n" +
                "        name: " + c.getName().replaceAll("_", "-") +"\n" +
                "        env:\n";
        String port = null;
        // using for-each loop for iteration over Map.entrySet()
        for (Map.Entry<String,String> entry : c.getProperties().entrySet())
        {
            if(entry.getKey().compareToIgnoreCase("port") == 0)
            {
                port = entry.getValue();
                String[] ports = port.split(":");
                targetPort = ports[1];                
                continue;
            }
            String value = entry.getValue();
            if (value.equals("true")) value = "\"true\"";
            if (value.equals("false")) value = "\"false\"";
            s = s.concat("        - name: " + entry.getKey().toUpperCase() + "\n" +
                    "          value: " + value  + "\n"
            );
        }
        // DEPRECATED        
//        for (Map.Entry<String,String> entry : c.getExt_requirements().entrySet())
//        {
//            s = s.concat("        - name: " + entry.getKey().toUpperCase() + "\n" +
//                    "          value: " + entry.getValue().replaceAll("_", "-").concat("-service") + "\n"
//            );
//        }
        if (targetPort != null)
        {
            String portName = c.getName().replaceAll("_", "-");
            if (portName.length() > 15)
                portName = portName.substring(0, 15);
            s = s.concat("        ports:\n" +
                    "        - containerPort: "+ targetPort + "\n" +
                    "          name: " +  portName + "\n"
            );
        }
        for (Volume v : c.getVolumes())
        {
            s = s.concat("        volumeMounts:\n" +
                    "        - name: " + c.getName().replaceAll("_", "-").concat("-persistent-storage") + "\n");
            for (Map.Entry<String,String> entry : v.getProperties().entrySet())
            {
                if(entry.getKey().compareToIgnoreCase("location") == 0)
                {
                    s = s.concat("          mountPath: " + entry.getValue() + "\n");
                }
            }
        }
        for (Volume v : c.getVolumes())
        {
            s = s.concat(                "      volumes:\n" +
                    "      - name: " + c.getName().replaceAll("_", "-").concat("-persistent-storage") +"\n" +
                    "        emptyDir:\n" +
                    "          medium: Memory\n");
            // TODO: for true volumes
            //"        persistentVolumeClaim:\n" +
            //"          claimName: " + v.getName().replaceAll("_","-").concat("-claim") + "\n");
        }
        return s;
    }
}
