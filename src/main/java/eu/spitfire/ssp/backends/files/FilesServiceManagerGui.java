package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.server.webservices.DefaultHttpRequestProcessor;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * The GUI is just an HTML page to show which resources states are stored in which local files.
 *
 * @author Oliver Kleine
*/
public class FilesServiceManagerGui implements DefaultHttpRequestProcessor {

    private FilesObserver filesObserver;

    /**
     * @param filesObserver The instance of {@link FilesObserver} to observe directory
     */
    public FilesServiceManagerGui(FilesObserver filesObserver){
        this.filesObserver = filesObserver;
    }

    @Override
    public void processHttpRequest(SettableFuture<HttpResponse> responseFuture, HttpRequest httpRequest) {

        HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(),
                HttpResponseStatus.OK);

        ChannelBuffer content = getHtmlContent();


        httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");

        httpResponse.setContent(content);

        responseFuture.set(httpResponse);
    }

    private ChannelBuffer getHtmlContent(){
        StringBuilder buf = new StringBuilder();
        buf.append("<html><body>\n");
        buf.append(String.format("<h2>Resources from files in %s</h2>\n", filesObserver.getObservedDirectory()));

        for(Object filePath : filesObserver.getObservedFiles().toArray()){
            buf.append(String.format("<h3>%s</h3>\n", filePath));
            buf.append("<ul>\n");
            Object[] resources = filesObserver.getObservedResources((Path) filePath).toArray();
            for(Object resource : resources){
                buf.append(String.format("<li>%s</li>\n", resource));
            }
            buf.append("</ul>\n");
        }

        buf.append("</body></html>\n");

        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
    }
}
