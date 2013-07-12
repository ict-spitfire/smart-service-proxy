package eu.spitfire.ssp.gateway;

import com.google.common.util.concurrent.SettableFuture;

import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 01.07.13
 * Time: 11:36
 * To change this template use File | Settings | File Templates.
 */
public class InternalRegisterServiceMessage {

    private SettableFuture<URI> registrationFuture;
    private String servicePath;
    private AbstractGateway gateway;

    public InternalRegisterServiceMessage(SettableFuture<URI> registrationFuture, String servicePath, AbstractGateway gateway) {
        this.registrationFuture = registrationFuture;
        this.servicePath = servicePath;
        this.gateway = gateway;
    }

    public String getServicePath() {
        return servicePath;
    }

    public AbstractGateway getGateway() {
        return gateway;
    }

    public SettableFuture<URI> getRegistrationFuture() {
        return registrationFuture;
    }
}
