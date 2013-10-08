package eu.spitfire.ssp.backends.uberdust;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Forwards and incoming HTTP post request to Uberdust.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustPostRequest extends Thread {
    /**
     * Logger.
     */
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final URL url;

    public UberdustPostRequest(final String url) throws MalformedURLException {
        this.url = new URL(url);
    }

    @Override
    public void run() {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("charset", "utf-8");
            connection.connect();
            connection.getResponseCode();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
