package eu.spitfire.ssp.backends.generic;

import com.hp.hpl.jena.rdf.model.Model;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 03.10.13
 * Time: 17:49
 * To change this template use File | Settings | File Templates.
 */
public class ExpiringModel {

    private Model model;
    private Date expiry;

    public ExpiringModel(Model model, Date expiry){
        this.model = model;
        this.expiry = expiry;
    }

    public Model getModel() {
        return model;
    }

    public Date getExpiry() {
        return expiry;
    }
}
