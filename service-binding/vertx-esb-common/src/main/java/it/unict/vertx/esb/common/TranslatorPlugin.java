package it.unict.vertx.esb.common;

import it.unict.vertx.esb.common.translator.utils.DeploymentUnit;

public interface TranslatorPlugin {
    String translateDu(DeploymentUnit du);
}
