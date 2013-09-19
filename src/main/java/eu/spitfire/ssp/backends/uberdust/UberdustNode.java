package eu.spitfire.ssp.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import eu.spitfire.ssp.server.payloadserialization.Language;
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
public class UberdustNode {
    /**
     * Logger.
     */
    private static Logger log = Logger.getLogger(UberdustNode.class.getName());

    private static Pattern lightZone = Pattern.compile(":lz[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    private static Pattern fan = Pattern.compile(":ac[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    private static Pattern relay = Pattern.compile(":[1-9][0-9]*r", Pattern.CASE_INSENSITIVE);

    /**
     * Node name.
     */
    String name;
    /**
     * Testbed ID.
     */
    String testbed;
    /**
     * Uberdust Capability Name.
     */
    private final String capability;
    private final String capabilityResource;
    /**
     * Double Reading.
     */
    private Double value;
    /**
     * Timestamp of the latest reading.
     */
    private Date time;
    /**
     * Longtitude.
     */
    private final String x;
    /**
     * Lattitude.
     */
    private final String y;
    private final List<String> rooms;
    private final List<String> workstations;
    private final String prefix;
    private final String locationName;

    /**
     * Constructor for the node reading.
     *
     * @param name
     * @param testbed
     * @param prefix
     * @param capability
     * @param value
     * @param time
     * @throws JSONException
     */
    public UberdustNode(String name, String testbed, String prefix, String capability, Double value, Date time) {
        String locationName1;
        List<String> rooms1;
        List<String> workstation1;
        String capabilityResource1;
        String y1;
        String x1;

        this.name = name;
        this.capability = capability;
        this.value = value;
        this.time = time;
        this.testbed = testbed;
        this.prefix = prefix;
        try {
            x1 = UberdustClient.getInstance().getNodeX(testbed, name);
        } catch (IOException e) {
            x1 = "0";
        }
        x = x1;

        try {
            y1 = UberdustClient.getInstance().getNodeY(testbed, name);
        } catch (IOException e) {
            y1 = "0";
        }
        y = y1;

        try {
            rooms1 = UberdustClient.getInstance().getNodeRooms(Integer.parseInt(testbed), name);
        } catch (Exception e) {
            rooms1 = null;
        }
        rooms = rooms1;

        try {
            workstation1 = UberdustClient.getInstance().getNodeWorkstations(Integer.parseInt(testbed), name);
        } catch (Exception e) {
            workstation1 = null;
        }
        workstations = workstation1;

        try {
            capabilityResource1 = getSameAs(UberdustNode.getCapabilityResourceURI(this));
        } catch (URISyntaxException e) {
            capabilityResource1 = "null";
        }
        capabilityResource = capabilityResource1;

        try {
            locationName1 = ((Testbed) UberdustClient.getInstance().getTestbedById(Integer.parseInt(testbed))).getName();
        } catch (IOException e) {
            locationName1 = prefix;
        } catch (JSONException e) {
            locationName1 = prefix;
        }
        locationName = locationName1;
    }

    @Override
    public String toString() {
        return "UberdustNode{" +
                "name='" + name + '\'' +
                ", capability='" + capability + '\'' +
                '}';
    }

    public String toRdf_XML() {
        Writer sw = new StringWriter();
        getModel().write(sw, Language.RDF_XML.lang, null);
        return sw.toString();
    }

    public String toRdf_N3() {
        Writer sw = new StringWriter();
        getModel().write(sw, Language.RDF_N3.lang, null);
        return sw.toString();
    }

    public String toRdf_TURTLE() {
        Writer sw = new StringWriter();
        getModel().write(sw, Language.RDF_N3.lang, null);
        return sw.toString();
    }

    /**
     * N3 RDF description of the node.
     *
     * @return a string containing the N3 rdf description.
     * @throws URISyntaxException should not happen.
     */
    public String toRDF() throws URISyntaxException {
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yy-MM-dd'T'HH:mm:ss'Z'");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));

        String description = "" +
                "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + ">\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#long>\n" +
                "\"" + y + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#lat>\n" +
                "\"" + x + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://purl.org/dc/terms/#date>\n" +
                "\"" + dateFormatGmt.format(time) + "\";\n" +
                "<http://www.ontologydesignpatterns.org/ont/dul/DUL.owl/hasLocation>\n" +
                "\"" + locationName + "\"";
        if ((lightZone.matcher(capability).find() || relay.matcher(capability).find()) && !name.contains("0x2b0")) {

            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>.\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>\n" +
                    "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#switch>;\n" +
                    "<http://spitfire-project.eu/ontology/ns/value>\n" +
                    "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n";

        } else if (fan.matcher(capability).find() || name.contains("0x2b0")) {
            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>.\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>\n" +
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
                description += "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + ">\n" +
                        "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest>\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/>.\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/> \n" +
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                        "<http://spitfire-project.eu/foi/Room>.";
            }
        }

        if (workstations != null) {
            for (String workstation : workstations) {
                description += "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + ">\n" +
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
    public Model getModel() {
        Model model = ModelFactory.createDefaultModel();
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(toRDF().getBytes(Charset.forName("UTF-8")));
            model.read(bin, null, Language.RDF_N3.lang);
        } catch (URISyntaxException e) {

        }
        return model;
    }

    public static String getResourceURI(final UberdustNode node) throws URISyntaxException {
        return getResourceURI(node.getTestbed(), node.getName(), node.getCapability());
    }

    public static String getResourceURI(final String testbed, final String node, final String capability) throws URISyntaxException {
//        return "http://uberdust.cti.gr/rest/testbed/" + node.getTestbed() + "/node/" + node.getName() + "/capability/" + node.getCapability() + "/";
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + testbed + "/node/" + node + "/capability/" + capability + "/", null, null).toString();
    }

    public static String getCapabilityResourceURI(final UberdustNode node) throws URISyntaxException {
//        return "http://uberdust.cti.gr/rest/testbed/" + node.getTestbed() + "/capability/" + node.getCapability() + "/rdf";
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + node.getTestbed() + "/capability/" + node.getCapability() + "/rdf", null, null).toString();
    }

    public String getName() {
        return name;
    }

    public String getCapability() {
        return capability;
    }

    public String getTestbed() {
        return testbed;
    }

    public void update(Double doubleReading, Date date) {
        this.value = doubleReading;
        this.time = date;
    }

    public static void main(String[] args) {
        UberdustClient.setUberdustURL("http://uberdust.cti.gr");
        UberdustNode node = new UberdustNode("urn:wisebed:ctitestbed:0x190", "1", "urn:wisebed:ctitestbed:", "urn:wisebed:node:capability:pir", 1.0, new Date());
        System.out.println("=========================================================================================");
        System.out.println(node.toRdf_XML());
        System.out.println("=========================================================================================");
        System.out.println(node.toRdf_N3());
        System.out.println("=========================================================================================");
        System.out.println(node.toRdf_TURTLE());
    }
}
