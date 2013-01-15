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
package eu.spitfire_project.smart_service_proxy.backends.generator;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire_project.smart_service_proxy.core.Backend;
import eu.spitfire_project.smart_service_proxy.core.httpServer.EntityManager;
import eu.spitfire_project.smart_service_proxy.core.SelfDescription;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.io.StringReader;
import java.net.URI;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * User: henning
 * Date: 31.01.12
 * Time: 20:03
 * To change this template use File | Settings | File Templates.
 */
public class GeneratorBackend extends Backend {
    private int nodes, fois;
    private double[] sensorValues;
    private int[] foiNr;
    private double pMeasurement;
    private double pFoi;
    private TimerTask alterEntitiesTask;
	void log(String s) {
		System.out.println(System.currentTimeMillis() + " " + s);
		System.out.flush();
	}

    /**
     *
     * @param nodes Number of nodes to create
     * @param fois Number of feature of interests
     * @param pMeasurement
     * @param pFoi
     */
    public GeneratorBackend(int nodes, int fois, double pMeasurement, double pFoi) {
        this.nodes = nodes;
        this.fois = fois;
        this.pMeasurement = pMeasurement;
        this.pFoi = pFoi;
        this.sensorValues = new double[nodes];
        this.foiNr = new int[nodes];

        alterEntitiesTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                alterEntities();
            }
        };
    }

    private void createEntities() {
		log("create_fake_entites");
        int nodesPerFoi = nodes / fois;
        int node = 0;
        for(int foi=0; foi<fois && node<nodes; foi++) {
            for(int foinode=0; foinode<nodesPerFoi && node<nodes; foinode++, node++) {
                sensorValues[node] = 0.0;
                foiNr[node] = foi;
                EntityManager.getInstance().entityCreated(URI.create(getPrefix() + node), this);
            }
        }
		log("end_create_fake_entities");
    }

    private void alterEntities() {
		log("alter_fake_entities");
        Random rand = new Random();
        for(int i=0; i<nodes; i++) {
            double p = rand.nextDouble();
            if(p <= pFoi) {
                changeFoi(i);
            }
            p = rand.nextDouble();
            if(p <= pMeasurement) {
                changeMeasurement(i);
            }
        }
		log("end_alter_fake_entities");

        Timer t = new HashedWheelTimer();
        t.newTimeout(alterEntitiesTask, 5, TimeUnit.MINUTES);
    }

    private void changeMeasurement(int i) {
        sensorValues[i] += 0.1;
    }

    private void changeFoi(int i) {
        foiNr[i]++;
        foiNr[i] %= fois;
    }
    
    private Model getModel(String uri) {
        String uri1 = uri.split("#")[0];
        String[] parts = uri1.split("/");
        int i = Integer.parseInt(parts[parts.length - 1]);

        Model m = ModelFactory.createDefaultModel();

        String tmpl = String.format(
                Locale.ROOT,
                "@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#> .\n" +
                "@prefix dul: <http://www.loa-cnr.it/ontologies/DUL.owl#> .\n" +
                "@prefix : <%s/static/ontology.owl#> .\n" +
                "<%s>\n" +
                "  ssn:attachedSystem [ \n" +
                "    ssn:featureOfInterest :foi%d ; \n" +
                "	 ssn:observedProperty :Temperature ; \n" +
                "	 dul:hasValue \"%f\" \n" +
                "  ] . \n"
                ,
                EntityManager.SSP_DNS_NAME,
                uri,
                foiNr[i],
                sensorValues[i]
        );
        m.read(new StringReader(tmpl), ".", "N3");
        return m;
    }

    @Override
    public void bind() {
        super.bind();
        createEntities();

        Timer t = new HashedWheelTimer();
        t.newTimeout(alterEntitiesTask, 5, TimeUnit.MINUTES);
    } // bind()


    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(!(e.getMessage() instanceof HttpRequest)){
            super.messageReceived(ctx, e);
        }
        HttpRequest request = (HttpRequest) e.getMessage();
        String uri = request.getUri();
        uri = EntityManager.getInstance().toThing(uri).toString();
        Model m = getModel(uri);
        ChannelFuture future = Channels.write(ctx.getChannel(), new SelfDescription(m, URI.create(uri)));
        if(!HttpHeaders.isKeepAlive(request)){
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }


}
