package eu.spitfire.ssp.backends.generic.exceptions;

import com.hp.hpl.jena.rdf.model.ResIterator;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 02.10.13
 * Time: 16:34
 * To change this template use File | Settings | File Templates.
 */
public class MultipleSubjectsInModelException extends Exception{

    public MultipleSubjectsInModelException(ResIterator subjectIterator){
        super(createMessage(subjectIterator));
    }

    private static String createMessage(ResIterator subjectIterator){
        StringBuffer buffer = new StringBuffer();
        buffer.append("Subjects: ");
        while(subjectIterator.hasNext()){
            buffer.append(subjectIterator.nextResource().toString());
            if(subjectIterator.hasNext())
                buffer.append(", ");
        }
        return buffer.toString();
    }
}
