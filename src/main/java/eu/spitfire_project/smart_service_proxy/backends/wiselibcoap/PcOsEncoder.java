package eu.spitfire_project.smart_service_proxy.backends.wiselibcoap;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * Created by IntelliJ IDEA.
 * User: maxpagel
 * Date: 28.06.12
 * Time: 13:43
 * To change this template use File | Settings | File Templates.
 */
public class PcOsEncoder extends OneToOneEncoder{
    private NodeRegistry nodeRegistry;

    public PcOsEncoder(NodeRegistry nodeRegistry) {

        this.nodeRegistry = nodeRegistry;
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        if (msg instanceof ChannelBuffer) {
            ChannelBuffer buffer = (ChannelBuffer) msg;
            byte [] message = new byte[Math.min(buffer.array().length + 6 ,96)];
            message[0] = 10;
//            message[1] = 0;
            message[1] = 'O';
            message[2] = 0;
            message[3] = (byte) (0xFF & 0);
            message[4] = (byte) (0xFF & 0 >> 8);
            message[5] =51;
            System.arraycopy(buffer.array(),0,message,6,Math.min(buffer.array().length,90));
//            for(int i = 0; i<message.length;i++){
//                System.out.println(message[i]);
//            }
            return ChannelBuffers.wrappedBuffer(message);
        }else return msg;

    }
}
