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
package eu.spitfire_project.smart_service_proxy.backends.coap;

import org.jboss.netty.channel.ChannelHandlerContext;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Oliver Kleine
 * @author Stefan Hüske
 */

public class OpenCoAPRequestManagerThread extends Thread {

    private static OpenCoAPRequestManagerThread instance = new OpenCoAPRequestManagerThread();

    private OpenCoAPRequestManagerThread(){
        //private constructor to make it singleton
    }

    /**
     * Returns the one and only instance of OpenCoAPRequestManagerThread
     * @return the one and only instance of OpenCoAPRequestManagerThread
     */
    public static OpenCoAPRequestManagerThread getInstance(){
        return instance;
    }

    ReentrantLock lock = new ReentrantLock();
    List<OpenCoapRequest> list = new LinkedList<OpenCoapRequest>();

    /**
     * Removes timed out requests from the list of {@link OpenCoapRequest} instances and after calling the
     * instances onTimeOut() method.
     */
    @Override
    public void run() {
        while(true) {
            try {
                lock.lock();
                try {
                    for (int i = list.size() - 1; i >= 0; i--) {
                        OpenCoapRequest r = list.get(i);
                        if (r.isTimedOut()) {
                            list.remove(i);
                            r.onTimeOut();
                        }
                    }
                } finally {
                    lock.unlock();
                }
                Thread.sleep(200);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }


    public boolean isOpen(ChannelHandlerContext ctx) {
        lock.lock();
        try {
            for (OpenCoapRequest r : list) {
                if (r.getCtx() == ctx) {
                    return true;
                }
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    public void addOpenRequest(OpenCoapRequest openCoapRequest) {
        if (!isOpen(openCoapRequest.getCtx())) {
            lock.lock();
            try {
                list.add(openCoapRequest);
            } finally {
                lock.unlock();
            }
        }
    }

    public void removeOpenRequest(ChannelHandlerContext ctx) {
        lock.lock();
        try {
            for (int i = list.size() - 1; i >= 0; i--) {
                OpenCoapRequest r = list.get(i);
                if (r.getCtx() == ctx) {
                    list.remove(i);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
