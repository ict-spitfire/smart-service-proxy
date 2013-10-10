package eu.spitfire.ssp.backends.uberdust;

import com.google.common.util.concurrent.SettableFuture;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.backends.generic.exceptions.MultipleSubjectsInModelException;
import eu.spitfire.ssp.backends.generic.messages.InternalResourceStatusMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The {@link UberdustHttpRequestProcessor} is the {@link eu.spitfire.ssp.server.webservices.SemanticHttpRequestProcessor} instance to handle
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
     * The time in milliseconds a status may be cached after a request.
     */
    public static long LIFETIME_MILLIS = 5000;

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

    private void handlePost(SettableFuture<InternalResourceStatusMessage> settableFuture, HttpRequest httpRequest) {

        String uberdustURL = httpRequest.getUri().replaceAll("/\\?uri=", "").replaceAll("attachedSystem", "");
        String payloadString = new String(httpRequest.getContent().toByteBuffer().array());
        if ("on".equals(payloadString)) {
            uberdustURL += "1/";
        } else if ("off".equals(payloadString)) {
            uberdustURL += "0/";
        } else {
            uberdustURL += payloadString + "/";
        }
        System.out.println(uberdustURL);
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
        } else {
            System.out.println("skipping");
        }
    }
}