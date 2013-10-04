//package eu.spitfire.ssp.backends.files;
//
//import com.hp.hpl.jena.rdf.model.*;
//import eu.spitfire.ssp.server.payloadserialization.Language;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.OutputStream;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Random;
//
///**
// * Created with IntelliJ IDEA.
// * User: olli
// * Date: 31.08.13
// * Time: 14:57
// * To change this template use File | Settings | File Templates.
// */
//public class RandomFileCreator {
//
//    private static String prefix = "http://www.example.org";
//    private static Random random = new Random(); // perhaps make it a class variable so you don't make a new one every time
//    private static Logger log = LoggerFactory.getLogger(RandomFileCreator.class.getName());
//
//    public static void createRandomResources(Path directory, int number){
//        try{
//            for(int i = 0; i < number; i++){
//                if(i % 5 == 0)
//                    Thread.sleep(300);
//                Model model = createRandomModel(i+1);
//                writeModelToFile(Paths.get(directory.toString(), "thing-" + (i + 1) + ".n3"), model);
//            }
//        }
//        catch(Exception e){
//            log.error("Exception while writing random files_OLD.", e);
//        }
//    }
//
//    public static Model createRandomModel(int index){
//        Model model = ModelFactory.createDefaultModel();
//        Resource subject = ResourceFactory.createResource(prefix + "/thing/" + index);
//
//        for(int i = 0; i < 5; i++){
//            Property predicate = ResourceFactory.createProperty(createRandomUri());
//            RDFNode object = ResourceFactory.createResource(createRandomUri());
//
//            Statement statement = ResourceFactory.createStatement(subject, predicate, object);
//            model.add(statement);
//        }
//
//        return model;
//    }
//
//    public static String createRandomUri(){
//        String result = prefix;
//        int parts = random.nextInt(5) + 1;
//        StringBuilder sb = new StringBuilder();
//
//        for(int i = 0; i < parts; i++){
//            sb.append("/");
//            int length = random.nextInt(10) + 1;
//            for(int j = 0; j < length; j++) {
//                char c = (char)(random.nextInt(122-97) + 97);
//                sb.append(c);
//            }
//        }
//        return result + sb.toString();
//    }
//
//    public static void writeModelToFile(Path filePath, Model model) throws Exception{
//        File file = new File(filePath.toString() + ".tmp");
//        OutputStream outputStream = new FileOutputStream(file);
//
//        model.write(outputStream, Language.RDF_N3.lang);
//        outputStream.flush();
//        outputStream.close();
//
//        file.renameTo(new File(filePath.toString()));
//    }
//
//    public static void main(String[] args) throws Exception {
//        Model model = createRandomModel(1);
//        writeModelToFile(Paths.get("/home/olli/test/thing-1.n3"), model);
//    }
//}
