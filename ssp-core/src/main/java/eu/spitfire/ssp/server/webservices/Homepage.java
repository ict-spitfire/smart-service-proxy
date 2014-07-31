package eu.spitfire.ssp.server.webservices;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
* Provides a list of all registered services on this proxy server.
*
* @author Oliver Kleine
*/
public class Homepage extends HttpWebservice {

//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public Homepage(ExecutorService ioExecutor, ScheduledExecutorService internalTasksExcecutor){
        super(ioExecutor, internalTasksExcecutor, "html/homepage.html");
    }


//    @Override
//    public void processHttpRequest(Channel channel, HttpRequest httpRequest, InetSocketAddress clientAddress) {
//        log.debug("Received HTTP request for list of available services!");
//
//        try{
//            HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
//                    HttpResponseStatus.OK, getHtmlContent(), "text/html");
//
//            writeHttpResponse(channel, httpResponse, clientAddress);
//        }
//        catch(Exception ex){
//                log.error("Internal Server Error because of exception!", ex);
//                HttpResponse httpResponse = HttpResponseFactory.createHttpResponse(httpRequest.getProtocolVersion(),
//                        HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
//
//                writeHttpResponse(channel, httpResponse, clientAddress);
//        }
//    }
//
//    private ChannelBuffer getHtmlContent() throws IOException {
//        String htmlPath = "html/homepage.html";
//        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(htmlPath);
//        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//
//        StringBuilder content = new StringBuilder();
//        String line = reader.readLine();
//        while(line != null){
//            content.append(line);
//            content.append("\n");
//            line = reader.readLine();
//        }
//
//        return ChannelBuffers.wrappedBuffer(content.toString().getBytes(Charset.forName("UTF-8")));
//    }
//
//
//
//    private ChannelBuffer getHtmlListOfServices() {
//
//        Map<String, StringBuilder> semanticServices = new TreeMap<>();
//
//        StringBuilder otherServices = new StringBuilder();
//
//        for(String uri : services.keySet()){
//            HttpWebservice httpWebservice = services.get(uri);
//
//            if(httpWebservice instanceof DataOriginMapper){
//                DataOriginMapper semanticProxyWebservice = (DataOriginMapper) httpWebservice;
//                String backendName = semanticProxyWebservice.getBackendName();
//                String nextGraph = String.format("<li><a href=\"%s\">%s</a></li>\n", uri, uri);
//
//                if(semanticServices.containsKey(backendName)){
//                    semanticServices.get(backendName).append(nextGraph);
//                }
//
//                else{
//                    StringBuilder stringBuilder = new StringBuilder();
//                    stringBuilder.append(nextGraph);
//
//                    semanticServices.put(backendName, stringBuilder);
//                }
//
//            }
//            else{
//                if(!("/".equals(uri) || "/favicon.ico".equals(uri))){
//                    otherServices.append(String.format("<li><a href=\"%s\">%s</a></li>\n", uri, uri));
//                }
//            }
//        }
//
//        StringBuilder buf = new StringBuilder();
//        buf.append("<html><body>\n");
//        buf.append("<h2>Info and Administration Services</h2>\n");
//        buf.append("<ul>\n");
//        buf.append(otherServices.toString());
//        buf.append("</ul>\n");
//        buf.append("<h2>Proxy Service URIs for registered Semantic Resources</h2>");
//
//        for(String backendName : semanticServices.keySet()){
//            buf.append(String.format("<h3>Backend: %s</h3>\n", backendName));
//            buf.append("<ul>\n");
//            buf.append(semanticServices.get(backendName));
//            buf.append("</ul>\n");
//        }
//
//        buf.append("</body></html>\n");
//
//        return ChannelBuffers.wrappedBuffer(buf.toString().getBytes(Charset.forName("UTF-8")));
//    }
}
