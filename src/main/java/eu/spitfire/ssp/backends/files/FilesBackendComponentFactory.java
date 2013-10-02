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
//package eu.spitfire.ssp.backends.files;
//
//import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
//import eu.spitfire.ssp.backends.generic.DataOriginAccessory;
//import eu.spitfire.ssp.backends.generic.DataOriginRegistry;
//import eu.spitfire.ssp.server.channels.LocalPipelineFactory;
//
//import java.nio.file.Path;
//import java.util.concurrent.ScheduledExecutorService;
//
///**
//* The FilesBackendComponentFactory is responsible for the web services backed by N3 files in the directory given as
//* constructor parameter. That means every file gets a URI to be requested via GET request to read its content.
//* There are only GET requests supported. Other methods but GET cause a response
//* with status "method not allowed". GET requests on resources backed by malformed files (i.e. content
//* is anything but valid "N3") cause an "internal server error" response.
//*
//* @author Oliver Kleine
//*/
//
//public class FilesBackendComponentFactory extends BackendComponentFactory<Path> {
//
//
//    private final Path directory;
//
//    protected FilesBackendComponentFactory(String prefix, LocalPipelineFactory localPipelineFactory,
//                                           ScheduledExecutorService scheduledExecutorService, Path directory) throws Exception {
//        super(prefix, localPipelineFactory, scheduledExecutorService);
//        this.directory = directory;
//    }
//
//    @Override
//    public void initialize() {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    @Override
//    public DataOriginRegistry<Path> createDataOriginRegistry() {
//        return null;  //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    @Override
//    public DataOriginAccessory<Path> createDataOriginAccessory() {
//        return null;  //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    @Override
//    public void shutdown() {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//}
//
//
//
