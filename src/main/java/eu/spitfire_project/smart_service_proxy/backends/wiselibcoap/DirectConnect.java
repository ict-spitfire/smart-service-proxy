package eu.spitfire_project.smart_service_proxy.backends.wiselibcoap;

import de.uniluebeck.itm.netty.handlerstack.dlestxetx.DleStxEtxFramingDecoder;
import de.uniluebeck.itm.netty.handlerstack.dlestxetx.DleStxEtxFramingEncoder;
import de.uniluebeck.itm.netty.handlerstack.isense.ISensePacketDecoder;
import de.uniluebeck.itm.netty.handlerstack.isense.ISensePacketEncoder;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.InvalidMessageException;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import de.uniluebeck.itm.spitfire.nCoap.message.options.InvalidOptionException;
import de.uniluebeck.itm.spitfire.nCoap.message.options.ToManyOptionsException;
import de.uniluebeck.itm.tr.util.ForwardingScheduledExecutorService;
import de.uniluebeck.itm.tr.util.StringUtils;
import de.uniluebeck.itm.wsn.deviceutils.listener.writers.HumanReadableWriter;
import de.uniluebeck.itm.wsn.deviceutils.listener.writers.Writer;
import de.uniluebeck.itm.wsn.drivers.core.Device;
import de.uniluebeck.itm.wsn.drivers.factories.DeviceFactory;
import de.uniluebeck.itm.wsn.drivers.factories.DeviceFactoryImpl;
import de.uniluebeck.itm.wsn.drivers.factories.DeviceType;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.iostream.IOStreamAddress;
import org.jboss.netty.channel.iostream.IOStreamChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by IntelliJ IDEA.
 * User: maxpagel
 * Date: 28.06.12
 * Time: 10:32
 * To change this template use File | Settings | File Templates.
 */
public class DirectConnect {
    private static final Logger log = LoggerFactory.getLogger(DirectConnect.class);
    private Device device;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ClientBootstrap bootstrap;
    private Writer outWriter;
    private Channel channel;
    private CoapMessageHandler coapMessageHandler;
    private NodeRegistry nodeRegistry;

    public static void main(String[] args) {
        configureLoggingDefaults();
        DirectConnect connection = new DirectConnect("/dev/tty.usbserial-000013FD", new NodeRegistry());
        try {
            Thread.sleep(3000);
            while(true){
                connection.sendCoapRequest();
                Thread.sleep(1000);
            }
        } catch (ToManyOptionsException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InvalidOptionException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (URISyntaxException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InvalidMessageException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    DirectConnect(String DeviceAddress,final NodeRegistry nodeRegistry) {

        // Replace this ExecutorService with your existing one
        ExecutorService executorService = Executors.newCachedThreadPool();

        ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(1);
        ScheduledExecutorService executor = new ForwardingScheduledExecutorService(scheduleService, executorService);
        DeviceFactory factory = new DeviceFactoryImpl();

        device = factory.create(executor, DeviceType.ISENSE);
        try {
            device.connect(DeviceAddress);

            inputStream = device.getInputStream();
            outputStream = device.getOutputStream();

            bootstrap = new ClientBootstrap(new IOStreamChannelFactory(executorService));

            outWriter = new HumanReadableWriter(System.out);
            coapMessageHandler = new CoapMessageHandler();

            this.nodeRegistry = nodeRegistry;

            bootstrap.setPipelineFactory(
                    new ChannelPipelineFactory() {
                        public ChannelPipeline getPipeline()
                                throws Exception {
                            DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
//                            pipeline.addLast(
//                                    "loggingHandler", new SimpleChannelHandler() {
//                                @Override
//                                public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e)
//                                        throws Exception {
//                                    final ChannelBuffer message = (ChannelBuffer) e.getMessage();
//                                    byte[] messageBytes = new byte[message.readableBytes()];
//                                    message.readBytes(messageBytes);
//                                    outWriter.write(messageBytes);
//                                    ctx.sendUpstream(e);
//                                }
//                            });
                            pipeline.addLast("DleStxEtxDecoder", new DleStxEtxFramingDecoder());
                            pipeline.addLast("DleStxEtxEncoder", new DleStxEtxFramingEncoder());
                            pipeline.addLast("isenseEnc", new ISensePacketEncoder());
                            pipeline.addLast("isenseDec", new ISensePacketDecoder());
                            pipeline.addLast("PcOsEncoder", new PcOsEncoder(nodeRegistry));
                            pipeline.addLast("PcOsDecoder", new PcOsDecoder(nodeRegistry));
                            pipeline.addLast("coapMessageEncoder", new WiselibCoapMessageEncoder());
                            pipeline.addLast("coapMessageDecoder", new WiselibCoapMessageDecoder());
                            pipeline.addLast("CoapMessageHandler", coapMessageHandler);
                            return pipeline;
                        }
                    });

            // Make a new connection.
            ChannelFuture connectFuture = bootstrap.connect(new IOStreamAddress(inputStream, outputStream));

            // Wait until the connection is made successfully.
            channel = connectFuture.awaitUninterruptibly().getChannel();
            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            while (!Thread.interrupted()) {

                                try {

                                    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                                    device.getOutputStream().write(StringUtils.fromStringToByteArray(in.readLine()));

                                } catch (IOException e) {
                                    log.error("{}", e);
                                    System.exit(1);
                                }
                            }
                        }
                    }).start();

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }

    public void requestEnteties(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        Thread.sleep(3000);
                        sendCoapRequest();
                    } catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            }
        }).start();

    }

    public void sendCoapRequest()
            throws Exception, InvalidOptionException, URISyntaxException, InvalidMessageException {
        CoapRequest request = new CoapRequest(MsgType.CON, Code.GET, URI.create("coap://" + 0xFF +".de" + "/sensor1_complete"));
        request.setMessageID(1);
        log.info("sending coap packet");
        channel.write(request);
        log.info("sending coap packet done");

    }

    public void sendToNode(byte[] data)
            throws IOException {
        outputStream.write(data);

    }


    private static void configureLoggingDefaults() {
        PatternLayout patternLayout = new PatternLayout("%-13d{HH:mm:ss,SSS} | %-25.25c{2} | %-5p | %m%n");

        final Appender appender = new ConsoleAppender(patternLayout);
        org.apache.log4j.Logger.getRootLogger().removeAllAppenders();
        org.apache.log4j.Logger.getRootLogger().addAppender(appender);
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    }

    public void close()
            throws IOException {
        device.close();
    }

    public CoapMessageHandler getCoapMessageHandler() {
        return coapMessageHandler;
    }

    public void setCoapMessageHandler(CoapMessageHandler coapMessageHandler) {
        this.coapMessageHandler = coapMessageHandler;
    }
}
