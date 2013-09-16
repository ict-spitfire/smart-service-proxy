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
package eu.spitfire.ssp.backends.files;

import com.google.common.util.concurrent.SettableFuture;
import eu.spitfire.ssp.backends.AbstractBackendManager;
import eu.spitfire.ssp.server.webservices.HttpRequestProcessor;
import org.jboss.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.ScheduledExecutorService;

/**
* The FilesBackendManager is responsible for the web services backed by N3 files in the directory given as
* constructor parameter. That means every file gets a URI to be requested via GET request to read its content.
* There are only GET requests supported. Other methods but GET cause a response
* with status "method not allowed". GET requests on resources backed by malformed files (i.e. content
* is anything but valid "N3") cause an "internal server error" response.
*
* @author Oliver Kleine
*/
public class FilesBackendManager extends AbstractBackendManager {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private HttpRequestProcessorForFiles httpRequestProcessor;

    private Path observedDirectory;
    private FilesObserver filesObserver;

    private boolean copyExamples;
    private int numberOfRandomFiles;

    /**
     * Constructor for a new FileBackend instance which provides all files in the specified directory
     * as resources.
     *
     * @param directory the paths to the directory where the files are located
     */
    public FilesBackendManager(String prefix, LocalServerChannel localChannel,
                               ScheduledExecutorService scheduledExecutorService, boolean copyExamples,
                               int numberOfRandomFiles, String directory) throws IOException {
        super(prefix, localChannel, scheduledExecutorService);

        this.observedDirectory = Paths.get(directory);

        this.copyExamples = copyExamples;
        this.numberOfRandomFiles = numberOfRandomFiles;
        Path examplesDirectory = observedDirectory.resolve("examples");
        if(Files.exists(examplesDirectory))
            deleteRecursicvly(examplesDirectory.toFile());

        httpRequestProcessor = new HttpRequestProcessorForFiles();
        this.filesObserver = new FilesObserver(this, observedDirectory, scheduledExecutorService, localChannel);
    }

    SettableFuture<URI> registerResource(final URI resourceUri){
        final SettableFuture<URI> resourceRegistrationFuture = registerResource(resourceUri, httpRequestProcessor);

        resourceRegistrationFuture.addListener(new Runnable(){
            @Override
            public void run() {
                try{
                    URI resourceProxyUri = resourceRegistrationFuture.get();
                    log.info("Successfully registered resource {} with proxy Uri {}", resourceUri, resourceProxyUri);
                }
                catch (Exception e) {
                    log.error("Exception during registration of services from local file.", e);
                }
            }
        }, scheduledExecutorService);

        return resourceRegistrationFuture;
    }

    /**
     * Returns an {@link HttpRequestProcessor} to provide the GUI in HTML
     * @return an {@link HttpRequestProcessor} to provide the GUI in HTML
     */
    @Override
    public HttpRequestProcessor getGui() {
        return new FilesServiceManagerGui(filesObserver);
    }

    /**
     * Starts the observation of the directory specidied as constructor argument
     */
    @Override
    public void initialize() {
        try{
            //start the observation of the directory
            scheduledExecutorService.submit(filesObserver);

            //create directory ./examples
            if(copyExamples){
                Path examplesDirectory = observedDirectory.resolve("examples");
                Files.createDirectory(examplesDirectory);
                copyExampleFiles(examplesDirectory);

            }

            RandomFileCreator.createRandomResources(observedDirectory, numberOfRandomFiles);


        }
        catch (IOException e) {
            log.error("Exception during initialization!", e);
        }
    }



    private void copyExampleFiles(Path examplesDirectory){
        try{
            //Wait for examples directory to be observed
            while(!filesObserver.getObservedDirectories().contains(examplesDirectory)){};

            //Copy the example files
            String[] exampleFiles = new String[]{"file1.n3", "file2.n3"};

            for(int i = 0; i < exampleFiles.length; i++){
                InputStream inputStream = getClass().getResourceAsStream("examples/" + exampleFiles[i]);

                Path filePath = Paths.get(observedDirectory.toString() + "/examples/" + exampleFiles[i]);
                File file = new File(filePath.toString() + ".tmp");

                OutputStream outputStream = new FileOutputStream(file);

                int nextByte = inputStream.read();
                while(nextByte != -1){
                    outputStream.write(nextByte);
                    nextByte = inputStream.read();
                }

                outputStream.flush();
                outputStream.close();

                file.renameTo(new File(filePath.toString()));
            }
        }
        catch (FileNotFoundException e) {
            log.error("Error while copying file.", e);
        }
        catch (IOException e) {
            log.error("Error while creating, modifying or deleting file or directory.", e);
        }
    }

    /**
     * Deletes the given file from the file system. If the given file is a directory then all files and
     * sub-directories are also deleted.
     *
     * @param file the {@link File} instance that represents the file (or directory) to be deleted from the file-system
     */
    private void deleteRecursicvly(File file){
        log.debug("Try to delete {}", file.getAbsolutePath());
        if (file.isDirectory()) {
            String[] children = file.list();
            for (int i = 0; i < children.length; i++) {
                File child = new File(file, children[i]);
                deleteRecursicvly(child);
            }
            if(file.delete())
                log.debug("Deleted directory {}", file.getAbsolutePath());
        }
        else{
            if(file.delete())
                log.debug("Deleted file {}", file.getAbsolutePath());
        }
    }

    @Override
    public void shutdown() {

    }
}



