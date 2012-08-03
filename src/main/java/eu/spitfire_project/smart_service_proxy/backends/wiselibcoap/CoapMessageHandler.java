package eu.spitfire_project.smart_service_proxy.backends.wiselibcoap;


import de.uniluebeck.itm.spitfire.nCoap.message.CoapMessage;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.WiselibProtocol;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: maxpagel
 * Date: 28.06.12
 * Time: 13:37
 * To change this template use File | Settings | File Templates.
 */
public class CoapMessageHandler extends SimpleChannelHandler {
    private static final Logger logger = LoggerFactory.getLogger(CoapMessageHandler.class);

    private List<CoapListener> listeners;

    public CoapMessageHandler() {
        listeners = new Vector<CoapListener>();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
//        super.messageReceived(ctx, e);
        if (e.getMessage() instanceof CoapResponse) {
            CoapMessage coapMessage = (CoapMessage) e.getMessage();
            logger.info("received coap Message length: " + coapMessage.getPayload().readableBytes() + " with payload " + coapMessage.getPayload().toString());
            ChannelBuffer buffer = coapMessage.getPayload();
            byte[] payloadBuffer = new byte[buffer.readableBytes()];
            StringBuilder builder = new StringBuilder();
            buffer.readBytes(payloadBuffer);
            String s = new String(payloadBuffer);
            builder.append(s.endsWith("\n") ? s.substring(0, s.length() ) : s);
//            logger.info("Payload length: " + payloadBuffer.length + " as string: " + builder.toString());
            
            try {


                WiselibProtocol.SemanticEntity se = WiselibProtocol.SemanticEntity.parseFrom(payloadBuffer);
                System.out.println(se.toString());
                notifyListeners(se);


            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
        } else ctx.sendUpstream(e);
    }

    public static String toHexString(byte[] bites){
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < bites.length; ++i) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("0x");
            sb.append(Integer.toHexString(bites[i] & 0xFF));
        }
        return sb.toString();
    }

    Vector<Vector<Byte>> filterType(byte type, Vector<Vector<Byte>> in) {
        Vector<Vector<Byte>> r = new Vector<Vector<Byte>>();
        for (Vector<Byte> v : in) {
            //System.out.println("filterType " + v.get(0) + " " + v.get(1) + " " + v.get(2));
            if (v.size() >= 3 && v.get(1) == type) {
                r.add(v);
            }
        }
        return r;
    }

    Vector<Vector<Byte>> deHex(String[] stringbytes) {
        Vector<Byte> r = new Vector<Byte>();
        for (int i = 0; i < stringbytes.length; i++) {
            //System.out.println(stringbytes[i]);
            int v = Integer.decode(stringbytes[i]);
            r.add((byte) (v <= 127 ? v : -128 - v)); // seriously java, fuck off.
        }
        Vector<Vector<Byte>> rr = new Vector<Vector<Byte>>();
        rr.add(r);
        return rr;
    }

    public void addListener(CoapListener listener){
        listeners.add(listener);
    }

    public void removeListener(CoapListener listener){
        listeners.remove(listener);
    }
    public void notifyListeners(WiselibProtocol.SemanticEntity se){
        for (CoapListener listener : listeners) {
            listener.onSemanticDescription(se);
        }
    }
}
    