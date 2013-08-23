/**
* Copyright (c) 2012, all partners of project SPITFIRE (core://www.spitfire-project.eu)
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
package eu.spitfire.ssp.proxyservicemanagement.files;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.proxyservicemanagement.AbstractProxyServiceManager;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
* The FilesProxyServiceManager is responsible for the webServices backed by the files (except of *.swp) in the directory given as
* constructor parameter. That means every file gets a URI to be requested via GET request to read its content.
* There are only GET requests supported. Other methods but GET cause a response
* with status "method not allowed". GET requests on resources backed by malformed files (i.e. content
* is anything but valid "N3") cause an "internal server error" response.
*
* @author Oliver Kleine
*/
public class FilesProxyServiceManager extends AbstractProxyServiceManager {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private HttpRequestProcessorForLocalFiles httpRequestProcessor;

    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys;
    private boolean copyExamples;

    /**
     * Constructor for a new FileBackend instance which provides all files in the specified directory
     * as resources.
     *
     * @param directories the paths to the directories where the files are located
     */
    public FilesProxyServiceManager(String prefix, LocalServerChannel localChannel,
                                    ScheduledExecutorService scheduledExecutorService, boolean copyExamples,
                                    String... directories) throws IOException {
        super(prefix, localChannel, scheduledExecutorService);

        watchService = FileSystems.getDefault().newWatchService();

        watchKeys = new HashMap<>();

        //Register directory to be watched, i.e. observed for changes
        for(int i = 0; i < directories.length; i++){
            Path directoryPath = Paths.get(directories[i]);
            WatchKey watchKey = directoryPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watchKeys.put(watchKey, directoryPath);
        }

        this.copyExamples = copyExamples;

        httpRequestProcessor = new HttpRequestProcessorForLocalFiles();
    }


    @Override
    public HttpRequestProcessor getGui() {
        return null;
    }

    /**
     * Starts the observation of the directory specidied as constructor argument
     */
    @Override
    public void initialize(){
        scheduledExecutorService.schedule(new Runnable(){

            @Override
            public void run() {
                while(true){
                    WatchKey watchKey;
                    try{
                        watchKey = watchService.take();
                    }
                    catch (InterruptedException e) {
                        log.error("Exception while taking watch key.", e);
                        return;
                    }

                    Path directory = watchKeys.get(watchKey);
                    if(directory == null){
                        log.error("Directory not recognized ({}).", directory);
                        continue;
                    }

                    for(WatchEvent ev : watchKey.pollEvents()){
                        //Which kind of event occured?
                        WatchEvent.Kind eventKind = ev.kind();

                        if(eventKind == OVERFLOW){
                            log.warn("Unhandled event kind OVERFLOW");
                            continue;
                        }

                        Object context = ev.context();
                        if(context instanceof Path){
                            Path filePath = directory.resolve((Path) context);
                            log.debug("Event {} at path {}", eventKind, filePath);

                            if(eventKind == ENTRY_CREATE){
                                SettableFuture<URI> proxyResourceUriFuture = SettableFuture.create();
                                URI resourceUri;
                                try {
                                    resourceUri = new URI(null, null, null, -1,
                                            "/" + getPrefix() + "/" + directory.relativize(filePath).toString(), null, null);

                                } catch (URISyntaxException e) {
                                   log.error("This should never happen!", e);
                                    continue;
                                }
                                registerResource(proxyResourceUriFuture, resourceUri, httpRequestProcessor);
                            }
                        }
                    }

                    // reset key and remove from set if directory no longer accessible
                    boolean valid = watchKey.reset();
                    if (!valid) {
                        log.error("Stopped observation of directory {}.", watchKeys.get(watchKey));
                        watchKeys.remove(watchKey);
                    }
                }
            }
        }, 0, TimeUnit.SECONDS);

        if(copyExamples)
            copyExamples();
    }



    private void copyExamples() {
        String[] exampleFiles = new String[]{"file01.txt", "file02.txt"};

        for(int i = 0; i < exampleFiles.length; i++){
            InputStream inputStream = getClass().getResourceAsStream("examples/" + exampleFiles[i]);

            String directory = watchKeys.get(watchKeys.keySet().iterator().next()).toString();
            File file = new File(directory + "/" + exampleFiles[i]);
            try {
                OutputStream outputStream = new FileOutputStream(file);

                int nextByte = inputStream.read();
                while(nextByte != -1){
                    outputStream.write(nextByte);
                    nextByte = inputStream.read();
                }

                outputStream.flush();
                outputStream.close();

                log.info("Written file {}", directory + "/" + exampleFiles[i]);
            }
            catch (FileNotFoundException e) {
                log.error("Error while copying file examples/{}", exampleFiles[i]);
            }
            catch (IOException e) {
                log.error("This should never happen.", e);
            }
        }
    }

    @Override
    public void shutdown() {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}

