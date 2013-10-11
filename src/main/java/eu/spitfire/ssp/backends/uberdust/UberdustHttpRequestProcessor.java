package eu.spitfire.ssp.backends.uberdust;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.backends.generic.exceptions.MultipleSubjectsInModelException;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The {@link UberdustHttpRequestProcessor} is the {@link eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor} instance to handle
 * incoming HTTP requests for the simple example resource (<code>http://example.org/JohnSmith</code>.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustHttpRequestProcessor implements SemanticHttpRequestProcessor {
    /**
     * Logger.
     */
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * The observer containing the updated information from Uberdust.
     */
    private UberdustObserver uberdustObserver;
    /**
     * Executor used for communicating back to Uberdust.
     */

    /**
     * @param backendComponentFactory
     * @param uberdustObserver        an instance of an {@link eu.spitfire.ssp.backends.uberdust.UberdustObserver} that retrieves updated measurements.
     * @throws Exception if some error occurred (this should actually never happen!)
     */
    public UberdustHttpRequestProcessor(BackendComponentFactory backendComponentFactory, UberdustObserver uberdustObserver) {
        this.uberdustObserver = uberdustObserver;
    }

    @Override
    public void processHttpRequest(SettableFuture<InternalResourceStatusMessage> settableFuture, HttpRequest httpRequest) {
        log.info("Received request for path {}.", httpRequest.getUri());

        if (httpRequest.getMethod() == HttpMethod.GET) {
            //handle get
//            handleGet(dataOriginResponseFuture, httpRequest);

        } else if (httpRequest.getMethod() == HttpMethod.POST) {
            //handle post
            handlePost(settableFuture, httpRequest);
//        } else {
        }

    }

    /**
     * Handles an incoming post request to an uberdust registered sensor device.
     *
     * @param settableFuture
     * @param httpRequest    the request to be forwarded.
     */
    private void handlePost(SettableFuture<InternalResourceStatusMessage> settableFuture, HttpRequest httpRequest) {
        String uberdustURI = null;
        //contains the url to be used to communicate with Uberdust.
        String uberdustURL;
        try {
//            check for a minified actuator url
            uberdustURI = UberdustNodeHelper.unWrap(httpRequest.getUri().replaceAll("/\\?uri=", ""));
        } catch (URISyntaxException e) {
            log.error(e.getMessage(), e);
            return;
        }
        if (uberdustURI != null) {
            uberdustURL = uberdustURI.toString();
        } else {
            uberdustURL = httpRequest.getUri().replaceAll("/\\?uri=", "");
        }
        uberdustURL = uberdustURL.replaceAll("attachedSystem", "");
        System.out.println(uberdustURL);
        String payloadString = new String(httpRequest.getContent().toByteBuffer().array());
        if ("on".equals(payloadString)) {
            uberdustURL += "1/";
        } else if ("off".equals(payloadString)) {
            uberdustURL += "0/";
        } else {
            uberdustURL += "" + payloadString + "/";
        }
        //disable switching for powerstrips
        if (!uberdustURL.contains("150")) {
            try {
                (new UberdustPostRequest(uberdustURL)).start();
                settableFuture.set(new InternalResourceStatusMessage(ModelFactory.createDefaultModel()));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (MultipleSubjectsInModelException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }
}