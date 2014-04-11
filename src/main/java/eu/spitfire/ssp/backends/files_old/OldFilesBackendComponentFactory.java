package eu.spitfire.ssp.backends.files_old;

import eu.spitfire.ssp.backends.generic.*;
import eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice;
import eu.spitfire.ssp.backends.generic.observation.DataOriginObserver;
import eu.spitfire.ssp.backends.generic.registration.DataOriginRegistry;
import org.apache.commons.configuration.Configuration;
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
public class OldFilesBackendComponentFactory extends BackendComponentFactory<Path>{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private OldHttpProxyWebserviceForFiles httpRequestProcessor;

    private WatchService watchService;
    private Path directory;
//    private OldFilesObserver filesObserver;
    private OldFilesWatcher oldFilesWatcher;
    private boolean copyExamples;

    public OldFilesBackendComponentFactory(String prefix, Configuration config, ScheduledExecutorService executorService)
            throws Exception {

        super(prefix, config, executorService);

        this.directory = new File(config.getString("files.directory")).toPath();
        this.copyExamples = config.getBoolean("files.copyExamples", false);

        this.watchService = FileSystems.getDefault().newWatchService();
        this.httpRequestProcessor = new OldHttpProxyWebserviceForFiles(this);
    }

    @Override
    public HttpSemanticProxyWebservice getSemanticProxyWebservice(DataOrigin<Path> dataOrigin) {
        return null;
    }

    @Override
    public DataOriginObserver<Path> getDataOriginObserver(DataOrigin<Path> dataOrigin) {
        return null;
    }

    public WatchService getWatchService(){
        return this.watchService;
    }


//    public OldFilesObserver getFilesObserver(){
//        return this.filesObserver;
//    }

    public OldFilesWatcher getOldFilesWatcher(){
        return this.oldFilesWatcher;
    }

//    @Override
//    public HttpSemanticProxyWebservice getSemanticProxyWebservice() {
//        return this.httpRequestProcessor;
//    }

    @Override
    public void initialize() throws Exception{
        this.filesObserver = new OldFilesObserver(this);
        this.oldFilesWatcher = new OldFilesWatcher(this);
        this.oldFilesWatcher.initialize(directory);
        //((OldFilesRegistry) getDataOriginRegistry()).initialize();

        if(copyExamples)
            copyExampleFiles(directory);
    }

    @Override
    public DataOriginRegistry<Path> createDataOriginRegistry() {
        log.info("Create Files Registry");
        return new OldFilesRegistry(this);
    }

    @Override
    public void shutdown() {
        //Nothing to do
    }


    private void copyExampleFiles(Path examplesDirectory){
        try{

            if(!examplesDirectory.toFile().exists())
                Files.createDirectory(examplesDirectory);

            //while(!oldFilesWatcher.getWatchedDirectoriesWithFiles().contains(examplesDirectory)){};

            //Copy the example files_old
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
