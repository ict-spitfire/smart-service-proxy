package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.BackendComponentFactory;
import eu.spitfire.ssp.backends.generic.DataOriginRegistry;
import eu.spitfire.ssp.backends.generic.SemanticHttpRequestProcessor;
import eu.spitfire.ssp.server.channels.LocalPipelineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 04.10.13
 * Time: 17:01
 * To change this template use File | Settings | File Templates.
 */
public class FilesBackendComponentFactory extends BackendComponentFactory<Path>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private HttpRequestProcessorForFiles httpRequestProcessor;

    private WatchService watchService;
    private Path directory;
    private FilesObserver filesObserver;
    private FilesWatcher filesWatcher;
    private boolean copyExamples;

    public FilesBackendComponentFactory(String prefix, LocalPipelineFactory localPipelineFactory,
                                        ScheduledExecutorService scheduledExecutorService, String sspHostName,
                                        int sspHttpPort, Path directory, boolean copyExamples)
            throws Exception {

        super(prefix, localPipelineFactory, scheduledExecutorService, sspHostName, sspHttpPort);

        this.directory = directory;
        this.copyExamples = copyExamples;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.httpRequestProcessor = new HttpRequestProcessorForFiles(this);
    }

    public WatchService getWatchService(){
        return this.watchService;
    }

    public FilesObserver getFilesObserver(){
        return this.filesObserver;
    }

    public FilesWatcher getFilesWatcher(){
        return this.filesWatcher;
    }

    @Override
    public SemanticHttpRequestProcessor getHttpRequestProcessor() {
        return this.httpRequestProcessor;
    }

    @Override
    public void initialize() throws Exception{
        this.filesObserver = new FilesObserver(this);
        this.filesWatcher = new FilesWatcher(this);
        this.filesWatcher.initialize(directory);
        //((FilesRegistry) getDataOriginRegistry()).initialize();

        if(copyExamples)
            copyExampleFiles(directory);
    }

    @Override
    public DataOriginRegistry<Path> createDataOriginRegistry() {
        log.info("Create Files Registry");
        return new FilesRegistry(this);
    }

    @Override
    public void shutdown() {
        //Nothing to do
    }


    private void copyExampleFiles(Path examplesDirectory){
        try{

            //deleteRecursicvly(examplesDirectory.toFile());
            if(!examplesDirectory.toFile().exists())
                Files.createDirectory(examplesDirectory);

            //while(!filesWatcher.getWatchedDirectoriesWithFiles().contains(examplesDirectory)){};

            //Copy the example files
            String[] exampleFiles = new String[]{"example-file1.n3", "example-file2.n3"};

            for(int i = 0; i < exampleFiles.length; i++){
                InputStream inputStream = getClass().getResourceAsStream(exampleFiles[i]);

               // Path filePath = Paths.get(directory.toString() +  "/examples/" + exampleFiles[i]);
                Path filePath = Paths.get(directory.toString() +  "/" + exampleFiles[i]);
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
}
