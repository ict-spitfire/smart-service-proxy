//package eu.spitfire.ssp.backends.generic;
//
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.common.util.concurrent.SettableFuture;
//import eu.spitfire.ssp.server.webservices.HttpWebservice;
//import eu.spitfire.ssp.utils.HttpResponseFactory;
//import org.jboss.netty.buffer.ChannelBuffer;
//import org.jboss.netty.buffer.ChannelBuffers;
//import org.jboss.netty.channel.*;
//import org.jboss.netty.handler.codec.http.*;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//import java.nio.charset.Charset;
//import java.util.Collection;
//
///**
// * Created with IntelliJ IDEA.
// * User: olli
// * Date: 17.09.13
// * Time: 17:49
// * To change this template use File | Settings | File Templates.
// */
//public class WebserviceForGraphsList<T> extends HttpWebservice {
//
//    private String backendName;
//    private Collection<DataOrigin<T>> dataOrigins;
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//
//
//    public WebserviceForGraphsList(String backendName, Collection<DataOrigin<T>> dataOrigins){
//        this.backendName = backendName;
//        this.dataOrigins = dataOrigins;
//    }
//
//
//    @Override
//    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {
//
//        log.debug("Incmoing HTTP request for URI {}", httpRequest.getUri());
//
//        HttpResponse httpResponse;
//
//        try{
//            httpResponse = new DefaultHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.OK);
//            ChannelBuffer content = getHtmlContent();
//            httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
//            httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");
//            httpResponse.setContent(content);
//        }
//
//        catch(Exception ex){
//            httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
//                    HttpResponseStatus.INTERNAL_SERVER_ERROR, ex);
//        }
//
//        ChannelFuture future = Channels.write(channel, httpResponse, clientAddress);
//        future.addListener(ChannelFutureListener.CLOSE);
//    }
//
//
//    private ChannelBuffer getHtmlContent(){
//        StringBuilder buf = new StringBuilder();
//        buf.append("<html><body>\n");
//
//        buf.append(String.format("<h2>Data Origins registered at backend: %s</h2>\n", this.backendName));
//
//        buf.append("<ol>\n");
//        for(DataOrigin<T> dataOrigin : dataOrigins){
//            buf.append("\t<li>\n");
//            buf.append("\t\t").append(String.format("Data Origin:\t%s", dataOrigin.getIdentifier())).append("\n");
//            buf.append("\t\t").append(String.format("Graph Name:\t%s", dataOrigin.getGraphName())).append("\n");
//            buf.append("\t</li>\n");
//        }
//        buf.append("</ol>");
//
//        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
//    }
//
//}
