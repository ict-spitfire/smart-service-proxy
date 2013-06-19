/**
 * Copyright (c) 2012, all partners of project SPITFIRE (http://www.spitfire-project.eu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package eu.spitfire.ssp.backends.coap.noderegistration;

import de.uniluebeck.itm.spitfire.nCoap.application.server.CoapServerApplication;
import de.uniluebeck.itm.spitfire.nCoap.communication.reliability.outgoing.RetransmissionTimeoutMessage;
import eu.spitfire.ssp.backends.coap.CoapBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * @author Oliver Kleine
 */
public class CoapNodeRegistrationServer extends CoapServerApplication {

    private static Logger log = LoggerFactory.getLogger(CoapNodeRegistrationServer.class.getName());

    private ArrayList<CoapBackend> coapBackends = new ArrayList<CoapBackend>();

    private static CoapNodeRegistrationServer instance = new CoapNodeRegistrationServer();

    private CoapNodeRegistrationServer(){
        this.registerService(new CoapNodeRegistrationService());
        log.debug("Constructed. CoAP server listining on port ");
    }

    public static CoapNodeRegistrationServer getInstance(){
        return instance;
    }

    public boolean addCoapBackend(CoapBackend coapBackend){
        if(coapBackends.add(coapBackend)){
            log.debug("Registered new backend for prefix: " + coapBackend.getPrefix());
            return true;
        }
        return false;
    }

//    public void fakeRegistration(InetAddress inetAddress){
//        executorService.schedule(new CoapResourceDiscoverer(inetAddress), 0, TimeUnit.SECONDS);
//    }

    @Override
    public void handleRetransmissionTimeout(RetransmissionTimeoutMessage timeoutMessage) {
        log.info("No ACK received for sent message.");
    }

    ArrayList<CoapBackend> getCoapBackends(){
        return coapBackends;
    }


}
