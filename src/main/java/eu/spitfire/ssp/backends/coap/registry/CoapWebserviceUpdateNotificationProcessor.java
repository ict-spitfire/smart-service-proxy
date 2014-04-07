package eu.spitfire.ssp.backends.coap.registry;

import de.uniluebeck.itm.ncoap.communication.observe.client.UpdateNotificationProcessor;
import eu.spitfire.ssp.backends.coap.CoapBackendComponentFactory;
import eu.spitfire.ssp.backends.coap.CoapWebserviceResponseProcessor;

import java.net.URI;

/**
 * Created by olli on 07.04.14.
 */
public class CoapWebserviceUpdateNotificationProcessor extends CoapWebserviceResponseProcessor implements
        UpdateNotificationProcessor {

    public CoapWebserviceUpdateNotificationProcessor(CoapBackendComponentFactory backendComponentFactory,
                                                     URI dataOrigin, URI resourceUri) {

        super(backendComponentFactory, dataOrigin, resourceUri);
    }


    @Override
    public boolean continueObservation() {
        return true;
    }
}
