///**
//* Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
//* All rights reserved.
//*
//* Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
//* following conditions are met:
//*
//*  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
//*    disclaimer.
//*
//*  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
//*    following disclaimer in the documentation and/or other materials provided with the distribution.
//*
//*  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
//*    products derived from this software without specific prior written permission.
//*
//* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
//* INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
//* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
//* GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
//* LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
//* OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//*/
//package eu.spitfire.ssp.gateway.files;
//
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.common.util.concurrent.ListeningExecutorService;
//import com.google.common.util.concurrent.MoreExecutors;
//import eu.spitfire.ssp.core.webservice.HttpRequestProcessor;
//import eu.spitfire.ssp.gateway.AbstractProxyServiceManager;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.nio.file.*;
//import java.util.List;
//import java.util.concurrent.Callable;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//
//import static java.nio.file.StandardWatchEventKinds.*;
//
///**
//* The FilesGateway is responsible for the webServices backed by the files (except of *.swp) in the directory given as
//* constructor parameter. That means every file gets a URI to be requested via GET request to read its content.
//* There are only GET requests supported. Other methods but GET cause a response
//* with status "method not allowed". GET requests on resources backed by malformed files (i.e. content
//* is anything but valid "N3") cause an "internal server error" response.
//*
//* @author Oliver Kleine
//* @author Henning Hasemann
//*/
//public class FilesGateway extends AbstractProxyServiceManager {
//
//    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
//
//    private String directory;
//    private WatchService watchService;
//    private WatchKey watchKey;
//    private ListenableFuture<List<WatchEvent<?>>> watchFuture;
//
//    //private ScheduledExecutorService scheduledExecutorService;
//
//
//    /**
//     * Constructor for a new FileBackend instance which provides all files in the specified directory
//     * as resources.
//     *
//     * @param directory the path to the directory where the files are located
//     */
//    public FilesGateway(String prefix, String directory) throws IOException {
//        super(prefix);
//        this.directory = directory;
//
//        watchService = FileSystems.getDefault().newWatchService();
//        Path directoryPath = Paths.get(directory);
//
//        //Register directory to be watched, i.e. observed for changes
//        watchKey = directoryPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
//    }
//
//
//    @Override
//    public HttpRequestProcessor getGui() {
//        return null;
//    }
//
//    /**
//     * Register all files as new resources at the HttpRequestDispatcher (ignore *.swp files)
//     */
//    @Override
//    public void initialize(){
//        //watchFuture = listeningExecutorService.submit(new WatchTask());
//    }
//
//    private class WatchTask implements Callable<List<WatchEvent<?>>>{
//
//        @Override
//        public List<WatchEvent<?>> call() throws Exception {
//            while(true){
//                watchKey = watchService.poll(10, TimeUnit.SECONDS);
//
//                if(watchKey == null)
//                    continue;
//
//                List<WatchEvent<?>> events = watchKey.pollEvents();
//
//                if (events.isEmpty())
//                    continue;
//
//                for (WatchEvent<?> event : events) {
//                    WatchEvent.Kind<?> kind = event.kind();
//
//                    if (kind == OVERFLOW)
//                        continue;
//
//
//                }
//            }
//        }
//    }
//}
//
