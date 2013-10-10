package eu.spitfire.ssp.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import eu.spitfire.ssp.utils.Language;
import eu.uberdust.communication.UberdustClient;
import eu.wisebed.wisedb.model.Testbed;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Conversion Class for Uberdust Node Readings to their semantic descriptions.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustNodeHelper {
    /**
     * Logger.
     */
    private static Logger log = Logger.getLogger(UberdustNodeHelper.class.getName());

    private static Pattern lightZone = Pattern.compile(":lz[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    private static Pattern fan = Pattern.compile(":ac[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    private static Pattern relay = Pattern.compile(":[1-9][0-9]*r", Pattern.CASE_INSENSITIVE);

    /**
     * Constructor for the node reading.
     *
     * @param name
     * @param testbed
     * @param prefix
     * @param capability
     * @param value
     * @param time
     * @throws org.json.JSONException
     */
    public static Model generateDescription(String name, String testbed, String prefix, String capability, Double value, Date time) {
        String locationName1;
        List<String> rooms1;
        List<String> workstation1;
        String capabilityResource1;
        String y1;
        String x1;

        try {
            x1 = UberdustClient.getInstance().getNodeX(testbed, name);
        } catch (IOException e) {
            x1 = "0";
        }

        try {
            y1 = UberdustClient.getInstance().getNodeY(testbed, name);
        } catch (IOException e) {
            y1 = "0";
        }

        try {
            rooms1 = UberdustClient.getInstance().getNodeRooms(Integer.parseInt(testbed), name);
        } catch (Exception e) {
            rooms1 = null;
        }

        try {
            workstation1 = UberdustClient.getInstance().getNodeWorkstations(Integer.parseInt(testbed), name);
        } catch (Exception e) {
            workstation1 = null;
        }

        try {
            capabilityResource1 = getSameAs(getCapabilityResourceURI(testbed, capability));
        } catch (URISyntaxException e) {
            capabilityResource1 = "null";
        }

        try {
            locationName1 = ((Testbed) UberdustClient.getInstance().getTestbedById(Integer.parseInt(testbed))).getName();
        } catch (IOException e) {
            locationName1 = prefix;
        } catch (JSONException e) {
            locationName1 = prefix;
        }
        if (locationName1.equals("Gen6")) {
            try {
                locationName1 = (UberdustClient.getInstance().getLastNodeReading(Integer.parseInt(testbed), name, "name").getJSONArray("readings").getJSONObject(0)).getString("stringReading");
            } catch (Exception e) {
                log.error(e);
            }
        }

        return getModel(testbed, prefix, capabilityResource1, x1, y1, time, capability, locationName1, String.valueOf(value), rooms1, workstation1, name);
    }

    public static Statement createUpdateStatement(URI subject, Double value) throws Exception {
        String statement = "<" + subject + ">" +
                "<http://spitfire-project.eu/ontology/ns/value>\n" +
                "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n";
        Model model = ModelFactory.createDefaultModel();
        ByteArrayInputStream bin = new ByteArrayInputStream(statement.getBytes(Charset.forName("UTF-8")));
        model.read(bin, null, Language.RDF_N3.lang);

        return model.listStatements().nextStatement();
    }

    /**
     * N3 RDF description of the node.
     *
     * @return a string containing the N3 rdf description.
     * @throws java.net.URISyntaxException should not happen.
     */
    public static String toRDF(
            final String testbed,
            final String prefix,
            final String capabilityResource,
            final String x,
            final String y,
            final Date time,
            final String capability,
            final String locationName,
            final String value,
            final List<String> rooms,
            final List<String> workstations,
            final String node

    ) throws URISyntaxException {
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yy-MM-dd'T'HH:mm:ss'Z'");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        final URI resourceURI = new URI(getResourceURI(testbed, node, capability));
        String description = "" +
                "<" + (resourceURI).toString() + ">\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#long>\n" +
                "\"" + y + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#lat>\n" +
                "\"" + x + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://purl.org/dc/terms/#date>\n" +
                "\"" + dateFormatGmt.format(time) + "\";\n" +
                "<http://www.ontologydesignpatterns.org/ont/dul/DUL.owl/hasLocation>\n" +
                "\"" + locationName + "\"";
        if ((lightZone.matcher(capability).find() || relay.matcher(capability).find())) {

            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>.\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>\n" +
                    "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#switch>;\n" +
                    "<http://spitfire-project.eu/ontology/ns/value>\n" +
                    "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n";

        } else if (fan.matcher(capability).find()) {
            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>.\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>\n" +
                    "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#fan>;\n" +
                    "<http://spitfire-project.eu/ontology/ns/value>\n" +
                    "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n";
        } else {
            description += ";\n" +
                    "<http://spitfire-project.eu/ontology/ns/obs>\n" +
                    "<" + capabilityResource + ">;\n" +
                    "<http://spitfire-project.eu/ontology/ns/value>\n" +
                    "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n";
        }
        if (rooms != null) {
            for (String room : rooms) {
                description += "<" + (resourceURI).toString() + ">\n" +
                        "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest>\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/>.\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/> \n" +
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                        "<http://spitfire-project.eu/foi/Room>.";
            }
        }

        if (workstations != null) {
            for (String workstation : workstations) {
                description += "<" + (resourceURI).toString() + ">\n" +
                        "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest> <http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + workstation + "/>.\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + workstation + "/>\n" +
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                        "<http://www.freebase.com/m/0v3bzfq>.";
            }
        }

        description += "\n" +
                "\n" +
                "<" + capabilityResource + ">\n" +
                "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                "<http://purl.oclc.org/NET/ssnx/ssn#Property>.";
        return description;
    }

    private static String getSameAs(String uberdustURL) {
//        final String uberdustURL = "http://uberdust.cti.gr/rest/testbed/1/capability/urn:wisebed:node:capability:light/rdf";
        String sameAs = uberdustURL;
        try {
            URL url = new URL(uberdustURL);
            Model m = ModelFactory.createDefaultModel();
            m.read(uberdustURL);
            StmtIterator props = m.getResource(uberdustURL).listProperties();
            while (props.hasNext()) {
                Statement something = props.next();
                if ("http://www.w3.org/2002/07/owl#sameAs".equals(something.getPredicate().toString())) {
                    sameAs = something.getObject().toString();
                    if (sameAs.contains("null")) {
                        sameAs = uberdustURL;
                    }
                }
            }
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        return sameAs;
    }

    /**
     * Returns a Jena Model of the Node.
     *
     * @return the jena model describing the node.
     */
    public static Model getModel(final String testbed,
                                 final String prefix,
                                 final String capabilityResource,
                                 final String x,
                                 final String y,
                                 final Date time,
                                 final String capability,
                                 final String locationName,
                                 final String value,
                                 final List<String> rooms,
                                 final List<String> workstations,
                                 final String name) {
        Model model = ModelFactory.createDefaultModel();
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(toRDF(
                    testbed,
                    prefix,
                    capabilityResource,
                    x,
                    y,
                    time,
                    capability,
                    locationName,
                    value,
                    rooms,
                    workstations,
                    name
            ).getBytes(Charset.forName("UTF-8")));
            model.read(bin, null, Language.RDF_N3.lang);
        } catch (URISyntaxException e) {

        }
        return model;
    }


    public static String getResourceURI(final String testbed, final String node, final String capability) throws URISyntaxException {
//        return "http://uberdust.cti.gr/rest/testbed/" + node.getTestbed() + "/node/" + node.getName() + "/capability/" + node.getCapability() + "/";
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + testbed + "/node/" + node + "/capability/" + capability + "/", null, null).toString();
    }

    public static String getCapabilityResourceURI(final String testbed, final String capability) throws URISyntaxException {
//        return "http://uberdust.cti.gr/rest/testbed/" + node.getTestbed() + "/capability/" + node.getCapability() + "/rdf";
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + testbed + "/capability/" + capability + "/rdf", null, null).toString();
    }

}