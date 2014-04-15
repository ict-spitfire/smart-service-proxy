//package eu.spitfire.ssp.backends.files_old;
//
//import com.hp.hpl.jena.rdf.model.Model;
//import com.hp.hpl.jena.rdf.model.ModelFactory;
//import eu.spitfire.ssp.utils.Language;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.*;
//import java.nio.file.Files;
//import java.nio.file.Path;
//
///**
//* Created with IntelliJ IDEA.
//* User: olli
//* Date: 04.10.13
//* Time: 16:47
//* To change this template use File | Settings | File Templates.
//*/
//public abstract class OldFilesResourceToolBox {
//
//    private static Logger log = LoggerFactory.getLogger(OldFilesResourceToolBox.class.getName());
//
//    public static Model readModelFromFile(Path file) throws FileNotFoundException {
//        BufferedReader fileReader = new BufferedReader(new FileReader(file.toString()));
//        Model model = ModelFactory.createDefaultModel();
//        model.read(fileReader, null, Language.RDF_N3.lang);
//
//        return model;
//    }
//
//    public static void writeModelToFile(Path file, Model model) throws IOException {
//        //Write new status to the file
//        log.info("Write model to file {}", file);
//        FileWriter fileWriter = new FileWriter(file.toFile());
//        model.write(fileWriter, Language.RDF_N3.lang);
//        fileWriter.flush();
//        fileWriter.close();
//    }
//
//    public static void recreateFile(Path file, Model model) throws IOException {
//        log.info("Delete file {}", file);
//        Files.delete(file);
//        writeModelToFile(file, model);
//    }
//}
