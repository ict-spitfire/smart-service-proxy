//package eu.spitfire.ssp.gateways.coap.observation;
//
//import eu.spitfire.ssp.gateway.coap.CoapProxyServiceCreator;
//import eu.spitfire.ssp.core.pipeline.handler.ModelCache;
//import org.apache.log4j.Logger;
//import org.jboss.netty.channel.*;
//
///**
// * Created with IntelliJ IDEA.
// * User: spitfire
// * Date: 4/18/13
// * Time: 5:05 PM
// * To change this template use File | Settings | File Templates.
// */
//public class ResourceUpdateChannelPipelineFactory implements ChannelPipelineFactory{
//
//    private CoapProxyServiceCreator coapBackend;
//
//    public ResourceUpdateChannelPipelineFactory(CoapProxyServiceCreator coapBackend){
//        this.coapBackend = coapBackend;
//    }
//
//    @Override
//    public ChannelPipeline getPipeline() throws Exception {
//        ChannelPipeline pipeline = Channels.pipeline();
//
//        pipeline.addLast("Logging Sink", new SimpleChannelDownstreamHandler(){
//
//            private Logger log = Logger.getLogger(this.getClass().getName());
//
//            @Override
//            public void writeRequested(ChannelHandlerContext ctx, MessageEvent me){
//                log.error("Unexcepctedly received message of type " + me.getMessage().getClass().getName());
//            }
//        });
//
//        pipeline.addLast("Cache", ModelCache.getInstance());
//        pipeline.addLast("CoapProxyServiceCreator", coapBackend);
//        return pipeline;
//    }
//}
