package eu.spitfire_project.smart_service_proxy.backends.wiselibcoap;

import com.google.protobuf.ByteString;
import de.uniluebeck.itm.netty.handlerstack.isense.ISensePacket;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.WiselibProtocol;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

/**
 * Created by IntelliJ IDEA.
 * User: maxpagel
 * Date: 28.06.12
 * Time: 14:12
 * To change this template use File | Settings | File Templates.
 */
public class PcOsDecoder extends OneToOneDecoder {
    private NodeRegistry nodeRegistry;

    public PcOsDecoder(NodeRegistry nodeRegistry) {

        this.nodeRegistry = nodeRegistry;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        if (msg instanceof ISensePacket) {
            ISensePacket packet = (ISensePacket) msg;
            System.out.println( "received IsensePacket length: " + packet.getPayload().readableBytes() + " Bytes: " + packet.toString());
            if(packet.getPayload().getByte(0) == (byte)'I'){
                int offset = 16 ;//+ packet.getPayload().arrayOffset();
                int nodeId = 0xFF & packet.getPayload().getByte(2);
                nodeId += (0xFF & packet.getPayload().getByte(3)) << 8;
//                byte[] message = new byte[packet.getPayload().readableBytes()-offset];
//                System.arraycopy(packet.getPayload().array(),offset,message,0,packet.getPayload().readableBytes()-offset);
//                System.out.println("readable bytes of wrapped buffer: " + packet.getPayload().readableBytes());
                WiselibProtocol.MessageWrapper.Builder messageWrapperBuilder = WiselibProtocol.MessageWrapper.newBuilder();
                messageWrapperBuilder.setMessage(
                        ByteString.copyFrom(packet.getPayload().toByteBuffer(offset,packet.getPayload().readableBytes()-offset)));
                messageWrapperBuilder.setNodeId(nodeId);
                messageWrapperBuilder.setUrl(nodeRegistry.getUrlAddress(nodeId));
//                ChannelBuffer buffer = packet.getPayload().slice(offset,packet.getPayload().readableBytes()-offset);
                return messageWrapperBuilder.build();
//                return ChannelBuffers.wrappedBuffer(message);
            }
        }
        return msg;
    }


}
