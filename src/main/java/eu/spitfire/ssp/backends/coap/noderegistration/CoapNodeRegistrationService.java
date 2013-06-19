package eu.spitfire.ssp.backends.coap.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.server.webservice.NotObservableWebService;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import org.apache.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is the WebService for new sensor nodes to register at. It's path is <code>/here_i_am</code>. It only accepts
 * {@link CoapRequest}s with code {@link Code#POST}. Any contained payload is ignored.
 *
 * Upon reception of such a request the service sends a {@link CoapRequest} with {@link Code#GET} to the
 * <code>/.well-known/core</code> resource of the sensor node to discover the services available on the new node.
 *
 * @author Oliver Kleine
 */
class CoapNodeRegistrationService extends NotObservableWebService<Boolean> {

    private static Logger log = Logger.getLogger(CoapNodeRegistrationService.class.getName());

    CoapNodeRegistrationService(){
        super("/here_i_am", Boolean.TRUE);
    }

    /**
     * Returns an empty {@link CoapResponse} with the proper {@link Code}
     *
     * @param request The {@link CoapRequest} to be processed
     * @param remoteAddress The address of the sender of the request
     * @return an empty {@link CoapResponse} instance (i.e. without payload) with code
     *          <ul>
     *              <li>
     *                  {@link Code#CREATED_201} if the list of newly available services was successfully discovered
     *              </li>
     *              <li>
     *                  {@link Code#METHOD_NOT_ALLOWED_405} if the request code was not {@link Code#POST}
     *              </li>
     *              <li>
     *                  {@link Code#GATEWAY_TIMEOUT_504} if the service discovery is not finished within 2 minutes
     *              </li>
     *              <li>
     *                  {@link Code#INTERNAL_SERVER_ERROR_500} if another error occured
     *              </li>
     *          </ul>
     */
    @Override
    public CoapResponse processMessage(CoapRequest request, InetSocketAddress remoteAddress) {
        log.info("Process registration message from " + remoteAddress.getAddress());

        //Only POST messages are allowed
        if(request.getCode() != Code.POST){
            return new CoapResponse(Code.METHOD_NOT_ALLOWED_405);
        }

        try {
            log.debug(String.format("Registration request from %s", remoteAddress.getAddress()));
            CoapResourceDiscoverer resourceDiscoverer = new CoapResourceDiscoverer(remoteAddress.getAddress());

            //wait at most 2 minutes to discover new resources
            resourceDiscoverer.getFuture().get(2, TimeUnit.MINUTES);
            return new CoapResponse(Code.CREATED_201);

        }
        catch (InterruptedException e) {
            log.error("Error while waiting for /.well-known/core resource.", e);
            return new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
        }
        catch (ExecutionException e) {
            log.error("Error while waiting for /.well-known/core resource.", e);
            return new CoapResponse(Code.INTERNAL_SERVER_ERROR_500);
        }
        catch (TimeoutException e) {
            log.error("Error while waiting for /.well-known/core resource.", e);
            return new CoapResponse(Code.GATEWAY_TIMEOUT_504);
        }
     }

    @Override
    public void shutdown() {
        //Nothing to do
    }
}
