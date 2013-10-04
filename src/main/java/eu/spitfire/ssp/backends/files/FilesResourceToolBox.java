package eu.spitfire.ssp.backends.files;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire.ssp.backends.generic.ResourceToolbox;
import eu.spitfire.ssp.server.payloadserialization.Language;

import java.io.*;
import java.nio.file.Path;

/**
* Created with IntelliJ IDEA.
* User: olli
* Date: 04.10.13
* Time: 16:47
* To change this template use File | Settings | File Templates.
*/
public abstract class FilesResourceToolBox extends ResourceToolbox{

    public static Model readModelFromFile(Path filePath) throws FileNotFoundException {
        BufferedReader fileReader = new BufferedReader(new FileReader(filePath.toString()));
        Model model = ModelFactory.createDefaultModel();
        model.read(fileReader, null, Language.RDF_N3.lang);

        return model;
    }

    public static void writeModelToFile(Path filePath, Model model) throws IOException {
        //Write new status to the file
        FileWriter fileWriter = new FileWriter(filePath.toFile());
        model.write(fileWriter, Language.RDF_N3.lang);
        fileWriter.flush();
        fileWriter.close();
    }
}
